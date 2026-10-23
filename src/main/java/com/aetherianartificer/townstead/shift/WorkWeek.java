package com.aetherianartificer.townstead.shift;

import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.shift.template.ShiftTemplate;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateRegistry;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** A villager's schedule for each day of the week, as the shift applier would resolve it. */
public final class WorkWeek {
    private WorkWeek() {}

    /** One 24-hour shift array per day of the week. Days MCA runs itself use its default. */
    public static List<int[]> days(MinecraftServer server, VillagerEntityMCA villager) {
        TownsteadVillager.ScheduleState schedule = TownsteadVillagers.get(villager).schedule();
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        int count = profile != null && profile.daysPerWeek() > 0 ? profile.daysPerWeek() : 7;
        boolean weekly = ShiftData.MODE_WEEKLY.equals(schedule.mode());
        List<String> week = schedule.weekDayTemplates();
        List<int[]> out = new ArrayList<>(count);
        for (int day = 0; day < count; day++) {
            int[] shifts = weekly ? template(server, day < week.size() ? week.get(day) : "") : null;
            if (shifts == null) shifts = schedule.hasCustomShifts() ? schedule.copyShifts() : ShiftData.getVanillaDefault();
            out.add(shifts);
        }
        return out;
    }

    public static int workHours(int[] shifts) {
        int hours = 0;
        for (int shift : shifts) if (shift == ShiftData.ORD_WORK) hours++;
        return hours;
    }

    private static int[] template(MinecraftServer server, String templateId) {
        if (templateId == null || templateId.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(templateId);
        Optional<ShiftTemplate> template = id == null ? Optional.empty() : ShiftTemplateRegistry.resolve(server, id);
        return template.map(ShiftTemplate::copyShifts).orElse(null);
    }
}
