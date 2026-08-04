package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.work.WorkTaskDeclarations;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * Hiring and firing a career whose workplaces Townstead counts.
 *
 * <p>One rule for every such trade, because there was never more than one: an idle villager in a
 * village with a free seat takes the job, and a villager holding the job with no seat left loses
 * it. Capacity is not a suggestion — if it were, the count a player is shown would drift from the
 * number of workers actually standing in the room.</p>
 *
 * <p>Only demotes what Townstead itself assigns. A profession anchored to another mod's job-site
 * POI is hired and fired by that mod's rules, and stripping it here would fight the brain's claim
 * in an endless flap.</p>
 */
public final class ProfessionAutoAssign {

    private ProfessionAutoAssign() {}

    /**
     * One pass for one villager and one career.
     *
     * @param taskType the work task that identifies the career
     * @param enabled  whether this trade is available at all (mods installed, config on)
     * @param interval how often to ask, in ticks — the whole check walks the village
     */
    public static void tick(VillagerEntityMCA villager, ResourceLocation taskType,
                            boolean enabled, int interval) {
        if (!enabled) return;
        if (interval > 0 && villager.tickCount % interval != 0) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (villager.isBaby() || !villager.isAlive() || villager.isSleeping()) return;

        ProfessionDef def = ProfessionSites.defForTask(taskType);
        if (def == null) return;
        VillagerProfession assignable = ProfessionSites.professionForTask(taskType);
        VillagerProfession current = villager.getVillagerData().getProfession();

        if (WorkTaskDeclarations.professionDeclares(current, taskType)) {
            // Already doing this work: keep it only while a seat is still theirs.
            if (current == assignable
                    && ProfessionSites.assignedSite(level, villager, def).isEmpty()) {
                villager.setProfession(VillagerProfession.NONE);
            }
            return;
        }
        if (current != VillagerProfession.NONE) return;
        if (assignable == null) return;
        if (!ProfessionSites.hasFreeSite(level, villager, def)) return;
        villager.setProfession(assignable);
    }
}
