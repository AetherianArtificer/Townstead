package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.recipe.DiscoveredRecipe;
import com.aetherianartificer.townstead.work.recipe.StationType;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
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
    public enum StationPhase { IDLE, WORKING, READY, INVALID_CONTENTS, FOREIGN }

    public interface Adapter {

        /** Whether this physical station can perform this recipe. */
        default boolean supports(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                 DiscoveredRecipe recipe) {
            return StationProtocols.defOwnsRecipe(def, recipe);
        }

        /** Optional special-cycle support for water purification. */
        default boolean supportsPurification(ServerLevel level, BlockPos anchor, WorkstationDef def) {
            return false;
        }

        /** Insert impure water for the optional purification cycle. */
        default boolean insertPurification(ServerLevel level, VillagerEntityMCA villager,
                                           BlockPos anchor, WorkstationDef def,
                                           ThirstCompatBridge bridge) {
            return false;
        }

        /** Dynamic free-job count, or a negative value when the generic capacity rules should answer. */
        default int capacity(ServerLevel level, BlockPos anchor, WorkstationDef def) {
            return -1;
        }

        /** Copies of one recipe this station can commit in a single producer cycle. */
        default int batchCapacity(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                  DiscoveredRecipe recipe) {
            return 1;
        }

        /** Supplies required by the station's live state rather than by the recipe itself. */
        default List<RecipeIngredient> additionalInputs(ServerLevel level, BlockPos anchor,
                                                        WorkstationDef def,
                                                        DiscoveredRecipe recipe) {
            return List.of();
        }

        /** Canonical cell for a multi-block station, or null when the supplied cell is canonical. */
        default @Nullable BlockPos anchor(ServerLevel level, BlockPos pos, WorkstationDef def) {
            return null;
        }

        /** Classify the station's current contents/state. */
        StationPhase phase(ServerLevel level, BlockPos anchor, WorkstationDef def, @Nullable DiscoveredRecipe recipe);

        /**
         * Move the recipe's inputs from the villager's inventory into the station, exactly as
         * a player's interaction would (containers returned, items shrunk). Returns false if
         * the station refused; callers roll staged items back to storage.
         */
        boolean insert(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                       WorkstationDef def, DiscoveredRecipe recipe);

        /** Batched form; adapters that advertise batching consume exactly this many copies. */
        default boolean insertBatch(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                                    WorkstationDef def, DiscoveredRecipe recipe, int copies) {
            return insert(level, villager, anchor, def, recipe);
        }

        /** Optional explicit player-like work action after insertion (cut, press, crank, etc.). */
        default boolean work(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                             WorkstationDef def, DiscoveredRecipe recipe) {
            return true;
        }

        /**
         * Move the finished product out of the station into the villager's inventory or the
         * world at the station (drops are picked up by the collect sweep). Returns true when
         * something was retrieved.
         */
        boolean collect(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
                        WorkstationDef def, DiscoveredRecipe recipe);

        /**
         * Recover finished output whose producer session no longer remembers a recipe. Most
         * stations need the recipe to identify their product; furnace/campfire-style stations
         * have a physical output channel and can unload it safely without one.
         */
        default boolean collectAvailable(ServerLevel level, VillagerEntityMCA villager,
                                         BlockPos anchor, WorkstationDef def) {
            return false;
        }

        /** Extracts contents which cannot participate in any recipe owned by this block. */
        default List<ItemStack> extractInvalidContents(ServerLevel level, BlockPos anchor,
                                                       WorkstationDef def) {
            return List.of();
        }

        /** Whether a committed batch still has recipe inputs physically resident in the block. */
        default boolean hasPendingInputs(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                         DiscoveredRecipe recipe) {
            return false;
        }

        /**
         * Whether the station's observed, unfinished contents can be explained by this recipe.
         * Used to resume work after reload without relying on an in-memory ownership record.
         */
        default boolean matchesPendingInputs(ServerLevel level, BlockPos anchor, WorkstationDef def,
                                             DiscoveredRecipe recipe) {
            return false;
        }
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
        // A furnace's slots have fixed roles and a craft surface has none at all, so their
        // adapters are implied by the role rather than stated per def. Authors can still name
        // one to override.
        if (def.adapter() == null && def.role() == StationType.FURNACE_STATION) {
            return byName(FurnaceStationAdapter.NAME);
        }
        if (def.adapter() == null && def.role() == StationType.CRAFT_SURFACE) {
            return byName(CraftSurfaceAdapter.NAME);
        }
        return byName(def.adapter());
    }
}
