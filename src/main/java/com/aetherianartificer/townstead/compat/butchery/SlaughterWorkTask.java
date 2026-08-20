package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.villager.ProfessionProgress;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.aetherianartificer.townstead.tick.WorkToolTicker;
import com.google.common.collect.ImmutableMap;
import com.aetherianartificer.townstead.work.WorkTaskDeclarations;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import com.aetherianartificer.townstead.work.job.WorkJobDef;
import com.aetherianartificer.townstead.work.job.WorkJobs;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Animal;
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
 * Butchers at Tier 2+ shops walk to whitelisted farm animals within shop
 * bounds, kill them over multiple strokes, then carry the resulting
 * carcass item by hand to the nearest free {@code butchery:hook} and
 * hang it — matching what a player does in Butchery (kill → pick up the
 * carcass item → walk to a hook → right-click to hang). No teleportation:
 * the villager physically walks the carcass from the kill site to the
 * display hook. Downstream {@link CarcassWorkTask} handles bleeding and
 * processing.
 *
 * <p>Scope guards: {@link SlaughterPolicy} filters species, excludes babies /
 * named / pets, and enforces a per-villager throttle. Requires a hook
 * inside the shop bounds so the player has placed explicit slaughter
 * infrastructure; without one, this task never fires.
 */
public class SlaughterWorkTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 600;
    private static final double ATTACK_RANGE_SQ = 4.0;
    private static final float WALK_SPEED = 0.58f;
    private static final int PATH_TIMEOUT_TICKS = 120;
    /** Ticks between strokes. 1s reads as deliberate butcher pacing, not
     *  the frantic-attack rhythm of a mob or a fleeing player. */
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    /** Floor damage per stroke. Chickens / rabbits (≤4 HP) die in one. */
    private static final float MIN_STROKE_DAMAGE = 4.0f;
    /** Divisor over max health for bigger targets so hoglins / polar bears
     *  still die in 3-4 strokes rather than a long grind. */
    private static final float STROKE_HEALTH_DIVISOR = 4.0f;

    private enum Phase {
        /** Walk to the live target. */
        PATH,
        /** Within range, strike until it dies. */
        ATTACK,
        /** Animal killed, walk to a free hook carrying the carcass item. */
        CARRY,
        /** At the hook, place the carcass block and finish. */
        PLACE
    }

    private static final int PLACE_COOLDOWN_TICKS = 15;
    private static final double HOOK_ARRIVAL_DISTANCE_SQ = 2.89;
    private static final int CARRY_PATH_TIMEOUT_TICKS = 300;

    //? if >=1.21 {
    private static final ResourceLocation SOUND_CHAIN_HIT =
            ResourceLocation.parse("block.chain.hit");
    //?} else {
    /*private static final ResourceLocation SOUND_CHAIN_HIT =
            new ResourceLocation("block.chain.hit");
    *///?}

    @Nullable private LivingEntity target;
    @Nullable private Building activeBuilding;
    @Nullable private WorkJobDef activeJob;
    @Nullable private BlockPos targetHook;
    @Nullable private BlockPos hookStandPos;
    private Phase phase = Phase.PATH;
    private long startedTick;
    private long lastPathTick;
    private long nextAttackTick;
    private long nextPlaceTick;
    private ItemStack preSlaughterMainHand = ItemStack.EMPTY;
    private boolean swappedToKnife;

    public SlaughterWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    /** Whether this job has anything waiting, for the order list to defer to. */
    static boolean hasWorkWaiting(net.minecraft.server.level.ServerLevel level,
                                  net.conczin.mca.entity.VillagerEntityMCA villager) {
        return pickBuildingWithTarget(level, villager) != null;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return false;
        if (!SlaughterPolicy.slaughterEnabledFor(villager)) return false;
        if (!WorkTaskDeclarations.permitsTask(villager, WorkTaskTypes.SLAUGHTER)) return false;
        // The worksite may have been told to leave this job alone, to work only what is
        // on its list, or to do something above this first. Costs nothing where no
        // activity line exists.
        if (!com.aetherianartificer.townstead.work.order.WorksiteOrders.mayStart(
                level, villager, WorkTaskTypes.SLAUGHTER)) return false;
        if (onThrottle(villager, level.getGameTime())) return false;
        if (CarcassWorkTask.hasActionableWork(level, villager)) return false;
        return pickBuildingWithTarget(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Pick pick = pickBuildingWithTarget(level, villager);
        if (pick == null) return;
        activeBuilding = pick.building;
        activeJob = jobDef();
        target = pick.target;
        phase = Phase.PATH;
        startedTick = gameTime;
        lastPathTick = gameTime;
        nextAttackTick = gameTime + ATTACK_COOLDOWN_TICKS;
        setWalkTarget(villager, target.blockPosition());
        equipKnifeIfAvailable(villager);
    }

    private record Pick(Building building, LivingEntity target) {}

    @Nullable
    private static Pick pickBuildingWithTarget(ServerLevel level, VillagerEntityMCA villager) {
        WorkJobDef job = jobDef();
        if (job == null || !hasAvailableDestination(level, villager, job)) return null;
        WorkJobDef.Role source = job.first(WorkJobDef.RoleKind.ENTITY);
        if (source == null) return null;
        for (Building building : villageBuildings(villager)) {
            if (!building.isComplete() || !source.matchesBuilding(building.getType())) continue;
            LivingEntity target = findTargetIn(level, villager, building);
            if (target != null) return new Pick(building, target);
        }
        return null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (gameTime - startedTick > MAX_DURATION) return false;
        switch (phase) {
            case PATH, ATTACK -> {
                if (target == null || !target.isAlive()) return false;
                // Animal wandered out of the huntable building (e.g. a pen with
                // no fence on one side). Abort rather than chase across the
                // world or path-timeout standing in the doorway.
                if (activeBuilding != null && !activeBuilding.containsPos(target.blockPosition())) return false;
                if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) return false;
            }
            case CARRY -> {
                if (targetHook == null) return false;
                if (!isDestination(level, targetHook, activeJob)) return false;
                if (!destinationAvailable(level, targetHook, activeJob)) return false;
                if (gameTime - lastPathTick > CARRY_PATH_TIMEOUT_TICKS) return false;
                if (!villagerCarriesCarcass(villager)) return false;
            }
            case PLACE -> {
                if (targetHook == null) return false;
                if (!isDestination(level, targetHook, activeJob)) return false;
                if (!destinationAvailable(level, targetHook, activeJob)) return false;
                if (!villagerCarriesCarcass(villager)) return false;
            }
        }
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        switch (phase) {
            case PATH, ATTACK -> tickKillLoop(level, villager, gameTime);
            case CARRY -> tickCarry(level, villager, gameTime);
            case PLACE -> tickPlace(level, villager, gameTime);
        }
    }

    private void tickKillLoop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (target == null) return;
        villager.getLookControl().setLookAt(target, 30f, 30f);

        double dsq = villager.distanceToSqr(target);
        if (phase == Phase.PATH) {
            if (dsq <= ATTACK_RANGE_SQ) {
                phase = Phase.ATTACK;
                nextAttackTick = gameTime + ATTACK_COOLDOWN_TICKS;
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                setWalkTarget(villager, target.blockPosition());
            }
            return;
        }

        // ATTACK phase
        if (dsq > ATTACK_RANGE_SQ * 1.5) {
            phase = Phase.PATH;
            setWalkTarget(villager, target.blockPosition());
            return;
        }
        if (gameTime < nextAttackTick) return;
        villager.swing(InteractionHand.MAIN_HAND, true);
        if (swappedToKnife) {
            ButcherToolDamage.consumeKnifeUse(villager);
        }
        DamageSource source = level.damageSources().mobAttack(villager);
        float damage = strokeDamageFor(target);
        float healthBefore = target.getHealth();
        boolean hurt = target.hurt(source, damage);
        nextAttackTick = gameTime + ATTACK_COOLDOWN_TICKS;
        if (hurt && !target.isAlive()) {
            onTargetKilled(level, villager, target, gameTime);
            target = null;
            return;
        }

        // Butchery can cancel the lethal death event, leave the animal alive, and materialize
        // its carcass as an item. Observe that public result instead of attacking forever or
        // importing Butchery's event implementation. Only consume a same-tick expected carcass.
        if (target.isAlive() && damage >= healthBefore) {
            ItemStack externalCarcass = takeFreshExternalCarcass(level, target);
            if (!externalCarcass.isEmpty()) {
                LivingEntity killed = target;
                killed.discard();
                onTargetKilled(level, villager, killed, gameTime, externalCarcass);
                target = null;
            }
        }
    }

    private void tickCarry(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetHook == null) return;
        BlockPos anchor = hookStandPos != null ? hookStandPos : targetHook.below();
        villager.getLookControl().setLookAt(
                targetHook.getX() + 0.5, targetHook.getY(), targetHook.getZ() + 0.5);
        double dsq = villager.distanceToSqr(
                anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
        if (dsq <= HOOK_ARRIVAL_DISTANCE_SQ) {
            phase = Phase.PLACE;
            nextPlaceTick = gameTime + PLACE_COOLDOWN_TICKS;
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            return;
        }
        lastPathTick = gameTime;
        setWalkTarget(villager, anchor);
    }

    private void tickPlace(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetHook == null) return;
        villager.getLookControl().setLookAt(
                targetHook.getX() + 0.5, targetHook.getY() + 0.5, targetHook.getZ() + 0.5);
        if (gameTime < nextPlaceTick) return;

        int slot = findCarriedCarcassSlot(villager);
        if (slot < 0) return; // lost the item somehow; bail

        ItemStack carried = villager.getInventory().getItem(slot);
        Block carcass = resolveCarcassBlock(carried);
        if (carcass == null) return;

        BlockPos carcassPos = placementPos(activeJob, targetHook);
        BlockState existing = level.getBlockState(carcassPos);
        if (!existing.isAir() && !existing.canBeReplaced()) return;

        BlockState placed = placedState(activeJob, carcass, level.getBlockState(targetHook));
        level.setBlock(carcassPos, placed, 3);
        level.playSound(null, targetHook,
                BuiltInRegistries.SOUND_EVENT.get(SOUND_CHAIN_HIT),
                SoundSource.NEUTRAL, 1f, 1f);
        villager.swing(InteractionHand.MAIN_HAND, true);
        carried.shrink(1);
        villager.getInventory().setChanged();
        markThrottle(villager, gameTime);
        awardXp(villager, 2, gameTime);

        targetHook = null;
        hookStandPos = null;
    }

    /**
     * Per-stroke damage scales with the target's max health so small
     * animals die in one strike and larger ones need a proper 3-4 stroke
     * sequence. Matches a butcher's deliberate pacing rather than a one-
     * shot kill, which reads as unnatural when you see it happen.
     */
    private static float strokeDamageFor(LivingEntity target) {
        return Math.max(MIN_STROKE_DAMAGE, target.getMaxHealth() / STROKE_HEALTH_DIVISOR);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        target = null;
        activeBuilding = null;
        activeJob = null;
        targetHook = null;
        hookStandPos = null;
        phase = Phase.PATH;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        restorePreSlaughterHand(villager);
    }

    private void equipKnifeIfAvailable(VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        int knifeSlot = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (WorkToolTicker.isKnife(inv.getItem(i))) {
                knifeSlot = i;
                break;
            }
        }
        if (knifeSlot < 0) return;
        ItemStack current = villager.getMainHandItem();
        // Only stash something worth restoring. If main hand is already a knife
        // or empty, there's nothing meaningful to return to after slaughter.
        preSlaughterMainHand = current.isEmpty() ? ItemStack.EMPTY : current.copy();
        villager.setItemInHand(InteractionHand.MAIN_HAND, inv.getItem(knifeSlot).copy());
        swappedToKnife = true;
    }

    private void restorePreSlaughterHand(VillagerEntityMCA villager) {
        if (!swappedToKnife) return;
        villager.setItemInHand(InteractionHand.MAIN_HAND, preSlaughterMainHand);
        preSlaughterMainHand = ItemStack.EMPTY;
        swappedToKnife = false;
    }

    // --- helpers ---

    @Nullable
    private static LivingEntity findTargetIn(ServerLevel level, VillagerEntityMCA villager, Building building) {
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        if (p0 == null || p1 == null) return null;
        AABB search = new AABB(
                Math.min(p0.getX(), p1.getX()), Math.min(p0.getY(), p1.getY()), Math.min(p0.getZ(), p1.getZ()),
                Math.max(p0.getX(), p1.getX()) + 1, Math.max(p0.getY(), p1.getY()) + 1, Math.max(p0.getZ(), p1.getZ()) + 1);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, search,
                a -> building.containsPos(a.blockPosition())
                        && SlaughterPolicy.canSlaughter(villager, a));
        LivingEntity best = null;
        double bestDsq = Double.MAX_VALUE;
        for (Animal a : animals) {
            double dsq = a.distanceToSqr(villager);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = a;
            }
        }
        return best;
    }

    /**
     * On kill: select a free hook the villager should walk the carcass
     * to, put the carcass item into the villager's inventory, and switch
     * to the CARRY phase. No block is placed yet — that happens when the
     * villager arrives at the hook in {@link #tickPlace}.
     */
    private void onTargetKilled(ServerLevel level, VillagerEntityMCA villager,
                                LivingEntity killed, long gameTime) {
        onTargetKilled(level, villager, killed, gameTime, makeCarcassItemFor(killed));
    }

    private void onTargetKilled(ServerLevel level, VillagerEntityMCA villager,
                                LivingEntity killed, long gameTime, ItemStack carcassItem) {
        BlockPos hook = selectFreeHook(level, villager, killed.blockPosition());
        if (hook == null) {
            // No available hook anywhere; drop the carcass item at the kill
            // site so the player can hang it manually. Better than losing
            // it silently.
            BlockPos dropAt = killed.blockPosition();
            ItemStack drop = carcassItem;
            if (!drop.isEmpty()) {
                ItemEntity ie = new ItemEntity(level,
                        dropAt.getX() + 0.5, dropAt.getY() + 0.25, dropAt.getZ() + 0.5, drop);
                ie.setPickUpDelay(10);
                level.addFreshEntity(ie);
            }
            markThrottle(villager, gameTime);
            awardXp(villager, 2, gameTime);
            return;
        }

        if (carcassItem.isEmpty()) {
            markThrottle(villager, gameTime);
            return;
        }
        ItemStack remaining = villager.getInventory().addItem(carcassItem);
        if (!remaining.isEmpty()) {
            // Inventory full; drop what didn't fit at the kill site and
            // abort the carry. Player can hang it.
            BlockPos dropAt = killed.blockPosition();
            ItemEntity ie = new ItemEntity(level,
                    dropAt.getX() + 0.5, dropAt.getY() + 0.25, dropAt.getZ() + 0.5, remaining);
            ie.setPickUpDelay(10);
            level.addFreshEntity(ie);
            markThrottle(villager, gameTime);
            awardXp(villager, 2, gameTime);
            return;
        }

        targetHook = hook.immutable();
        hookStandPos = findHookStandPos(level, villager, targetHook);
        phase = Phase.CARRY;
        lastPathTick = gameTime;
        setWalkTarget(villager, hookStandPos != null ? hookStandPos : targetHook.below());
    }

    private static ItemStack takeFreshExternalCarcass(ServerLevel level, LivingEntity target) {
        ResourceLocation expected = SlaughterPolicy.carcassIdFor(target.getType());
        if (expected == null) return ItemStack.EMPTY;
        AABB area = target.getBoundingBox().inflate(2.0);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area,
                candidate -> candidate.tickCount <= 1
                        && expected.equals(BuiltInRegistries.ITEM.getKey(
                                candidate.getItem().getItem())))) {
            ItemStack carcass = item.getItem().split(1);
            if (item.getItem().isEmpty()) item.discard();
            return carcass;
        }
        return ItemStack.EMPTY;
    }

    /** Nearest free hook: either in the building that held the target
     *  (in-shop kill) or in any carcass-capable shop in the village
     *  (pen kill). A "free" hook has air at hook.below(). */
    @Nullable
    private BlockPos selectFreeHook(ServerLevel level, VillagerEntityMCA villager, BlockPos origin) {
        WorkJobDef job = activeJob != null ? activeJob : jobDef();
        WorkJobDef.Role destination = job == null ? null : job.first(WorkJobDef.RoleKind.BLOCK);
        if (job == null || destination == null) return null;
        if (activeBuilding != null && destination.matchesBuilding(activeBuilding.getType())) {
            BlockPos local = findFreeDestinationInBuilding(level, activeBuilding, job);
            if (local != null) return local;
        }
        // Fall back to the nearest free hook elsewhere in the village. The
        // scanner is tier-sorted, which is good for capability checks but
        // makes pen kills cross town when a higher-tier shop happens to sort
        // first. Distance keeps the carcass pipeline visually local.
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (Building building : villageBuildings(villager)) {
            if (!building.isComplete() || !destination.matchesBuilding(building.getType())) continue;
            BlockPos hook = findFreeDestinationInBuilding(level, building, job);
            if (hook == null) continue;
            double dsq = hook.distSqr(origin);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = hook;
            }
        }
        return best;
    }

    @Nullable
    private static BlockPos findFreeDestinationInBuilding(ServerLevel level, Building building,
                                                          WorkJobDef job) {
        WorkJobDef.Role destination = job.first(WorkJobDef.RoleKind.BLOCK);
        if (destination == null) return null;
        for (ResourceLocation block : destination.blocks()) {
            var positions = building.getBlocks().get(block);
            if (positions == null) continue;
            for (BlockPos pos : positions) {
                if (isDestination(level, pos, job) && destinationAvailable(level, pos, job)) return pos;
            }
        }
        return null;
    }

    private static boolean isDestination(ServerLevel level, BlockPos pos, @Nullable WorkJobDef job) {
        if (job == null) return false;
        WorkJobDef.Role destination = job.first(WorkJobDef.RoleKind.BLOCK);
        if (destination == null) return false;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        return destination.blocks().contains(id);
    }

    private static boolean destinationAvailable(ServerLevel level, BlockPos destination,
                                                @Nullable WorkJobDef job) {
        BlockState existing = level.getBlockState(placementPos(job, destination));
        return existing.isAir() || existing.canBeReplaced();
    }

    private static ItemStack makeCarcassItemFor(LivingEntity killed) {
        ResourceLocation carcassId = SlaughterPolicy.carcassIdFor(killed.getType());
        if (carcassId == null || !BuiltInRegistries.BLOCK.containsKey(carcassId)) return ItemStack.EMPTY;
        Block carcass = BuiltInRegistries.BLOCK.get(carcassId);
        if (carcass == null) return ItemStack.EMPTY;
        return new ItemStack(carcass.asItem());
    }

    @Nullable
    private static Block resolveCarcassBlock(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem bi)) return null;
        return bi.getBlock();
    }

    /**
     * Matches Butchery's PlacecowcarcassProcedure: a hung fresh carcass
     * sits at blockstate=1 and takes its facing from the hook above so
     * the body orientation reads naturally under that specific hook.
     */
    private static BlockState placedState(@Nullable WorkJobDef job, Block carcass, BlockState sourceState) {
        BlockState state = carcass.defaultBlockState();
        WorkJobDef.Role destination = job == null ? null : job.first(WorkJobDef.RoleKind.BLOCK);
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

    private static BlockPos placementPos(@Nullable WorkJobDef job, BlockPos destination) {
        WorkJobDef.Role role = job == null ? null : job.first(WorkJobDef.RoleKind.BLOCK);
        WorkJobDef.Placement placement = role == null ? WorkJobDef.Placement.DEFAULT : role.placement();
        return destination.offset(placement.x(), placement.y(), placement.z());
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
    private static BlockPos findHookStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos hook) {
        BlockPos sausage = hook.below();
        BlockPos[] candidates = {
                sausage.north(), sausage.south(), sausage.east(), sausage.west()
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

    private static boolean villagerCarriesCarcass(VillagerEntityMCA villager) {
        return findCarriedCarcassSlot(villager) >= 0;
    }

    private static int findCarriedCarcassSlot(VillagerEntityMCA villager) {
        WorkJobDef job = jobDef();
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

    private static @Nullable WorkJobDef jobDef() {
        return WorkJobs.first(WorkTaskTypes.SLAUGHTER, WorkJobDef.ENTITY_DELIVERY);
    }

    private static boolean hasAvailableDestination(ServerLevel level, VillagerEntityMCA villager,
                                                   WorkJobDef job) {
        WorkJobDef.Role destination = job.first(WorkJobDef.RoleKind.BLOCK);
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

    private static final String SLAUGHTER_TICK_KEY = "townstead_lastSlaughterTick";

    private static boolean onThrottle(VillagerEntityMCA villager, long gameTime) {
        long last = TownsteadVillagers.get(villager).professionMemory().cooldown(SLAUGHTER_TICK_KEY);
        return gameTime - last < SlaughterPolicy.throttleTicks();
    }

    private static void markThrottle(VillagerEntityMCA villager, long gameTime) {
        TownsteadVillagers.get(villager).professionMemory().setCooldown(SLAUGHTER_TICK_KEY, gameTime);
    }

    private static void awardXp(VillagerEntityMCA villager, int amount, long gameTime) {
        if (amount <= 0) return;
        ProfessionProgress.GainResult result = com.aetherianartificer.townstead.profession.career.CareerProgression
                .completeWork(villager, com.aetherianartificer.townstead.profession.career.Careers.BUTCHER,
                        amount, gameTime, "townstead:slaughtered", null, null, amount);
        if (result.tierUp()) {
            ButcherTradeLevelSync.syncToTier(villager, result.tierAfter());
        }
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos pos) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(pos), WALK_SPEED, 1));
    }
}
