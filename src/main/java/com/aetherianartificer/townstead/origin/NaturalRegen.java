package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.types.DisableRegenGeneType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Scopes the suppression of natural (food-driven) health regen to the body of
 * {@code FoodData.tick}. The food-tick mixin raises the flag around the tick, and the
 * heal listener cancels a heal that fires while it is up <em>and</em> the entity
 * expresses {@link DisableRegenGeneType} — so only the regen vanilla performs inside
 * the food tick is blocked, not potions, golden apples, or regeneration effects.
 *
 * <p>The flag is a {@link ThreadLocal} because the server ticks each player's
 * {@code FoodData} sequentially on one thread and the heal fires synchronously within
 * that call.</p>
 */
public final class NaturalRegen {

    private static final ThreadLocal<Boolean> IN_FOOD_TICK = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private NaturalRegen() {}

    public static void enter() {
        IN_FOOD_TICK.set(Boolean.TRUE);
    }

    public static void exit() {
        IN_FOOD_TICK.set(Boolean.FALSE);
    }

    /** True when a heal on this entity right now is the food tick's natural regen and should be blocked. */
    public static boolean isSuppressed(LivingEntity entity) {
        return IN_FOOD_TICK.get()
                && !ExpressedGenes.instancesOf(entity, DisableRegenGeneType.Instance.class).isEmpty();
    }
}
