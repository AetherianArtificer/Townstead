package com.aetherianartificer.townstead.compat.mca;

import net.conczin.mca.network.c2s.GetVillageRequest;
import net.minecraft.server.level.ServerPlayer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Runtime seam for MCA's floor-system builds (1.21.1 feature branch and its
 * eventual 1.20.1 backport). Pre-floor MCA leaves the client responsible for
 * requesting a village refresh after building actions; floor-system MCA
 * pushes the snapshot server-side through the static
 * {@code GetVillageRequest.sendResponse(ServerPlayer)}. Townstead compiles
 * against pre-floor jars, so the method is resolved reflectively and its
 * presence doubles as the floor-system detection signal.
 */
public final class McaFloorCompat {
    private static final MethodHandle SEND_RESPONSE = resolveSendResponse();

    private McaFloorCompat() {}

    /** True when the installed MCA build carries the floor system. */
    public static boolean hasFloorSystem() {
        return SEND_RESPONSE != null;
    }

    /**
     * Push MCA's village snapshot (and rank/reputation state) to the player.
     * No-op on pre-floor MCA, where the client requests its own refresh.
     */
    public static void pushVillageResponse(ServerPlayer player) {
        if (SEND_RESPONSE == null) return;
        try {
            SEND_RESPONSE.invokeExact(player);
        } catch (Throwable ignored) {
            // Resolution succeeded, so a throw here is MCA's own send failing;
            // the client just misses one refresh.
        }
    }

    private static MethodHandle resolveSendResponse() {
        try {
            // getDeclaredMethod + setAccessible: the method was public on the
            // first floor-system builds but is package-private on newer ones.
            Method method = GetVillageRequest.class.getDeclaredMethod("sendResponse", ServerPlayer.class);
            method.setAccessible(true);
            return MethodHandles.lookup().unreflect(method);
        } catch (Throwable t) {
            return null;
        }
    }
}
