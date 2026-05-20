package com.aetherianartificer.townstead.shift;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;

import java.util.ArrayList;
import java.util.List;

public final class ShiftData {

    public static final int HOURS_PER_DAY = 24;
    public static final int TICKS_PER_HOUR = 1000;

    // Activity ordinals stored in the int array
    public static final int ORD_IDLE = 0;
    public static final int ORD_WORK = 1;
    public static final int ORD_MEET = 2;
    public static final int ORD_REST = 3;

    public static final Activity[] ORDINAL_TO_ACTIVITY = {
            Activity.IDLE, Activity.WORK, Activity.MEET, Activity.REST
    };

    public static final String[] ORDINAL_TO_KEY = {
            "townstead.shift.activity.idle",
            "townstead.shift.activity.work",
            "townstead.shift.activity.meet",
            "townstead.shift.activity.rest"
    };

    public static final int[] ORDINAL_COLORS = {
            0xFFA0A0A0, // IDLE - gray
            0xFF4488CC, // WORK - blue
            0xFF55AA55, // MEET - green
            0xFF6644AA, // REST - purple
    };

    public static final String[] ORDINAL_LABELS = { "I", "W", "M", "R" };

    private static final String KEY_SHIFTS = "shifts";
    private static final String KEY_TEMPLATE_ID = "template_id";
    private static final String KEY_MODE = "mode";
    private static final String KEY_WEEK_DAYS = "week_days";

    /** Schedule modes stored under {@link #KEY_MODE}. */
    public static final String MODE_DAILY = "daily";
    public static final String MODE_WEEKLY = "weekly";

    // Default schedule matching vanilla VILLAGER_DEFAULT:
    // Tick hour 0 = 6 AM. The vanilla schedule is:
    //   0-1 (6-7AM): IDLE, 2-9 (8AM-3PM): WORK, 10 (4PM): MEET,
    //   11 (5PM): IDLE, 12-23 (6PM-5AM): REST
    private static final int[] VANILLA_DEFAULT = buildVanillaDefault();

    private ShiftData() {}

