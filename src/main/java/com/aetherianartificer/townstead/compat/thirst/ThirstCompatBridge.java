package com.aetherianartificer.townstead.compat.thirst;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface ThirstCompatBridge {

    /**
     * The top step of the bridge purity scale: water that is finished, whatever the mod calls
     * it. Below this a container is purification input; at it, purification output.
     */
    int PURITY_PURIFIED = 3;

    boolean isActive();
    /** Live backend toggle, separate from installation/API availability. */
    default boolean isThirstEnabled() { return true; }
    boolean itemRestoresThirst(ItemStack stack);
    boolean isDrink(ItemStack stack);
    boolean isPurityWaterContainer(ItemStack stack);
    int hydration(ItemStack stack);
    int quenched(ItemStack stack);
    int purity(ItemStack stack);
    float exhaustionBiomeModifier(Level level, BlockPos pos);
    default float exhaustionBiomeModifier(Level level, BlockPos pos, ThermalHydrationContext thermal) {
        return exhaustionBiomeModifier(level, pos);
    }
    default float thermalExhaustionPerTick(ThermalHydrationContext thermal) { return 0; }
    boolean extraHydrationToQuenched();
    PurityResult evaluatePurity(int purity, RandomSource random);

    /**
     * The lowest purity this mod counts as finished water: the purification line stops here and
     * order counts start here. {@link #PURITY_PURIFIED} unless the mod's own heat stops lower.
     */
    default int finishedPurity() { return PURITY_PURIFIED; }

    /**
     * Applies the mod's own illness for drinking this water to a non-player drinker, by the mod's
     * own rules. Returns true when it handled sickness, so the caller skips the generic nausea and
     * poison from {@link #evaluatePurity}; false leaves that path in place.
     */
    default boolean applyWaterSickness(LivingEntity drinker, ItemStack stack, RandomSource random) {
        return false;
    }

    /**
     * The mod's reading of the water in a block, taken before a villager draws from it, or null.
     * A mod that grades a drawn container after the interaction (on the player's hand, at the end
     * of the tick) never reaches the stand-in's vessel, so the grade is carried over by hand.
     */
    default Object sampleBlockWater(net.minecraft.server.level.ServerLevel level, BlockPos pos) { return null; }

    /** Grades {@code serving} with a {@link #sampleBlockWater} reading when the mod left it unstamped. */
    default ItemStack gradeServing(ItemStack serving, Object sample) { return serving; }
    ResourceLocation iconTexture();

    boolean supportsPurification();
    /** Whether the shared boiling workflow can process this vessel. */
    default boolean canBoil(ItemStack stack) { return isPurityWaterContainer(stack); }

    /**
     * The item an ordered purification line wears and counts, or null for the vanilla water
     * bottle. What a village purifies water <em>in</em> is the thirst mod's own idiom: bottles
     * under Thirst Was Taken and Thirst Was Reclaimed, canteens under Legendary Survival
     * Overhaul. The work itself keeps taking any impure container the bridge recognises.
     */
    default ResourceLocation purificationOutput() { return null; }

    default void purifyResult(ItemStack input, ItemStack output) {}

    /**
     * Called after a villager consumes a drink. Mutates the stack in-place to reflect
     * consumption (e.g. reduce canteen capacity). Returns the remainder item that should
     * stay in inventory, or {@link ItemStack#EMPTY} if the item is fully consumed
     * (normal shrink behavior).
     */
    default ItemStack onDrinkConsumed(ItemStack drinkStack) { return ItemStack.EMPTY; }

    ThirstIconInfo iconInfo(int thirst);

    /** Current player thirst, or NaN when the active compat cannot read it. */
    default double playerThirst(Player player) { return Double.NaN; }

    /**
     * Restores player thirst through the active backend, the write twin of
     * {@link #playerThirst}. {@code immediate} raises the thirst bar itself and
     * {@code lasting} the saturation-style reserve behind it, matching the two halves a
     * villager's {@code applyDrink} takes. Returns false when the backend exposes no way
     * to write, so a caller can tell "restored nothing" from "cannot restore".
     */
    default boolean restorePlayerThirst(Player player, int immediate, int lasting) { return false; }

    record PurityResult(boolean applyHydration, boolean sickness, boolean poison, int purity) {}

    record ThirstIconInfo(ResourceLocation texture, int u, int v, int texW, int texH) {}
}
