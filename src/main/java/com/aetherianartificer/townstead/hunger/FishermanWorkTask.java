package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.ai.work.WorkMovement;
import com.aetherianartificer.townstead.ai.work.WorkNavigationMetrics;
import com.aetherianartificer.townstead.ai.work.WorkNavigationResult;
import com.aetherianartificer.townstead.ai.work.WorkPathing;
import com.aetherianartificer.townstead.ai.work.WorkSiteRef;
import com.aetherianartificer.townstead.ai.work.WorkTarget;
import com.aetherianartificer.townstead.ai.work.WorkTargetFailures;
import com.aetherianartificer.townstead.ai.work.WorkTargetProgress;
import com.aetherianartificer.townstead.ai.work.WorkTaskAdapter;
import com.aetherianartificer.townstead.fatigue.FatigueData;
//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.google.common.collect.ImmutableMap;
import com.mojang.authlib.GameProfile;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
//? if >=1.21 {
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
//?}
//? if neoforge {
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.network.PacketDistributor;
//?} else if forge {
/*import net.minecraftforge.common.util.FakePlayerFactory;
*///?}
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fisherman work task: villagers with a barrel POI fish from nearby water. Uses a per-level
 * FakePlayer to own a real FishingHook, rolls the vanilla fishing loot table, and deposits
 * the catch to the barrel (or nearby storage as a fallback).
 *
 * State machine:
 *   IDLE → FETCH_ROD → GO_TO_WATER → CAST → WAIT_FOR_BITE → REEL → IDLE
 *                                                                ↓ (inv ≥ threshold)
 *                                                      RETURN_TO_BARREL → DEPOSIT → IDLE
 */
