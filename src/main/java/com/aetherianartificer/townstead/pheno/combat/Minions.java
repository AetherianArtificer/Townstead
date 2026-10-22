package com.aetherianartificer.townstead.pheno.combat;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Summons owned by a living thing, made by {@code pheno:spawn_entity} with an {@code owner}. The
 * ownership record lives in the entity's persistent data, so a summon keeps its loyalty, its
 * expiry and its behaviour across a save and reload instead of waking up as a wild mob.
 *
 * <p>An allied summon loses its own target goals and instead defends its owner, attacks what its
 * owner attacks, and hunts nearby monsters; it never targets its owner or their allies. A
 * following summon keeps up with its owner and is brought along when left far behind. A summon
 * past its {@code duration} vanishes in a puff of smoke.</p>
 */
public final class Minions {

    public static final String TAG = "townstead:minion";

    /** How a summon behaves; stored with it. */
    public record Spec(boolean allied, boolean follow, int durationTicks, boolean burnsInSun) {}

    private Minions() {}

    /** Bind {@code summon} to {@code owner} and give it its behaviour. */
    public static void bind(Entity summon, LivingEntity owner, Spec spec) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("owner", owner.getUUID());
        if (spec.durationTicks() > 0) tag.putLong("expires", summon.level().getGameTime() + spec.durationTicks());
        tag.putBoolean("allied", spec.allied());
        tag.putBoolean("follow", spec.follow());
        tag.putBoolean("burns", spec.burnsInSun());
        summon.getPersistentData().put(TAG, tag);
        // Behaviour is applied when the summon joins the world (see onJoin), once per load.
        if (summon instanceof Mob mob) mob.setPersistenceRequired();
    }

    public static boolean isMinion(@Nullable Entity entity) {
        return entity != null && entity.getPersistentData().contains(TAG);
    }

    public static @Nullable UUID ownerId(@Nullable Entity entity) {
        if (entity == null) return null;
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(TAG)) return null;
        CompoundTag tag = data.getCompound(TAG);
        return tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }

    public static @Nullable LivingEntity owner(Entity entity) {
        UUID id = ownerId(entity);
        if (id == null || !(entity.level() instanceof ServerLevel level)) return null;
        return level.getEntity(id) instanceof LivingEntity living ? living : null;
    }

    /** Apply behaviour when a summon enters the world: freshly summoned, or loaded from a save. */
    public static void onJoin(Entity entity) {
        if (entity.level().isClientSide || !(entity instanceof Mob mob) || !isMinion(mob)) return;
        configure(mob);
    }

    private static void configure(Mob mob) {
        CompoundTag tag = mob.getPersistentData().getCompound(TAG);
        if (tag.getBoolean("allied")) {
            mob.targetSelector.removeAllGoals(goal -> true);
            mob.setTarget(null);
            mob.targetSelector.addGoal(1, new DefendOwnerGoal(mob));
            mob.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false,
                    target -> target instanceof Enemy && !Allies.areAllied(mob, target)));
        }
        if (tag.getBoolean("follow") && mob instanceof PathfinderMob pathfinder) {
            mob.goalSelector.addGoal(3, new FollowOwnerGoal(pathfinder));
        }
    }

    /** Living tick for summons: expiry, loyalty, and staying out of the sun when asked. */
    public static void tick(LivingEntity entity) {
        if (!(entity instanceof Mob mob) || !(mob.level() instanceof ServerLevel level) || !isMinion(mob)) return;
        CompoundTag tag = mob.getPersistentData().getCompound(TAG);
        if (tag.contains("expires") && level.getGameTime() >= tag.getLong("expires")) {
            level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY(0.5), mob.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
            mob.discard();
            return;
        }
        if (tag.getBoolean("allied") && mob.getTarget() != null && Allies.areAllied(mob, mob.getTarget())) {
            mob.setTarget(null);
        }
        if (!tag.getBoolean("burns") && mob.isOnFire() && !mob.isInLava() && level.isDay()
                && level.canSeeSky(mob.blockPosition())) {
            mob.clearFire();
        }
    }

    /** Targets whoever the owner was hurt by or last hurt. */
    private static final class DefendOwnerGoal extends TargetGoal {
        private LivingEntity enemy;
        private int seenOwnerHurt;
        private int seenOwnerHit;

        DefendOwnerGoal(Mob mob) {
            super(mob, false);
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            LivingEntity owner = owner(mob);
            if (owner == null) return false;
            LivingEntity hurtBy = owner.getLastHurtByMob();
            if (hurtBy != null && owner.getLastHurtByMobTimestamp() != seenOwnerHurt && usable(hurtBy)) {
                enemy = hurtBy;
                seenOwnerHurt = owner.getLastHurtByMobTimestamp();
                return true;
            }
            LivingEntity hit = owner.getLastHurtMob();
            if (hit != null && owner.getLastHurtMobTimestamp() != seenOwnerHit && usable(hit)) {
                enemy = hit;
                seenOwnerHit = owner.getLastHurtMobTimestamp();
                return true;
            }
            return false;
        }

        private boolean usable(LivingEntity target) {
            return target.isAlive() && !Allies.areAllied(mob, target) && canAttack(target, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(enemy);
            super.start();
        }
    }

    /** Walks after the owner and catches up by teleport when left far behind. */
    private static final class FollowOwnerGoal extends Goal {
        private final PathfinderMob mob;
        private LivingEntity owner;
        private int recalc;

        FollowOwnerGoal(PathfinderMob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (mob.getTarget() != null) return false;
            owner = owner(mob);
            return owner != null && owner.level() == mob.level() && mob.distanceToSqr(owner) > 36;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && mob.getTarget() == null && mob.distanceToSqr(owner) > 9;
        }

        @Override
        public void tick() {
            mob.getLookControl().setLookAt(owner, 10f, mob.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = 10;
            if (mob.distanceToSqr(owner) > 24 * 24) {
                mob.moveTo(owner.getX(), owner.getY(), owner.getZ(), mob.getYRot(), mob.getXRot());
                mob.getNavigation().stop();
            } else {
                mob.getNavigation().moveTo(owner, 1.1);
            }
        }

        @Override
        public void stop() {
            owner = null;
            mob.getNavigation().stop();
        }
    }
}
