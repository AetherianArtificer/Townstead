package com.aetherianartificer.townstead.origin.needs;

import com.aetherianartificer.townstead.origin.ExpressedGenes;
import com.aetherianartificer.townstead.origin.gene.types.DietGeneType;
import com.aetherianartificer.townstead.origin.gene.types.HydrationGeneType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-side query for needs switched off by a race's diet/hydration genes: a {@code diet:"none"}
 * race does not eat (hunger off), a {@code liquid:"none"} race does not drink (thirst off). Read by
 * the hunger/thirst tickers to skip a need's decay (pinning it full, so the seek behavior never
 * fires). The client mirrors this from the synced gene catalog to hide the need's interact-screen
 * icon (see {@code ClientNeeds}).
 */
public final class NeedSuppression {

    private NeedSuppression() {}

    public static boolean suppressesHunger(LivingEntity entity) {
        for (DietGeneType.Instance gene : ExpressedGenes.instancesOf(entity, DietGeneType.Instance.class)) {
            if (gene.disablesHunger()) return true;
        }
        return false;
    }

    public static boolean suppressesThirst(LivingEntity entity) {
        for (HydrationGeneType.Instance gene : ExpressedGenes.instancesOf(entity, HydrationGeneType.Instance.class)) {
            if (gene.disablesThirst()) return true;
        }
        return false;
    }
}