public class FishermanWorkTask extends Behavior<VillagerEntityMCA> implements WorkTaskAdapter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/FishermanWorkTask");

    private static final int VERTICAL_RADIUS = 3;
    // Water bodies are usually below the barrel (ponds, shorelines at lower Y), so
    // search biased downward.
    private static final int WATER_VERTICAL_RADIUS_DOWN = 8;
    private static final int WATER_VERTICAL_RADIUS_UP = 3;
    private static final int CLOSE_ENOUGH = 1;
    private static final double ARRIVAL_DISTANCE_SQ = 1.44; // slightly farther for fishing
    private static final float WALK_SPEED = 0.52f;
    // MAX_DURATION caps how long the Behavior can run before vanilla forces a
    // restart. Bites can take up to ~45s and a full cycle (walk → aim → wait →
    // reel) can occupy most of a work shift, so give it plenty of headroom.
    private static final int MAX_DURATION = 6000;
    private static final int TARGET_STUCK_TICKS = 60;
    private static final int TARGET_BLACKLIST_TICKS = 200;
    private static final int PATHFAIL_MAX_RETRIES = 3;
    private static final int IDLE_BACKOFF_TICKS = 60;
    private static final int REQUEST_RANGE = 24;
    private static final int FETCH_ROD_TIMEOUT_TICKS = 200;
    private static final int GO_TO_WATER_TIMEOUT_TICKS = 300;
    private static final int CAST_COOLDOWN_TICKS = 40;
    // Pre-cast wind-up: face water, shift weight, brief pause so cast feels
    // deliberate rather than drive-by.
    private static final int AIM_DURATION_TICKS = 24;
    // Bite wait: 10s floor with up to 35s of patience. Lure enchant shaves
    // 5s per level like vanilla. Total: patient enough to read as "waiting."
    private static final int BITE_MIN_TICKS = 200;
    private static final int BITE_RANDOM_TICKS = 700;
    private static final int BITE_LURE_REDUCTION_TICKS = 100;
    // Last stretch of the wait: nibble cue plays, villager leans in.
    private static final int NIBBLE_LEAD_TICKS = 30;
    // Ambient micro-actions while waiting: subtle look offsets so the villager
    // reads as alive rather than frozen.
    private static final int AMBIENT_LOOK_INTERVAL_TICKS = 80;
    private static final int AMBIENT_GLANCE_INTERVAL_TICKS = 240;
    private static final int AMBIENT_GLANCE_RADIUS = 6;
    private static final int STORAGE_DEPOSIT_RADIUS = 16;
    private static final int STORAGE_DEPOSIT_VERTICAL = 3;
    // Close-enough radius to consider the villager arrived at the barrel for
    // deposit purposes. Wider than ARRIVAL_DISTANCE_SQ because the deposit
    // code uses a 16-block storage radius — we don't need to be precisely on
    // the nav-chosen stand block, just in the neighborhood of the barrel.
    private static final double RETURN_TO_BARREL_ARRIVAL_RADIUS_SQ = 9.0; // 3 blocks

    // Derive a stable FakePlayer UUID from the villager's UUID so each fisherman gets
    // their own owner (prevents multi-fisherman state collisions) and the FakePlayer
    // persists across shifts via FakePlayerFactory's internal caching.
    private static GameProfile fishingProfileFor(VillagerEntityMCA villager) {
        UUID vUuid = villager.getUUID();
        UUID fakeUuid = UUID.nameUUIDFromBytes(("townstead:fisherman:" + vUuid).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new GameProfile(fakeUuid, "[TownsteadFisherman]");
    }

    private enum Phase {
        IDLE,
        FETCH_ROD,
        GO_TO_WATER,
        AIM,
        CAST,
        WAIT_FOR_BITE,
        REEL,
        RETURN_TO_BARREL,
        DEPOSIT
    }

    // ── Task state ──
    private Phase phase = Phase.IDLE;
    private long phaseEnteredTick;
    private @Nullable BlockPos stationAnchor;
    private @Nullable FishingWaterIndex.FishingSpot currentWaterSpot;
    private long biteDeadline = Long.MAX_VALUE;
    private long nextCastReadyTick;
    private @Nullable WeakReference<FishingHook> currentHook;
    private @Nullable ItemStack currentRod;
    // Set once per bite when NIBBLE_LEAD_TICKS remain — triggers the lean-in
    // look and plays a splash cue.
    private boolean nibbleTriggered;
    // Independent timers for subtle look variations during WAIT_FOR_BITE.
    private long nextAmbientLookTick;
    private long nextAmbientGlanceTick;

    // Hand visuals (rod held during work shift) are handled by WorkToolTicker,
    // which runs per-tick on the server and swaps the villager's main hand
    // to match a profession→tool rule.

    // ── Request/debug cadence ──
    private HungerData.FishermanBlockedReason blockedReason = HungerData.FishermanBlockedReason.NONE;
    private long nextRequestTick;
    private long nextDebugTick;

    // ── Navigation tracking ──
    private final WorkTargetProgress targetProgress = new WorkTargetProgress();
    private final WorkTargetFailures targetFailures = new WorkTargetFailures();

    // Nearby-barrel fallback when the vanilla JOB_SITE memory isn't populated
    // (MCA villagers don't always set it the way vanilla fishermen do).
    private static final int BARREL_SEARCH_RADIUS = 24;
    private static final int BARREL_SEARCH_VERTICAL = 4;
    private static final long BARREL_CACHE_TTL_TICKS = 60L;

    private @Nullable BlockPos cachedBarrelAnchor;
    private long cachedBarrelUntilTick;

    public FishermanWorkTask() {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), MAX_DURATION);
    }

    // ── Lifecycle ──

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (townstead$isFatigueGated(villager)) return false;
        VillagerBrain<?> brain = villager.getVillagerBrain();
        if (villager.getVillagerData().getProfession() != VillagerProfession.FISHERMAN) return false;
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) return false;
        if (townstead$getCurrentScheduleActivity(villager) != Activity.WORK) return false;
        BlockPos anchor = townstead$resolveBarrelAnchor(level, villager);
        return anchor != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        phase = Phase.IDLE;
        phaseEnteredTick = gameTime;
        stationAnchor = townstead$resolveBarrelAnchor(level, villager);
        currentWaterSpot = null;
        biteDeadline = Long.MAX_VALUE;
        nextCastReadyTick = 0L;
        currentHook = null;
        currentRod = FishermanSupplyManager.findRodInInventory(villager.getInventory());
        targetProgress.reset();
        nextRequestTick = 0L;
        nibbleTriggered = false;
        nextAmbientLookTick = 0L;
        nextAmbientGlanceTick = 0L;
        townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.NONE);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (townstead$isFatigueGated(villager)) return false;
        VillagerBrain<?> brain = villager.getVillagerBrain();
        if (villager.getVillagerData().getProfession() != VillagerProfession.FISHERMAN) return false;
        if (brain.isPanicking() || villager.getLastHurtByMob() != null) return false;
        if (townstead$getCurrentScheduleActivity(villager) != Activity.WORK) return false;
        if (stationAnchor == null) {
            stationAnchor = townstead$resolveBarrelAnchor(level, villager);
        }
        return stationAnchor != null;
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        discardHook(level);
        releaseCurrentWaterSpot(level, villager);
        phase = Phase.IDLE;
        currentRod = null;
        biteDeadline = Long.MAX_VALUE;
        nextCastReadyTick = 0L;
        targetProgress.reset();
        targetFailures.reset();
        WorkPathing.clearMovementIntent(villager);
        townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.NONE);
    }

    @Override
    protected void tick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        debugTick(level, villager, gameTime);

        if (stationAnchor == null) {
            stationAnchor = townstead$resolveBarrelAnchor(level, villager);
        }
        if (stationAnchor == null) {
            townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.NO_BARREL);
            townstead$maybeAnnounceRequest(level, villager, gameTime);
            return;
        }

        // Refresh cached rod reference if it got removed from inventory (broken, dropped, or pulled out).
        if (currentRod != null && (currentRod.isEmpty() || !inventoryContains(villager.getInventory(), currentRod))) {
            currentRod = FishermanSupplyManager.findRodInInventory(villager.getInventory());
            if (currentRod == null) {
                enterPhase(Phase.FETCH_ROD, gameTime);
            }
        }

        switch (phase) {
            case IDLE -> tickIdle(level, villager, gameTime);
            case FETCH_ROD -> tickFetchRod(level, villager, gameTime);
            case GO_TO_WATER -> tickGoToWater(level, villager, gameTime);
            case AIM -> tickAim(level, villager, gameTime);
            case CAST -> tickCast(level, villager, gameTime);
            case WAIT_FOR_BITE -> tickWaitForBite(level, villager, gameTime);
            case REEL -> tickReel(level, villager, gameTime);
            case RETURN_TO_BARREL -> tickReturnToBarrel(level, villager, gameTime);
            case DEPOSIT -> tickDeposit(level, villager, gameTime);
        }

        townstead$maybeAnnounceRequest(level, villager, gameTime);
    }

    // ── Phase handlers ──

    private void tickIdle(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        int threshold = Math.max(1, TownsteadConfig.FISHERMAN_INVENTORY_FULL_THRESHOLD.get());
        int nonRodCount = countNonRodItems(villager.getInventory());
        if (nonRodCount >= threshold) {
            enterPhase(Phase.RETURN_TO_BARREL, gameTime);
            return;
        }

        if (currentRod == null || currentRod.isEmpty()) {
            enterPhase(Phase.FETCH_ROD, gameTime);
            return;
        }

        if (gameTime < nextCastReadyTick) {
            // short cooldown before next cast; hold position.
            return;
        }

        // Look up a water spot on-demand. Picks a random unclaimed spot; if no
        // water within the configured radius, expands the search automatically.
        if (currentWaterSpot == null) {
            if (!acquireUnclaimedWaterSpot(level, villager, gameTime)) {
                townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.NO_WATER);
                return;
            }
        }

        townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.NONE);
        enterPhase(Phase.GO_TO_WATER, gameTime);
    }

    private void tickFetchRod(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        ItemStack inv = FishermanSupplyManager.findRodInInventory(villager.getInventory());
        if (inv != null && !inv.isEmpty()) {
            currentRod = inv;
            townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.NONE);
            enterPhase(Phase.IDLE, gameTime);
            return;
        }

        if (FishermanSupplyManager.pullRodFromStorage(
                level, villager, stationAnchor,
                STORAGE_DEPOSIT_RADIUS, STORAGE_DEPOSIT_VERTICAL)) {
            currentRod = FishermanSupplyManager.findRodInInventory(villager.getInventory());
            if (currentRod != null && !currentRod.isEmpty()) {
                townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.NONE);
                enterPhase(Phase.IDLE, gameTime);
                return;
            }
        }

        if (gameTime - phaseEnteredTick >= FETCH_ROD_TIMEOUT_TICKS) {
            townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.NO_ROD);
            // Stay in FETCH_ROD but reset timer so we retry periodically.
            phaseEnteredTick = gameTime;
        }
    }

    private void tickGoToWater(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (currentWaterSpot == null) {
            if (!acquireUnclaimedWaterSpot(level, villager, gameTime)) {
                townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.NO_WATER);
                enterPhase(Phase.IDLE, gameTime);
                return;
            }
        }

        BlockPos stand = currentWaterSpot.standPos();
        if (targetFailures.isBlacklisted(stand, gameTime)) {
            // Release this spot so other fishermen can try it and this villager looks elsewhere.
            releaseCurrentWaterSpot(level, villager);
            enterPhase(Phase.IDLE, gameTime);
            return;
        }

        // Fast path: already at the water stand (e.g. we just reeled in and
        // inventory hasn't tripped the deposit threshold, or a previous hold
        // teleport left us here). Skip a pointless nav cycle that could get
        // rejected as BLOCKED despite zero distance to travel.
        double dxs = villager.getX() - (stand.getX() + 0.5);
        double dzs = villager.getZ() - (stand.getZ() + 0.5);
        if (dxs * dxs + dzs * dzs <= ARRIVAL_DISTANCE_SQ) {
            targetProgress.reset();
            enterPhase(Phase.AIM, gameTime);
            return;
        }

        WorkNavigationResult result = WorkMovement.tickMoveToTarget(
                villager,
                WorkTarget.zonePoint(stand, stationAnchor, "water"),
                WALK_SPEED,
                CLOSE_ENOUGH,
                ARRIVAL_DISTANCE_SQ,
                targetProgress,
                targetFailures,
                gameTime,
                TARGET_STUCK_TICKS,
                PATHFAIL_MAX_RETRIES,
                TARGET_BLACKLIST_TICKS
        );
        if (result == WorkNavigationResult.MOVING) {
            if (gameTime - phaseEnteredTick > GO_TO_WATER_TIMEOUT_TICKS) {
                // Took too long; back off and retry with a different spot.
                targetFailures.recordFailure(stand, gameTime, PATHFAIL_MAX_RETRIES, TARGET_BLACKLIST_TICKS);
                releaseCurrentWaterSpot(level, villager);
                targetProgress.reset();
                enterPhase(Phase.IDLE, gameTime);
            }
            return;
        }
        if (result == WorkNavigationResult.ARRIVED) {
            targetProgress.reset();
            enterPhase(Phase.AIM, gameTime);
            return;
        }
        if (result == WorkNavigationResult.BLOCKED) {
            if (TownsteadConfig.DEBUG_VILLAGER_AI.get()) {
                LOGGER.info("[Fisherman] GO_TO_WATER blocked: villager@({},{},{}) stand={} water={} anchor={}",
                        villager.getX(), villager.getY(), villager.getZ(),
                        stand, currentWaterSpot == null ? "?" : currentWaterSpot.waterPos(),
                        stationAnchor);
            }
            releaseCurrentWaterSpot(level, villager);
            targetProgress.reset();
            townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.UNREACHABLE);
            enterPhase(Phase.IDLE, gameTime);
        }
    }

    /**
     * Pre-cast wind-up. The villager stands at the water's edge, faces the
     * intended cast target, and pauses briefly so the upcoming swing reads
     * as a deliberate cast rather than a drive-by arm wave.
     */
    private void tickAim(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (currentWaterSpot == null) {
            enterPhase(Phase.IDLE, gameTime);
            return;
        }
        BlockPos waterPos = currentWaterSpot.waterPos();
        villager.getLookControl().setLookAt(
                waterPos.getX() + 0.5, waterPos.getY() + 0.5, waterPos.getZ() + 0.5);
        townstead$holdPosition(villager);
        if (gameTime - phaseEnteredTick >= AIM_DURATION_TICKS) {
            enterPhase(Phase.CAST, gameTime);
        }
    }

    /**
     * Stop the villager in place. MCA's WORK activity has concurrent behaviors
     * that can set WALK_TARGET every tick (wander, job-site drift, etc.); we
     * erase that memory, halt navigation, and kill horizontal motion. If the
     * villager has drifted noticeably off the stand spot we snap them back —
     * micro-jitter is strictly preferable to a fisherman wandering mid-wait.
     * Vertical velocity is preserved so gravity still pulls them down to the
     * ground normally.
     */
    private void townstead$holdPosition(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
        Vec3 mv = villager.getDeltaMovement();
        villager.setDeltaMovement(0.0, Math.min(mv.y, 0.0), 0.0);
        villager.setJumping(false);
        if (currentWaterSpot != null) {
            BlockPos stand = currentWaterSpot.standPos();
            double dx = villager.getX() - (stand.getX() + 0.5);
            double dz = villager.getZ() - (stand.getZ() + 0.5);
            double distSq = dx * dx + dz * dz;
            // Also snap Y back to the stand level if the villager has drifted
            // vertically — otherwise a float/fall during fishing leaves them
            // lodged at an off-Y position, which confuses the navigator when
            // the next phase tries to path anywhere.
            double dy = Math.abs(villager.getY() - stand.getY());
            if (distSq > 0.36 || dy > 0.5) {
                villager.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            }
        }
    }

    private void tickCast(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (currentRod == null || currentRod.isEmpty()) {
            currentRod = FishermanSupplyManager.findRodInInventory(villager.getInventory());
            if (currentRod == null) {
                enterPhase(Phase.FETCH_ROD, gameTime);
                return;
            }
        }
        if (currentWaterSpot == null) {
            enterPhase(Phase.IDLE, gameTime);
            return;
        }

        // The claimed waterPos is the block immediately next to our stand —
        // always ~1 block away, which gives short disappointing casts. Pick a
        // random-but-farther water block in the pond for the actual cast aim
        // so the villager throws out into open water when there's room.
        BlockPos waterPos = currentWaterSpot.waterPos();
        BlockPos castTarget = townstead$pickCastTarget(level, villager, waterPos);
        villager.getLookControl().setLookAt(
                castTarget.getX() + 0.5, castTarget.getY() + 0.5, castTarget.getZ() + 0.5);
        townstead$holdPosition(villager);
        villager.swing(InteractionHand.MAIN_HAND);

        if (!spawnHook(level, villager, castTarget)) {
            // Owner couldn't be obtained; stay in cast and try again next tick.
            return;
        }

        level.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                net.minecraft.sounds.SoundEvents.FISHING_BOBBER_THROW,
                net.minecraft.sounds.SoundSource.NEUTRAL,
                0.5F, 0.4F / (level.random.nextFloat() * 0.4F + 0.8F));

        int lure = townstead$fishingSpeedLevel(level, currentRod);
        int wait = BITE_MIN_TICKS + level.random.nextInt(Math.max(1, BITE_RANDOM_TICKS))
                - lure * BITE_LURE_REDUCTION_TICKS;
        biteDeadline = gameTime + Math.max(BITE_MIN_TICKS, wait);
        nibbleTriggered = false;
        nextAmbientLookTick = gameTime + AMBIENT_LOOK_INTERVAL_TICKS;
        nextAmbientGlanceTick = gameTime + AMBIENT_GLANCE_INTERVAL_TICKS;
        enterPhase(Phase.WAIT_FOR_BITE, gameTime);
    }

    /**
     * Sets up the FakePlayer + FishingHook. Returns false if the FakePlayer is
     * unavailable. Called from tickCast initially, and from tickWaitForBite if the
     * hook self-despawns (vanilla checks owner's hand/distance each tick).
     */
    private boolean spawnHook(ServerLevel level, VillagerEntityMCA villager, BlockPos waterPos) {
        if (currentRod == null || currentRod.isEmpty()) return false;
        ServerPlayer fakePlayer = getFishingActor(level, villager);
        if (fakePlayer == null) return false;

        // Compute yaw (horizontal aim) directly toward the water. For PITCH
        // we use BALLISTIC aim, not line-of-sight: given vanilla's ~0.94
        // block/tick cast speed and gravity 0.03/tick², find the launch
        // angle whose trajectory actually lands in the water block. Using
        // line-of-sight pitch on water that's close and below produces a
        // near-vertical cast that plops the bobber onto the shore instead
        // of arcing into the pond.
        double dx = (waterPos.getX() + 0.5) - villager.getX();
        double dy = (waterPos.getY() + 0.5) - villager.getEyeY();
        double dz = (waterPos.getZ() + 0.5) - villager.getZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = townstead$ballisticPitch(horizDist, dy);

        fakePlayer.setPos(villager.getX(), villager.getY(), villager.getZ());
        fakePlayer.setYRot(yaw);
        fakePlayer.setXRot(pitch);
        fakePlayer.setYHeadRot(yaw);
        fakePlayer.xRotO = pitch;
        fakePlayer.yRotO = yaw;
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, currentRod.copy());

        int luck = townstead$fishingLuckLevel(level, currentRod);
        int lure = townstead$fishingSpeedLevel(level, currentRod);

        // Vanilla constructor places the hook 0.3 blocks forward of the
        // FakePlayer at eye height and applies the normalized look-vector
        // velocity scaled by (0.6/length + 0.5) + gaussian jitter. Since
        // we set the FakePlayer's yaw/pitch to the BALLISTIC angle, vanilla
        // now produces a correctly-aimed cast arc. Do not override position
        // or deltaMovement afterwards.
        FishingHook hook = new FishingHook(fakePlayer, level, luck, lure);
        level.addFreshEntity(hook);
        currentHook = new WeakReference<>(hook);
        townstead$broadcastHookLink(level, hook.getId(), villager.getId());
        if (TownsteadConfig.DEBUG_VILLAGER_AI.get()) {
            LOGGER.info("[Fisherman] cast hook id={} from ({},{},{}) yaw={} pitch={} horizDist={} dy={} v={}",
                    hook.getId(), villager.getX(), villager.getY(), villager.getZ(),
                    yaw, pitch, horizDist, dy, hook.getDeltaMovement());
        }
        return true;
    }

    // Cast-target tuning. The villager stands right at the water edge, so the
    // claimed "fishing spot" is always 1 block away — a limp cast. We scan
    // water blocks in a box around the stand and pick a random one from the
    // far-third of candidates, so casts land somewhere in the middle of the
    // pond when there's room, but fall back gracefully on tiny puddles.
    private static final int CAST_TARGET_HORIZ_RADIUS = 8;
    private static final int CAST_TARGET_VERTICAL_RADIUS = 2;
    private static final double CAST_TARGET_MIN_DIST = 3.0;
    private static final double CAST_TARGET_MAX_DIST = 8.0;

    /**
     * Choose a water block to aim the cast at. Prefers blocks 3–8 blocks from
     * the villager horizontally; if the pond is too small to offer any, falls
     * back to the fallback nearWaterPos (the adjacent water block).
     */
    private static BlockPos townstead$pickCastTarget(ServerLevel level, VillagerEntityMCA villager, BlockPos fallbackNearWaterPos) {
        double vx = villager.getX();
        double vz = villager.getZ();
        BlockPos center = fallbackNearWaterPos;
        List<BlockPos> candidates = new java.util.ArrayList<>();
        double maxDistSq = CAST_TARGET_MAX_DIST * CAST_TARGET_MAX_DIST;
        double minDistSq = CAST_TARGET_MIN_DIST * CAST_TARGET_MIN_DIST;
        for (BlockPos p : BlockPos.betweenClosed(
                center.offset(-CAST_TARGET_HORIZ_RADIUS, -CAST_TARGET_VERTICAL_RADIUS, -CAST_TARGET_HORIZ_RADIUS),
                center.offset(CAST_TARGET_HORIZ_RADIUS, CAST_TARGET_VERTICAL_RADIUS, CAST_TARGET_HORIZ_RADIUS))) {
            BlockState state = level.getBlockState(p);
            if (!state.getFluidState().isSource()) continue;
            if (!state.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) continue;
            BlockPos above = p.above();
            BlockState aboveState = level.getBlockState(above);
            // Need open sky above so the arc isn't blocked by stone/roofs.
            if (!aboveState.isAir() && !aboveState.getCollisionShape(level, above).isEmpty()) continue;
            double dxh = (p.getX() + 0.5) - vx;
            double dzh = (p.getZ() + 0.5) - vz;
            double horizSq = dxh * dxh + dzh * dzh;
            if (horizSq < minDistSq || horizSq > maxDistSq) continue;
            candidates.add(p.immutable());
        }
        if (candidates.isEmpty()) return fallbackNearWaterPos;
        // Weight toward farther spots: sort by distance desc, pick from top half.
        candidates.sort((a, b) -> {
            double da = (a.getX() + 0.5 - vx) * (a.getX() + 0.5 - vx) + (a.getZ() + 0.5 - vz) * (a.getZ() + 0.5 - vz);
            double db = (b.getX() + 0.5 - vx) * (b.getX() + 0.5 - vx) + (b.getZ() + 0.5 - vz) * (b.getZ() + 0.5 - vz);
            return Double.compare(db, da);
        });
        int topHalf = Math.max(1, candidates.size() / 2);
        return candidates.get(level.random.nextInt(topHalf));
    }

    /**
     * Solve for the launch angle (Minecraft pitch convention: positive = down)
     * that makes a projectile of vanilla cast speed ~0.94 blocks/tick land at
     * (horizDist, dy) relative to the launcher, under gravity 0.03 blocks/tick².
     * Returns the LOW-arc solution (direct, fast cast) — high-arc would lob it
     * way up, which looks wrong for a fishing rod.
     *
     * Drag is ignored in the formula; for short 2–10 block casts the resulting
     * angle lands within ~half a block of the target, which is fine since the
     * water block is typically part of a larger pond. For unreachable targets
     * (out of range) we fall back to direct line-of-sight aim.
     */
    private static float townstead$ballisticPitch(double horizDist, double dy) {
        if (horizDist < 0.25) {
            // Degenerate: villager is basically on top of the target. Aim
            // straight down so the hook drops into whatever water is below.
            return 80.0F;
        }
        final double v = 0.94;
        final double g = 0.03;
        double k = g * horizDist / (2.0 * v * v);
        double disc = 1.0 - 4.0 * k * (dy / horizDist + k);
        if (disc < 0) {
            return (float) -Math.toDegrees(Math.atan2(dy, horizDist));
        }
        double u = (1.0 - Math.sqrt(disc)) / (2.0 * k);
        double angleAbove = Math.atan(u);
        return (float) -Math.toDegrees(angleAbove);
    }

    /**
     * Tell tracking clients that this hook should render its line from the
     * given villager's rod hand. Unlinking is handled lazily on the client
     * (FishermanLineRenderer skips dead hooks and evicts their entries), so
     * we only need to broadcast on link. Failures are swallowed: the line
     * is purely cosmetic.
     */
    private static void townstead$broadcastHookLink(ServerLevel level, int hookEntityId, int villagerEntityId) {
        try {
            net.minecraft.world.entity.Entity hook = level.getEntity(hookEntityId);
            if (hook == null) return;
            FishermanHookLinkPayload payload = new FishermanHookLinkPayload(hookEntityId, villagerEntityId);
            //? if neoforge {
            PacketDistributor.sendToPlayersTrackingEntity(hook, payload);
            //?} else if forge {
            /*TownsteadNetwork.sendToTrackingEntity(hook, payload);
            *///?}
            if (TownsteadConfig.DEBUG_VILLAGER_AI.get()) {
                LOGGER.info("[Fisherman] broadcast hook-link hookId={} villagerId={}", hookEntityId, villagerEntityId);
            }
        } catch (Throwable t) {
            if (TownsteadConfig.DEBUG_VILLAGER_AI.get()) {
                LOGGER.warn("[Fisherman] hook-link broadcast failed: {}", t.toString());
            }
        }
    }

    /**
     * Refresh FakePlayer position + rod between ticks while a hook is active.
     * Vanilla FishingHook.shouldStopFishing() self-discards the hook if the owner
     * isn't within 32 blocks or doesn't have a rod in hand, so we keep those stats
     * current. Safe to call every tick.
     */
    private void maintainFakePlayerForHook(ServerLevel level, VillagerEntityMCA villager) {
        if (currentRod == null || currentRod.isEmpty()) return;
        ServerPlayer fakePlayer = getFishingActor(level, villager);
        if (fakePlayer == null) return;
        fakePlayer.setPos(villager.getX(), villager.getEyeY(), villager.getZ());
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, currentRod.copy());
    }

    private void tickWaitForBite(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        // Keep FakePlayer fresh so vanilla doesn't self-despawn our hook mid-wait.
        maintainFakePlayerForHook(level, villager);
        townstead$holdPosition(villager);

        FishingHook hook = currentHook == null ? null : currentHook.get();
        if (hook == null || !hook.isAlive() || hook.level() != level) {
            // Hook went away (chunk unload, vanilla cleanup, owner distance). Respawn
            // without resetting biteDeadline — the villager's wait continues.
            if (currentWaterSpot != null) {
                spawnHook(level, villager, currentWaterSpot.waterPos());
            }
            hook = currentHook == null ? null : currentHook.get();
            if (hook == null) {
                // Couldn't respawn (no FakePlayer, no rod). Don't bail the phase —
                // just wait; if deadline hits we'll REEL and roll loot anyway.
                if (gameTime >= biteDeadline) enterPhase(Phase.REEL, gameTime);
                return;
            }
        }

        Vec3 hookPos = hook.position();
        long ticksUntilBite = biteDeadline - gameTime;
        boolean nibbling = ticksUntilBite <= NIBBLE_LEAD_TICKS;

        if (nibbling) {
            // Lean-in: look slightly below the bobber so the head tilts forward.
            villager.getLookControl().setLookAt(hookPos.x, hookPos.y - 0.35, hookPos.z);
            if (!nibbleTriggered) {
                nibbleTriggered = true;
                level.playSound(null, hookPos.x, hookPos.y, hookPos.z,
                        net.minecraft.sounds.SoundEvents.FISHING_BOBBER_SPLASH,
                        net.minecraft.sounds.SoundSource.NEUTRAL,
                        0.35F, 1.1F + level.random.nextFloat() * 0.2F);
            }
        } else {
            tickAmbientLooks(level, villager, gameTime, hookPos);
        }

        if (gameTime >= biteDeadline) {
            enterPhase(Phase.REEL, gameTime);
        }
    }

    /**
     * Subtle look variations during the long bite wait so the villager reads
     * as alive. Base gaze tracks the bobber; every AMBIENT_LOOK_INTERVAL_TICKS
     * we nudge it slightly off to simulate a small head shift, and every
     * AMBIENT_GLANCE_INTERVAL_TICKS we briefly look at a nearby entity.
     */
    private void tickAmbientLooks(ServerLevel level, VillagerEntityMCA villager, long gameTime, Vec3 hookPos) {
        if (gameTime >= nextAmbientGlanceTick) {
            nextAmbientGlanceTick = gameTime + AMBIENT_GLANCE_INTERVAL_TICKS
                    + level.random.nextInt(AMBIENT_GLANCE_INTERVAL_TICKS);
            var others = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                    villager.getBoundingBox().inflate(AMBIENT_GLANCE_RADIUS),
                    e -> e != villager && e.isAlive());
            if (!others.isEmpty()) {
                var target = others.get(level.random.nextInt(others.size()));
                villager.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
                return;
            }
        }
        if (gameTime >= nextAmbientLookTick) {
            nextAmbientLookTick = gameTime + AMBIENT_LOOK_INTERVAL_TICKS
                    + level.random.nextInt(AMBIENT_LOOK_INTERVAL_TICKS);
            double yawJitter = (level.random.nextDouble() - 0.5) * 1.5;
            double pitchJitter = (level.random.nextDouble() - 0.5) * 0.6;
            villager.getLookControl().setLookAt(
                    hookPos.x + yawJitter, hookPos.y + pitchJitter, hookPos.z + yawJitter);
            return;
        }
        villager.getLookControl().setLookAt(hookPos.x, hookPos.y, hookPos.z);
    }

    private void tickReel(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        FishingHook hook = currentHook == null ? null : currentHook.get();
        ItemStack rod = currentRod;
        if (rod == null || rod.isEmpty()) {
            discardHook(level);
            enterPhase(Phase.FETCH_ROD, gameTime);
            return;
        }

        ItemStack rodCopy = rod.copy();

        Vec3 origin = hook != null ? hook.position()
                : new Vec3(villager.getX(), villager.getY(), villager.getZ());

        level.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                net.minecraft.sounds.SoundEvents.FISHING_BOBBER_RETRIEVE,
                net.minecraft.sounds.SoundSource.NEUTRAL,
                0.5F, 0.4F / (level.random.nextFloat() * 0.4F + 0.8F));

        List<ItemStack> loot = rollFishingLoot(level, hook, villager, rodCopy, origin);

        if (!loot.isEmpty()) {
            SimpleContainer inv = villager.getInventory();
            for (ItemStack item : loot) {
                if (item == null || item.isEmpty()) continue;
                ItemStack copy = item.copy();
                ItemStack remainder = inv.addItem(copy);
                if (!remainder.isEmpty() && stationAnchor != null) {
                    NearbyItemSources.insertIntoNearbyStorage(
                            level, villager, remainder,
                            STORAGE_DEPOSIT_RADIUS, STORAGE_DEPOSIT_VERTICAL,
                            stationAnchor);
                }
            }
        }

        // Damage the rod. Signature differs between versions.
        //? if >=1.21 {
        rod.hurtAndBreak(1, level, (ServerPlayer) null, itm -> {});
        //?} else {
        /*rod.hurtAndBreak(1, villager, v -> {});
        *///?}

        if (rod.isEmpty() || rod.getDamageValue() >= rod.getMaxDamage()) {
            // Rod broke. Clear cache; next tick will re-enter FETCH_ROD.
            currentRod = null;
        }

        villager.swing(InteractionHand.MAIN_HAND);
        discardHook(level);

        nextCastReadyTick = gameTime + CAST_COOLDOWN_TICKS;
        enterPhase(Phase.IDLE, gameTime);
    }

    private void tickReturnToBarrel(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null) {
            enterPhase(Phase.IDLE, gameTime);
            return;
        }

        BlockPos stand = WorkPathing.nearestStandCandidate(level, villager, stationAnchor, null);
        BlockPos target = stand != null ? stand : stationAnchor;

        // Fast path: deposit has a wide radius (STORAGE_DEPOSIT_RADIUS = 16),
        // so we only need to be "near the barrel" — not precisely on the
        // nav-chosen stand block. Checking against the ANCHOR (the actual
        // barrel) with a generous radius rescues the villager from limbo
        // when they're standing at a not-quite-on-the-stand spot and the
        // pathfinder can't plot a tiny path from there to the stand.
        double axd = villager.getX() - (stationAnchor.getX() + 0.5);
        double azd = villager.getZ() - (stationAnchor.getZ() + 0.5);
        if (axd * axd + azd * azd <= RETURN_TO_BARREL_ARRIVAL_RADIUS_SQ) {
            targetProgress.reset();
            townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.NONE);
            enterPhase(Phase.DEPOSIT, gameTime);
            return;
        }

        WorkNavigationResult result = WorkMovement.tickMoveToTarget(
                villager,
                WorkTarget.zonePoint(target, stationAnchor, "barrel"),
                WALK_SPEED,
                CLOSE_ENOUGH,
                ARRIVAL_DISTANCE_SQ,
                targetProgress,
                targetFailures,
                gameTime,
                TARGET_STUCK_TICKS,
                PATHFAIL_MAX_RETRIES,
                TARGET_BLACKLIST_TICKS
        );
        if (result == WorkNavigationResult.ARRIVED) {
            targetProgress.reset();
            enterPhase(Phase.DEPOSIT, gameTime);
            return;
        }
        if (result == WorkNavigationResult.BLOCKED) {
            if (TownsteadConfig.DEBUG_VILLAGER_AI.get()) {
                double ddx = villager.getX() - (target.getX() + 0.5);
                double ddy = villager.getY() - target.getY();
                double ddz = villager.getZ() - (target.getZ() + 0.5);
                double dist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                LOGGER.info("[Fisherman] RETURN_TO_BARREL blocked: villager@({},{},{}) stand={} anchor={} distToTarget={}",
                        villager.getX(), villager.getY(), villager.getZ(),
                        stand, stationAnchor, dist);
            }
            targetProgress.reset();
            townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.UNREACHABLE);
            enterPhase(Phase.IDLE, gameTime);
        }
    }

    private void tickDeposit(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null) {
            enterPhase(Phase.IDLE, gameTime);
            return;
        }
        boolean moved = FishermanSupplyManager.depositCatches(
                level, villager, stationAnchor,
                STORAGE_DEPOSIT_RADIUS, STORAGE_DEPOSIT_VERTICAL);
        if (countNonRodItems(villager.getInventory()) == 0) {
            townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.NONE);
            enterPhase(Phase.IDLE, gameTime);
            return;
        }
        if (!moved) {
            townstead$setBlockedReason(level, villager, HungerData.FishermanBlockedReason.NO_STORAGE);
            // stay in deposit briefly, then back to idle to re-evaluate
            if (gameTime - phaseEnteredTick > IDLE_BACKOFF_TICKS) {
                enterPhase(Phase.IDLE, gameTime);
            }
        } else {
            enterPhase(Phase.IDLE, gameTime);
        }
    }

    // ── Helpers ──

    private void enterPhase(Phase next, long gameTime) {
        this.phase = next;
        this.phaseEnteredTick = gameTime;
    }

    private void discardHook(ServerLevel level) {
        if (currentHook != null) {
            FishingHook hook = currentHook.get();
            if (hook != null && hook.isAlive()) {
                // Client will evict the link once the entity despawns client-side;
                // no unlink packet needed.
                hook.discard();
            }
            currentHook = null;
        }
    }

    private static boolean inventoryContains(SimpleContainer inv, ItemStack stack) {
        if (inv == null || stack == null) return false;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i) == stack) return true;
        }
        return false;
    }

    private static int countNonRodItems(SimpleContainer inv) {
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() instanceof FishingRodItem) continue;
            total += stack.getCount();
        }
        return total;
    }

    /** Compact per-slot inventory summary for the debug overlay. */
    private static String townstead$summarizeInventory(SimpleContainer inv) {
        if (inv == null) return "null";
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (shown++ > 0) sb.append(",");
            //? if >=1.21 {
            String key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            //?} else {
            /*String key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            *///?}
            sb.append(key).append("x").append(stack.getCount());
            if (shown >= 6) { sb.append(",..."); break; }
        }
        return shown == 0 ? "empty" : sb.toString();
    }

    private int townstead$waterSearchRadius() {
        return Math.max(4, TownsteadConfig.FISHERMAN_WATER_SEARCH_RADIUS.get());
    }

    private int townstead$waterFallbackRadius() {
        // When nothing is available near the barrel, cast a wider net so the fisherman
        // can still find water somewhere in the area. Cap at 48 to bound scan cost.
        int primary = townstead$waterSearchRadius();
        return Math.min(48, Math.max(primary + 8, primary * 3));
    }

    private boolean acquireUnclaimedWaterSpot(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (stationAnchor == null) return false;
        FishingWaterIndex.FishingSpot spot = FishingWaterIndex.availableSpot(
                level, villager, stationAnchor,
                townstead$waterSearchRadius(),
                WATER_VERTICAL_RADIUS_DOWN,
                WATER_VERTICAL_RADIUS_UP,
                townstead$waterFallbackRadius());
        if (spot == null) return false;
        long untilTick = gameTime + MAX_DURATION + 20L;
        if (!FishingSpotClaims.tryClaim(level, villager.getUUID(), spot.waterPos(), untilTick)) {
            return false;
        }
        currentWaterSpot = spot;
        return true;
    }

    private void releaseCurrentWaterSpot(ServerLevel level, VillagerEntityMCA villager) {
        if (currentWaterSpot == null) return;
        FishingSpotClaims.release(level, villager.getUUID(), currentWaterSpot.waterPos());
        currentWaterSpot = null;
    }

    private @Nullable BlockPos townstead$resolveBarrelAnchor(ServerLevel level, VillagerEntityMCA villager) {
        // Prefer the vanilla-assigned job site when MCA has populated it.
        Optional<GlobalPos> jobSite = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (jobSite.isPresent()) {
            GlobalPos pos = jobSite.get();
            if (pos.dimension().equals(level.dimension())) {
                BlockPos anchor = pos.pos();
                if (level.getBlockState(anchor).is(Blocks.BARREL)) return anchor.immutable();
            }
        }
        // Fallback: scan for a nearby barrel. Cached briefly so start-condition checks
        // don't re-scan every tick while the villager is idle.
        long gameTime = level.getGameTime();
        if (cachedBarrelAnchor != null && gameTime <= cachedBarrelUntilTick) {
            if (level.getBlockState(cachedBarrelAnchor).is(Blocks.BARREL)) return cachedBarrelAnchor;
            cachedBarrelAnchor = null;
        }
        BlockPos center = villager.blockPosition();
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(
                center.offset(-BARREL_SEARCH_RADIUS, -BARREL_SEARCH_VERTICAL, -BARREL_SEARCH_RADIUS),
                center.offset(BARREL_SEARCH_RADIUS, BARREL_SEARCH_VERTICAL, BARREL_SEARCH_RADIUS))) {
            if (!level.getBlockState(p).is(Blocks.BARREL)) continue;
            double d = villager.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = p.immutable();
            }
        }
        cachedBarrelAnchor = best;
        cachedBarrelUntilTick = gameTime + BARREL_CACHE_TTL_TICKS;
        return best;
    }

    private static Activity townstead$getCurrentScheduleActivity(VillagerEntityMCA self) {
        long dayTime = self.level().getDayTime() % 24000L;
        return self.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    private static boolean townstead$isFatigueGated(VillagerEntityMCA villager) {
        if (!TownsteadConfig.isVillagerFatigueEnabled()) return false;
        //? if neoforge {
        CompoundTag fatigue = villager.getData(Townstead.FATIGUE_DATA);
        //?} else {
        /*CompoundTag fatigue = villager.getPersistentData().getCompound("townstead_fatigue");
        *///?}
        return FatigueData.isGated(fatigue) || FatigueData.getFatigue(fatigue) >= FatigueData.DROWSY_THRESHOLD;
    }

    // ── FakePlayer + fishing loot ──

    private static @Nullable ServerPlayer getFishingActor(ServerLevel level, VillagerEntityMCA villager) {
        if (level == null || villager == null) return null;
        try {
            return FakePlayerFactory.get(level, fishingProfileFor(villager));
        } catch (Throwable t) {
            return null;
        }
    }

    private static int townstead$fishingLuckLevel(ServerLevel level, ItemStack rod) {
        if (rod == null || rod.isEmpty()) return 0;
        //? if >=1.21 {
        var registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Optional<Holder.Reference<Enchantment>> luckHolder = registry.getHolder(Enchantments.LUCK_OF_THE_SEA);
        return luckHolder.map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, rod)).orElse(0);
        //?} else {
        /*return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FISHING_LUCK, rod);
        *///?}
    }

    private static int townstead$fishingSpeedLevel(ServerLevel level, ItemStack rod) {
        if (rod == null || rod.isEmpty()) return 0;
        //? if >=1.21 {
        var registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Optional<Holder.Reference<Enchantment>> lureHolder = registry.getHolder(Enchantments.LURE);
        return lureHolder.map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, rod)).orElse(0);
        //?} else {
        /*return EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FISHING_SPEED, rod);
        *///?}
    }

    private static List<ItemStack> rollFishingLoot(ServerLevel level, @Nullable FishingHook hook,
                                                    VillagerEntityMCA villager, ItemStack rodCopy, Vec3 origin) {
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.TOOL, rodCopy);
        if (hook != null) {
            builder.withParameter(LootContextParams.THIS_ENTITY, hook);
        } else {
            builder.withParameter(LootContextParams.THIS_ENTITY, villager);
        }
        LootParams params = builder.create(LootContextParamSets.FISHING);

        LootTable table;
        //? if >=1.21 {
        table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        //?} else {
        /*table = level.getServer().getLootData().getLootTable(BuiltInLootTables.FISHING);
        *///?}
        if (table == null) return List.of();
        try {
            return table.getRandomItems(params);
        } catch (Throwable t) {
            LOGGER.debug("Fisherman loot roll failed: {}", t.toString());
            return List.of();
        }
    }

    // ── Request chat + NBT sync ──

    private void townstead$setBlockedReason(ServerLevel level, VillagerEntityMCA villager,
                                            HungerData.FishermanBlockedReason reason) {
        if (blockedReason == reason) return;
        blockedReason = reason;

        //? if neoforge {
        CompoundTag hunger = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag hunger = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        if (HungerData.getFishermanBlockedReason(hunger) != reason) {
            HungerData.setFishermanBlockedReason(hunger, reason);
            //? if neoforge {
            villager.setData(Townstead.HUNGER_DATA, hunger);
            //?} else {
            /*villager.getPersistentData().put("townstead_hunger", hunger);
            *///?}
        }
        //? if neoforge {
        PacketDistributor.sendToPlayersTrackingEntity(villager, new FishermanStatusSyncPayload(villager.getId(), reason.id()));
        //?} else if forge {
        /*TownsteadNetwork.sendToTrackingEntity(villager, new FishermanStatusSyncPayload(villager.getId(), reason.id()));
        *///?}
        if (reason == HungerData.FishermanBlockedReason.NONE) {
            nextRequestTick = 0;
        } else {
            long soonest = level.getGameTime() + 40;
            if (nextRequestTick < soonest) nextRequestTick = soonest;
        }
    }

    private void townstead$maybeAnnounceRequest(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!TownsteadConfig.ENABLE_FISHERMAN_REQUEST_CHAT.get()) return;
        if (blockedReason == HungerData.FishermanBlockedReason.NONE) return;
        if (gameTime < nextRequestTick) return;
        if (level.getNearestPlayer(villager, REQUEST_RANGE) == null) {
            nextRequestTick = gameTime + 200;
            return;
        }

        String key = switch (blockedReason) {
            case NO_ROD -> "dialogue.chat.fisherman_request.no_rod/" + (1 + level.random.nextInt(6));
            case NO_WATER -> "dialogue.chat.fisherman_request.no_water/" + (1 + level.random.nextInt(6));
            case NO_STORAGE -> "dialogue.chat.fisherman_request.no_storage/" + (1 + level.random.nextInt(4));
            case UNREACHABLE -> "dialogue.chat.fisherman_request.unreachable/" + (1 + level.random.nextInt(4));
            case NO_BARREL, NONE -> null;
        };
        if (key == null) return;

        villager.sendChatToAllAround(key);
        villager.getLongTermMemory().remember("townstead.fisherman_request.any");
        villager.getLongTermMemory().remember("townstead.fisherman_request." + blockedReason.id());

        int interval = Math.max(200, TownsteadConfig.FISHERMAN_REQUEST_INTERVAL_TICKS.get());
        nextRequestTick = gameTime + interval;
    }

    // ── Debug ──

    private void debugTick(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        if (!TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        if (gameTime < nextDebugTick) return;
        if (!(level.getNearestPlayer(villager, REQUEST_RANGE) instanceof ServerPlayer player)) return;
        String name = villager.getName().getString();
        String id = villager.getUUID().toString();
        if (id.length() > 8) id = id.substring(0, 8);
        WorkSiteRef site = activeWorkSite(level, villager);
        WorkTarget target = activeWorkTarget(level, villager);
        WorkNavigationMetrics.Snapshot navSnapshot = WorkNavigationMetrics.snapshot();
        String anchor = stationAnchor == null ? "none" : stationAnchor.getX() + "," + stationAnchor.getY() + "," + stationAnchor.getZ();
        FishingHook hookRef = currentHook == null ? null : currentHook.get();
        String hookInfo;
        if (hookRef == null) {
            hookInfo = "none";
        } else {
            String water = currentWaterSpot == null ? "?"
                    : currentWaterSpot.waterPos().getX() + "," + currentWaterSpot.waterPos().getY() + "," + currentWaterSpot.waterPos().getZ();
            hookInfo = "id=" + hookRef.getId()
                    + " pos=" + String.format("%.1f,%.1f,%.1f", hookRef.getX(), hookRef.getY(), hookRef.getZ())
                    + " inWater=" + hookRef.isInWater()
                    + " onGround=" + hookRef.onGround()
                    + " dead=" + !hookRef.isAlive()
                    + " water=" + water;
        }
        int invNonRod = countNonRodItems(villager.getInventory());
        int invThreshold = Math.max(1, TownsteadConfig.FISHERMAN_INVENTORY_FULL_THRESHOLD.get());
        String invSummary = townstead$summarizeInventory(villager.getInventory());
        player.sendSystemMessage(Component.literal("[FishermanDBG:" + name + "#" + id + "] phase=" + phase.name()
                + " anchor=" + anchor
                + " target=" + (target == null ? "none" : target.describe())
                + " blocked=" + blockedReason.name()
                + " rod=" + (currentRod == null ? "none" : (currentRod.getDamageValue() + "/" + currentRod.getMaxDamage()))
                + " site=" + (site == null ? "none" : site.describe())
                + " hook=" + hookInfo
                + " inv=" + invNonRod + "/" + invThreshold + " (" + invSummary + ")"
                + " nav=" + navSnapshot.snapshotRebuilds() + "/" + navSnapshot.pathAttempts()
                + "/" + navSnapshot.pathSuccesses() + "/" + navSnapshot.pathFailures()));
        nextDebugTick = gameTime + 100L;
    }

    // ── WorkTaskAdapter ──

    @Override
    public @Nullable WorkSiteRef activeWorkSite(ServerLevel level, VillagerEntityMCA villager) {
        return stationAnchor == null ? null : WorkSiteRef.zone(stationAnchor, townstead$waterSearchRadius(), VERTICAL_RADIUS);
    }

    @Override
    public @Nullable WorkTarget activeWorkTarget(ServerLevel level, VillagerEntityMCA villager) {
        if (stationAnchor == null) return null;
        return switch (phase) {
            case GO_TO_WATER -> currentWaterSpot == null ? null
                    : WorkTarget.zonePoint(currentWaterSpot.standPos(), stationAnchor, "water");
            case RETURN_TO_BARREL -> WorkTarget.zonePoint(stationAnchor, stationAnchor, "barrel");
            default -> null;
        };
    }

    @Override
    public float navigationWalkSpeed(ServerLevel level, VillagerEntityMCA villager) {
        return WALK_SPEED;
    }

    @Override
    public int navigationCloseEnough(ServerLevel level, VillagerEntityMCA villager) {
        return CLOSE_ENOUGH;
    }

    @Override
    public double navigationArrivalDistanceSq(ServerLevel level, VillagerEntityMCA villager) {
        return ARRIVAL_DISTANCE_SQ;
    }

    @Override
    public String navigationState(ServerLevel level, VillagerEntityMCA villager) {
        return phase.name();
    }

    @Override
    public String navigationBlockedState(ServerLevel level, VillagerEntityMCA villager) {
        return blockedReason.name();
    }
}
