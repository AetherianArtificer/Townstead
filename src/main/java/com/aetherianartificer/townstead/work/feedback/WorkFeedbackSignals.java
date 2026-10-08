package com.aetherianartificer.townstead.work.feedback;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Runtime facts exposed to data-authored work-feedback conditions. */
public final class WorkFeedbackSignals {
    private static final Map<ResourceLocation, Predicate<VillagerEntityMCA>> SIGNALS =
            new LinkedHashMap<>();

    private WorkFeedbackSignals() {}

    public static void register(ResourceLocation id, Predicate<VillagerEntityMCA> signal) {
        if (id == null || signal == null) return;
        SIGNALS.put(id, signal);
    }

    public static boolean test(ResourceLocation id, VillagerEntityMCA villager) {
        Predicate<VillagerEntityMCA> signal = SIGNALS.get(id);
        if (signal == null || villager == null) return false;
        try {
            return signal.test(villager);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static boolean isRegistered(ResourceLocation id) {
        return id != null && SIGNALS.containsKey(id);
    }
}
