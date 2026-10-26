package com.aetherianartificer.townstead.chronicle.emit;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.WeakHashMap;

/**
 * Detects single→married transitions by polling relationship state on a slow
 * per-villager stride. Poll-based on purpose: it catches every marriage path
 * (wedding ring, dialogue, MCA's autonomous village marriages) on both MCA
 * branches without a mixin. The first observation of a villager is a silent
 * baseline so already-married villagers never fire on world load.
 */
public final class MarriageWatcher {

    private static final int STRIDE_TICKS = 100;

    private static final WeakHashMap<VillagerEntityMCA, Boolean> LAST_MARRIED = new WeakHashMap<>();
    private static final Object LOCK = new Object();

    private MarriageWatcher() {}

    public static void tick(VillagerEntityMCA villager, long gameTime) {
        if ((gameTime + villager.getId()) % STRIDE_TICKS != 0) return;
        boolean married;
        try {
            var relationship = EntityRelationship.of(villager).orElse(null);
            if (relationship == null) return;
            married = relationship.isMarried();
            Boolean last;
            synchronized (LOCK) {
                last = LAST_MARRIED.put(villager, married);
            }
            if (last == null || last || !married) return;

            LivingEntity spouse = relationship.getPartner()
                    .filter(e -> e instanceof LivingEntity)
                    .map(e -> (LivingEntity) e)
                    .orElse(null);
            ChronicleTaps.marriage(villager, spouse);
        } catch (Throwable ignored) {
            // Relationship API hiccups (unloaded partners, cross-version drift)
            // must not break the tick path.
        }
    }

    public static void clearAll() {
        synchronized (LOCK) {
            LAST_MARRIED.clear();
        }
    }
}
