package com.aetherianartificer.townstead.compat.mca;

import net.conczin.mca.entity.ai.Traits;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Binary compatibility boundary for MCA's trait registry migration. */
public final class McaTraitCompat {
    private static final Method GET_STRING = method(Traits.class, "get", String.class);
    private static final Method GET_ID = method(Traits.Trait.class, "getId");
    private static final Method REGISTER = method(Traits.class, "register",
            ResourceLocation.class, float.class, float.class, boolean.class);
    private static final Method LEGACY_REGISTER = method(Traits.class, "registerTrait",
            String.class, float.class, float.class, boolean.class);
    private static final Method LEGACY_VALUE_OF = method(Traits.Trait.class, "valueOf", String.class);
    private static final Method LEGACY_ID = method(Traits.Trait.class, "id");

    private McaTraitCompat() {}

    /** Resolve either a legacy trait name or a namespaced registry id. */
    public static Optional<Traits.Trait> resolve(@Nullable String reference) {
        if (reference == null || reference.isBlank()) return Optional.empty();
        String normalized = reference.trim();
        String canonical = canonical(normalized);
        if (canonical == null) return Optional.empty();

        // Registry MCA uses lowercase namespaced ids. Trying candidates does not
        // register aliases: every successful lookup returns the one canonical entry.
        Set<String> registryReferences = new LinkedHashSet<>();
        registryReferences.add(normalized);
        registryReferences.add(canonical);
        if (canonical.indexOf(':') < 0) registryReferences.add("mca:" + canonical);
        for (String candidate : registryReferences) {
            Traits.Trait trait = asTrait(invoke(GET_STRING, null, candidate));
            if (matches(normalized, trait)) return Optional.of(trait);
        }

        // Old NeoForge is case-exact and stores lowercase ids; old Forge uppercases
        // internally. Probe both forms, but reject its UNKNOWN sentinel by id.
        Set<String> legacyReferences = new LinkedHashSet<>();
        legacyReferences.add(normalized);
        legacyReferences.add(canonical);
        legacyReferences.add(canonical.toUpperCase(Locale.ROOT));
        for (String candidate : legacyReferences) {
            Traits.Trait trait = asTrait(invoke(LEGACY_VALUE_OF, null, candidate));
            if (matches(normalized, trait)) return Optional.of(trait);
        }
        return Optional.empty();
    }

    /** Stable id on registry MCA; the legacy unnamespaced id on older MCA. */
    public static @Nullable String id(@Nullable Traits.Trait trait) {
        if (trait == null) return null;
        Object registryId = invoke(GET_ID, trait);
        if (registryId instanceof ResourceLocation id) return id.toString();
        Object legacyId = invoke(LEGACY_ID, trait);
        return legacyId instanceof String id ? id : null;
    }

    /**
     * Register a trait on either MCA registry generation. Registration is idempotent: datapack reloads
     * and an integrated server's client catalog reuse the existing immutable MCA entry.
     */
    public static Optional<Traits.Trait> register(@Nullable String reference, float chance, float inherit,
                                                  boolean usableOnPlayer) {
        String canonical = canonical(reference);
        if (canonical == null) return Optional.empty();
        Optional<Traits.Trait> existing = resolve(canonical);
        if (existing.isPresent()) return existing;

        ResourceLocation id = ResourceLocation.tryParse(canonical);
        Object registered = id == null ? null : invoke(REGISTER, null, id, chance, inherit, usableOnPlayer);
        if (!(registered instanceof Traits.Trait)) {
            registered = invoke(LEGACY_REGISTER, null, canonical, chance, inherit, usableOnPlayer);
        }
        if (!(registered instanceof Traits.Trait trait)) return Optional.empty();

        rekeyLegacyRegistry(canonical, trait);
        return Optional.of(trait);
    }

    /** Package-private contract probes used without initializing MCA's mod-loader-bound Traits class. */
    static @Nullable Class<?> registryLookupOwner() {
        return GET_STRING == null ? null : GET_STRING.getDeclaringClass();
    }

    static @Nullable Class<?> registryRegistrationOwner() {
        return REGISTER == null ? null : REGISTER.getDeclaringClass();
    }

    private static @Nullable Traits.Trait asTrait(@Nullable Object result) {
        if (result instanceof Optional<?> optional) result = optional.orElse(null);
        return result instanceof Traits.Trait trait ? trait : null;
    }

    private static boolean matches(String reference, @Nullable Traits.Trait trait) {
        String actual = id(trait);
        String expected = canonical(reference);
        String found = actual == null ? null : canonical(actual);
        if (expected == null || found == null) return false;
        if (expected.indexOf(':') >= 0) return expected.equals(found);
        return expected.equals(found) || ("mca:" + expected).equals(found);
    }

    private static @Nullable String canonical(@Nullable String reference) {
        if (reference == null) return null;
        String value = reference.trim();
        return value.isBlank() ? null : value.toLowerCase(Locale.ROOT);
    }

    /** Old Forge uppercases valueOf lookups although addon traits were registered under lowercase ids. */
    @SuppressWarnings("unchecked")
    private static void rekeyLegacyRegistry(String id, Traits.Trait trait) {
        if (resolve(id).orElse(null) == trait) return;
        try {
            Field field = Traits.class.getField("TRAIT_REGISTRY");
            Object value = field.get(null);
            if (!(value instanceof Map<?, ?> raw)) return;
            Map<Object, Object> registry = (Map<Object, Object>) raw;
            registry.remove(id);
            registry.put(id.toUpperCase(Locale.ROOT), trait);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ignored) {
            // Registry MCA has no public map and does not need this legacy repair.
        }
    }

    private static @Nullable Method method(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
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
