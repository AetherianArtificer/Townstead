package com.aetherianartificer.townstead.leatherworking;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
/** Leatherworking runtime facts supplied to data-authored profession feedback. */
public final class LeatherworkerWorkFeedback {

    private LeatherworkerWorkFeedback() {}

    public static void bootstrap() {
        register("no_hide");
        register("no_salt");
        register("no_wet_sponge");
        register("no_storage");
    }

    private static void register(String reason) {
        //? if >=1.21 {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("townstead_leatherworking", reason);
        //?} else {
        /*ResourceLocation id = new ResourceLocation("townstead_leatherworking", reason);
        *///?}
        com.aetherianartificer.townstead.work.feedback.WorkFeedbackSignals.register(id,
                villager -> hasReason(villager, reason));
    }

    private static boolean hasReason(VillagerEntityMCA villager, String reason) {
        if (!(villager.level() instanceof net.minecraft.server.level.ServerLevel level)) return false;
        if (!com.aetherianartificer.townstead.work.WorkTaskDeclarations.permitsTask(
                villager, com.aetherianartificer.townstead.profession.def.WorkTaskTypes.TAN)) return false;
        for (LeatherworkerJob job : LeatherworkerJobs.all()) {
            if (!job.isAvailable()) continue;
            if (reason.equals(job.missingSupplyReason(level, villager))) return true;
        }
        return false;
    }
}
