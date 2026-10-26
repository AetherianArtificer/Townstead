package com.aetherianartificer.townstead.work.feedback;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.schedule.Activity;

/** Schedules and delivers data-authored profession feedback. */
public final class WorkFeedbackTicker {
    private static final int OBSERVATION_INTERVAL_TICKS = 20;

    private WorkFeedbackTicker() {}

    public static void bootstrap() {
        com.aetherianartificer.townstead.work.job.BlockInteractionWorkTask.bootstrapFeedbackSignals();
    }

    public static void tick(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (!TownsteadConfig.isWorkFeedbackEnabled()) return;
        long gameTime = level.getGameTime();
        if (Math.floorMod(gameTime + villager.getId(), OBSERVATION_INTERVAL_TICKS) != 0) return;
        if (!onWorkShift(villager, level)) return;

        if (observeRisingRules(level, villager)) return;

        if (!TownsteadConfig.isRepeatedWorkRequestsEnabled()) return;
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

    /** Evaluate data-authored false-to-true edges, seeding first sight silently. */
    private static boolean observeRisingRules(ServerLevel level, VillagerEntityMCA villager) {
        for (ProfessionFeedbackRegistry.Channel channel : ProfessionFeedbackRegistry.all()) {
            if (!ProfessionFeedbackRegistry.matchesProfession(channel.profession(), villager)) continue;
            for (ProfessionFeedbackDocument.Rule rule : channel.risingRules()) {
                String key = "townstead:work_feedback/rising/" + rule.source();
                boolean current = ProfessionFeedbackRegistry.matches(rule.when(), villager);
                Boolean previous = TownsteadVillagers.get(villager).professionMemory()
                        .feedbackObservation(key);
                if (previous == null) {
                    TownsteadVillagers.get(villager).professionMemory()
                            .setFeedbackObservation(key, current);
                    continue;
                }
                if (!current) {
                    if (previous) TownsteadVillagers.get(villager).professionMemory()
                            .setFeedbackObservation(key, false);
                    continue;
                }
                if (previous || level.getNearestPlayer(villager, channel.range()) == null) continue;
                if (speak(villager, rule, new Object[0])) {
                    TownsteadVillagers.get(villager).professionMemory()
                            .setFeedbackObservation(key, true);
                    return true;
                }
            }
        }
        return false;
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
