package com.aetherianartificer.townstead.villager;

import com.aetherianartificer.townstead.compat.mca.McaResidency;
import com.aetherianartificer.townstead.journey.Journeys;
import com.aetherianartificer.townstead.pheno.state.EntityStates;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import com.aetherianartificer.townstead.visitor.Visitors;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Someone in a state that keeps them out of villages ({@code "resident": false}) belongs to none:
 * MCA builds their brain as a guest's and never gives them a home. MCA picks the brain when the
 * villager is made, so it is rebuilt here whenever the state comes or goes, and when it ends they
 * look for a village like anyone else.
 */
public final class NonResidents {

    /** Someone starting over (cured, freed) who has not settled anywhere yet. */
    public static final ResourceLocation NEWCOMER = ResourceLocation.tryParse("townstead_state:newcomer");
    /** Someone a story brought to town, who stays only if the story settles them. */
    public static final ResourceLocation VISITOR = ResourceLocation.tryParse("townstead_state:visitor");

    // Whether each villager was last seen out of every village; absent until first seen this session.
    private static final Map<VillagerEntityMCA, Boolean> SEEN = new WeakHashMap<>();

    /** The answer in MCA's main dialogue that asks a newcomer to stay; offered to newcomers only. */
    public static final String STAY_ANSWER = "newcomer_stay";

    private NonResidents() {}

    public static boolean isNewcomer(VillagerEntityMCA villager) {
        return EntityStates.resolve(villager, NEWCOMER).active();
    }

    /** Whether MCA may offer {@code answer} to {@code villager}: the stay answer is for newcomers. */
    public static boolean offers(String answer, VillagerEntityMCA villager) {
        return !STAY_ANSWER.equals(answer) || isNewcomer(villager);
    }

    /** A newcomer agreed to stay: they stop being one and join the village they are in. */
    public static void settle(VillagerEntityMCA villager) {
        EntityStates.clear(villager, NEWCOMER, null);
        McaResidency.joinVillage(villager);
    }

    public static void tick(VillagerEntityMCA villager) {
        // The first tick too: a villager loaded out of villages was given a resident's brain.
        if (villager.tickCount > 1 && villager.tickCount % 20 != 0 || !(villager.level() instanceof ServerLevel level)) return;
        // A visitor no story holds any more (turned and cured, or sent off and arrived elsewhere) is
        // only someone starting over.
        if (EntityStates.resolve(villager, VISITOR).active() && Visitors.role(villager) == null
                && !Journeys.isLeaving(villager.getUUID())) {
            EntityStates.clear(villager, VISITOR, null);
            EntityStates.set(villager, NEWCOMER, 1, 0, null);
        }
        boolean now = EntityStates.nonResident(villager);
        Boolean before;
        synchronized (SEEN) {
            before = SEEN.put(villager, now);
        }
        if (before == null ? !now : before == now) return;
        if (now) McaResidency.leaveVillage(villager);
        villager.refreshBrain(level);
        if (!now) McaResidency.joinVillage(villager);
    }
}
