package com.aetherianartificer.townstead.replace.behavior;

import com.aetherianartificer.townstead.root.disposition.Disposition;
import com.aetherianartificer.townstead.root.disposition.Dispositions;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * {@code pheno:hunt}: a wild villager goes after the nearest thing it regards as hostile, the way
 * the mob it replaces would. With {@code shun_daylight} it neither picks nor chases a target
 * standing in the sun.
 */
public class HuntTask extends Behavior<VillagerEntityMCA> {
    private static final int SEARCH_INTERVAL = 20;
    private static final int ATTACK_INTERVAL = 20;
    private static final float CHASE_SPEED = 0.7f;
    private static final double REACH = 1.5;

    private @Nullable LivingEntity target;
    private long nextSearch;

    public HuntTask() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.REGISTERED), 1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (level.getGameTime() < nextSearch || villager.isSleeping()) return false;
        nextSearch = level.getGameTime() + SEARCH_INTERVAL;
        BehaviorTypes.Hunt hunt = BehaviorProfiles.behavior(villager, BehaviorTypes.HUNT, BehaviorTypes.Hunt.class);
        if (hunt == null) return false;
        target = level.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(hunt.radius()),
                        other -> huntable(level, villager, other, hunt) && villager.hasLineOfSight(other))
                .stream().min(Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
        return target != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target != null) villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        BehaviorTypes.Hunt hunt = BehaviorProfiles.behavior(villager, BehaviorTypes.HUNT, BehaviorTypes.Hunt.class);
        if (hunt == null || target == null || villager.isSleeping()) return false;
        if (villager.distanceToSqr(target) > hunt.radius() * hunt.radius() * 2.25) return false;
        return gameTime % SEARCH_INTERVAL != 0 || huntable(level, villager, target, hunt);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target == null) return;
        double reach = villager.getBbWidth() + target.getBbWidth() + REACH;
        if (villager.distanceToSqr(target) > reach * reach) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, target, CHASE_SPEED, 1);
            return;
        }
        villager.getLookControl().setLookAt(target, 30f, 30f);
        // A bite in progress holds the cooldown, so the two never land together.
        if (villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) return;
        villager.swing(InteractionHand.MAIN_HAND);
        villager.doHurtTarget(target);
        villager.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, ATTACK_INTERVAL);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        villager.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        target = null;
    }

    private static boolean huntable(ServerLevel level, VillagerEntityMCA villager, LivingEntity other, BehaviorTypes.Hunt hunt) {
        if (other == villager || !other.isAlive()) return false;
        if (other instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        if (hunt.shunDaylight() && level.isDay() && level.canSeeSky(other.blockPosition())) return false;
        return Dispositions.between(villager, other) == Disposition.HOSTILE;
    }
}
