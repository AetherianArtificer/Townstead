package com.aetherianartificer.townstead.compat.mca;

import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Binary compatibility boundary for MCA's personality enum-to-registry migration.
 *
 * <p>MCA 7.6/7.7 exposes a Java enum. Newer MCA exposes a registry-backed class while retaining
 * most enum-shaped methods. Calls to new methods must remain reflective so the same Townstead jar
 * can still load with an older MCA.</p>
 */
public final class McaPersonalityCompat {
    private static final Method GET_ID = method("getId");
    private static final Method GET_STRING = method("get", String.class);
    private static final Method ALL = method("all");
    private static final Method ENCODE_DIALOGUE_ID = method("encodeDialogueId", ResourceLocation.class);

    private McaPersonalityCompat() {}

    /** Stable namespaced id on registry MCA; an {@code mca:lowercase_name} id on enum MCA. */
    public static String id(@Nullable Personality personality) {
        if (personality == null) return "mca:unassigned";
        Object value = invoke(GET_ID, personality);
        if (value instanceof ResourceLocation id) return id.toString();
        return "mca:" + legacyName(personality).toLowerCase(Locale.ROOT);
    }

    /** Enum-style uppercase name for legacy behavior tables. Custom registered types retain their id. */
    public static String legacyName(@Nullable Personality personality) {
        if (personality == null) return "UNASSIGNED";
        String id = idWithoutLegacyFallback(personality);
        if (id != null) {
            int separator = id.indexOf(':');
            return separator < 0 || id.startsWith("mca:")
                    ? id.substring(separator + 1).toUpperCase(Locale.ROOT)
                    : id;
        }
        return personality.name();
    }

    /** Resolve either a legacy enum name or a namespaced registry id. */
    public static Optional<Personality> resolve(@Nullable String reference) {
        if (reference == null || reference.isBlank()) return Optional.empty();
        Object registryResult = invoke(GET_STRING, null, reference.trim());
        if (registryResult instanceof Optional<?> optional) {
            Object value = optional.orElse(null);
            if (value instanceof Personality personality) return Optional.of(personality);
        }
        String legacy = reference.trim();
        int separator = legacy.indexOf(':');
        if (separator >= 0) {
            if (!legacy.regionMatches(true, 0, "mca", 0, 3)) return Optional.empty();
            legacy = legacy.substring(separator + 1);
        }
        try {
            return Optional.of(Personality.valueOf(legacy.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /** All personalities known to MCA. On old MCA this is simply the enum constants. */
    public static List<Personality> all() {
        Object registryResult = invoke(ALL, null);
        if (registryResult instanceof List<?> list) {
            return list.stream().filter(Personality.class::isInstance).map(Personality.class::cast).toList();
        }
        return Arrays.asList(Personality.values());
    }

    /** Value placed after MCA's {@code #E} dialogue flag, in the format that MCA version expects. */
    public static String dialogueId(@Nullable Personality personality) {
        if (personality == null) return "UNASSIGNED";
        Object rawId = invoke(GET_ID, personality);
        if (rawId instanceof ResourceLocation id) {
            Object encoded = invoke(ENCODE_DIALOGUE_ID, null, id);
            return encoded instanceof String text ? text : id.toString().replace(".", "%2E");
        }
        return personality.name();
    }

    private static @Nullable String idWithoutLegacyFallback(Personality personality) {
        Object value = invoke(GET_ID, personality);
        return value instanceof ResourceLocation id ? id.toString() : null;
    }

    private static @Nullable Method method(String name, Class<?>... parameterTypes) {
        try {
            return Personality.class.getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static @Nullable Object invoke(@Nullable Method method, @Nullable Object receiver, Object... args) {
        if (method == null) return null;
        try {
            return method.invoke(Modifier.isStatic(method.getModifiers()) ? null : receiver, args);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }
}
