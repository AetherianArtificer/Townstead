package com.aetherianartificer.townstead.reaction.trigger.event;

import com.aetherianartificer.townstead.reaction.ReactionDispatcher;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * Translates villager task lifecycle into generic {@code task} trigger
 * events. v1 wires the hook into {@code ProducerWorkTask} only; the
 * task id used is the villager's profession registry id at the moment
 * the task starts or stops, so authors can write triggers like
 * {@code task: townstead:cook phase: start}.
 *
 * <p>Phases:</p>
 * <ul>
 *   <li>{@code start} — task begins executing.
 *   <li>{@code stop} — task is no longer running, for any reason.
 * </ul>
 */
public final class TaskEventBridge {
    private TaskEventBridge() {}

    public static void onStart(ServerLevel level, VillagerEntityMCA villager) {
        dispatch(level, villager, "start");
    }

    public static void onStop(ServerLevel level, VillagerEntityMCA villager) {
        dispatch(level, villager, "stop");
    }

    /**
     * Emit {@code stop:<reason>} (or plain {@code stop} when the reason
     * is null/blank). Used by the producer task to differentiate
     * give-ups from normal task changes.
     */
    public static void onStop(ServerLevel level, VillagerEntityMCA villager, String reason) {
        if (reason == null || reason.isBlank()) {
            dispatch(level, villager, "stop");
        } else {
            dispatch(level, villager, "stop:" + reason);
            // Also emit the bare "stop" phase so authors who don't care
            // about reasons can still trigger on it.
            dispatch(level, villager, "stop");
        }
    }

    private static void dispatch(ServerLevel level, VillagerEntityMCA villager, String phase) {
        if (level == null || villager == null) return;
        ResourceLocation taskId = professionId(villager);
        if (taskId == null) return;
        ReactionDispatcher.onTaskTransition(level, villager, taskId, phase);
    }

    private static ResourceLocation professionId(VillagerEntityMCA villager) {
        try {
            VillagerProfession profession = villager.getVillagerData().getProfession();
            if (profession == null) return null;
            return BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        } catch (Throwable t) {
            return null;
        }
    }
}
