package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.hunger.ButcherProgressData;
import com.aetherianartificer.townstead.tick.WorkToolTicker;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
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

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return false;
        if (!SlaughterPolicy.slaughterEnabledFor(villager)) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return false;
        if (onThrottle(villager, level.getGameTime())) return false;
        return pickBuildingWithTarget(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        Pick pick = pickBuildingWithTarget(level, villager);
        if (pick == null) return;
        activeBuilding = pick.building;
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
        // Require a hook somewhere in the village so the carcass has a destination,
        // regardless of whether the animal is in a shop or in a pen.
        if (ButcheryShopScanner.findHookInAnyShop(level, villager) == null) return null;
        for (ButcheryShopScanner.HuntRef ref : ButcheryShopScanner.huntableBuildings(level, villager)) {
            LivingEntity t = findTargetIn(level, villager, ref.building());
            if (t != null) return new Pick(ref.building(), t);
        }
        return null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (gameTime - startedTick > MAX_DURATION) return false;
        switch (phase) {
            case PATH, ATTACK -> {
                if (target == null || !target.isAlive()) return false;
                if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) return false;
            }
            case CARRY -> {
                if (targetHook == null) return false;
                if (!isHook(level, targetHook)) return false;
                if (!hookBelowAvailable(level, targetHook)) return false;
                if (gameTime - lastPathTick > CARRY_PATH_TIMEOUT_TICKS) return false;
                if (!villagerCarriesCarcass(villager)) return false;
            }
            case PLACE -> {
                if (targetHook == null) return false;
                if (!isHook(level, targetHook)) return false;
                if (!hookBelowAvailable(level, targetHook)) return false;
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
        boolean killed = target.hurt(source, damage);
        nextAttackTick = gameTime + ATTACK_COOLDOWN_TICKS;
        if (killed && !target.isAlive()) {
            onTargetKilled(level, villager, target, gameTime);
            target = null;
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

        BlockPos carcassPos = targetHook.below();
        BlockState existing = level.getBlockState(carcassPos);
        if (!existing.isAir() && !existing.canBeReplaced()) return;

        BlockState placed = freshHungState(carcass, level.getBlockState(targetHook));
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
        BlockPos origin = villager.blockPosition();
        AABB search = AABB.ofSize(
                new Vec3(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5),
                24, 8, 24);
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
        BlockPos hook = selectFreeHook(level, villager);
        if (hook == null) {
            // No available hook anywhere; drop the carcass item at the kill
            // site so the player can hang it manually. Better than losing
            // it silently.
            BlockPos dropAt = killed.blockPosition();
            ItemStack drop = makeCarcassItemFor(killed);
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

        ItemStack carcassItem = makeCarcassItemFor(killed);
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

    /** Nearest free hook: either in the building that held the target
     *  (in-shop kill) or in any carcass-capable shop in the village
     *  (pen kill). A "free" hook has air at hook.below(). */
    @Nullable
    private BlockPos selectFreeHook(ServerLevel level, VillagerEntityMCA villager) {
        if (activeBuilding != null) {
            BlockPos local = findFreeHookInBuilding(level, activeBuilding);
            if (local != null) return local;
        }
        // Fall back to any butcher shop's free hook elsewhere in the village.
        for (ButcheryShopScanner.ShopRef ref : ButcheryShopScanner.carcassCapableShops(level, villager)) {
            BlockPos hook = findFreeHookInBuilding(level, ref.building());
            if (hook != null) return hook;
        }
        return null;
    }

    @Nullable
    private static BlockPos findFreeHookInBuilding(ServerLevel level, Building building) {
        //? if >=1.21 {
        ResourceLocation hookId = ResourceLocation.fromNamespaceAndPath("butchery", "hook");
        //?} else {
        /*ResourceLocation hookId = new ResourceLocation("butchery", "hook");
        *///?}
        var positions = building.getBlocks().get(hookId);
        if (positions == null) return null;
        for (BlockPos pos : positions) {
            if (!isHook(level, pos)) continue;
            if (!hookBelowAvailable(level, pos)) continue;
            return pos;
        }
        return null;
    }

    private static boolean isHook(ServerLevel level, BlockPos pos) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("butchery", "hook").equals(id);
        //?} else {
        /*return new ResourceLocation("butchery", "hook").equals(id);
        *///?}
    }

    private static boolean hookBelowAvailable(ServerLevel level, BlockPos hook) {
        BlockState below = level.getBlockState(hook.below());
        return below.isAir() || below.canBeReplaced();
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
    private static BlockState freshHungState(Block carcass, BlockState hookState) {
        BlockState state = carcass.defaultBlockState();
        Property<?> blockstate = state.getBlock().getStateDefinition()
                .getProperty(CarcassStateMachine.BLOCKSTATE_PROPERTY);
        if (blockstate instanceof IntegerProperty ip
                && ip.getPossibleValues().contains(CarcassStateMachine.HUNG_BLOCKSTATE)) {
            state = state.setValue(ip, CarcassStateMachine.HUNG_BLOCKSTATE);
        }
        Property<?> facing = state.getBlock().getStateDefinition().getProperty("facing");
        Property<?> hookFacing = hookState.getBlock().getStateDefinition().getProperty("facing");
        if (facing instanceof DirectionProperty dp
                && hookFacing instanceof DirectionProperty hdp) {
            Direction d = hookState.getValue(hdp);
            if (dp.getPossibleValues().contains(d)) {
                state = state.setValue(dp, d);
            }
        }
        return state;
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
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem bi)) continue;
            if (bi.getBlock().defaultBlockState().is(CarcassStateMachine.CARCASS_TAG)) return i;
        }
        return -1;
    }

    private static boolean onThrottle(VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        long last = data.getLong("townstead_lastSlaughterTick");
        return gameTime - last < SlaughterPolicy.throttleTicks();
    }

    private static void markThrottle(VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        data.putLong("townstead_lastSlaughterTick", gameTime);
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        data.putLong("townstead_lastSlaughterTick", gameTime);
        villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }

    private static void awardXp(VillagerEntityMCA villager, int amount, long gameTime) {
        if (amount <= 0) return;
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        ButcherProgressData.GainResult result = ButcherProgressData.addXp(data, amount, gameTime);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
        if (result.tierUp()) {
            ButcherTradeLevelSync.syncToTier(villager, result.tierAfter());
        }
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos pos) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(pos), WALK_SPEED, 1));
    }
}
