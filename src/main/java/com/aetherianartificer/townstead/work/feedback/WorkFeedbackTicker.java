package com.aetherianartificer.townstead.work.feedback;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.schedule.Activity;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CopyOnWriteArrayList;

/** Schedules and delivers data-authored profession feedback. */
public final class WorkFeedbackTicker {
    private static final java.util.List<Observer> OBSERVERS = new CopyOnWriteArrayList<>();

    /** A runtime integration that can discover an immediate, one-shot work event. */
    public interface Observer {
        ResourceLocation id();

        @Nullable Event observe(ServerLevel level, VillagerEntityMCA villager);
    }

    public record Event(ResourceLocation profession, String rule, Object[] arguments,
                        Runnable delivered) {
        public Event(ResourceLocation profession, String rule) {
            this(profession, rule, new Object[0], () -> {});
        }
    }

    private WorkFeedbackTicker() {}

    public static void bootstrap() {
        com.aetherianartificer.townstead.work.job.BlockInteractionWorkTask.bootstrapFeedbackSignals();
        com.aetherianartificer.townstead.compat.butchery.ButcheryWorkFeedback.bootstrap();
        com.aetherianartificer.townstead.leatherworking.LeatherworkerWorkFeedback.bootstrap();
    }

    public static void register(Observer observer) {
        if (observer == null || observer.id() == null) return;
        OBSERVERS.removeIf(existing -> existing.id().equals(observer.id()));
        OBSERVERS.add(observer);
    }

    public static void tick(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (!TownsteadConfig.isWorkFeedbackEnabled()) return;
        if (!onWorkShift(villager, level)) return;

        for (Observer observer : OBSERVERS) {
            Event event = observer.observe(level, villager);
            if (event != null && send(villager, event.profession(), event.rule(),
                    level.getGameTime(), event.arguments())) {
                event.delivered().run();
                return;
            }
        }

        if (!TownsteadConfig.isRepeatedWorkRequestsEnabled()) return;
        long gameTime = level.getGameTime();
        for (ProfessionFeedbackRegistry.Channel channel : ProfessionFeedbackRegistry.all()) {
            if (!ProfessionFeedbackRegistry.matchesProfession(channel.profession(), villager)) continue;
            if (level.getNearestPlayer(villager, channel.range()) == null) continue;
            if (onThrottle(villager, channel, gameTime)) continue;
            for (ProfessionFeedbackDocument.Rule rule : channel.periodicRules()) {
                if (!ProfessionFeedbackRegistry.matches(rule.when(), villager)) continue;
                if (speak(villager, rule, new Object[0])) {
                    markSent(villager, channel, gameTime);
                    return;
                }
            }
        }
    }

    /** Deliver a named rule discovered by a work task. JSON owns the wording and variants. */
    public static boolean send(VillagerEntityMCA villager, ResourceLocation profession,
                               String ruleId, long gameTime, Object... arguments) {
        if (villager == null || !(villager.level() instanceof ServerLevel level)) return false;
        if (!TownsteadConfig.isWorkFeedbackEnabled()) return false;
        ProfessionFeedbackRegistry.Channel channel =
                ProfessionFeedbackRegistry.byProfession(profession);
        if (channel == null || !ProfessionFeedbackRegistry.matchesProfession(profession, villager)) return false;
        ProfessionFeedbackDocument.Rule rule = channel.rule(ruleId);
        if (rule == null || !ProfessionFeedbackRegistry.matches(rule.when(), villager)) return false;
        if (!rule.immediate() && !TownsteadConfig.isRepeatedWorkRequestsEnabled()) return false;
        if (level.getNearestPlayer(villager, channel.range()) == null) return false;
        if (!rule.immediate() && onThrottle(villager, channel, gameTime)) return false;
        if (!speak(villager, rule, arguments)) return false;
        if (!rule.immediate()) markSent(villager, channel, gameTime);
        return true;
    }

    private static boolean speak(VillagerEntityMCA villager,
                                 ProfessionFeedbackDocument.Rule rule,
                                 Object[] arguments) {
        villager.sendChatToAllAround(rule.translation(),
                arguments == null ? new Object[0] : arguments);
        return true;
    }

    private static boolean onWorkShift(VillagerEntityMCA villager, ServerLevel level) {
        Brain<?> brain = villager.getBrain();
        long dayTime = level.getDayTime() % 24000L;
        return brain.getSchedule().getActivityAt((int) dayTime) == Activity.WORK;
    }

    private static boolean onThrottle(VillagerEntityMCA villager,
                                      ProfessionFeedbackRegistry.Channel channel,
                                      long gameTime) {
        long last = TownsteadVillagers.get(villager).professionMemory()
                .cooldown(cooldownKey(channel));
        return gameTime - last < effectiveInterval(channel);
    }

    /** Whether a work task should spend time discovering a repeatable request. */
    public static boolean repeatedRequestsEnabled() {
        return TownsteadConfig.isWorkFeedbackEnabled()
                && TownsteadConfig.isRepeatedWorkRequestsEnabled();
    }

    /** The authored profession interval constrained by the server's universal spam floor. */
    public static long effectiveInterval(ResourceLocation profession) {
        ProfessionFeedbackRegistry.Channel channel =
                ProfessionFeedbackRegistry.byProfession(profession);
        long authored = channel == null ? 1200L : channel.interval();
        return Math.max(authored, TownsteadConfig.minimumWorkRequestIntervalTicks());
    }

    private static long effectiveInterval(ProfessionFeedbackRegistry.Channel channel) {
        return Math.max(channel.interval(), TownsteadConfig.minimumWorkRequestIntervalTicks());
    }

    private static void markSent(VillagerEntityMCA villager,
                                 ProfessionFeedbackRegistry.Channel channel,
                                 long gameTime) {
        TownsteadVillagers.get(villager).professionMemory()
                .setCooldown(cooldownKey(channel), gameTime);
    }

    private static String cooldownKey(ProfessionFeedbackRegistry.Channel channel) {
        return "townstead:work_feedback/" + channel.profession();
    }
}
