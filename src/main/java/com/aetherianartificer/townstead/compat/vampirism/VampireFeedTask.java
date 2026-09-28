package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.aetherianartificer.townstead.performance.PerformanceProviders;
import com.aetherianartificer.townstead.performance.PerformanceRequest;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A hungry vampire villager walks to prey and drinks: an animal by default, or a person as far as
 * the feeding setting allows (never another vampire). Both actors play Astra's paired clips; the
 * blood changes hands at the clips' contact tick, through Vampirism's own blood pools, and the
 * bite can pass on Sanguinare by Vampirism's own rules.
 */
public class VampireFeedTask extends Behavior<VillagerEntityMCA> {
    private static final TagKey<EntityType<?>> PREY = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.tryParse("townstead:vampire_prey"));
    private static final TagKey<EntityType<?>> EXCLUDED = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.tryParse("townstead:vampire_prey_excluded"));
    private static final ResourceLocation BITE = ResourceLocation.tryParse("townstead:bite");
    private static final ResourceLocation BITTEN = ResourceLocation.tryParse("townstead:bitten");
    private static final int SEARCH_RADIUS = 16;
    private static final float WALK_SPEED = 0.6f;
    private static final double REACH_SQR = 2.25;
    // The clips: 56 ticks, fangs meet the neck at tick 11.
    private static final int CLIP_TICKS = 56;
    private static final int CONTACT_TICK = 11;
    private static final int MAX_DURATION = 600;
    private static final int COOLDOWN_TICKS = 1200;
    private static final int BLOOD_PER_BITE = 2;
    private static final int NUTRITION_PER_BLOOD = 3;

    private @Nullable LivingEntity prey;
    private int biteTick = -1;
    private long nextSearch;

    public VampireFeedTask() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (level.getGameTime() < nextSearch || !VampireVillagers.isVampire(villager)) return false;
        if (!TownsteadConfig.isVillagerHungerEnabled() || villager.isSleeping()) return false;
        if (villager.getLastHurtByMob() != null) return false;
        if (TownsteadVillagers.get(villager).needs().hunger() > HungerData.ADEQUATE_THRESHOLD) return false;
        nextSearch = level.getGameTime() + 100;
        prey = findPrey(level, villager);
        return prey != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        biteTick = -1;
        if (prey != null) BehaviorUtils.setWalkAndLookTargetMemories(villager, prey, WALK_SPEED, 1);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (prey == null || !prey.isAlive() || villager.getLastHurtByMob() != null) return false;
        return biteTick < 0 ? villager.distanceToSqr(prey) < SEARCH_RADIUS * SEARCH_RADIUS * 4 : biteTick <= CLIP_TICKS;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (prey == null) return;
        if (biteTick < 0) {
            if (villager.distanceToSqr(prey) > REACH_SQR) {
                BehaviorUtils.setWalkAndLookTargetMemories(villager, prey, WALK_SPEED, 1);
                return;
            }
            beginBite(level, villager);
            return;
        }
        villager.getLookControl().setLookAt(prey, 30f, 30f);
        if (prey instanceof PathfinderMob mob) mob.getNavigation().stop();
        if (++biteTick == CONTACT_TICK) drink(level, villager);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (biteTick >= 0) nextSearch = gameTime + COOLDOWN_TICKS;
        prey = null;
        biteTick = -1;
    }

    private void beginBite(ServerLevel level, VillagerEntityMCA villager) {
        biteTick = 0;
        villager.getNavigation().stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getLookControl().setLookAt(prey, 30f, 30f);
        PerformanceProviders.play(level, new PerformanceRequest(villager, BITE, "action", CLIP_TICKS, 60,
                PerformanceRequest.Fallback.VANILLA_GESTURE));
        if (isPerson(prey)) {
            prey.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, villager.getEyePosition());
            PerformanceProviders.play(level, new PerformanceRequest(prey, BITTEN, "action", CLIP_TICKS, 60,
                    PerformanceRequest.Fallback.NONE));
        }
    }

    private void drink(ServerLevel level, VillagerEntityMCA villager) {
        if (prey == null) return;
        int drawn = BLOOD_PER_BITE;
        if (prey instanceof PathfinderMob mob) {
            Object pool = VampirismBlood.creature(mob);
            // A creature Vampirism gives blood is drained from its own pool; a tagged one it
            // does not know simply gives a bite's worth.
            if (pool != null && VampirismBlood.maxBlood(pool) > 0) {
                drawn = Math.min(BLOOD_PER_BITE, VampirismBlood.blood(pool));
                VampirismBlood.setBlood(pool, VampirismBlood.blood(pool) - drawn);
            }
            if (pool != null && drawn > 0 && isPerson(prey) && Switchboard.get(TownsteadConfig.VAMPIRE_INFECTION)) {
                VampirismBlood.infect(pool);
            }
        } else if (prey instanceof Player player && Switchboard.get(TownsteadConfig.VAMPIRE_INFECTION)) {
            VampirismBlood.infect(player);
        }
        if (drawn <= 0) return;
        TownsteadVillager.Needs needs = TownsteadVillagers.get(villager).needs();
        float scale = com.aetherianartificer.townstead.root.hook.PhenoHooks.foodMultiplier(villager);
        needs.applyFood(drawn * NUTRITION_PER_BLOOD, 0.4f, scale);
        needs.setLastAteTime(level.getGameTime());
        EntityStates.add(villager, VampireVillagers.BLOOD, drawn, 0, null);
        if (isPerson(prey)) {
            com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps.taboo(villager,
                    "townstead:fed_on_person", EntityType.getKey(prey.getType()),
                    Map.of("willing", String.valueOf(isSpouse(villager, prey))));
        }
    }

    private @Nullable LivingEntity findPrey(ServerLevel level, VillagerEntityMCA villager) {
        VampireFeeding feeding = Switchboard.get(TownsteadConfig.VAMPIRE_FEEDING);
        AABB area = villager.getBoundingBox().inflate(SEARCH_RADIUS, 6, SEARCH_RADIUS);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, area,
                other -> other != villager && other.isAlive() && isPrey(villager, other, feeding));
        return candidates.stream().min(Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
    }

    private static boolean isPrey(VillagerEntityMCA villager, LivingEntity other, VampireFeeding feeding) {
        if (other instanceof VillagerEntityMCA person) {
            if (VampireVillagers.isVampire(person) || person.isBaby() || feeding == VampireFeeding.OFF) return false;
            return feeding == VampireFeeding.ANYONE || isSpouse(villager, person);
        }
        if (other instanceof ServerPlayer player) {
            if (feeding != VampireFeeding.ANYONE || player.isCreative() || player.isSpectator()) return false;
            return !EntityStates.resolve(player, VampireVillagers.STATE).active();
        }
        // Any creature Vampirism gives blood (vanilla or modded) is prey unless a pack spares it;
        // the prey tag adds any it does not know. Monsters, villagers, babies and pets are not.
        if (!(other instanceof PathfinderMob mob) || other.isBaby() || other.getType().is(EXCLUDED)) return false;
        if (other instanceof net.minecraft.world.entity.monster.Enemy
                || other instanceof net.minecraft.world.entity.npc.AbstractVillager) return false;
        if (other.hasCustomName() || other instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame()
                || other instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse && horse.isTamed()) {
            return false;
        }
        Object pool = VampirismBlood.creature(mob);
        if (pool != null && VampirismBlood.maxBlood(pool) > 0) return VampirismBlood.blood(pool) > 0;
        return other.getType().is(PREY);
    }

    private static boolean isPerson(@Nullable LivingEntity entity) {
        return entity instanceof VillagerEntityMCA || entity instanceof Player;
    }

    private static boolean isSpouse(VillagerEntityMCA villager, LivingEntity other) {
        return villager.getRelationships().isMarriedTo(other.getUUID());
    }
}
