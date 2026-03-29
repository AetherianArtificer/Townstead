package com.aetherianartificer.townstead.fatigue;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public final class RestDebugData {
    private static final String KEY_REST_OVERRIDE_ACTIVE = "restOverrideActive";
    private static final String KEY_REST_OVERRIDE_REASON = "restOverrideReason";
    private static final String KEY_REST_DEBUG_REASON = "restDebugReason";
    private static final String KEY_REST_DEBUG_BLOCK = "restDebugBlock";
    private static final String KEY_REST_DEBUG_TARGET = "restDebugTarget";

    private RestDebugData() {}

    public static boolean isRestOverrideActive(CompoundTag tag) {
        return tag.getBoolean(KEY_REST_OVERRIDE_ACTIVE);
    }

    public static void setRestOverride(CompoundTag tag, boolean active, SleepReason reason) {
        tag.putBoolean(KEY_REST_OVERRIDE_ACTIVE, active);
        tag.putString(KEY_REST_OVERRIDE_REASON, active ? reason.id() : SleepReason.NONE.id());
    }

    public static SleepReason getRestOverrideReason(CompoundTag tag) {
        return SleepReason.fromId(tag.getString(KEY_REST_OVERRIDE_REASON));
    }

    public static void setRestDebugDecision(CompoundTag tag, SleepReason reason, SleepBlockReason blockReason, BlockPos targetBed) {
        tag.putString(KEY_REST_DEBUG_REASON, reason.id());
        tag.putString(KEY_REST_DEBUG_BLOCK, blockReason.id());
        if (targetBed != null) {
            tag.putLong(KEY_REST_DEBUG_TARGET, targetBed.asLong());
        }
    }

    public static String getRestDebugReasonId(CompoundTag tag) {
        String value = tag.getString(KEY_REST_DEBUG_REASON);
        return value.isEmpty() ? SleepReason.NONE.id() : value;
    }

    public static String getRestDebugBlockId(CompoundTag tag) {
        String value = tag.getString(KEY_REST_DEBUG_BLOCK);
        return value.isEmpty() ? SleepBlockReason.NONE.id() : value;
    }

    public static long getRestDebugTargetBed(CompoundTag tag) {
        return tag.contains(KEY_REST_DEBUG_TARGET) ? tag.getLong(KEY_REST_DEBUG_TARGET) : Long.MIN_VALUE;
    }
}
