package com.aetherianartificer.townstead.compat.farmersdelight.cook;

import com.aetherianartificer.townstead.compat.farmersdelight.cook.ModRecipeRegistry.DiscoveredRecipe;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named code primitives for protocol workstations ({@code passive_station} /
 * {@code place_surface} defs), referenced from data by {@code "adapter"} — the same move as
 * pheno power types: data composes stations, adapters supply the few operations a foreign
 * block only exposes through mod-specific methods. Every operation must act the way a player
 * would: real items leave the villager's inventory into the block, real items come out of the
 * block into the villager's hands or the world. No conjuring.
 *
 * <p>The {@code townstead:item_handler} default drives any block whose insert/extract surface
 * is a plain item capability; a def needs a custom adapter only when the block's interactions
 * are method-bound (Pizza Delight's basin) or its harvest is (the pizza peel lift).</p>
 */
public final class StationAdapters {

    /** Where a protocol station is in its cycle. */
    public enum StationPhase { IDLE, WORKING, READY, FOREIGN }

    public interface Adapter {

        /** Classify the station's current contents/state. */
        StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def, @Nullable DiscoveredRecipe recipe);

        /**
         * Move the recipe's inputs from the villager's inventory into the station, exactly as
         * a player's interaction would (containers returned, items shrunk). Returns false if
         * the station refused; callers roll staged items back to storage.
         */
        boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                       WorkstationDef def, DiscoveredRecipe recipe);

        /**
         * Move the finished product out of the station into the villager's inventory or the
         * world at the station (drops are picked up by the collect sweep). Returns true when
         * something was retrieved.
         */
        boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                        WorkstationDef def, DiscoveredRecipe recipe);
    }

    public static final String DEFAULT_ITEM_HANDLER = "townstead:item_handler";

    private static final Map<String, Adapter> ADAPTERS = new ConcurrentHashMap<>();

    private StationAdapters() {}

    public static void register(String name, Adapter adapter) {
        if (name == null || adapter == null) return;
        ADAPTERS.put(name.toLowerCase(Locale.ROOT), adapter);
    }

    @Nullable
    public static Adapter byName(@Nullable String name) {
        if (name == null || name.isBlank()) return ADAPTERS.get(DEFAULT_ITEM_HANDLER);
        return ADAPTERS.get(name.toLowerCase(Locale.ROOT));
    }

    @Nullable
    public static Adapter forDef(@Nullable WorkstationDef def) {
        if (def == null) return null;
        return byName(def.adapter());
    }
}
