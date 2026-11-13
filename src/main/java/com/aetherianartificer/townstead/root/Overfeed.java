package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.ability.ResourceValues;
import com.aetherianartificer.townstead.root.gene.types.OverfeedGeneType;
import com.aetherianartificer.townstead.root.gene.types.ResourceGeneType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/** Runs {@link OverfeedGeneType}: eating past full, and what the surplus turns into. */
public final class Overfeed {

    private static final Map<LivingEntity, Boolean> STARTED_FULL = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    private Overfeed() {}

    /** Whether a full player may start eating anyway: only while the meter has room. */
    public static boolean canEatPastFull(Player player) {
        if (player.level().isClientSide) return !Powers.componentsOf(player, OverfeedGeneType.Instance.class).isEmpty();
        for (OverfeedGeneType.Instance gene : Powers.componentsOf(player, OverfeedGeneType.Instance.class)) {
            ResourceGeneType.Instance meter = ResourceValues.meter(player, gene.resource());
            if (meter != null && ResourceValues.get(player, gene.resource()) < meter.max()) return true;
        }
        return false;
    }

    public static void onUseStart(LivingEntity entity) {
        if (entity.level().isClientSide || !(entity instanceof Player player)) return;
        STARTED_FULL.put(player, !player.getFoodData().needsFood());
    }

    public static void onUseFinish(LivingEntity entity, ItemStack used) {
        if (entity.level().isClientSide) return;
        boolean full = Boolean.TRUE.equals(STARTED_FULL.remove(entity));
        var itemId = BuiltInRegistries.ITEM.getKey(used.getItem());
        for (OverfeedGeneType.Instance gene : Powers.componentsOf(entity, OverfeedGeneType.Instance.class)) {
            int gain = gene.items().getOrDefault(itemId, 0);
            if (full) gain += nutrition(used, entity) * gene.perNutrition();
            if (gain > 0) ResourceValues.change(entity, gene.resource(), gain);
        }
    }

    private static int nutrition(ItemStack stack, LivingEntity entity) {
        //? if neoforge {
        FoodProperties food = stack.getFoodProperties(entity);
        return food == null ? 0 : food.nutrition();
        //?} else {
        /*FoodProperties food = stack.getFoodProperties(entity);
        return food == null ? 0 : food.getNutrition();
        *///?}
    }
}
