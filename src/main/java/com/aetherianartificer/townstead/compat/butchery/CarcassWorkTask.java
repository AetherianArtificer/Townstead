package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.hunger.ButcherProgressData;
import com.aetherianartificer.townstead.tick.WorkToolTicker;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Butcher villagers at Tier 2+ shops drive drained carcass blocks through the
 * state machine (head → skin → cut 1 → cut 2 → cut 3), dropping the
 * corresponding Butchery loot tables directly into inventory and awarding
 * butcher XP per stage.
 *
 * <p>Scope for step 4 MVP: processes only carcasses already drained (the
 * bleeding transition is step 4.5). Overflow drops to the ground at the
 * villager's feet; proper station routing (skin rack, pestle, etc.) lands in
 * a later pass.
 *
 * <p>See {@code docs/design/butchery_integration.md} section 6 for the full
 * stage list.
 */
public class CarcassWorkTask extends Behavior<VillagerEntityMCA> {
    private static final int MAX_DURATION = 1200;
    private static final double ARRIVAL_DISTANCE_SQ = 2.89; // stand within ~1.7 blocks
    private static final float WALK_SPEED = 0.55f;
    /** Per cutting stroke on a drained carcass (head, skin, cuts 1-3). */
    private static final int CUT_STROKE_COOLDOWN_TICKS = 30;
    /** Brief beat between arrival-at-a-ripe-carcass and the first cut so the
     *  drain→cut transition reads as a distinct moment. */
    private static final int POST_DRAIN_PAUSE_TICKS = 20;
    private static final int PATH_TIMEOUT_TICKS = 200;
    private static final int STAND_SEARCH_RADIUS = 2;
    private static final int STAND_DROP_LIMIT = 6;

    //? if >=1.21 {
    private static final ResourceLocation HOOK_ID = ResourceLocation.parse("butchery:hook");
    private static final ResourceLocation SOUND_SWEEP =
            ResourceLocation.parse("entity.player.attack.sweep");
    private static final ResourceLocation SOUND_HONEY_HIT =
            ResourceLocation.parse("block.honey_block.hit");
    private static final ResourceLocation SOUND_AXE_STRIP =
            ResourceLocation.parse("item.axe.strip");
    private static final ResourceLocation SOUND_HONEY_STEP =
            ResourceLocation.parse("block.honey_block.step");
    //?} else {
    /*private static final ResourceLocation HOOK_ID = new ResourceLocation("butchery", "hook");
    private static final ResourceLocation SOUND_SWEEP =
            new ResourceLocation("entity.player.attack.sweep");
    private static final ResourceLocation SOUND_HONEY_HIT =
            new ResourceLocation("block.honey_block.hit");
    private static final ResourceLocation SOUND_AXE_STRIP =
            new ResourceLocation("item.axe.strip");
    private static final ResourceLocation SOUND_HONEY_STEP =
            new ResourceLocation("block.honey_block.step");
    *///?}

    private enum Phase { PATH, PROCESS }

    @Nullable private BlockPos targetCarcass;
    @Nullable private BlockPos standPos;
    private Phase phase = Phase.PATH;
    private long startedTick;
    private long nextStageTick;
    private long lastPathTick;
    private boolean stalled;

