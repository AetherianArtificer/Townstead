package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * Hiring and firing a career whose workplaces Townstead counts.
 *
 * <p>One rule for every such trade, because there was never more than one: an idle villager in a
 * village with a free seat takes the job, and a villager holding the job with no seat left loses
 * it. Capacity is not a suggestion — if it were, the count a player is shown would drift from the
 * number of workers actually standing in the room.</p>
 *
 * <p>Demotes what Townstead seats: the career's own profession and every registered carrier of
 * it (see {@link ProfessionCarriers}). A profession anchored to some other mod's job-site POI
 * that means nothing to Townstead is hired and fired by that mod's rules, and stripping it here
 * would fight the brain's claim in an endless flap.</p>
 */
public final class ProfessionAutoAssign {

    private static final ResourceLocation COOK = ResourceLocation.tryParse("townstead:cook");

    private ProfessionAutoAssign() {}

    /** Whether this career hires at all right now: its mods are present and its toggle is on. */
    public static boolean enabled(ProfessionDef def) {
        if (def == null) return false;
        return !COOK.equals(def.id()) || TownsteadConfig.isTownsteadCookEnabled();
    }

    /**
     * One pass for one villager and one career.
     *
     * @param taskType the work task that identifies the career
     * @param enabled  whether this trade is available at all (mods installed, config on)
     * @param interval how often to ask, in ticks — the whole check walks the village
     */
    public static void tick(VillagerEntityMCA villager, ResourceLocation taskType,
                            boolean enabled, int interval) {
        tick(villager, ProfessionSites.defForTask(taskType), enabled, interval);
    }

    /** One pass for a concrete data-defined career, without using a shared task id as identity. */
    public static void tick(VillagerEntityMCA villager, ProfessionDef def,
                            boolean enabled, int interval) {
        if (!enabled) return;
        if (interval > 0 && villager.tickCount % interval != 0) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (villager.isBaby() || !villager.isAlive() || villager.isSleeping()) return;

        if (def == null) return;
        VillagerProfession assignable = ProfessionSites.professionFor(def);
        VillagerProfession current = villager.getVillagerData().getProfession();

        if (current == assignable || ProfessionCarriers.carries(current, def)) {
            // Already doing this work: keep it only while a seat is still theirs. A carrier
            // arrived holding its own job block; hand that back so the room reads as free.
            if (ProfessionSites.assignedSite(level, villager, def).isEmpty()) {
                narrate(level, villager, "SEAT:released " + ProfessionSlotRules.professionKey(current)
                        + " from " + def.id() + ": no seat left");
                releaseJobSite(villager);
                villager.setProfession(VillagerProfession.NONE);
                Townstead.townstead$broadcastProfessionTier(villager);
            }
            return;
        }
        if (current != VillagerProfession.NONE) return;
        if (assignable == null) return;
        if (!def.eligible(villager)) return;
        if (!ProfessionSites.hasFreeSite(level, villager, def)) return;
        narrate(level, villager, "SEAT:hired into " + def.id());
        villager.setProfession(assignable);
        Townstead.townstead$broadcastProfessionTier(villager);
    }

    /** Narrates a hire or release to the nearest player when villager-AI debugging is on. */
    private static void narrate(ServerLevel level, VillagerEntityMCA villager, String message) {
        if (!TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        if (!(level.getNearestPlayer(villager, 24)
                instanceof net.minecraft.server.level.ServerPlayer player)) return;
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                "[Seats:" + villager.getName().getString() + "] " + message));
    }

    private static void releaseJobSite(VillagerEntityMCA villager) {
        villager.releasePoi(MemoryModuleType.JOB_SITE);
        villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
        villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
        villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
    }

    /** Practiced building careers are hired and fired by Townstead's seat resolver. */
    public static boolean managesDefinition(ProfessionDef def) {
        if (def == null || !def.isRoot()) return false;
        return def.jobSites().stream().anyMatch(
                com.aetherianartificer.townstead.profession.def.JobSiteProvider.Building.class::isInstance);
    }
}
