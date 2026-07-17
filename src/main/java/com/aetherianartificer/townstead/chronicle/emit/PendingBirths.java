package com.aetherianartificer.townstead.chronicle.emit;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Births detected at {@code Pregnancy.createChild} fire their chronicle tap on
 * the child's first dispatched tick instead — at createChild-return the child
 * has no position yet, so witnesses and village resolution would anchor at the
 * world origin. The per-baby rate gate in {@link ChronicleTaps#birth} dedupes
 * against the post-spawn call sites (DirectBirth, baby item).
 */
public final class PendingBirths {

    private static final Set<LivingEntity> PENDING =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private PendingBirths() {}

    public static void mark(LivingEntity baby) {
        PENDING.add(baby);
    }

    public static void tick(VillagerEntityMCA villager) {
        if (PENDING.remove(villager)) {
            ChronicleTaps.birth(villager);
        }
    }

    public static void clearAll() {
        PENDING.clear();
    }
}
