package com.aetherianartificer.townstead.profession;

import java.util.List;

/**
 * Client-side cache for available profession data including slot info.
 */
public final class ProfessionClientStore {
    private static volatile List<String> availableProfessions = List.of();
    private static volatile List<Integer> usedSlots = List.of();
    private static volatile List<Integer> maxSlots = List.of();

    private ProfessionClientStore() {}

    public static void set(List<String> professions, List<Integer> used, List<Integer> max) {
        availableProfessions = professions != null ? List.copyOf(professions) : List.of();
        usedSlots = used != null ? List.copyOf(used) : List.of();
        maxSlots = max != null ? List.copyOf(max) : List.of();
    }

    public static List<String> getProfessions() {
        return availableProfessions;
    }

    public static int getUsed(int index) {
        return index >= 0 && index < usedSlots.size() ? usedSlots.get(index) : 0;
    }

    public static int getMax(int index) {
        return index >= 0 && index < maxSlots.size() ? maxSlots.get(index) : -1;
    }

    /** Returns true if the profession at this index has a slot limit and is full. */
    public static boolean isFull(int index) {
        int max = getMax(index);
        return max >= 0 && getUsed(index) >= max;
    }

    public static void clear() {
        availableProfessions = List.of();
        usedSlots = List.of();
        maxSlots = List.of();
    }
}
