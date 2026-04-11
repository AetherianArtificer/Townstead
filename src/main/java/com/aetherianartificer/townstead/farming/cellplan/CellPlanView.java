package com.aetherianartificer.townstead.farming.cellplan;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * Read-only query interface for the cell plan, expressed in resolved 3D positions.
 * The farmer AI uses this — it never reads CellPlan maps directly.
 * {@link com.aetherianartificer.townstead.hunger.farm.FarmBlueprint} implements this
 * with resolved Y coordinates from the heightmap scan.
 */
public interface CellPlanView {

    CellPlanView EMPTY = new CellPlanView() {};

    /**
     * True if this position must be excluded from all AI scans (harvest, plant, till, groom).
     */
    default boolean isProtected(BlockPos pos) { return false; }

    /**
     * Returns the seed override for this crop position.
     * <ul>
     *   <li>{@code null} — no opinion, use FarmerCropPreferences scoring</li>
     *   <li>{@link SeedAssignment#AUTO} — explicitly defers to scoring</li>
     *   <li>{@link SeedAssignment#NONE} — leave unplanted, skip for planting</li>
     *   <li>{@link SeedAssignment#PROTECTED} — hands off entirely</li>
     *   <li>item registry ID — plant exactly this seed</li>
     * </ul>
     */
    @Nullable
    default String seedOverride(BlockPos pos) { return null; }

    /**
     * Returns the soil type required at this soil position.
     * {@code null} means no opinion (farmer uses default behavior).
     */
    @Nullable
    default SoilType soilOverride(BlockPos pos) { return null; }

    /**
     * Filters seed slot selection. Returns the inventory slot index of the seed
     * that satisfies the override for the given plant position, or -1 to skip.
     * Falls through to the provided fallback for null/AUTO overrides.
     */
    default int filterSeedSlot(SimpleContainer inv, BlockPos plantPos,
                                BiFunction<SimpleContainer, BlockPos, Integer> fallback) {
        String override = seedOverride(plantPos);
        if (override == null || SeedAssignment.AUTO.equals(override)) {
            return fallback.apply(inv, plantPos);
        }
        if (SeedAssignment.NONE.equals(override) || SeedAssignment.PROTECTED.equals(override)) {
            return -1;
        }
        // Specific item ID — find it in inventory
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty()) {
                String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
                if (override.equals(id)) return i;
            }
        }
        return -1; // don't plant something else in an overridden cell
    }
}
