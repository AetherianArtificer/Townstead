package com.aetherianartificer.townstead.work.martial;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.profession.career.CareerProgression;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.root.disposition.Disposition;
import com.aetherianartificer.townstead.root.disposition.DispositionGroups;
import com.aetherianartificer.townstead.root.disposition.Dispositions;
import com.aetherianartificer.townstead.work.job.WorkJobDef;
import com.aetherianartificer.townstead.work.job.WorkJobs;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Runs the martial Jobs a villager's profession declares (see {@link MartialJob}). One behavior
 * serves them all so they never pull the villager two ways: a fight comes first, then a sweep past
 * the border when one is due, then the first of patrol, hold post or drill. A weapon matching the
 * Job's {@code arms} comes out of the villager's inventory for the shift and goes back after.
 */
public class MartialWorkTask extends Behavior<VillagerEntityMCA> {
    private static final int JOB_CHECK = 40;
    private static final int SEARCH_INTERVAL = 20;
    private static final int MELEE_INTERVAL = 20;
    private static final int BOW_INTERVAL = 30;
    private static final int CROSSBOW_INTERVAL = 45;
    private static final double RANGED_REACH = 12;
    private static final double REACH = 1.5;
    private static final float WALK_SPEED = 0.5f;
    private static final float CHASE_SPEED = 0.7f;
    private static final int HOLD_XP_INTERVAL = 1200;
    private static final int PLACE_REACH = 6;

    private List<MartialJob> jobs = List.of();
    private long nextJobCheck;
    private @Nullable Village home;
    private @Nullable LivingEntity target;
    private @Nullable MartialJob engaging;
    private @Nullable BlockPos post;
    private @Nullable BlockPos place;
    private long lingerUntil, nextSearch, nextSweep, nextShot, nextHoldXp, sessionEnd;
    private int drawnSlot = -1;

