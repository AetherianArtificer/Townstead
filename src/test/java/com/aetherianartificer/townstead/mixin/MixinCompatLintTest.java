package com.aetherianartificer.townstead.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps Townstead's mixins from locking other mods out of shared code. An exclusive injector
 * (@ModifyConstant, @Redirect, @ModifyArgs, @Overwrite) takes its target alone, so another mod's
 * injector on the same spot fails, and a mod that requires it crashes the game. A member merged into
 * the target under a plain name can collide with another mod's. A {@code require} of our own turns
 * another mod's change to a target into a crash; {@code expect} only drops the feature. Reads the
 * compiled mixins, so each version's test run checks its own branch.
 */
class MixinCompatLintTest {
    private static final String CONFIG = "townstead.mixins.json";
    private static final int DEFAULT_PRIORITY = 1000;
    private static final Set<String> EXCLUSIVE = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/Overwrite;");
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final List<String> MIXIN_ANNOTATIONS = List.of(
            "Lorg/spongepowered/asm/mixin/", "Lcom/llamalad7/mixinextras/");
    private static final String PREFIX = "townstead$";

    private static final String LSO = "receiver is coerced to Object (LSO is not on the classpath), which a "
            + "@WrapOperation handler may not accept; require = 0 inside one LSO-internal method";
    private static final String MCA_DEFAULT = "MCA declares it only as an interface default, with interface "
            + "defaults as its callers, so there is no body to inject into";

    /**
     * Exceptions kept on purpose, as {@code SimpleClassName#method} with the reason. Each needs one:
     * prefer @WrapOperation, @ModifyExpressionValue or @Inject, or a screen event over an override.
     */
    private static final Map<String, String> ALLOWED = Map.of(
            "LsoRoomQueryMixin#townstead$player", LSO,
            "LsoRoomQueryMixin#townstead$resistance", LSO,
            "LsoRoomQueryMixin#townstead$worldResistance", LSO,
            "LsoRoomQueryMixin#townstead$environment", LSO,
            "MessengerVoiceMixin#getTranslatable", MCA_DEFAULT,
            "MessengerVoiceMixin#isToYoungToSpeak", MCA_DEFAULT);

    @Test
    void mixinsShareTheirTargets() throws Exception {
        List<String> problems = new ArrayList<>();
        JsonObject config = read(CONFIG);
        String pkg = config.get("package").getAsString();
        if (config.has("injectors") && config.getAsJsonObject("injectors").has("defaultRequire")
                && config.getAsJsonObject("injectors").get("defaultRequire").getAsInt() > 0) {
            problems.add(CONFIG + ": injectors.defaultRequire above 0 crashes the game whenever another mod changes a target");
        }
        int checked = 0;
        for (String list : List.of("mixins", "client", "server")) {
            if (!config.has(list)) continue;
            for (JsonElement entry : config.getAsJsonArray(list)) {
                ClassNode mixin = classNode(pkg + "." + entry.getAsString());
                if (mixin == null) continue;
                checked++;
                check(mixin, problems);
            }
        }
        assertTrue(checked > 100, "Only " + checked + " mixin classes found; the lint read the wrong config");
        assertTrue(problems.isEmpty(), problems.size() + " mixin compatibility problem(s):\n  "
                + String.join("\n  ", problems));
    }

    private static void check(ClassNode mixin, List<String> problems) {
        // Accessor and invoker interfaces add nothing that can collide.
        if ((mixin.access & Opcodes.ACC_INTERFACE) != 0) return;
        String simple = mixin.name.substring(mixin.name.lastIndexOf('/') + 1);
        boolean yields = priority(mixin) < DEFAULT_PRIORITY;
        for (MethodNode method : mixin.methods) {
            String id = simple + "#" + method.name;
            for (AnnotationNode annotation : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                if (EXCLUSIVE.contains(annotation.desc) && !ALLOWED.containsKey(id)) {
                    problems.add(id + ": " + shortName(annotation.desc)
                            + " locks other mods out of this target; use @WrapOperation, @ModifyExpressionValue or @Inject");
                }
                Object require = value(annotation, "require");
                if (require instanceof Integer count && count > 0 && !ALLOWED.containsKey(id)) {
                    problems.add(id + ": require = " + count + " crashes the game when another mod changes this "
                            + "target; use expect = " + count + " so a miss only drops this feature");
                }
            }
            if (method.name.startsWith("<") || (method.access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE)) != 0) continue;
            if (hasMixinAnnotation(method.visibleAnnotations, method.invisibleAnnotations)) continue;
            if (method.name.toLowerCase().startsWith(PREFIX) || yields || ALLOWED.containsKey(id)) continue;
            problems.add(id + ": merged into the target under a plain name; mark it @Unique, prefix it "
                    + PREFIX + ", or, for an override the target only inherits, give the mixin priority below "
                    + DEFAULT_PRIORITY + " so other mods' overrides win");
        }
        for (FieldNode field : mixin.fields) {
            if ((field.access & Opcodes.ACC_SYNTHETIC) != 0) continue;
            if (hasMixinAnnotation(field.visibleAnnotations, field.invisibleAnnotations)) continue;
            if (field.name.toLowerCase().startsWith(PREFIX)) continue;
            problems.add(simple + "." + field.name + ": field merged under a plain name; mark it @Unique or prefix it " + PREFIX);
        }
    }

    private static int priority(ClassNode mixin) {
        for (AnnotationNode annotation : annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations)) {
            if (MIXIN.equals(annotation.desc) && value(annotation, "priority") instanceof Integer priority) return priority;
        }
        return DEFAULT_PRIORITY;
    }

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values == null) return null;
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            if (name.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        }
        return null;
    }

    private static boolean hasMixinAnnotation(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        for (AnnotationNode annotation : annotations(visible, invisible)) {
            for (String prefix : MIXIN_ANNOTATIONS) if (annotation.desc.startsWith(prefix)) return true;
        }
        return false;
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> out = new ArrayList<>();
        if (visible != null) out.addAll(visible);
        if (invisible != null) out.addAll(invisible);
        return out;
    }

    private static String shortName(String desc) {
        return "@" + desc.substring(desc.lastIndexOf('/') + 1, desc.length() - 1);
    }

    private static JsonObject read(String resource) throws Exception {
        try (InputStream stream = MixinCompatLintTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertTrue(stream != null, resource + " is not on the test classpath");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static ClassNode classNode(String name) throws Exception {
        try (InputStream stream = MixinCompatLintTest.class.getClassLoader()
                .getResourceAsStream(name.replace('.', '/') + ".class")) {
            if (stream == null) return null;
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, ClassReader.SKIP_CODE);
            return node;
        }
    }
}
