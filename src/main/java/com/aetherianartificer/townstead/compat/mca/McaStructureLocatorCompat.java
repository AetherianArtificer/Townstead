package com.aetherianartificer.townstead.compat.mca;

import java.lang.reflect.Method;
import java.util.UUID;

/** MCA builds with the async structure locator; older builds search on a plain executor and have nothing to cancel. */
public final class McaStructureLocatorCompat {
    private static final Method GET_LOCATOR = locatorMethod("getStructureLocator");
    private static final Method CANCEL = GET_LOCATOR == null ? null
            : method(GET_LOCATOR.getReturnType(), "cancel", UUID.class);

    private McaStructureLocatorCompat() {}

    /** Drops a pending structure search owned by this player or villager. */
    public static void cancel(UUID owner) {
        if (CANCEL == null) return;
        try {
            CANCEL.invoke(GET_LOCATOR.invoke(null), owner);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Method locatorMethod(String name) {
        try {
            return method(Class.forName("net.conczin.mca.MCA"), name);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException | LinkageError e) {
            return null;
        }
    }
}
