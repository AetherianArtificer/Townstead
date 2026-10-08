package com.aetherianartificer.townstead.api.v1;

import com.aetherianartificer.townstead.api.v1.model.ShiftTemplateSnapshot;
import com.aetherianartificer.townstead.api.v1.result.ScheduleResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;

/**
 * Shift schedules. Shifts are 24 ordinals, one per hour from midnight: 0 idle, 1 work, 2 meet,
 * 3 rest. Every write applies the schedule, persists it, and syncs it to tracking clients.
 */
public interface SchedulesApi {

    int IDLE = 0;
    int WORK = 1;
    int MEET = 2;
    int REST = 3;

    /** Built-in templates plus the server's user-defined ones. */
    List<ShiftTemplateSnapshot> templates(MinecraftServer server);

    Optional<ShiftTemplateSnapshot> template(MinecraftServer server, ResourceLocation id);

    /** Puts the villager on daily mode with a template's shifts. */
    ScheduleResult applyTemplate(Entity villager, ResourceLocation templateId, ResourceLocation source);

    /** Puts the villager on daily mode with explicit shifts; exactly 24 ordinals. */
    ScheduleResult setCustomShifts(Entity villager, List<Integer> shifts, ResourceLocation source);

    /** Puts the villager on weekly mode with one template id per weekday; an empty id keeps the custom shifts. */
    ScheduleResult setWeeklyTemplates(Entity villager, List<String> templateIdsByWeekday, ResourceLocation source);
}
