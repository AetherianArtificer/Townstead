package com.aetherianartificer.townstead.work.job;

import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.google.common.collect.ImmutableMap;
import com.aetherianartificer.townstead.work.WorkTaskDeclarations;
import com.aetherianartificer.townstead.profession.ProfessionSites;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.work.station.StationSupplies;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Executes data-authored {@code entity_delivery} Jobs. A worker walks to a
 * matching living source, performs the declared Pheno action until the declared
 * result appears, carries that real item to a free destination, and places it
 * using the authored offset and block-state mapping.
 *
 * <p>The engine knows no species, tool, output, or workstation names. It never
 * creates the expected result: another mod, vanilla, or the action itself must
 * emit it into the world for the delivery to continue.</p>
 */
public class EntityDeliveryWorkTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 600;
    private static final float WALK_SPEED = 0.58f;
    private static final int PATH_TIMEOUT_TICKS = 120;

    private enum Phase {
        /** Walk to the live target. */
        PATH,
        /** Within range, perform the declared action and observe its result. */
        ACT,
        /** Result acquired, walk to a free destination while carrying it. */
        CARRY,
        /** At the destination, place the carried result and finish. */
        PLACE
    }

    private static final int PLACE_COOLDOWN_TICKS = 15;
    private static final double DESTINATION_ARRIVAL_DISTANCE_SQ = 2.89;
    private static final int CARRY_PATH_TIMEOUT_TICKS = 300;

    @Nullable private LivingEntity target;
    @Nullable private Building activeBuilding;
    @Nullable private WorkJobDef activeJob;
    @Nullable private BlockPos destinationPos;
    @Nullable private BlockPos destinationStandPos;
    private Phase phase = Phase.PATH;
    private long startedTick;
    private long lastPathTick;
    private long nextActionTick;
    private long nextPlaceTick;
    private ItemStack previousMainHand = ItemStack.EMPTY;
    private boolean equippedJobItem;

    public EntityDeliveryWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    /** Whether this job has anything waiting, for the order list to defer to. */
    public static boolean hasWork(ServerLevel level, VillagerEntityMCA villager,
                                  @Nullable ResourceLocation task) {
        return pickSource(level, villager, task) != null;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        Pick pick = pickSource(level, villager, null);
        if (pick == null) return false;
        ResourceLocation task = pick.job.task();
        if (!WorkTaskDeclarations.permitsTask(villager, task)) return false;
        if (!com.aetherianartificer.townstead.work.order.WorksiteOrders.mayStart(
                level, villager, task)) return false;
        if (onCooldown(villager, pick.job, level)) return false;
        return !com.aetherianartificer.townstead.work.WorkActivities.hasHigherPriorityWork(
                level, villager, task);
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Pick pick = pickSource(level, villager, null);
        if (pick == null) return;
        activeBuilding = pick.building;
        activeJob = pick.job;
        if (!acquireJobItem(level, villager, activeJob)) {
            activeBuilding = null;
            activeJob = null;
            return;
        }
        target = pick.target;
        phase = Phase.PATH;
        startedTick = gameTime;
        lastPathTick = gameTime;
        nextActionTick = gameTime + activeJob.source().interval();
        setWalkTarget(villager, target.blockPosition());
        equipJobItem(villager);
    }

    private record Pick(WorkJobDef job, Building building, LivingEntity target) {}

    @Nullable
    private static Pick pickSource(ServerLevel level, VillagerEntityMCA villager,
                                   @Nullable ResourceLocation onlyTask) {
        for (WorkJobDef job : WorkJobs.forType(WorkJobDef.ENTITY_DELIVERY)) {
            if (onlyTask != null && !onlyTask.equals(job.task())) continue;
            if (!WorkTaskDeclarations.permitsTask(villager, job.task())) continue;
            if (onCooldown(villager, job, level) || !hasAvailableDestination(level, villager, job)) continue;
            WorkJobDef.EntitySource source = job.source();
            if (source == null) continue;
            for (Building building : villageBuildings(villager)) {
                if (!building.isComplete() || !source.matchesBuilding(building.getType())) continue;
                LivingEntity target = findTargetIn(level, villager, building, job);
                if (target != null) return new Pick(job, building, target);
            }
        }
        return null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (gameTime - startedTick > MAX_DURATION) return false;
        switch (phase) {
            case PATH, ACT -> {
                if (target == null || !target.isAlive()) return false;
                if (activeBuilding != null && !activeBuilding.containsPos(target.blockPosition())) return false;
                if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) return false;
            }
            case CARRY -> {
                if (destinationPos == null) return false;
                if (!isDestination(level, destinationPos, activeJob)) return false;
                if (!destinationAvailable(level, destinationPos, activeJob)) return false;
                if (gameTime - lastPathTick > CARRY_PATH_TIMEOUT_TICKS) return false;
                if (!villagerCarriesResult(villager, activeJob)) return false;
            }
            case PLACE -> {
                if (destinationPos == null) return false;
                if (!isDestination(level, destinationPos, activeJob)) return false;
                if (!destinationAvailable(level, destinationPos, activeJob)) return false;
                if (!villagerCarriesResult(villager, activeJob)) return false;
            }
        }
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        switch (phase) {
            case PATH, ACT -> tickSource(level, villager, gameTime);
            case CARRY -> tickCarry(level, villager, gameTime);
            case PLACE -> tickPlace(level, villager, gameTime);
        }
    }

    private void tickSource(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target == null) return;
        WorkJobDef.EntitySource source = activeJob == null ? null : activeJob.source();
        if (source == null) return;
        villager.getLookControl().setLookAt(target, 30f, 30f);

        double dsq = villager.distanceToSqr(target);
        double actionRangeSq = source.range() * source.range();
        if (phase == Phase.PATH) {
            if (dsq <= actionRangeSq) {
                phase = Phase.ACT;
                nextActionTick = gameTime + source.interval();
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                setWalkTarget(villager, target.blockPosition());
            }
            return;
        }

        if (dsq > actionRangeSq * 1.5) {
            phase = Phase.PATH;
            setWalkTarget(villager, target.blockPosition());
            return;
        }
        if (gameTime < nextActionTick) return;
        float healthBefore = target.getHealth();
        source.action().run(new com.aetherianartificer.townstead.pheno.action.ActionContext(
                villager, target));
        ItemStack result = takeFreshResult(level, target, activeJob);
        boolean changed = target.getHealth() < healthBefore || !result.isEmpty();
        if (changed && equippedJobItem) {
            com.aetherianartificer.townstead.work.item.WorkToolDurability
                    .damageFirstMatching(villager, source::matches);
        }
        nextActionTick = gameTime + source.interval();
        if (!result.isEmpty()) {
            LivingEntity completed = target;
            // A producer may cancel a terminal action after materialising its replacement.
            // Once the declared output exists, the source has been consumed for this Job.
            if (completed.isAlive()) completed.discard();
            acceptResult(level, villager, completed.blockPosition(), gameTime, result);
            target = null;
        }
    }

    private void tickCarry(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (destinationPos == null) return;
        BlockPos anchor = destinationStandPos != null ? destinationStandPos : destinationPos.below();
        villager.getLookControl().setLookAt(
                destinationPos.getX() + 0.5, destinationPos.getY(), destinationPos.getZ() + 0.5);
        double dsq = villager.distanceToSqr(
                anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
        if (dsq <= DESTINATION_ARRIVAL_DISTANCE_SQ) {
            phase = Phase.PLACE;
            nextPlaceTick = gameTime + PLACE_COOLDOWN_TICKS;
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            return;
        }
        setWalkTarget(villager, anchor);
    }

    private void tickPlace(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (destinationPos == null) return;
        villager.getLookControl().setLookAt(
                destinationPos.getX() + 0.5, destinationPos.getY() + 0.5, destinationPos.getZ() + 0.5);
        if (gameTime < nextPlaceTick) return;

        int slot = findCarriedResultSlot(villager, activeJob);
        if (slot < 0) return; // lost the item somehow; bail

        ItemStack carried = villager.getInventory().getItem(slot);
        Block resultBlock = resolveResultBlock(carried);
        if (resultBlock == null) return;

        BlockPos placedPos = placementPos(activeJob, destinationPos);
        BlockState existing = level.getBlockState(placedPos);
        if (!existing.isAir() && !existing.canBeReplaced()) return;

        BlockState placed = placedState(activeJob, resultBlock, level.getBlockState(destinationPos));
        level.setBlock(placedPos, placed, 3);
        WorkJobDef.Placement placement = activeJob.destination().placement();
        if (placement.action() != null) {
            placement.action().run(new com.aetherianartificer.townstead.pheno.action.block.BlockActionContext(
                    level, placedPos, villager));
        }
        villager.swing(InteractionHand.MAIN_HAND, true);
        carried.shrink(1);
        villager.getInventory().setChanged();
        markCooldown(villager, activeJob, gameTime);
        awardXp(villager, activeJob, activeJob.source().xp(), gameTime);

        destinationPos = null;
        destinationStandPos = null;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        target = null;
        activeBuilding = null;
        activeJob = null;
        destinationPos = null;
        destinationStandPos = null;
        phase = Phase.PATH;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        restorePreviousHand(villager);
    }

    private void equipJobItem(VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        int itemSlot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (activeJob != null && activeJob.source() != null
                    && activeJob.source().matches(inv.getItem(i))) {
                itemSlot = i;
                break;
            }
        }
        if (itemSlot < 0) return;
        ItemStack current = villager.getMainHandItem();
        previousMainHand = current.isEmpty() ? ItemStack.EMPTY : current.copy();
        villager.setItemInHand(InteractionHand.MAIN_HAND, inv.getItem(itemSlot).copy());
        equippedJobItem = true;
    }

    private static boolean acquireJobItem(ServerLevel level, VillagerEntityMCA villager,
                                          @Nullable WorkJobDef job) {
        WorkJobDef.EntitySource source = job == null ? null : job.source();
        if (source == null) return false;
        if (source.item() == null) return true;
        SimpleContainer inventory = villager.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (source.matches(inventory.getItem(slot))) return true;
        }
        ResourceLocation professionId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(
                villager.getVillagerData().getProfession());
        var extent = ProfessionSites.extentOf(level, villager, ProfessionDefs.byId(professionId));
        StationSupplies.pullMatching(level, villager, source::matches, 1,
                villager.blockPosition(), extent);
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (source.matches(inventory.getItem(slot))) return true;
        }
        return false;
    }

    private void restorePreviousHand(VillagerEntityMCA villager) {
        if (!equippedJobItem) return;
        villager.setItemInHand(InteractionHand.MAIN_HAND, previousMainHand);
        previousMainHand = ItemStack.EMPTY;
        equippedJobItem = false;
    }

    // --- helpers ---

    @Nullable
    private static LivingEntity findTargetIn(ServerLevel level, VillagerEntityMCA villager,
                                             Building building, WorkJobDef job) {
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        if (p0 == null || p1 == null) return null;
        AABB search = new AABB(
                Math.min(p0.getX(), p1.getX()), Math.min(p0.getY(), p1.getY()), Math.min(p0.getZ(), p1.getZ()),
                Math.max(p0.getX(), p1.getX()) + 1, Math.max(p0.getY(), p1.getY()) + 1, Math.max(p0.getZ(), p1.getZ()) + 1);
        WorkJobDef.EntitySource source = job.source();
        if (source == null) return null;
        var declaredTask = WorkTaskDeclarations.first(villager, job.task());
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, search,
                entity -> building.containsPos(entity.blockPosition())
                        && !villager.getUUID().equals(entity.getUUID())
                        && source.matches(entity)
                        && (declaredTask == null || declaredTask.allowsEntity(entity.getType())));
        LivingEntity best = null;
        double bestDsq = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double dsq = candidate.distanceToSqr(villager);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = candidate;
            }
        }
        return best;
    }

    private void acceptResult(ServerLevel level, VillagerEntityMCA villager,
                              BlockPos sourcePos, long gameTime, ItemStack result) {
        BlockPos destination = selectDestination(level, villager, sourcePos);
        if (destination == null) {
            ItemStack drop = result;
            if (!drop.isEmpty()) {
                ItemEntity ie = new ItemEntity(level,
                        sourcePos.getX() + 0.5, sourcePos.getY() + 0.25, sourcePos.getZ() + 0.5, drop);
                ie.setPickUpDelay(10);
                level.addFreshEntity(ie);
            }
            markCooldown(villager, activeJob, gameTime);
            awardXp(villager, activeJob, activeJob.source().xp(), gameTime);
            return;
        }

        ItemStack remaining = villager.getInventory().addItem(result);
        if (!remaining.isEmpty()) {
            ItemEntity ie = new ItemEntity(level,
                    sourcePos.getX() + 0.5, sourcePos.getY() + 0.25, sourcePos.getZ() + 0.5, remaining);
            ie.setPickUpDelay(10);
            level.addFreshEntity(ie);
            markCooldown(villager, activeJob, gameTime);
            awardXp(villager, activeJob, activeJob.source().xp(), gameTime);
            return;
        }

        destinationPos = destination.immutable();
        destinationStandPos = findDestinationStandPos(level, villager, destinationPos, activeJob);
        phase = Phase.CARRY;
        lastPathTick = gameTime;
        setWalkTarget(villager, destinationStandPos != null ? destinationStandPos : destinationPos.below());
    }

    private static ItemStack takeFreshResult(ServerLevel level, LivingEntity target,
                                             @Nullable WorkJobDef job) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());
        ResourceLocation expected = job == null ? null : job.resultFor(entityId);
        if (expected == null) return ItemStack.EMPTY;
        AABB area = target.getBoundingBox().inflate(2.0);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area,
                candidate -> candidate.tickCount <= 1
                        && expected.equals(BuiltInRegistries.ITEM.getKey(
                                candidate.getItem().getItem())))) {
            ItemStack result = item.getItem().split(1);
            if (item.getItem().isEmpty()) item.discard();
            return result;
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    private BlockPos selectDestination(ServerLevel level, VillagerEntityMCA villager, BlockPos origin) {
        WorkJobDef job = activeJob;
        WorkJobDef.BlockTarget destination = job == null ? null : job.destination();
        if (job == null || destination == null) return null;
        if (activeBuilding != null && destination.matchesBuilding(activeBuilding.getType())) {
            BlockPos local = findFreeDestinationInBuilding(level, activeBuilding, job);
            if (local != null) return local;
        }
        // Building discovery is capability-sorted; distance keeps a delivery local.
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (Building building : villageBuildings(villager)) {
            if (!building.isComplete() || !destination.matchesBuilding(building.getType())) continue;
            BlockPos candidate = findFreeDestinationInBuilding(level, building, job);
            if (candidate == null) continue;
            double dsq = candidate.distSqr(origin);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = candidate;
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos findFreeDestinationInBuilding(ServerLevel level, Building building,
                                                          WorkJobDef job) {
        WorkJobDef.BlockTarget destination = job.destination();
        if (destination == null) return null;
        for (ResourceLocation block : destination.blocks()) {
            var positions = building.getBlocks().get(block);
            if (positions == null) continue;
            for (BlockPos pos : positions) {
                if (isDestination(level, pos, job) && destinationAvailable(level, pos, job)) return pos;
            }
        }
        if (!destination.blockTags().isEmpty()) {
            for (var positions : building.getBlocks().values()) {
                for (BlockPos pos : positions) {
                    if (isDestination(level, pos, job) && destinationAvailable(level, pos, job)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private static boolean isDestination(ServerLevel level, BlockPos pos, @Nullable WorkJobDef job) {
        if (job == null) return false;
        WorkJobDef.BlockTarget destination = job.destination();
        if (destination == null) return false;
        return destination.matches(level, pos);
    }

    private static boolean destinationAvailable(ServerLevel level, BlockPos destination,
                                                @Nullable WorkJobDef job) {
        BlockState existing = level.getBlockState(placementPos(job, destination));
        return existing.isAir() || existing.canBeReplaced();
    }

    @Nullable
    private static Block resolveResultBlock(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem bi)) return null;
        return bi.getBlock();
    }

    private static BlockState placedState(@Nullable WorkJobDef job, Block result, BlockState sourceState) {
        BlockState state = result.defaultBlockState();
        WorkJobDef.BlockTarget destination = job == null ? null : job.destination();
        WorkJobDef.Placement placement = destination == null
                ? WorkJobDef.Placement.DEFAULT : destination.placement();
        for (var entry : placement.properties().entrySet()) {
            state = setProperty(state, entry.getKey(), entry.getValue());
        }
        for (String property : placement.copyProperties()) {
            String value = propertyValue(sourceState, property);
            if (value != null) state = setProperty(state, property, value);
        }
        return state;
    }

    private static BlockPos placementPos(@Nullable WorkJobDef job, BlockPos destinationPos) {
        WorkJobDef.BlockTarget destination = job == null ? null : job.destination();
        WorkJobDef.Placement placement = destination == null
                ? WorkJobDef.Placement.DEFAULT : destination.placement();
        return destinationPos.offset(placement.x(), placement.y(), placement.z());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState setProperty(BlockState state, String name, String rawValue) {
        Property property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) return state;
        Optional value = property.getValue(rawValue);
        return value.isPresent() ? state.setValue(property, (Comparable) value.get()) : state;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @Nullable String propertyValue(BlockState state, String name) {
        Property property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) return null;
        return property.getName((Comparable) state.getValue(property));
    }

    @Nullable
    private static BlockPos findDestinationStandPos(ServerLevel level, VillagerEntityMCA villager,
                                                    BlockPos destination, WorkJobDef job) {
        BlockPos placement = placementPos(job, destination);
        BlockPos[] candidates = {
                placement.north(), placement.south(), placement.east(), placement.west()
        };
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        BlockPos villagerPos = villager.blockPosition();
        for (BlockPos c : candidates) {
            BlockPos floor = findFloor(level, c);
            if (floor == null) continue;
            double dsq = floor.distSqr(villagerPos);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = floor;
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos findFloor(ServerLevel level, BlockPos start) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos().set(start);
        for (int drop = 0; drop <= 4; drop++) {
            BlockState below = level.getBlockState(cursor.below());
            if (!below.isAir() && !below.canBeReplaced()) {
                BlockState at = level.getBlockState(cursor);
                BlockState head = level.getBlockState(cursor.above());
                if ((at.isAir() || at.canBeReplaced())
                        && (head.isAir() || head.canBeReplaced())) {
                    return cursor.immutable();
                }
                return null;
            }
            cursor.move(Direction.DOWN);
        }
        return null;
    }

    private static boolean villagerCarriesResult(VillagerEntityMCA villager,
                                                 @Nullable WorkJobDef job) {
        return findCarriedResultSlot(villager, job) >= 0;
    }

    private static int findCarriedResultSlot(VillagerEntityMCA villager,
                                             @Nullable WorkJobDef job) {
        if (job == null) return -1;
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem bi)) continue;
            ResourceLocation block = BuiltInRegistries.BLOCK.getKey(bi.getBlock());
            if (job.producesBlock(block)) return i;
        }
        return -1;
    }

    private static boolean hasAvailableDestination(ServerLevel level, VillagerEntityMCA villager,
                                                   WorkJobDef job) {
        WorkJobDef.BlockTarget destination = job.destination();
        if (destination == null) return false;
        for (Building building : villageBuildings(villager)) {
            if (!building.isComplete() || !destination.matchesBuilding(building.getType())) continue;
            if (findFreeDestinationInBuilding(level, building, job) != null) return true;
        }
        return false;
    }

    private static List<Building> villageBuildings(VillagerEntityMCA villager) {
        Optional<net.conczin.mca.server.world.data.Village> village = villager.getResidency().getHomeVillage();
        if (village.isEmpty() || !village.get().isWithinBorder(villager)) {
            village = net.conczin.mca.server.world.data.Village.findNearest(villager);
        }
        if (village.isEmpty() || !village.get().isWithinBorder(villager)) return List.of();
        return List.copyOf(com.aetherianartificer.townstead.compat.mca.McaBuildings.all(village.get()));
    }

    private static boolean onCooldown(VillagerEntityMCA villager, WorkJobDef job,
                                      ServerLevel level) {
        long last = TownsteadVillagers.get(villager).professionMemory()
                .cooldown(job.activityKey());
        return level.getGameTime() - last < job.source().cooldown(level);
    }

    private static void markCooldown(VillagerEntityMCA villager, @Nullable WorkJobDef job,
                                     long gameTime) {
        if (job != null) TownsteadVillagers.get(villager).professionMemory()
                .setCooldown(job.activityKey(), gameTime);
    }

    private static void awardXp(VillagerEntityMCA villager, @Nullable WorkJobDef job,
                                int amount, long gameTime) {
        if (amount <= 0) return;
        String activity = job == null ? "townstead:entity_delivery" : job.activityKey();
        ResourceLocation career = ProfessionDefs.canonicalId(BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession()));
        com.aetherianartificer.townstead.profession.career.CareerProgression.completeWork(
                villager, career,
                amount, gameTime, activity, null, null, amount,
                job == null ? java.util.Map.of()
                        : java.util.Map.of("job", job.id().toString()));
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos pos) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(pos), WALK_SPEED, 1));
    }
}
