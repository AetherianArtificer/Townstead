package com.aetherianartificer.townstead.origin.loot;

import com.aetherianartificer.townstead.origin.CanonicalStage;
import com.aetherianartificer.townstead.origin.LifeCycle;
import com.aetherianartificer.townstead.origin.LifeStage;
import com.aetherianartificer.townstead.origin.LifeStageProgression;
import com.aetherianartificer.townstead.origin.OriginRegistry;
import com.aetherianartificer.townstead.origin.PlayerOrigin;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Spawns a dying entity's life-stage {@code death_loot} IN ADDITION to its normal drops. The drops are
 * authored inline on each stage of the origin's {@code life_cycle} gene, so an egg drops a spider egg
 * while an adult drops silk. Called from the server-side {@code LivingDeathEvent} for both MCA villagers
 * (their current life stage) and players (the origin's adult-presenting stage, since a player has no live
 * stage). No-op for everything else, so it costs nothing for ordinary mobs.
 */
public final class DeathLoot {

    private DeathLoot() {}

    public static void onDeath(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide) return;
        List<LootDrop> drops = dropsFor(entity);
        if (drops.isEmpty()) return;

        RandomSource rng = entity.getRandom();
        for (LootDrop d : drops) {
            int count = d.rollCount(rng);
            if (count <= 0) continue;
            Item item = BuiltInRegistries.ITEM.get(d.item());
            if (item == Items.AIR) continue;
            ItemEntity drop = new ItemEntity(entity.level(), entity.getX(), entity.getY() + 0.5, entity.getZ(),
                    new ItemStack(item, count));
            drop.setDefaultPickUpDelay();
            entity.level().addFreshEntity(drop);
        }
    }

    private static List<LootDrop> dropsFor(LivingEntity entity) {
        if (entity instanceof VillagerEntityMCA villager) {
            LifeStage stage = LifeStageProgression.currentStage(villager);
            return stage != null ? stage.deathLoot() : List.of();
        }
        if (entity instanceof Player player) {
            LifeStage stage = adultStageFor(PlayerOrigin.getOriginId(player));
            return stage != null ? stage.deathLoot() : List.of();
        }
        return List.of();
    }

    /** A player's loot comes from its origin's adult-presenting stage (else the last stage), or null. */
    private static LifeStage adultStageFor(String originStr) {
        if (originStr == null || originStr.isEmpty()) return null;
        LifeCycle cycle = OriginRegistry.effectiveLifeCycle(ResourceLocation.tryParse(originStr));
        if (cycle == null || cycle.isEmpty()) return null;
        for (LifeStage stage : cycle.stages()) {
            if (stage.presentsAs() == CanonicalStage.ADULT) return stage;
        }
        return cycle.stageAt(cycle.size() - 1);
    }
}
