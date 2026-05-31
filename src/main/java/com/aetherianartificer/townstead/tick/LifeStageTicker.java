package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.origin.LifeStageProgression;
import com.aetherianartificer.townstead.origin.SeniorEffects;
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

    private LifeStageTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;

        if (villager.tickCount % RESOLVE_INTERVAL_TICKS == 0) {
            boolean seniorChanged = LifeStageProgression.tickResolveStage(villager);
            if (seniorChanged) {
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

        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        if (life.isSenior()) {
            SeniorEffects.applySenior(villager);
        } else {
            SeniorEffects.clearSenior(villager);
        }
    }
}