    public MartialWorkTask() {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.REGISTERED), 1200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (level.getGameTime() < nextJobCheck) return false;
        nextJobCheck = level.getGameTime() + JOB_CHECK;
        if (villager.isSleeping() || villager.isBaby()) return false;
        jobs = jobsFor(villager);
        return !jobs.isEmpty() && village(level, villager) != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (home == null || villager.isSleeping()) return false;
        if (gameTime >= nextJobCheck) {
            nextJobCheck = gameTime + JOB_CHECK;
            jobs = jobsFor(villager);
        }
        return !jobs.isEmpty();
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        home = village(level, villager);
        post = null;
        place = null;
        lingerUntil = 0;
        sessionEnd = 0;
        drawWeapon(villager);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target != null) {
            if (!target.isAlive()) {
                if (target.getLastHurtByMob() == villager && engaging != null) award(villager, engaging.xp().kill(), gameTime, "townstead:slew_quarry");
                target = null;
            } else if (engaging == null || !engages(villager, target, engaging) || beyondChase(target, engaging)) {
                target = null;
            }
        }
        if (target == null && gameTime >= nextSearch) {
            nextSearch = gameTime + SEARCH_INTERVAL;
            for (MartialJob job : jobs) {
                LivingEntity found = nearest(level, villager, job);
                if (found != null) {
                    target = found;
                    engaging = job;
                    break;
                }
            }
        }
        if (target == null) {
            for (MartialJob job : jobs) {
                if (job.kind() != MartialJob.Kind.SWEEP || gameTime < nextSweep) continue;
                nextSweep = gameTime + job.sweep().interval();
                LivingEntity found = sighting(level, villager, job);
                if (found != null) {
                    target = found;
                    engaging = job;
                    break;
                }
            }
        }
        if (target != null) {
            fight(level, villager, target, engaging, gameTime);
            return;
        }
        villager.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        for (MartialJob job : jobs) {
            switch (job.kind()) {
                case PATROL -> patrol(level, villager, job, gameTime);
                case HOLD_POST -> hold(level, villager, job, gameTime);
                case DRILL -> drill(level, villager, job, gameTime);
                case SWEEP -> {
                    continue;
                }
            }
            return;
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        villager.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (villager.isUsingItem()) villager.stopUsingItem();
        target = null;
        engaging = null;
        post = null;
        place = null;
        home = null;
        sheatheWeapon(villager);
    }

    // ---------------------------------------------------------------- jobs

    /** The martial Jobs of the villager's profession whose tasks it may do now, in declared order. */
    private static List<MartialJob> jobsFor(VillagerEntityMCA villager) {
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        ProfessionDef def = ProfessionDefs.byId(professionId);
        if (def == null) return List.of();
        List<MartialJob> out = null;
        for (WorkTaskDef task : def.workTasks()) {
            List<WorkJobDef> martial = WorkJobs.martial(task.type());
            if (martial.isEmpty() || !task.available(villager)) continue;
            if (out == null) out = new ArrayList<>(2);
            for (WorkJobDef job : martial) out.add(job.martial());
        }
        return out == null ? List.of() : out;
    }

    // ---------------------------------------------------------------- fighting

    private void fight(ServerLevel level, VillagerEntityMCA villager, LivingEntity quarry, @Nullable MartialJob job, long gameTime) {
        villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, quarry);
        ItemStack weapon = villager.getMainHandItem();
        MartialJob.Style style = job == null ? MartialJob.Style.AUTO : job.arms().style();
        if (!weapon.isEmpty() && MartialJob.ranged(style, weapon.getItem())) {
            shootAt(level, villager, quarry, weapon, gameTime);
            return;
        }
        double reach = villager.getBbWidth() + quarry.getBbWidth() + REACH;
        if (villager.distanceToSqr(quarry) > reach * reach) {
            BehaviorUtils.setWalkAndLookTargetMemories(villager, quarry, CHASE_SPEED, 1);
            return;
        }
        villager.getLookControl().setLookAt(quarry, 30f, 30f);
        if (villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) return;
        villager.swing(InteractionHand.MAIN_HAND);
        villager.doHurtTarget(quarry);
        villager.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, MELEE_INTERVAL);
    }

    private void shootAt(ServerLevel level, VillagerEntityMCA villager, LivingEntity quarry, ItemStack weapon, long gameTime) {
        boolean visible = villager.hasLineOfSight(quarry);
        if (!visible || villager.distanceToSqr(quarry) > RANGED_REACH * RANGED_REACH) {
            if (villager.isUsingItem()) villager.stopUsingItem();
            BehaviorUtils.setWalkAndLookTargetMemories(villager, quarry, CHASE_SPEED, (int) (RANGED_REACH * 0.6));
            return;
        }
        villager.getNavigation().stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getLookControl().setLookAt(quarry, 30f, 30f);
        boolean crossbow = weapon.getItem() instanceof CrossbowItem;
        // A bow is drawn for its last second, so the pull shows before the shot.
        if (!crossbow && !villager.isUsingItem() && nextShot - gameTime <= 20) villager.startUsingItem(InteractionHand.MAIN_HAND);
        if (gameTime < nextShot) return;
        if (villager.isUsingItem()) villager.stopUsingItem();
        //? if >=1.21 {
        AbstractArrow arrow = ProjectileUtil.getMobArrow(villager, new ItemStack(Items.ARROW), 1f, weapon);
        //?} else {
        /*AbstractArrow arrow = ProjectileUtil.getMobArrow(villager, new ItemStack(Items.ARROW), 1f);
        *///?}
        double x = quarry.getX() - villager.getX();
        double y = quarry.getY(1.0 / 3.0) - arrow.getY();
        double z = quarry.getZ() - villager.getZ();
        double flat = Math.sqrt(x * x + z * z);
        arrow.shoot(x, y + flat * 0.2, z, crossbow ? 3.15f : 1.6f, 14 - level.getDifficulty().getId() * 4);
        // Shots make no items: nothing to pick up afterwards.
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        level.addFreshEntity(arrow);
        level.playSound(null, villager.blockPosition(), crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT,
                SoundSource.NEUTRAL, 1f, 1f / (villager.getRandom().nextFloat() * 0.4f + 0.8f));
        nextShot = gameTime + (crossbow ? CROSSBOW_INTERVAL : BOW_INTERVAL);
    }

    private static boolean engages(VillagerEntityMCA villager, LivingEntity other, MartialJob job) {
        if (other == villager || !other.isAlive()) return false;
        if (other instanceof Player player && (player.isCreative() || player.isSpectator())) return false;
        MartialJob.Engage engage = job.engage();
        if (engage.hostile() && Dispositions.between(villager, other) == Disposition.HOSTILE) return true;
        return !engage.groups().isEmpty() && engage.groups().contains(DispositionGroups.of(other));
    }

    private static @Nullable LivingEntity nearest(ServerLevel level, VillagerEntityMCA villager, MartialJob job) {
        double sight = job.engage().sight();
        return level.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(sight),
                        other -> engages(villager, other, job) && villager.hasLineOfSight(other))
                .stream().min(Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
    }

    /** Something to fight in the loaded ground just past the border, if any. */
    private @Nullable LivingEntity sighting(ServerLevel level, VillagerEntityMCA villager, MartialJob job) {
        if (home == null) return null;
        BoundingBox box = home.getBox();
        AABB area = new AABB(box.minX(), box.minY() - 16, box.minZ(), box.maxX() + 1, box.maxY() + 16, box.maxZ() + 1)
                .inflate(job.sweep().reach());
        return level.getEntitiesOfClass(LivingEntity.class, area, other -> engages(villager, other, job))
                .stream().min(Comparator.comparingDouble(villager::distanceToSqr)).orElse(null);
    }

    /** Past its Job's reach from home, the quarry is let go and the villager heads back. */
    private boolean beyondChase(LivingEntity quarry, MartialJob job) {
        if (home == null) return true;
        BoundingBox box = home.getBox();
        double reach = Math.max(box.getXSpan(), box.getZSpan()) / 2.0
                + (job.kind() == MartialJob.Kind.SWEEP ? job.sweep().reach() + 16 : job.engage().chase());
        return quarry.blockPosition().distSqr(home.getCenter()) > reach * reach;
    }

    // ---------------------------------------------------------------- duties

    private void patrol(ServerLevel level, VillagerEntityMCA villager, MartialJob job, long gameTime) {
        if (post == null) {
            post = edgePost(level, villager, job);
            lingerUntil = 0;
            if (post == null) return;
        }
        if (villager.blockPosition().closerThan(post, 3)) {
            if (lingerUntil == 0) {
                lingerUntil = gameTime + job.route().linger();
                award(villager, job.xp().post(), gameTime, "townstead:walked_patrol");
            }
            if (gameTime >= lingerUntil) post = null;
            return;
        }
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(post, WALK_SPEED, 2));
    }

    private void hold(ServerLevel level, VillagerEntityMCA villager, MartialJob job, long gameTime) {
        if (post == null) {
            BlockPos found = findPlace(level, villager, job);
            post = found == null ? null : com.aetherianartificer.townstead.building.BuildingCells.inside(level, found, 3, villager.getRandom());
            nextHoldXp = gameTime + HOLD_XP_INTERVAL;
            if (post == null) return;
        }
        if (!villager.blockPosition().closerThan(post, 2)) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(post, WALK_SPEED, 1));
            return;
        }
        if (gameTime >= nextHoldXp) {
            nextHoldXp = gameTime + HOLD_XP_INTERVAL;
            award(villager, job.xp().post(), gameTime, "townstead:held_post");
        }
    }

    private void drill(ServerLevel level, VillagerEntityMCA villager, MartialJob job, long gameTime) {
        if (place == null) {
            place = findPlace(level, villager, job);
            post = null;
            if (place == null) return;
        }
        if (post == null) {
            post = com.aetherianartificer.townstead.building.BuildingCells.inside(level, place, PLACE_REACH, villager.getRandom());
            if (post == null) {
                place = null;
                return;
            }
        }
        if (!villager.blockPosition().closerThan(post, 2)) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(post, WALK_SPEED, 1));
            return;
        }
        villager.getLookControl().setLookAt(place.getX() + 0.5, place.getY() + 1, place.getZ() + 0.5);
        if (sessionEnd == 0) sessionEnd = gameTime + job.drill().session();
        if (gameTime % 30 == 0) villager.swing(InteractionHand.MAIN_HAND);
        if (gameTime < sessionEnd) return;
        sessionEnd = 0;
        post = null;
        award(villager, job.xp().drill(), gameTime, "townstead:drilled");
        if (job.drill().after() != null) job.drill().after().run(new ActionContext(villager));
    }

    /** A point on the village edge, a little inside it, on the ground. */
    private @Nullable BlockPos edgePost(ServerLevel level, VillagerEntityMCA villager, MartialJob job) {
        if (home == null) return null;
        BoundingBox box = home.getBox();
        net.minecraft.core.Vec3i center = home.getCenter();
        double angle = villager.getRandom().nextDouble() * Math.PI * 2;
        double rx = Math.max(8, box.getXSpan() / 2.0 - job.route().inset());
        double rz = Math.max(8, box.getZSpan() / 2.0 - job.route().inset());
        BlockPos column = new BlockPos(center.getX() + (int) Math.round(Math.cos(angle) * rx), center.getY(),
                center.getZ() + (int) Math.round(Math.sin(angle) * rz));
        if (!level.hasChunkAt(column)) return null;
        return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
    }

    /** The Job's place: the villager's order altar, or the nearest matching block the village's buildings hold. */
    private @Nullable BlockPos findPlace(ServerLevel level, VillagerEntityMCA villager, MartialJob job) {
        MartialJob.Place wanted = job.place();
        if (wanted == null) return null;
        if (wanted.orderAltar()) {
            GlobalPos altar = com.aetherianartificer.townstead.politics.order.OrderPlaces.altar(level, villager);
            return altar != null && altar.dimension().equals(level.dimension()) ? altar.pos() : null;
        }
        if (home == null) return null;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Building building : McaBuildings.all(home)) {
            for (Map.Entry<ResourceLocation, List<BlockPos>> blocks : building.getBlocks().entrySet()) {
                for (BlockPos pos : blocks.getValue()) {
                    if (!level.isLoaded(pos) || !wanted.matches(level.getBlockState(pos))) continue;
                    double distance = pos.distSqr(villager.blockPosition());
                    if (distance < bestDistance) {
                        best = pos;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private static @Nullable Village village(ServerLevel level, VillagerEntityMCA villager) {
        return VillageManager.get(level).findNearestVillage(villager.blockPosition(), Village.MERGE_MARGIN).orElse(null);
    }

    // ---------------------------------------------------------------- arms

    private void drawWeapon(VillagerEntityMCA villager) {
        if (!villager.getMainHandItem().isEmpty()) return;
        var inventory = villager.getInventory();
        for (MartialJob job : jobs) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (!job.arms().matches(stack)) continue;
                villager.setItemInHand(InteractionHand.MAIN_HAND, stack);
                inventory.setItem(i, ItemStack.EMPTY);
                drawnSlot = i;
                return;
            }
        }
    }

    private void sheatheWeapon(VillagerEntityMCA villager) {
        if (drawnSlot < 0) return;
        drawnSlot = -1;
        ItemStack held = villager.getMainHandItem();
        if (held.isEmpty()) return;
        villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ItemStack left = villager.getInventory().addItem(held);
        if (!left.isEmpty()) villager.spawnAtLocation(left);
    }

    private static void award(VillagerEntityMCA villager, int xp, long gameTime, String verb) {
        if (xp <= 0) return;
        ResourceLocation career = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        CareerProgression.completeWork(villager, career, xp, gameTime, verb, null, null, xp);
    }
}
