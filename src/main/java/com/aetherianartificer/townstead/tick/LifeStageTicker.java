package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.root.LifeStageProgression;
import com.aetherianartificer.townstead.root.SeniorEffects;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;

/**
 * Drives Townstead life-stage progression on the server tick, independent of
 * MCA's {@code setAgeState}. MCA only fires setAgeState on its own AgeState
 * transitions (baby→…→adult) and never again once adult, so without a periodic
 * re-resolve an adult villager would never enter the Townstead SENIOR stage
 * (no hair desaturation, no speed penalty) as calendar days pass.
 *
 * <p>Two jobs:</p>
 * <ul>
 *   <li>Periodically (once per in-game day) re-resolve the current stage from
 *       birth + stage days, committing {@code isSenior}; re-broadcasts the life
 *       sync when the senior flag flips so client hair desaturation updates.</li>
 *   <li>Every tick, reconcile the transient senior speed modifier against
 *       {@code isSenior} — {@code addTransientModifier} doesn't survive save/load,
 *       so a re-loaded senior would otherwise spawn back at full speed. Idempotent.</li>
 * </ul>
 */
public final class LifeStageTicker {

    // Stage boundaries are day-quantized, so re-resolving once per in-game day is
    // ample; per-tick would be wasted work on the hot villager path.
    private static final int RESOLVE_INTERVAL_TICKS = 1200;
    // Reconcile MCA's breeding-age-driven body size with the resolved stage at ~1 Hz, so a freshly
    // loaded, spawned, or edited villager reaches its stage size within a second. Cheap: it no-ops for
    // adults and only writes when a pre-adult villager's age is out of step with its stage.
    private static final int BODY_SYNC_INTERVAL_TICKS = 20;

    private LifeStageTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;

        boolean reconcile = villager.tickCount % BODY_SYNC_INTERVAL_TICKS == 0;
        if (reconcile) {
            LifeStageProgression.syncMcaAgeToStage(villager);
            reconcileNoAgingTrait(villager);
        }

        if (villager.tickCount % RESOLVE_INTERVAL_TICKS == 0) {
            boolean seniorChanged = LifeStageProgression.tickResolveStage(villager);
            if (seniorChanged) {
                broadcastLifeSync(villager);
            }
        }

        if (reconcile) {
            TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
            if (life.isSenior()) {
                SeniorEffects.applySenior(villager);
            } else {
                SeniorEffects.clearSenior(villager);
            }

            // Per-stage movement changes only when stage data changes. Reconcile at
            // the same 1 Hz cadence as body state rather than rebuilding it every tick.
            com.aetherianartificer.townstead.root.LifeStage stage = LifeStageProgression.currentStage(villager);
            boolean immobile = stage != null && !stage.mobile();
            if (villager.isNoAi() != immobile) villager.setNoAi(immobile);
        }

        if (!villager.isAlive() || villager.isRemoved()) {
            LAST_NO_AGING.remove(villager.getId());
        }
    }

    // MCA 7.6.28+/7.7.18+ editor age-lock: fold NO_AGING trait toggles into the granted-ageless
    // flag, the runtime freeze authority. Edge-triggered on trait changes rather than level-synced,
    // so a pre-mirror mismatch (e.g. a potion grant from before the trait existed) is never
    // "corrected" into an unfreeze; the editor only operates on loaded villagers, so every toggle
    // is observed as an edge here. The potions write flag and trait together, producing no edge.
    private static final java.util.Map<Integer, Boolean> LAST_NO_AGING = new java.util.concurrent.ConcurrentHashMap<>();

    private static void reconcileNoAgingTrait(VillagerEntityMCA villager) {
        boolean locked = com.aetherianartificer.townstead.root.trait.NoAgingTrait.has(villager);
        Boolean last = LAST_NO_AGING.put(villager.getId(), locked);
        if (last == null || last == locked) return;
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        if (life.ageless() == locked) return;
        life.setAgeless(locked);
        TownsteadVillagers.flush(villager);
        broadcastLifeSync(villager);
    }

    private static void broadcastLifeSync(VillagerEntityMCA villager) {
        com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload payload =
                com.aetherianartificer.townstead.Townstead.townstead$lifeSync(villager);
        if (payload != null) {
            //? if neoforge {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(villager, payload);
            //?} else if forge {
            /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(villager, payload);
            *///?}
        }
    }
}
