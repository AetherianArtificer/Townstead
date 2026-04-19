package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Central "can a villager safely eat this?" filter. Inspects the stack's
 * vanilla {@link FoodProperties} and rejects anything with a status effect
 * whose {@link MobEffectCategory} is {@code HARMFUL} — the same category
 * vanilla tags Poison, Wither, Hunger, Weakness, Slowness, Instant Damage,
 * Nausea, etc. with. Pufferfish, spider eye, raw chicken, rotten flesh,
 * and poisonous potato are all caught by this automatically because their
 * food definitions declare those effects.
 *
 * Used by every pick-path that decides what a villager is about to eat:
 * {@code SeekFoodTask} (inventory, ground, storage, crop drops),
 * {@code CareForYoungTask} (feeding children), {@code HungerVillagerTicker}
 * (passive hunger), {@code HarvestWorkTask} (farmer self-feed), and
 * {@code ButcherSupplyManager} (reserved food slot). Further defended by
 * {@code VillagerEatSafetyMixin}, which blocks the final {@code eat()}
 * call as a backstop for any third-party code path we don't control.
 */
public final class FoodSafety {
    private FoodSafety() {}

    /**
     * True if the stack is food AND none of its guaranteed status effects
     * are harmful. Returns {@code false} for non-food items, so this is a
     * single-point check callers can use without also testing edibility.
     */
    public static boolean isSafeToEat(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        FoodProperties food = foodPropertiesOf(stack);
        if (food == null) return false;
        String harmful = firstHarmfulEffectName(food);
        if (harmful != null) {
            if (TownsteadConfig.DEBUG_VILLAGER_AI.get()) {
                Townstead.LOGGER.info("[FoodSafety] rejected {} (harmful effect: {})",
                        BuiltInRegistries.ITEM.getKey(stack.getItem()), harmful);
            }
            return false;
        }
        return true;
    }

    /**
     * Combined edibility + safety check: {@code nutrition > 0} AND no harmful
     * effects. Convenient for the many food-candidate predicates that already
     * filter by nutrition.
     */
    public static boolean isSafeNutritiousFood(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        FoodProperties food = foodPropertiesOf(stack);
        //? if >=1.21 {
        if (food == null || food.nutrition() <= 0) return false;
        //?} else {
        /*if (food == null || food.getNutrition() <= 0) return false;
        *///?}
        return firstHarmfulEffectName(food) == null;
    }

    private static FoodProperties foodPropertiesOf(ItemStack stack) {
        //? if >=1.21 {
        return stack.get(DataComponents.FOOD);
        //?} else {
        /*return stack.getFoodProperties(null);
        *///?}
    }

    /**
     * Returns the registry name of the first guaranteed harmful effect on
     * this food, or {@code null} if no such effect exists. Ignores effects
     * whose probability is 0 (vanilla never declares those, but defensive).
     */
    private static String firstHarmfulEffectName(FoodProperties food) {
        //? if >=1.21 {
        for (FoodProperties.PossibleEffect possible : food.effects()) {
            if (possible.probability() <= 0F) continue;
            MobEffectInstance effect = possible.effect();
            if (effect == null) continue;
            var holder = effect.getEffect();
            if (holder == null) continue;
            MobEffect mob = holder.value();
            if (mob.getCategory() == MobEffectCategory.HARMFUL) {
                return String.valueOf(BuiltInRegistries.MOB_EFFECT.getKey(mob));
            }
        }
        //?} else {
        /*for (com.mojang.datafixers.util.Pair<MobEffectInstance, Float> pair : food.getEffects()) {
            Float prob = pair.getSecond();
            if (prob == null || prob <= 0F) continue;
            MobEffectInstance effect = pair.getFirst();
            if (effect == null) continue;
            MobEffect mob = effect.getEffect();
            if (mob == null) continue;
            if (mob.getCategory() == MobEffectCategory.HARMFUL) {
                return String.valueOf(BuiltInRegistries.MOB_EFFECT.getKey(mob));
            }
        }
        *///?}
        return null;
    }
}
