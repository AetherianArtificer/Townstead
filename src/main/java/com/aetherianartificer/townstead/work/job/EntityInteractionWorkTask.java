package com.aetherianartificer.townstead.work.job;

import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.profession.ProfessionCapacity;
import com.aetherianartificer.townstead.profession.ProfessionSites;
import com.aetherianartificer.townstead.profession.career.CareerProgression;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.storage.PhysicalStorageDelivery;
import com.aetherianartificer.townstead.work.WorkActivities;
import com.aetherianartificer.townstead.work.WorkTaskDeclarations;
import com.aetherianartificer.townstead.work.order.WorksiteOrders;
import com.aetherianartificer.townstead.work.recipe.WorkIngredients;
import com.aetherianartificer.townstead.work.station.StationSupplies;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Executes data-authored {@code entity_interaction} Jobs: walk to a matching animal where it is
 * kept, use the declared item on it, keep only declared outputs, and carry them to storage. The
 * entity counterpart of {@link BlockInteractionWorkTask}; the Job names every species, tool and
 * product, so shearing, milking and anything a pack adds share this one behavior.
 */
public final class EntityInteractionWorkTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 600;
    private static final float WALK_SPEED = 0.55f;
    private static final int PATH_TIMEOUT_TICKS = 120;
    private static final double DELIVERY_RANGE_SQ = 5.0;
    private static final int WORK_DELAY = 16;
    private static final int START_SCAN_INTERVAL_TICKS = 10;

    private enum Phase { PATH, ACT, DELIVER }

    private record Pick(WorkJobDef job, WorkTaskDef task, WorkJobDef.EntityInteraction interaction,
                        LivingEntity target, @Nullable Building building) {}

    private @Nullable Pick active;
    private Phase phase = Phase.PATH;
    private long startedAt;
    private long lastPathTick;
    private long nextActionTick;
    private long nextStartScanTick = Long.MIN_VALUE;
    private Set<Long> worksite = Set.of();
    private final Set<Long> rejectedStorage = new HashSet<>();
    private @Nullable BlockPos deliveryTarget;
    private long deliverAt;

    public EntityInteractionWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    /** Whether this task has an animal waiting, for the order list and other jobs to defer to. */
    public static boolean hasWork(ServerLevel level, VillagerEntityMCA villager, @Nullable ResourceLocation task) {
        return pick(level, villager, task, false) != null;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        long gameTime = level.getGameTime();
        if (nextStartScanTick == Long.MIN_VALUE) {
            int stagger = Math.floorMod(villager.getId(), START_SCAN_INTERVAL_TICKS);
            nextStartScanTick = gameTime + stagger;
            if (stagger != 0) return false;
        } else if (gameTime < nextStartScanTick) {
            return false;
        }
        nextStartScanTick = gameTime + START_SCAN_INTERVAL_TICKS;

        // Products already in the worker's pockets go to storage before the next animal. With
        // nowhere to put them, keep working while there is room.
        if (deliveryPending(level, villager, assignedExtent(level, villager))) return true;
        return pick(level, villager, null, true) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        reset();
        startedAt = gameTime;
        lastPathTick = gameTime;
        worksite = assignedExtent(level, villager);
        if (deliveryPending(level, villager, worksite)) {
            phase = Phase.DELIVER;
            return;
        }
        Pick pick = pick(level, villager, null, true);
        if (pick == null || !acquireItem(level, villager, pick.interaction(), worksite)) return;
        active = pick;
        phase = Phase.PATH;
        nextActionTick = gameTime + pick.job().entityTarget().interval();
        setWalkTarget(villager, pick.target().blockPosition());
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (gameTime - startedAt > MAX_DURATION) return false;
        if (phase == Phase.DELIVER) {
            Predicate<ItemStack> carried = declaredOutputs(villager);
            return carried != null && PhysicalStorageDelivery.hasMatching(villager, carried);
        }
        if (active == null) return false;
        LivingEntity target = active.target();
        if (!target.isAlive() || !active.job().entityTarget().matches(target)
                || !active.interaction().ready(target)) return false;
        if (active.building() != null && !contains(level, villager, active.building(), target)) return false;
        if (active.interaction().requiresItem() && !carries(villager, active.interaction()::matches)) return false;
        return phase != Phase.PATH || gameTime - lastPathTick <= PATH_TIMEOUT_TICKS;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (phase == Phase.DELIVER) {
            tickDelivery(level, villager, gameTime);
            return;
        }
        if (active == null) return;
        LivingEntity target = active.target();
        WorkJobDef.EntityTarget definition = active.job().entityTarget();
        villager.getLookControl().setLookAt(target, 30f, 30f);
        double rangeSq = definition.range() * definition.range();
        double dsq = villager.distanceToSqr(target);
        if (phase == Phase.PATH) {
            if (dsq <= rangeSq) {
                phase = Phase.ACT;
                nextActionTick = gameTime + definition.interval();
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                lastPathTick = gameTime;
                setWalkTarget(villager, target.blockPosition());
            }
            return;
        }
        if (dsq > rangeSq * 1.5) {
            phase = Phase.PATH;
            setWalkTarget(villager, target.blockPosition());
            return;
        }
        if (gameTime < nextActionTick) return;
        perform(level, villager, active, gameTime);
        active = null;
        if (deliveryPending(level, villager, worksite)) {
            phase = Phase.DELIVER;
            deliverAt = gameTime + WORK_DELAY;
        }
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        reset();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private void reset() {
        active = null;
        phase = Phase.PATH;
        worksite = Set.of();
        rejectedStorage.clear();
        deliveryTarget = null;
    }

    private void perform(ServerLevel level, VillagerEntityMCA villager, Pick pick, long gameTime) {
        WorkJobDef.EntityInteraction interaction = pick.interaction();
        java.util.Map<ResourceLocation, Integer> before = counts(villager, interaction::producesOutput);
        ActionContext context = new ActionContext(villager, pick.target());
        interaction.action().run(context);
        if (!context.succeeded()) return;
        int produced = 0;
        ResourceLocation firstOutput = null;
        for (var entry : counts(villager, interaction::producesOutput).entrySet()) {
            int gained = entry.getValue() - before.getOrDefault(entry.getKey(), 0);
            if (gained <= 0) continue;
            produced += gained;
            if (firstOutput == null) firstOutput = entry.getKey();
        }
        villager.swing(InteractionHand.MAIN_HAND, true);
        ResourceLocation career = ProfessionDefs.canonicalId(BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()));
        CareerProgression.completeWork(villager, career, interaction.xp(), gameTime,
                interaction.activityKey(pick.job()), produced > 0 ? firstOutput : null,
                produced > 0 ? "item" : null, Math.max(1, produced),
                java.util.Map.of("job", pick.job().id().toString(),
                        "amount", Integer.toString(Math.max(1, produced))));
    }

    private void tickDelivery(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Predicate<ItemStack> carried = declaredOutputs(villager);
        if (carried == null || !PhysicalStorageDelivery.hasMatching(villager, carried)) return;
        if (deliveryTarget == null) {
            if (gameTime < deliverAt) return;
            deliveryTarget = PhysicalStorageDelivery.findDestination(level, villager, worksite, carried, rejectedStorage);
            deliverAt = gameTime + WORK_DELAY;
            if (deliveryTarget == null) {
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                return;
            }
        }
        BlockPos at = deliveryTarget;
        villager.getLookControl().setLookAt(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
        if (villager.distanceToSqr(Vec3.atCenterOf(at)) > DELIVERY_RANGE_SQ) {
            setWalkTarget(villager, at);
            deliverAt = gameTime + WORK_DELAY;
            return;
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (gameTime < deliverAt) return;
        int moved = PhysicalStorageDelivery.depositMatchingAt(level, villager, at, carried);
        if (moved <= 0) rejectedStorage.add(at.asLong());
        villager.swing(InteractionHand.MAIN_HAND, true);
        deliveryTarget = null;
        deliverAt = gameTime + WORK_DELAY;
    }

    /** The nearest animal any declared entity-interaction Job may work on now. */
    private static @Nullable Pick pick(ServerLevel level, VillagerEntityMCA villager,
                                       @Nullable ResourceLocation onlyTask, boolean respectOrders) {
        if (!hasFreeSlot(villager)) return null;
        Set<Long> assigned = null;
        Pick best = null;
        double bestDistance = Double.MAX_VALUE;
        for (WorkTaskDef task : WorkTaskDeclarations.all(villager)) {
            if (onlyTask != null && !onlyTask.equals(task.type())) continue;
            for (WorkJobDef job : WorkJobs.forTask(task.type())) {
                WorkJobDef.EntityTarget definition = job.entityTarget();
                if (definition == null) continue;
                if (respectOrders && (!WorksiteOrders.mayStartFromWorkplace(level, villager, task.type())
                        || WorkActivities.hasHigherPriorityWork(level, villager, task.type()))) continue;
                if (assigned == null) assigned = assignedExtent(level, villager);
                List<WorkJobDef.EntityInteraction> usable = new ArrayList<>();
                for (WorkJobDef.EntityInteraction interaction : definition.interactions()) {
                    if (!interaction.requiresItem() || WorkIngredients.matchingToolAvailable(
                            level, villager, interaction::matches, villager.blockPosition(), assigned)) {
                        usable.add(interaction);
                    }
                }
                if (usable.isEmpty()) continue;
                for (Area area : areas(level, villager, definition, assigned)) {
                    for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, area.box(),
                            entity -> entity != villager && definition.matches(entity)
                                    && task.allowsEntity(entity.getType())
                                    && area.holds(level, villager, entity))) {
                        double distance = candidate.distanceToSqr(villager);
                        if (distance >= bestDistance) continue;
                        for (WorkJobDef.EntityInteraction interaction : usable) {
                            if (!interaction.ready(candidate)) continue;
                            best = new Pick(job, task, interaction, candidate, area.building());
                            bestDistance = distance;
                            break;
                        }
                    }
                }
            }
        }
        return best;
    }

    /** Where a Job's animals may stand: its named buildings, or else the worker's own site. */
    private record Area(AABB box, @Nullable Building building, Set<Long> extent) {
        boolean holds(ServerLevel level, VillagerEntityMCA villager, LivingEntity entity) {
            if (building != null) return contains(level, villager, building, entity);
            return extent.contains(entity.blockPosition().asLong())
                    || extent.contains(entity.blockPosition().below().asLong());
        }
    }

    private static List<Area> areas(ServerLevel level, VillagerEntityMCA villager,
                                    WorkJobDef.EntityTarget definition, Set<Long> assigned) {
        if (definition.buildings().isEmpty()) {
            if (assigned.isEmpty()) return List.of();
            return List.of(new Area(boxOf(assigned), null, assigned));
        }
        var village = ProfessionCapacity.resolveVillage(villager);
        if (village.isEmpty()) return List.of();
        List<Area> out = new ArrayList<>();
        for (Building building : McaBuildings.all(village.get())) {
            if (!building.isComplete() || !definition.matchesBuilding(building.getType())) continue;
            BlockPos p0 = building.getPos0();
            BlockPos p1 = building.getPos1();
            if (p0 == null || p1 == null) continue;
            out.add(new Area(new AABB(
                    Math.min(p0.getX(), p1.getX()), Math.min(p0.getY(), p1.getY()) - 1, Math.min(p0.getZ(), p1.getZ()),
                    Math.max(p0.getX(), p1.getX()) + 1, Math.max(p0.getY(), p1.getY()) + 3,
                    Math.max(p0.getZ(), p1.getZ()) + 1), building, Set.of()));
        }
        return out;
    }

    private static boolean contains(ServerLevel level, VillagerEntityMCA villager, Building building,
                                    LivingEntity entity) {
        Village village = ProfessionCapacity.resolveVillage(villager).orElse(null);
        return village != null && McaBuildingCompat.contains(level, village, building, entity.blockPosition());
    }

    private static AABB boxOf(Set<Long> cells) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long packed : cells) {
            BlockPos pos = BlockPos.of(packed);
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX()); maxY = Math.max(maxY, pos.getY()); maxZ = Math.max(maxZ, pos.getZ());
        }
        return new AABB(minX, minY - 1, minZ, maxX + 1, maxY + 3, maxZ + 1);
    }

    private static boolean acquireItem(ServerLevel level, VillagerEntityMCA villager,
                                       WorkJobDef.EntityInteraction interaction, Set<Long> extent) {
        if (!interaction.requiresItem() || carries(villager, interaction::matches)) return true;
        StationSupplies.pullMatching(level, villager, interaction::matches, 1, villager.blockPosition(), extent);
        return carries(villager, interaction::matches);
    }

    /** Outputs of every entity-interaction Job the worker declares, or null when none declare any. */
    private static @Nullable Predicate<ItemStack> declaredOutputs(VillagerEntityMCA villager) {
        List<WorkJobDef.EntityInteraction> producing = new ArrayList<>();
        for (WorkTaskDef task : WorkTaskDeclarations.all(villager)) {
            for (WorkJobDef job : WorkJobs.forTask(task.type())) {
                if (job.entityTarget() == null) continue;
                for (WorkJobDef.EntityInteraction interaction : job.entityTarget().interactions()) {
                    if (!interaction.outputs().isEmpty()) producing.add(interaction);
                }
            }
        }
        if (producing.isEmpty()) return null;
        return stack -> {
            if (stack.isEmpty()) return false;
            for (WorkJobDef.EntityInteraction interaction : producing) {
                if (interaction.producesOutput(stack)) return true;
            }
            return false;
        };
    }

    private static Set<Long> assignedExtent(ServerLevel level, VillagerEntityMCA villager) {
        ProfessionDef profession = ProfessionDefs.byId(BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()));
        return ProfessionSites.extentOf(level, villager, profession);
    }

    private static boolean carries(VillagerEntityMCA villager, Predicate<ItemStack> matcher) {
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && matcher.test(stack)) return true;
        }
        return false;
    }

    private static boolean hasFreeSlot(VillagerEntityMCA villager) {
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).isEmpty()) return true;
        }
        return false;
    }

    private static boolean deliveryPending(ServerLevel level, VillagerEntityMCA villager, Set<Long> extent) {
        Predicate<ItemStack> carried = declaredOutputs(villager);
        return carried != null && PhysicalStorageDelivery.hasMatching(villager, carried)
                && PhysicalStorageDelivery.findDestination(level, villager, extent, carried, Set.of()) != null;
    }

    private static java.util.Map<ResourceLocation, Integer> counts(VillagerEntityMCA villager,
                                                                   Predicate<ItemStack> matcher) {
        java.util.Map<ResourceLocation, Integer> counts = new java.util.LinkedHashMap<>();
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !matcher.test(stack)) continue;
            counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos pos) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(pos), WALK_SPEED, 1));
    }
}
