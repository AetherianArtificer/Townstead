package com.aetherianartificer.townstead.shift;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.shift.template.ShiftTemplate;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateRegistry;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * Utility to apply a custom TownsteadSchedule to a villager's brain
 * based on their stored shift data.
 *
 * <p>Two modes are supported (see {@link ShiftData}):
 * <ul>
 *   <li>{@code daily} — one 24-slot array used every day (the original behavior).</li>
 *   <li>{@code weekly} — each weekday references a shift template; the array for
 *       <em>today's</em> day-of-week is resolved and installed. The day-of-week
 *       comes from the Townstead calendar, so re-applying on day rollover
 *       (see {@link #reapplyWeeklySchedules}) swaps in the next day's array.</li>
 * </ul>
 */
public final class ShiftScheduleApplier {

    private ShiftScheduleApplier() {}

    /**
     * Read the villager's shift data and set a custom schedule on their brain.
     * If the villager has no Townstead override (daily mode with no custom
     * shifts, or weekly mode with today's slot empty and no custom daily
     * fallback), MCA's existing schedule is left untouched.
     */
    public static void apply(VillagerEntityMCA villager) {
        if (villager.getBrain() == null) return;

        TownsteadVillager.ScheduleState schedule = TownsteadVillagers.get(villager).schedule();
        int[] resolved = resolveTodayShifts(villager, schedule);
        if (resolved == null) return;
        villager.getBrain().setSchedule(new TownsteadSchedule(resolved));
    }

    /**
     * Resolve the 24-slot array that should drive this villager's schedule
     * today, or {@code null} to mean "no Townstead override — preserve MCA's
     * schedule".
     */
    private static int[] resolveTodayShifts(VillagerEntityMCA villager, TownsteadVillager.ScheduleState schedule) {
        if (ShiftData.MODE_WEEKLY.equals(schedule.mode())) {
            MinecraftServer server = villager.level().getServer();
            int dow = 0;
            if (server != null) {
                CalendarDate today = TownsteadCalendar.today(server);
                dow = Math.max(0, today.dayOfWeek());
            }
            java.util.List<String> weekDays = schedule.weekDayTemplates();
            String tplId = dow >= 0 && dow < weekDays.size() ? weekDays.get(dow) : "";
            if (server != null && tplId != null && !tplId.isEmpty()) {
                ResourceLocation rl = tryParse(tplId);
                if (rl != null) {
                    Optional<ShiftTemplate> tpl = ShiftTemplateRegistry.resolve(server, rl);
                    if (tpl.isPresent()) return tpl.get().copyShifts();
                }
            }
            // Empty slot or a template that no longer exists: fall back to the
            // daily schedule if one was customized, else leave MCA's alone.
            return schedule.hasCustomShifts() ? schedule.copyShifts() : null;
        }

        // Daily mode.
        return schedule.hasCustomShifts() ? schedule.copyShifts() : null;
    }

    /**
     * Re-apply weekly schedules to every loaded villager that uses weekly mode.
     * Called once per in-game day rollover (and after a real-clock catch-up
     * jump) so each villager's schedule tracks the new day-of-week. O(loaded
     * villagers), off the per-tick hot path. Mirrors the iteration shape of
     * {@code AgeableCatchup}.
     */
    public static void reapplyWeeklySchedules(MinecraftServer server) {
        if (server == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (!(entity instanceof VillagerEntityMCA villager)) continue;
                if (!ShiftData.MODE_WEEKLY.equals(TownsteadVillagers.get(villager).schedule().mode())) continue;
                apply(villager);
            }
        }
    }

    /**
     * Temporarily override the villager's schedule to all-REST.
     * Used when the villager needs fatigue recovery outside their normal rest hours.
     * Call {@link #apply(VillagerEntityMCA)} to restore the original schedule.
     */
    public static void overrideToRest(VillagerEntityMCA villager) {
        if (villager.getBrain() == null) return;
        int[] allRest = new int[ShiftData.HOURS_PER_DAY];
        // REST ordinal from ShiftData.ORDINAL_TO_ACTIVITY
        int restOrdinal = 3; // REST
        java.util.Arrays.fill(allRest, restOrdinal);
        villager.getBrain().setSchedule(new TownsteadSchedule(allRest));
    }

    private static ResourceLocation tryParse(String s) {
        try {
            //? if >=1.21 {
            return ResourceLocation.parse(s);
            //?} else {
            /*return new ResourceLocation(s);
            *///?}
        } catch (Exception ex) {
            return null;
        }
    }
}
