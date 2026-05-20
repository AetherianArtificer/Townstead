package com.aetherianartificer.townstead.shift.weekplan;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Client mirror of the combined week-plan list (built-ins + user). Updated by
 * the {@code WeekPlanSyncPayload} handler. Mirrors {@code ShiftTemplateClientStore}.
 */
public final class WeekPlanClientStore {

    private static volatile List<WeekPlan> plans = List.of();

    private WeekPlanClientStore() {}

    public static void set(List<WeekPlan> updated) {
        if (updated == null || updated.isEmpty()) {
            plans = List.of();
            return;
        }
        List<WeekPlan> sorted = new ArrayList<>(updated.size());
        for (WeekPlan p : updated) if (p != null) sorted.add(p);
        sorted.sort(Comparator
                .<WeekPlan>comparingInt(p -> p.builtIn() ? 0 : 1)
                .thenComparing(p -> p.id().toString(), String.CASE_INSENSITIVE_ORDER));
        plans = List.copyOf(sorted);
    }

    public static List<WeekPlan> all() {
        return plans;
    }

    public static WeekPlan find(ResourceLocation id) {
        if (id == null) return null;
        for (WeekPlan p : plans) {
            if (id.equals(p.id())) return p;
        }
        return null;
    }

    public static WeekPlan find(String id) {
        if (id == null || id.isEmpty()) return null;
        for (WeekPlan p : plans) {
            if (id.equals(p.id().toString())) return p;
        }
        return null;
    }

    public static void clear() {
        plans = List.of();
    }
}
