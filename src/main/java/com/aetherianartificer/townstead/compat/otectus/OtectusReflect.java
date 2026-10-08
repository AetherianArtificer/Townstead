package com.aetherianartificer.townstead.compat.otectus;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Name-based access to the Otectus mods' API types. Nothing here names one of their classes in
 * bytecode, so Townstead loads and links the same whether or not they are installed, and a
 * renamed member degrades to "no value" rather than a linkage error.
 */
final class OtectusReflect {
    private static final Map<String, Optional<Class<?>>> CLASSES = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private OtectusReflect() {}

    static @Nullable Class<?> type(String name) {
        return CLASSES.computeIfAbsent(name, n -> {
            try {
                return Optional.of(Class.forName(n, false, OtectusReflect.class.getClassLoader()));
            } catch (Throwable t) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** Invokes a public no-argument method by name; null when absent or throwing. */
    static @Nullable Object call(@Nullable Object target, String method) {
        if (target == null) return null;
        Method m = method(target.getClass(), method, 0);
        if (m == null) return null;
        try {
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Invokes a public method by name and arity with the given arguments; null when absent or throwing. */
    static @Nullable Object call(@Nullable Object target, String method, Object... args) {
        if (target == null) return null;
        Method m = method(target.getClass(), method, args.length);
        if (m == null) return null;
        try {
            return m.invoke(target, args);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Invokes a public static method by exact parameter types; null when absent or throwing. */
    static @Nullable Object callStatic(@Nullable Class<?> owner, String method, Class<?>[] types, Object... args) {
        if (owner == null) return null;
        try {
            Method m = owner.getMethod(method, types);
            return m.invoke(null, args);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Invokes a public static method by name and arity; null when absent or throwing. */
    static @Nullable Object callStatic(@Nullable Class<?> owner, String method, Object... args) {
        if (owner == null) return null;
        Method m = method(owner, method, args.length);
        if (m == null) return null;
        try {
            return m.invoke(null, args);
        } catch (Throwable t) {
            return null;
        }
    }

    static String string(@Nullable Object value) {
        if (value == null) return "";
        if (value instanceof Optional<?> optional) return optional.map(String::valueOf).orElse("");
        if (value instanceof Enum<?> e) return e.name().toLowerCase(java.util.Locale.ROOT);
        return String.valueOf(value);
    }

    static @Nullable Object unwrap(@Nullable Object value) {
        if (value instanceof Optional<?> optional) return optional.orElse(null);
        return value;
    }

    private static @Nullable Method method(Class<?> owner, String name, int arity) {
        String key = owner.getName() + "#" + name + "/" + arity;
        return METHODS.computeIfAbsent(key, k -> {
            for (Method candidate : owner.getMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == arity) {
                    candidate.setAccessible(true);
                    return Optional.of(candidate);
                }
            }
            return Optional.empty();
        }).orElse(null);
    }
}
