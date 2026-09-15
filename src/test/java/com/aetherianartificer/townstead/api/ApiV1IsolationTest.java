package com.aetherianartificer.townstead.api;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The public API contract: nothing under {@code api/v1} may reference an MCA type, a loader type,
 * or a Townstead internal. A consumer compiled or reflected against {@code api.v1} must never be
 * linked to an MCA package layout, which is the whole reason the package exists.
 */
class ApiV1IsolationTest {
    private static final String API_PREFIX = "com/aetherianartificer/townstead/api/v1/";
    private static final String[] FORBIDDEN_PREFIXES = {
            "net/conczin/", "forge/net/mca/", "net/mca/",
            "net/neoforged/", "net/minecraftforge/",
            "org/spongepowered/",
    };

    @Test
    void apiV1NamesOnlyJavaMinecraftAndItself() throws IOException {
        List<Path> classFiles = apiClassFiles();
        assertFalse(classFiles.isEmpty(), "no compiled api/v1 classes found; set townstead.classes to the main output");
        List<String> violations = new ArrayList<>();
        for (Path file : classFiles) {
            ClassNode node = new ClassNode();
            new ClassReader(Files.readAllBytes(file)).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_FRAMES);
            for (String referenced : referencedClasses(file)) {
                if (allowed(referenced)) continue;
                violations.add(node.name + " -> " + referenced);
            }
        }
        assertTrue(violations.isEmpty(), "api/v1 leaks: " + String.join("\n", violations));
    }

    private static boolean allowed(String internalName) {
        if (internalName.startsWith("java/") || internalName.startsWith("javax/")) return true;
        if (internalName.startsWith("net/minecraft/")) return true;
        if (internalName.startsWith(API_PREFIX)) return true;
        for (String forbidden : FORBIDDEN_PREFIXES) {
            if (internalName.startsWith(forbidden)) return false;
        }
        // Anything else under Townstead is an internal leak; anything else at all is unexpected.
        return false;
    }

    /** Every class named in the constant pool, read the way the JVM would link it. */
    private static List<String> referencedClasses(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        ClassReader reader = new ClassReader(bytes);
        List<String> out = new ArrayList<>();
        char[] buffer = new char[reader.getMaxStringLength()];
        for (int i = 1; i < reader.getItemCount(); i++) {
            int offset = reader.getItem(i);
            if (offset == 0) continue;
            int tag = bytes[offset - 1];
            if (tag == 7) { // CONSTANT_Class
                String name = reader.readUTF8(offset, buffer);
                if (name.startsWith("[")) {
                    int at = name.indexOf('L');
                    if (at < 0) continue;
                    name = name.substring(at + 1, name.length() - 1);
                }
                out.add(name);
            }
        }
        return out;
    }

    private static List<Path> apiClassFiles() throws IOException {
        String property = System.getProperty("townstead.classes", "");
        List<Path> out = new ArrayList<>();
        for (String root : property.split(java.io.File.pathSeparator)) {
            if (root.isBlank()) continue;
            Path dir = Path.of(root).resolve(API_PREFIX);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(p -> p.toString().endsWith(".class")).forEach(out::add);
            }
        }
        return out;
    }

    @SuppressWarnings("unused")
    private static final int ASM = Opcodes.ASM9;
}
