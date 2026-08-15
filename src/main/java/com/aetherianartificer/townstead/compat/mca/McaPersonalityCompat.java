package com.aetherianartificer.townstead.compat.mca;

import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Binary compatibility boundary for MCA's personality enum-to-registry migration.
 *
 * <p>Legacy MCA exposes a Java enum; newer MCA exposes a registry-backed class and removes the
 * enum methods. Both generations are accessed reflectively here so one Townstead source tree can
 * compile and run against either supported line without linking absent methods.</p>
 */
public final class McaPersonalityCompat {
    private static final Method GET_ID = method("getId");
    private static final Method GET_STRING = method("get", String.class);
    private static final Method ALL = method("all");
    private static final Method ENCODE_DIALOGUE_ID = method("encodeDialogueId", ResourceLocation.class);
    private static final Method IS_VALID_FOR = method("isValidFor",
            net.conczin.mca.entity.ai.relationship.AgeState.class);
    private static final Method LEGACY_NAME = method("name");
    private static final Method LEGACY_VALUE_OF = method("valueOf", String.class);
    private static final Method LEGACY_VALUES = method("values");

    private McaPersonalityCompat() {}

    /** Stable namespaced id on registry MCA; an {@code mca:lowercase_name} id on enum MCA. */
    public static String id(@Nullable Personality personality) {
        if (personality == null) return "mca:unassigned";
        Object value = invoke(GET_ID, personality);
        if (value instanceof ResourceLocation id) return id.toString();
        Object legacy = invoke(LEGACY_NAME, personality);
        return legacy instanceof String name
                ? "mca:" + name.toLowerCase(Locale.ROOT)
                : "mca:unassigned";
    }

    /** Enum-style uppercase name for legacy behavior tables. Custom registered types retain their id. */
    public static String legacyName(@Nullable Personality personality) {
        if (personality == null) return "UNASSIGNED";
        Object legacy = invoke(LEGACY_NAME, personality);
        if (legacy instanceof String name) return name;
        String id = idWithoutLegacyFallback(personality);
        if (id != null) {
            int separator = id.indexOf(':');
            return separator < 0 || id.startsWith("mca:")
                    ? id.substring(separator + 1).toUpperCase(Locale.ROOT)
                    : id;
        }
        return "UNASSIGNED";
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
        Object enumResult = invoke(LEGACY_VALUE_OF, null, legacy.toUpperCase(Locale.ROOT));
        return enumResult instanceof Personality personality ? Optional.of(personality) : Optional.empty();
    }

    /** First of {@code references} this MCA build knows, for constants that were re-cut between lines. */
    public static Optional<Personality> resolveAny(String... references) {
        for (String reference : references) {
            Optional<Personality> found = resolve(reference);
            if (found.isPresent()) return found;
        }
        return Optional.empty();
    }

    /** MCA's irritable personality: {@code crabby} on current MCA, {@code grumpy} on legacy 1.20.1. */
    public static boolean isCrabby(@Nullable Personality personality) {
        return personality != null && personality == Crabby.VALUE;
    }

    /** All personalities known to MCA. On old MCA this is simply the enum constants. */
    public static List<Personality> all() {
        Object registryResult = invoke(ALL, null);
        if (registryResult instanceof List<?> list) {
            return list.stream().filter(Personality.class::isInstance).map(Personality.class::cast).toList();
        }
        Object enumResult = invoke(LEGACY_VALUES, null);
        if (enumResult != null && enumResult.getClass().isArray()) {
            List<Personality> personalities = new ArrayList<>(Array.getLength(enumResult));
            for (int i = 0; i < Array.getLength(enumResult); i++) {
                Object value = Array.get(enumResult, i);
                if (value instanceof Personality personality) personalities.add(personality);
            }
            return List.copyOf(personalities);
        }
        return List.of();
    }

    /** Value placed after MCA's {@code #E} dialogue flag, in the format that MCA version expects. */
    public static String dialogueId(@Nullable Personality personality) {
        if (personality == null) return "UNASSIGNED";
        Object rawId = invoke(GET_ID, personality);
        if (rawId instanceof ResourceLocation id) {
            Object encoded = invoke(ENCODE_DIALOGUE_ID, null, id);
            return encoded instanceof String text ? text : id.toString().replace(".", "%2E");
        }
        return legacyName(personality);
    }

    /** Whether MCA permits this personality for the supplied age; old enum MCA had no age predicate. */
    public static boolean isValidFor(@Nullable Personality personality,
                                     @Nullable net.conczin.mca.entity.ai.relationship.AgeState age) {
        if (personality == null || age == null) return false;
        if (IS_VALID_FOR == null) return true;
        return Boolean.TRUE.equals(invoke(IS_VALID_FOR, personality, age));
    }

    /** True for MCA's own namespace, excluding personalities registered by addons. */
    public static boolean isBuiltIn(@Nullable Personality personality) {
        String id = id(personality);
        return id.regionMatches(true, 0, "mca:", 0, 4);
    }

    private static @Nullable String idWithoutLegacyFallback(Personality personality) {
        Object value = invoke(GET_ID, personality);
        return value instanceof ResourceLocation id ? id.toString() : null;
    }

    private static final class Crabby {
        private static final Personality VALUE = resolveAny("crabby", "grumpy").orElse(null);
    }

    private static @Nullable Method method(String name, Class<?>... parameterTypes) {
        try {
            return Personality.class.getMethod(name, parameterTypes);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }

    private static @Nullable Object invoke(@Nullable Method method, @Nullable Object receiver, Object... args) {
        if (method == null) return null;
        try {
            return method.invoke(Modifier.isStatic(method.getModifiers()) ? null : receiver, args);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            return null;
        }
    }
}
