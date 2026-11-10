package com.aetherianartificer.townstead.sync;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Data a reload listener loads exists only on the server. Single player shares one JVM, so a client
 * read of that store works there and comes back empty on a dedicated server. Client code must read a
 * synced client store instead. This covers direct calls only, not reads through a shared helper.
 */
class ClientServerDataBoundaryTest {
    private static final String ROOT = "com/aetherianartificer/townstead/";
    private static final Set<String> LISTENER_TYPES = Set.of(
            "net/minecraft/server/packs/resources/SimpleJsonResourceReloadListener",
            "net/minecraft/server/packs/resources/SimplePreparableReloadListener",
            "net/minecraft/server/packs/resources/ResourceManagerReloadListener",
            "net/minecraft/server/packs/resources/PreparableReloadListener");
    private static final Pattern MUTATOR = Pattern.compile("(replace|set|load|install|put|register|reload).*");

    /** Stores a client method may call, each with the reason it is safe. */
    private static final Map<String, String> REVIEWED = Map.of(
            ROOT + "data/DataPackLang", "parseId is a pure string parse",
            ROOT + "pheno/particle/DataParticles", "the client side is filled by DataParticlesS2CPayload",
            ROOT + "block/haze/HazeKinds", "HazeClient uses the separate client store, filled by HazeKindsSyncPayload",
            ROOT + "root/rig/RigRegistry", "resolve is pure and is passed the client's own map",
            ROOT + "root/trait/TraitBridge", "the client registers traits from the synced catalog",
            ROOT + "root/trait/TraitJsonLoader", "the client enables traits from the synced catalog");

    /** Single calls allowed by name, each with the reason. */
    private static final Map<String, String> REVIEWED_CALLS = Map.of(
            "RigHitboxes.speciesScale calls RootRegistry.effectiveSpecies", "the client branch returns first",
            "RigHitboxes.speciesScale calls SpeciesRegistry.byId", "the client branch returns first");

    @Test
    void clientCodeReadsNoServerOnlyStore() throws IOException {
        Map<String, ClassNode> classes = readClasses();
        assertFalse(classes.isEmpty(), "no compiled classes found; set townstead.classes to the main output");
        Set<String> stores = serverStores(classes);
        assertFalse(stores.isEmpty(), "found no stores filled by a reload listener");

        Set<String> violations = new TreeSet<>();
        for (ClassNode node : classes.values()) {
            for (MethodNode method : node.methods) {
                if (!runsOnClient(node, method)) continue;
                for (AbstractInsnNode insn : method.instructions) {
                    if (!(insn instanceof MethodInsnNode call) || !stores.contains(call.owner)) continue;
                    if (call.name.equals("<init>")) continue;
                    if (call.owner.equals(node.name) || REVIEWED.containsKey(call.owner)) continue;
                    String line = simple(node.name) + "." + method.name + " calls " + simple(call.owner) + "." + call.name;
                    if (!REVIEWED_CALLS.containsKey(line)) violations.add(line);
                }
            }
        }
        assertTrue(violations.isEmpty(), "client code reads server-only data:\n" + String.join("\n", violations));
    }

    /** Classes a reload listener writes to, plus any listener that keeps its own static data. */
    private static Set<String> serverStores(Map<String, ClassNode> classes) {
        Set<String> stores = new HashSet<>();
        for (ClassNode node : classes.values()) {
            if (!isListener(node, classes) || isClientPackage(node.name)) continue;
            String host = node.name.contains("$") ? node.name.substring(0, node.name.indexOf('$')) : node.name;
            stores.add(host);
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode insn : method.instructions) {
                    if (insn instanceof MethodInsnNode call && call.owner.startsWith(ROOT) && !isClientPackage(call.owner)
                            && call.getOpcode() == Opcodes.INVOKESTATIC && MUTATOR.matcher(call.name).matches()) {
                        stores.add(call.owner);
                    }
                }
            }
        }
        return stores;
    }

    private static boolean isListener(ClassNode node, Map<String, ClassNode> classes) {
        for (ClassNode current = node; current != null; current = classes.get(current.superName)) {
            if (LISTENER_TYPES.contains(current.superName)) return true;
            for (String face : current.interfaces) {
                if (LISTENER_TYPES.contains(face)) return true;
            }
        }
        return false;
    }

    private static boolean runsOnClient(ClassNode node, MethodNode method) {
        if (node.name.equals(ROOT + "Townstead") || node.name.equals(ROOT + "TownsteadNetwork")) return false;
        if (simple(node.name).contains("Payload")) return false;
        if (isClientPackage(node.name) || simple(node.name).startsWith("Client")) return true;
        for (AbstractInsnNode insn : method.instructions) {
            String owner = null;
            if (insn instanceof MethodInsnNode call) owner = call.owner;
            else if (insn instanceof FieldInsnNode field) owner = field.owner;
            else if (insn instanceof TypeInsnNode type) owner = type.desc;
            if (owner != null && (owner.startsWith("net/minecraft/client/") || isClientPackage(owner))) return true;
        }
        return Type.getMethodDescriptor(Type.getReturnType(method.desc), Type.getArgumentTypes(method.desc))
                .contains("net/minecraft/client/");
    }

    private static boolean isClientPackage(String name) {
        return name.startsWith(ROOT + "client/") || name.startsWith(ROOT + "mixin/client/");
    }

    private static String simple(String name) {
        return name.substring(name.lastIndexOf('/') + 1);
    }

    private static Map<String, ClassNode> readClasses() throws IOException {
        Map<String, ClassNode> classes = new HashMap<>();
        List<Path> files = new ArrayList<>();
        for (String root : System.getProperty("townstead.classes", "").split(java.io.File.pathSeparator)) {
            if (root.isBlank() || !Files.isDirectory(Path.of(root))) continue;
            try (Stream<Path> walk = Files.walk(Path.of(root))) {
                walk.filter(path -> path.toString().endsWith(".class")).forEach(files::add);
            }
        }
        for (Path file : files) {
            ClassNode node = new ClassNode();
            new ClassReader(Files.readAllBytes(file)).accept(node, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);
            classes.put(node.name, node);
        }
        return classes;
    }
}