    private static int[] buildVanillaDefault() {
        int[] defaults = new int[HOURS_PER_DAY];
        Schedule schedule = Schedule.VILLAGER_DEFAULT;
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            Activity a = schedule.getActivityAt(hour * TICKS_PER_HOUR);
            defaults[hour] = activityToOrdinal(a);
        }
        return defaults;
    }

    public static int activityToOrdinal(Activity a) {
        if (a == Activity.WORK) return ORD_WORK;
        if (a == Activity.MEET) return ORD_MEET;
        if (a == Activity.REST) return ORD_REST;
        return ORD_IDLE;
    }

    public static int[] getShifts(CompoundTag tag) {
        if (!tag.contains(KEY_SHIFTS)) return VANILLA_DEFAULT.clone();
        int[] stored = tag.getIntArray(KEY_SHIFTS);
        if (stored.length != HOURS_PER_DAY) return VANILLA_DEFAULT.clone();
        return stored.clone();
    }

    public static void setShifts(CompoundTag tag, int[] shifts) {
        if (shifts == null || shifts.length != HOURS_PER_DAY) return;
        tag.putIntArray(KEY_SHIFTS, shifts.clone());
    }

    public static Activity getActivityAt(CompoundTag tag, int tickHour) {
        int[] shifts = getShifts(tag);
        int hour = Math.floorMod(tickHour, HOURS_PER_DAY);
        int ord = shifts[hour];
        if (ord < 0 || ord >= ORDINAL_TO_ACTIVITY.length) return Activity.IDLE;
        return ORDINAL_TO_ACTIVITY[ord];
    }

    public static boolean hasCustomShifts(CompoundTag tag) {
        return tag.contains(KEY_SHIFTS);
    }

    public static String getTemplateId(CompoundTag tag) {
        return tag.contains(KEY_TEMPLATE_ID) ? tag.getString(KEY_TEMPLATE_ID) : "";
    }

    public static void setTemplateId(CompoundTag tag, String id) {
        if (id == null || id.isEmpty()) {
            tag.remove(KEY_TEMPLATE_ID);
        } else {
            tag.putString(KEY_TEMPLATE_ID, id);
        }
    }

    public static boolean isDefault(CompoundTag tag) {
        if (!tag.contains(KEY_SHIFTS)) return true;
        int[] stored = tag.getIntArray(KEY_SHIFTS);
        if (stored.length != HOURS_PER_DAY) return true;
        for (int i = 0; i < HOURS_PER_DAY; i++) {
            if (stored[i] != VANILLA_DEFAULT[i]) return false;
        }
        return true;
    }

    public static int[] getVanillaDefault() {
        return VANILLA_DEFAULT.clone();
    }

    // ---- Schedule mode (daily vs weekly) ----

    /** Stored schedule mode; defaults to {@link #MODE_DAILY} when absent. */
    public static String getMode(CompoundTag tag) {
        if (!tag.contains(KEY_MODE)) return MODE_DAILY;
        String mode = tag.getString(KEY_MODE);
        return MODE_WEEKLY.equals(mode) ? MODE_WEEKLY : MODE_DAILY;
    }

    public static void setMode(CompoundTag tag, String mode) {
        if (MODE_WEEKLY.equals(mode)) {
            tag.putString(KEY_MODE, MODE_WEEKLY);
        } else {
            // Daily is the implicit default; don't bloat the tag with it.
            tag.remove(KEY_MODE);
        }
    }

    public static boolean isWeekly(CompoundTag tag) {
        return MODE_WEEKLY.equals(getMode(tag));
    }

    // ---- Weekly per-day template references ----

    /**
     * The stored per-weekday template-id list. Entries are template ids
     * ({@code "namespace:path"}); an empty string means "fall back to the
     * daily schedule" for that day. The list length is whatever was last
     * written (typically the calendar's daysPerWeek); callers must index
     * defensively via {@link #getWeekDayTemplate}.
     */
    public static List<String> getWeekDayTemplates(CompoundTag tag) {
        List<String> out = new ArrayList<>();
        if (!tag.contains(KEY_WEEK_DAYS, Tag.TAG_LIST)) return out;
        ListTag list = tag.getList(KEY_WEEK_DAYS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
    }

    /** Template id for the given 0-based day-of-week, or {@code ""} if unset/out of range. */
    public static String getWeekDayTemplate(CompoundTag tag, int dayOfWeek) {
        if (dayOfWeek < 0) return "";
        if (!tag.contains(KEY_WEEK_DAYS, Tag.TAG_LIST)) return "";
        ListTag list = tag.getList(KEY_WEEK_DAYS, Tag.TAG_STRING);
        if (dayOfWeek >= list.size()) return "";
        return list.getString(dayOfWeek);
    }

    public static void setWeekDayTemplates(CompoundTag tag, List<String> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            tag.remove(KEY_WEEK_DAYS);
            return;
        }
        ListTag list = new ListTag();
        for (String id : templateIds) list.add(StringTag.valueOf(id == null ? "" : id));
        tag.put(KEY_WEEK_DAYS, list);
    }

    /**
     * Set a single weekday slot, growing the stored list to {@code daysPerWeek}
     * (padding new slots with {@code ""}). No-op if the index is out of range.
     */
    public static void setWeekDayTemplate(CompoundTag tag, int dayOfWeek, int daysPerWeek, String templateId) {
        if (dayOfWeek < 0 || daysPerWeek <= 0 || dayOfWeek >= daysPerWeek) return;
        List<String> days = getWeekDayTemplates(tag);
        while (days.size() < daysPerWeek) days.add("");
        days.set(dayOfWeek, templateId == null ? "" : templateId);
        setWeekDayTemplates(tag, days);
    }

    /**
     * Convert tick-hour index (0 = 6 AM) to display hour (6, 7, ... 23, 0, 1, ... 5).
     */
    public static int toDisplayHour(int tickHour) {
        return (tickHour + 6) % 24;
    }

    /**
     * Format a display hour as "6AM", "12PM", etc.
     */
    public static String formatHour(int displayHour) {
        if (displayHour == 0) return "12A";
        if (displayHour == 12) return "12P";
        if (displayHour < 12) return displayHour + "A";
        return (displayHour - 12) + "P";
    }
}
