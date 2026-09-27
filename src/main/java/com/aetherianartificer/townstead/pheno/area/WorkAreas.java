package com.aetherianartificer.townstead.pheno.area;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Blocks that mark out a working area, such as a Field Post and the ground it plans. Each kind
 * registers how to tell whether a position lies inside one of its areas, so
 * {@code pheno:in_work_area} can ask without knowing any of them.
 */
public final class WorkAreas {
    private WorkAreas() {}

    @FunctionalInterface
    public interface Kind {
        boolean covers(ServerLevel level, BlockPos pos);
    }

    private static final Map<ResourceLocation, Kind> KINDS = new ConcurrentHashMap<>();

    public static void register(ResourceLocation id, Kind kind) {
        KINDS.put(id, kind);
    }

    public static boolean isKnown(ResourceLocation id) {
        return KINDS.containsKey(id);
    }

    /** Whether {@code pos} is inside an area of that kind, or of any kind when {@code id} is null. */
    public static boolean covers(ServerLevel level, BlockPos pos, ResourceLocation id) {
        if (id != null) {
            Kind kind = KINDS.get(id);
            return kind != null && kind.covers(level, pos);
        }
        for (Kind kind : KINDS.values()) {
            if (kind.covers(level, pos)) return true;
        }
        return false;
    }
}