    public CarcassWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return false;
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return false;
        if (!ButcherToolDamage.hasCleaver(villager)) return false;
        return findCarcassAcrossShops(level, villager) != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        targetCarcass = findCarcassAcrossShops(level, villager);
        if (targetCarcass == null) return;
        standPos = findStandPos(level, villager, targetCarcass);
        phase = Phase.PATH;
        startedTick = gameTime;
        lastPathTick = gameTime;
        nextStageTick = gameTime + CUT_STROKE_COOLDOWN_TICKS;
        stalled = false;
        setWalkTarget(villager, standPos != null ? standPos : targetCarcass);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetCarcass == null) return false;
        BlockState state = level.getBlockState(targetCarcass);
        if (!CarcassStateMachine.isProcessable(level, state, targetCarcass)) return false;
        if (!ButcherToolDamage.hasCleaver(villager)) return false;
        if (gameTime - startedTick > MAX_DURATION) { stalled = true; return false; }
        if (phase == Phase.PATH && gameTime - lastPathTick > PATH_TIMEOUT_TICKS) {
            stalled = true;
            return false;
        }
        return true;
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (targetCarcass == null) return;
        // Look at the middle of the hanging body (carcass block occupies the
        // space below the hook), slightly above center so the villager gazes
        // up at the work rather than at the floor.
        villager.getLookControl().setLookAt(
                targetCarcass.getX() + 0.5, targetCarcass.getY() + 1.0, targetCarcass.getZ() + 0.5);

        if (phase == Phase.PATH) {
            BlockPos anchor = standPos != null ? standPos : targetCarcass;
            double dsq = villager.distanceToSqr(
                    anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5);
            if (dsq <= ARRIVAL_DISTANCE_SQ) {
                phase = Phase.PROCESS;
                // First stroke cadence depends on whether we're about to drain
                // or cut; either cadence is fine here since the tick() below
                // will re-read the block state and pick the right one.
                nextStageTick = gameTime + CUT_STROKE_COOLDOWN_TICKS;
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            } else {
                // Re-stamp walk target periodically in case other tasks clear it.
                setWalkTarget(villager, anchor);
            }
            return;
        }

        // PROCESS phase
        if (gameTime < nextStageTick) return;
        BlockState current = level.getBlockState(targetCarcass);

        // Fresh carcass: mirror the player's cleaver-on-hanging-carcass flow.
        // A single opening cut initiates the 45-second passive drain; after
        // the timer matures, the next visit swaps the block for its drained
        // twin and cutting begins. No further strokes during the drain.
        if (CarcassStateMachine.isFreshCarcass(current)) {
            if (!CarcassStateMachine.hasBloodGrateBelow(level, targetCarcass)) {
                // Grate was removed mid-cycle; abort.
                targetCarcass = null;
                return;
            }
            long readyTick = CarcassStateMachine.readDrainReadyTick(level, targetCarcass);
            if (readyTick == 0L) {
                // First-visit drain initiation: one opening cut, set the
                // 900-tick timer, then release the task so the butcher can
                // go do other work while the blood drains. The carcass
                // won't reappear as processable until the timer ripens.
                villager.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
                playDrainSounds(level, targetCarcass);
                ButcherToolDamage.consumeCleaverUse(villager);
                CarcassStateMachine.initiateBleed(level, targetCarcass, gameTime);
                awardXp(villager, CarcassStateMachine.BLEED_XP, gameTime);
                targetCarcass = null; // end the task cleanly
                return;
            }
            if (gameTime >= readyTick) {
                // Drain matured while we were away (or on the next tick if
                // we just caught a ripe one). Do the fresh→drained swap
                // silently — the transition itself isn't a click event in
                // the mod — and start cutting after a short pause.
                CarcassStateMachine.bleed(level, targetCarcass);
                nextStageTick = gameTime + POST_DRAIN_PAUSE_TICKS;
                return;
            }
            // Still draining; shouldn't normally be reached because
            // isProcessable gates us out. Bail to be safe.
            targetCarcass = null;
            return;
        }

        // Drained carcass: one deliberate strike per stage, matching the
        // mod's HangingXcutupProcedure — swing, break-particles, cut sounds,
        // loot drop, block-state advance. The skin stage (2→3) uses a
        // skinning knife; every other stage uses the cleaver. Match the
        // mod's tool-specific path exactly: if the right tool is missing,
        // stall and complain rather than fudging the swing with the wrong
        // blade.
        CarcassStateMachine.Stage stage = CarcassStateMachine.Stage
                .forCurrentState(CarcassStateMachine.currentState(current));
        if (stage == null) {
            targetCarcass = null;
            return;
        }
        boolean needsKnife = stage == CarcassStateMachine.Stage.SKIN;
        if (needsKnife && !ButcherToolDamage.hasKnife(villager)) {
            // Emit the specific "need skinning knife" chat and bail without
            // setting `stalled` — we don't want stop() to also fire the
            // generic carcass_stuck chat on top of the specific complaint.
            emitNoKnifeChat(level, villager, gameTime);
            targetCarcass = null;
            return;
        }
        // Swap to the correct blade for this stroke. The tool remains in
        // hand until the next stroke (or WorkToolTicker's next pass for
        // out-of-task ticks).
        if (needsKnife) equipFromInventory(villager, WorkToolTicker::isKnife);
        else equipFromInventory(villager, WorkToolTicker::isCleaver);

        villager.swing(InteractionHand.MAIN_HAND, true);
        level.levelEvent(2001, targetCarcass,
                net.minecraft.world.level.block.Block.getId(current));
        playCutSounds(level, targetCarcass);
        List<ItemStack> drops = CarcassStateMachine.advance(level, targetCarcass);
        deposit(level, villager, drops);
        awardXp(villager, stage.xpGrant, gameTime);
        if (needsKnife) ButcherToolDamage.consumeKnifeUse(villager);
        else ButcherToolDamage.consumeCleaverUse(villager);
        nextStageTick = gameTime + CUT_STROKE_COOLDOWN_TICKS;

        // Terminal stage removed the block; finish.
        if (stage.toState < 0) {
            targetCarcass = null;
        }
    }

    /**
     * Copy a matching inventory tool into the main hand if the villager
     * isn't already holding one. WorkToolTicker treats cleavers and knives
     * both as "butcher tools" for the hand-display pass, so this doesn't
     * fight the ticker — it just narrows the display to the tool the
     * current stroke actually needs.
     */
    private static void equipFromInventory(VillagerEntityMCA villager, Predicate<ItemStack> matcher) {
        if (matcher.test(villager.getMainHandItem())) return;
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (matcher.test(stack)) {
                villager.setItemInHand(InteractionHand.MAIN_HAND, stack.copy());
                return;
            }
        }
    }

    private static void emitNoKnifeChat(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        long last = data.getLong(ButcheryComplaintsTicker.LAST_COMPLAINT_KEY);
        if (gameTime - last < ButcheryComplaintsTicker.COMPLAINT_INTERVAL_TICKS) return;
        String key = "dialogue.chat.butcher_request.no_skinning_knife/"
                + (1 + level.random.nextInt(3));
        villager.sendChatToAllAround(key);
        data.putLong(ButcheryComplaintsTicker.LAST_COMPLAINT_KEY, gameTime);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }

    private static void playDrainSounds(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos,
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(SOUND_SWEEP),
                net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f);
        level.playSound(null, pos,
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(SOUND_HONEY_HIT),
                net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f);
    }

    private static void playCutSounds(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos,
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(SOUND_AXE_STRIP),
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.3f, 1f);
        level.playSound(null, pos,
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(SOUND_HONEY_STEP),
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.3f, 1f);
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stalled) {
            emitStuckChat(level, villager, gameTime);
        }
        stalled = false;
        targetCarcass = null;
        standPos = null;
        phase = Phase.PATH;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private static void emitStuckChat(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        long last = data.getLong(ButcheryComplaintsTicker.LAST_COMPLAINT_KEY);
        if (gameTime - last < ButcheryComplaintsTicker.COMPLAINT_INTERVAL_TICKS) return;
        String key = "dialogue.chat.butcher_request.carcass_stuck/" + (1 + level.random.nextInt(3));
        villager.sendChatToAllAround(key);
        data.putLong(ButcheryComplaintsTicker.LAST_COMPLAINT_KEY, gameTime);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }

    // --- helpers ---

    /**
     * True if the villager has a processable carcass waiting in any of their
     * carcass-capable shops. Called by {@link com.aetherianartificer.townstead.hunger.ButcherWorkTask}
     * to yield the smoker while there's slaughterhouse work pending, so the
     * butcher prioritizes the higher-value carcass pipeline over smoking.
     */
    public static boolean hasPendingWork(ServerLevel level, VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return false;
        if (!ButcherToolDamage.hasCleaver(villager)) return false;
        return findCarcassAcrossShops(level, villager) != null;
    }

    /**
     * Search every carcass-capable building in the village for a processable
     * carcass. Nearest wins, so a butcher finishing a kill in the Slaughterhouse
     * will pick up that carcass before walking back to their Butcher Shop.
     */
    @Nullable
    private static BlockPos findCarcassAcrossShops(ServerLevel level, VillagerEntityMCA villager) {
        BlockPos origin = villager.blockPosition();
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (ButcheryShopScanner.ShopRef ref : ButcheryShopScanner.carcassCapableShops(level, villager)) {
            BlockPos candidate = findCarcassIn(level, origin, ref.building());
            if (candidate == null) continue;
            double dsq = candidate.distSqr(origin);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Hooks are tracked in the building's block index (they are required by
     * the building type), and {@link SlaughterWorkTask#onTargetKilled} always
     * places carcasses at {@code hook.below()}. So the set of candidate
     * carcass positions is exactly the positions directly below each hook.
     * No radius scan needed, and detection works regardless of how far the
     * villager has wandered from the slaughterhouse.
     */
    @Nullable
    private static BlockPos findCarcassIn(ServerLevel level, BlockPos origin,
            net.conczin.mca.server.world.data.Building building) {
        List<BlockPos> hooks = building.getBlocks().get(HOOK_ID);
        if (hooks == null || hooks.isEmpty()) return null;
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (BlockPos hook : hooks) {
            BlockPos carcassPos = hook.below();
            BlockState state = level.getBlockState(carcassPos);
            if (!CarcassStateMachine.isProcessable(level, state, carcassPos)) continue;
            double dsq = carcassPos.distSqr(origin);
            if (dsq < bestDsq) {
                bestDsq = dsq;
                best = carcassPos.immutable();
            }
        }
        return best;
    }

    /**
     * Find a walkable floor-level stand position near the carcass. The player
     * stands under/beside a hanging carcass and looks up to cut it, so we
     * locate the floor first (drop down from the carcass until we hit
     * something solid), then pick the closest walkable spot around that floor
     * patch. The old cardinal-only scan at the carcass's own Y failed
     * whenever the slaughterhouse had any ceiling clearance because the
     * below-block check always saw air.
     */
    @Nullable
    private static BlockPos findStandPos(ServerLevel level, VillagerEntityMCA villager, BlockPos carcass) {
        int floorY = findFloorY(level, carcass);
        if (floorY == Integer.MIN_VALUE) return null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        BlockPos villagerPos = villager.blockPosition();
        for (int dx = -STAND_SEARCH_RADIUS; dx <= STAND_SEARCH_RADIUS; dx++) {
            for (int dz = -STAND_SEARCH_RADIUS; dz <= STAND_SEARCH_RADIUS; dz++) {
                cursor.set(carcass.getX() + dx, floorY + 1, carcass.getZ() + dz);
                if (!isStandable(level, cursor)) continue;
                // Prefer spots close to directly under the carcass so the
                // animation reads as "standing at the workstation". Break ties
                // with distance to the villager so we don't backtrack.
                double cdx = cursor.getX() - carcass.getX();
                double cdz = cursor.getZ() - carcass.getZ();
                double carcassXz = cdx * cdx + cdz * cdz;
                double villagerDs = cursor.distSqr(villagerPos);
                double score = carcassXz * 4.0 + villagerDs;
                if (score < bestScore) {
                    bestScore = score;
                    best = cursor.immutable();
                }
            }
        }
        return best;
    }

    /**
     * Drop down from the carcass until we find a solid block, returning the Y
     * of that solid block. Returns {@link Integer#MIN_VALUE} if no floor is
     * found within {@link #STAND_DROP_LIMIT} blocks.
     */
    private static int findFloorY(ServerLevel level, BlockPos carcass) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy <= STAND_DROP_LIMIT; dy++) {
            cursor.set(carcass.getX(), carcass.getY() - dy, carcass.getZ());
            BlockState s = level.getBlockState(cursor);
            if (!s.isAir() && !s.is(CarcassStateMachine.CARCASS_TAG)) {
                return cursor.getY();
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean isStandable(ServerLevel level, BlockPos pos) {
        BlockState at = level.getBlockState(pos);
        if (!at.isAir() && !at.canBeReplaced()) return false;
        BlockState head = level.getBlockState(pos.above());
        if (!head.isAir() && !head.canBeReplaced()) return false;
        BlockState floor = level.getBlockState(pos.below());
        if (floor.isAir()) return false;
        return !floor.is(CarcassStateMachine.CARCASS_TAG);
    }

    private static void setWalkTarget(VillagerEntityMCA villager, BlockPos target) {
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(target), WALK_SPEED, 1));
    }

    private static void deposit(ServerLevel level, VillagerEntityMCA villager, List<ItemStack> drops) {
        for (ItemStack stack : drops) {
            if (stack.isEmpty()) continue;
            // Cut drops (hides, organs, meat) go into the villager's
            // inventory. ButcherDeliveryTask (priority 76) then walks them
            // to the skin rack / pestle / general storage. This avoids the
            // old teleporty "hide just appears in the skin rack" behavior.
            ItemStack leftover = villager.getInventory().addItem(stack);
            if (!leftover.isEmpty()) {
                ItemEntity ie = new ItemEntity(level,
                        villager.getX(), villager.getY() + 0.25, villager.getZ(), leftover);
                ie.setPickUpDelay(10);
                level.addFreshEntity(ie);
            }
        }
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
}
