package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.TownsteadAPI;
import com.aetherianartificer.townstead.api.TownsteadVillagerSnapshot;
import com.aetherianartificer.townstead.api.v1.SchedulesApi;
import com.aetherianartificer.townstead.api.v1.model.ShiftTemplateSnapshot;
import com.aetherianartificer.townstead.api.v1.result.ScheduleResult;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.shift.ShiftScheduleApplier;
import com.aetherianartificer.townstead.shift.ShiftScheduleSync;
import com.aetherianartificer.townstead.shift.template.ShiftTemplate;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateRegistry;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class SchedulesImpl implements SchedulesApi {

    @Override
    public List<ShiftTemplateSnapshot> templates(MinecraftServer server) {
        List<ShiftTemplateSnapshot> out = new ArrayList<>();
        try {
            if (server == null) return out;
            for (ShiftTemplate template : ShiftTemplateRegistry.combinedFor(server)) out.add(snapshot(template));
        } catch (Throwable t) {
            ApiSupport.swallow("schedules.templates", t);
        }
        return out;
    }

    @Override
    public Optional<ShiftTemplateSnapshot> template(MinecraftServer server, ResourceLocation id) {
        try {
            if (server == null || id == null) return Optional.empty();
            return ShiftTemplateRegistry.resolve(server, id).map(SchedulesImpl::snapshot);
        } catch (Throwable t) {
            ApiSupport.swallow("schedules.template", t);
            return Optional.empty();
        }
    }

    @Override
    public ScheduleResult applyTemplate(Entity entity, ResourceLocation templateId, ResourceLocation source) {
        try {
            if (!ApiSupport.writesAllowed(source)) return ScheduleResult.failed(ScheduleResult.Status.DISABLED, "writes from " + source + " are disabled");
            VillagerEntityMCA villager = ApiSupport.villager(entity);
            if (villager == null) return ScheduleResult.failed(ScheduleResult.Status.NOT_A_VILLAGER, "not an MCA villager");
            if (templateId == null || villager.getServer() == null) return ScheduleResult.failed(ScheduleResult.Status.INVALID, "no template id");
            Optional<ShiftTemplate> template = ShiftTemplateRegistry.resolve(villager.getServer(), templateId);
            if (template.isEmpty()) return ScheduleResult.failed(ScheduleResult.Status.UNKNOWN_TEMPLATE, "unknown template " + templateId);
            return writeDaily(villager, template.get().copyShifts(), templateId.toString());
        } catch (Throwable t) {
            ApiSupport.swallow("schedules.applyTemplate", t);
            return ScheduleResult.failed(ScheduleResult.Status.ERROR, t.toString());
        }
    }

    @Override
    public ScheduleResult setCustomShifts(Entity entity, List<Integer> shifts, ResourceLocation source) {
        try {
            if (!ApiSupport.writesAllowed(source)) return ScheduleResult.failed(ScheduleResult.Status.DISABLED, "writes from " + source + " are disabled");
            VillagerEntityMCA villager = ApiSupport.villager(entity);
            if (villager == null) return ScheduleResult.failed(ScheduleResult.Status.NOT_A_VILLAGER, "not an MCA villager");
            if (shifts == null || shifts.size() != ShiftData.HOURS_PER_DAY) {
                return ScheduleResult.failed(ScheduleResult.Status.INVALID, "expected " + ShiftData.HOURS_PER_DAY + " shift ordinals");
            }
            int[] array = new int[ShiftData.HOURS_PER_DAY];
            for (int i = 0; i < array.length; i++) {
                int ordinal = shifts.get(i) == null ? ShiftData.ORD_IDLE : shifts.get(i);
                if (ordinal < 0 || ordinal >= ShiftData.ORDINAL_TO_ACTIVITY.length) {
                    return ScheduleResult.failed(ScheduleResult.Status.INVALID, "shift ordinal out of range at hour " + i);
                }
                array[i] = ordinal;
            }
            return writeDaily(villager, array, "");
        } catch (Throwable t) {
            ApiSupport.swallow("schedules.setCustomShifts", t);
            return ScheduleResult.failed(ScheduleResult.Status.ERROR, t.toString());
        }
    }

    @Override
    public ScheduleResult setWeeklyTemplates(Entity entity, List<String> templateIdsByWeekday, ResourceLocation source) {
        try {
            if (!ApiSupport.writesAllowed(source)) return ScheduleResult.failed(ScheduleResult.Status.DISABLED, "writes from " + source + " are disabled");
            VillagerEntityMCA villager = ApiSupport.villager(entity);
            if (villager == null) return ScheduleResult.failed(ScheduleResult.Status.NOT_A_VILLAGER, "not an MCA villager");
            if (templateIdsByWeekday == null || templateIdsByWeekday.isEmpty()) {
                return ScheduleResult.failed(ScheduleResult.Status.INVALID, "expected one template id per weekday");
            }
            MinecraftServer server = villager.getServer();
            List<String> days = new ArrayList<>();
            for (String id : templateIdsByWeekday) {
                String safe = id == null ? "" : id;
                if (!safe.isEmpty()) {
                    ResourceLocation parsed = ResourceLocation.tryParse(safe);
                    if (parsed == null || server == null || ShiftTemplateRegistry.resolve(server, parsed).isEmpty()) {
                        return ScheduleResult.failed(ScheduleResult.Status.UNKNOWN_TEMPLATE, "unknown template " + safe);
                    }
                }
                days.add(safe);
            }
            TownsteadVillager state = TownsteadVillagers.get(villager);
            state.schedule().setMode(ShiftData.MODE_WEEKLY);
            state.schedule().setWeekDayTemplates(days);
            ShiftScheduleApplier.apply(villager);
            TownsteadVillagers.flush(villager);
            ShiftScheduleSync.broadcastWeek(villager);
            return applied(villager);
        } catch (Throwable t) {
            ApiSupport.swallow("schedules.setWeeklyTemplates", t);
            return ScheduleResult.failed(ScheduleResult.Status.ERROR, t.toString());
        }
    }

    private static ScheduleResult writeDaily(VillagerEntityMCA villager, int[] shifts, String templateId) {
        TownsteadVillager state = TownsteadVillagers.get(villager);
        state.schedule().setMode(ShiftData.MODE_DAILY);
        state.schedule().setShifts(shifts);
        state.schedule().setTemplateId(templateId);
        ShiftScheduleApplier.apply(villager);
        TownsteadVillagers.flush(villager);
        ShiftScheduleSync.broadcastShifts(villager, shifts);
        return applied(villager);
    }

    private static ScheduleResult applied(VillagerEntityMCA villager) {
        TownsteadVillagerSnapshot legacy = TownsteadAPI.villager(villager);
        return new ScheduleResult(ScheduleResult.Status.APPLIED,
                Optional.of(ApiSnapshots.schedule(legacy.schedule())), "");
    }

    static ShiftTemplateSnapshot snapshot(ShiftTemplate template) {
        List<Integer> shifts = new ArrayList<>();
        for (int ordinal : template.shifts()) shifts.add(ordinal);
        return new ShiftTemplateSnapshot(template.id(), template.displayName(), shifts, template.chronotype(),
                template.builtIn());
    }
}
