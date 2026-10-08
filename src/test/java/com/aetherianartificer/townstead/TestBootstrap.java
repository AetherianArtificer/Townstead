package com.aetherianartificer.townstead;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Lets the vanilla registry holders initialize inside unit tests.
 *
 * <p>{@code Bootstrap.bootStrap()} cannot run here: the test source set shadows {@code net.minecraft.core.BlockPos}
 * with a stub, so the block bootstrap fails on a missing method. Marking the bootstrap flag as done is enough for
 * {@code MappedRegistry} to construct, which is all that a class touching {@code Registries} needs.
 *
 * <p>The registries must then initialize from {@code BuiltInRegistries}, not from {@code Registries}. On 1.20.1 the
 * two classes initialize each other, so the first one touched wins: entering through {@code Registries} leaves its
 * keys null while {@code BuiltInRegistries} reads them.
 *
 * <p>The registries stay empty, so a test must not depend on their contents.
 */
public final class TestBootstrap {
    private static boolean done;

    private TestBootstrap() {}

    public static synchronized void ensure() {
        if (done) return;
        done = true;
        for (Field field : net.minecraft.server.Bootstrap.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != boolean.class) continue;
            try {
                field.setAccessible(true);
                field.setBoolean(null, true);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // A mapping without this flag means the registries initialize on their own.
            }
        }
        try {
            Class.forName(net.minecraft.core.registries.BuiltInRegistries.class.getName(), true,
                    TestBootstrap.class.getClassLoader());
        } catch (Throwable ignored) {
            // Let the test report the failure it actually cares about instead of this one.
        }
    }
}
