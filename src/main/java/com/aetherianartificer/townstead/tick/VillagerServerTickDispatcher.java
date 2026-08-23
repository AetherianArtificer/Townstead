package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.compat.butchery.ButcherToolAcquisitionTicker;
import com.aetherianartificer.townstead.compat.butchery.SkinRackJob;
import com.aetherianartificer.townstead.diagnostics.TownsteadProfiler;
import com.aetherianartificer.townstead.leatherworking.LeatherworkerSupplyAcquisitionTicker;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.storage.EmptyContainerDropoff;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;

public final class VillagerServerTickDispatcher {
    private VillagerServerTickDispatcher() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;

        long gameTime = villager.level().getGameTime();

        // Clean up dead/removed entities
        if (!villager.isAlive() || villager.isRemoved()) {
            FatigueVillagerTicker.forget(villager);
            WorkToolTicker.forget(villager);
            ButcherToolAcquisitionTicker.forget(villager);
            LeatherworkerSupplyAcquisitionTicker.forget(villager);
            SkinRackJob.forget(villager);
            EmptyContainerDropoff.forget(villager);
            return;
        }

        if (!TownsteadProfiler.enabled()) {
            tickUnprofiled(villager, gameTime);
            return;
        }

        tickProfiled(villager, gameTime);
    }

    private static void tickUnprofiled(VillagerEntityMCA villager, long gameTime) {
        CookAutoAssignTicker.tick(villager);
        BaristaAutoAssignTicker.tick(villager);
        CookTradeBackfillTicker.tick(villager);
        BaristaTradeBackfillTicker.tick(villager);
        HungerVillagerTicker.tick(villager);
        if (ThirstBridgeResolver.isActive()) ThirstVillagerTicker.tick(villager);
        FatigueVillagerTicker.tick(villager);
        EmptyContainerDropoff.tick(villager);
        ProfessionProgressMemoryTicker.tick(villager);
        GuardRestEnforcerTicker.tick(villager);
        ButcherToolAcquisitionTicker.tick(villager);
        LeatherworkerSupplyAcquisitionTicker.tick(villager);
        WorkToolTicker.tick(villager);
        com.aetherianartificer.townstead.work.feedback.WorkFeedbackTicker.tick(villager);
        com.aetherianartificer.townstead.reaction.ReactionLockTracker.tickFreeze(villager, gameTime);
        com.aetherianartificer.townstead.reaction.trigger.event.ContextTickHook.tick(villager, gameTime);
        com.aetherianartificer.townstead.calendar.VillagerLifeStamper.tick(villager);
        LifeStageTicker.tick(villager);
        com.aetherianartificer.townstead.root.rig.RigCrouch.tick(villager);
        com.aetherianartificer.townstead.root.ability.GeneAbilityTicker.tick(villager);
        com.aetherianartificer.townstead.root.disposition.DispositionReactions.tick(villager);
        com.aetherianartificer.townstead.root.attribute.GeneAttributeApplier.tick(villager);
        com.aetherianartificer.townstead.root.ability.ActiveAbilities.aiTick(villager);
        com.aetherianartificer.townstead.root.ability.GlideAI.tick(villager);
        com.aetherianartificer.townstead.root.ability.ResourceValues.tick(villager);
        com.aetherianartificer.townstead.root.collection.CollectionValues.tick(villager);
    }

    private static void tickProfiled(VillagerEntityMCA villager, long gameTime) {

        profile("villager.cook_auto_assign", () -> CookAutoAssignTicker.tick(villager));
        profile("villager.barista_auto_assign", () -> BaristaAutoAssignTicker.tick(villager));
        profile("villager.cook_trade_backfill", () -> CookTradeBackfillTicker.tick(villager));
        profile("villager.barista_trade_backfill", () -> BaristaTradeBackfillTicker.tick(villager));
        profile("villager.hunger", () -> HungerVillagerTicker.tick(villager));
        if (ThirstBridgeResolver.isActive()) {
            profile("villager.thirst", () -> ThirstVillagerTicker.tick(villager));
        }
        profile("villager.fatigue", () -> FatigueVillagerTicker.tick(villager));
        profile("villager.container_dropoff", () -> EmptyContainerDropoff.tick(villager));
        profile("villager.profession_memory", () -> ProfessionProgressMemoryTicker.tick(villager));
        profile("villager.guard_rest", () -> GuardRestEnforcerTicker.tick(villager));
        profile("villager.butcher_tool_acquire", () -> ButcherToolAcquisitionTicker.tick(villager));
        profile("villager.leatherworker_supply", () -> LeatherworkerSupplyAcquisitionTicker.tick(villager));
        profile("villager.work_tool", () -> WorkToolTicker.tick(villager));
        profile("villager.work_feedback", () ->
                com.aetherianartificer.townstead.work.feedback.WorkFeedbackTicker.tick(villager));
        profile("villager.reaction_lock", () ->
                com.aetherianartificer.townstead.reaction.ReactionLockTracker.tickFreeze(villager, gameTime));
        profile("villager.reaction_context", () ->
                com.aetherianartificer.townstead.reaction.trigger.event.ContextTickHook.tick(villager, gameTime));
        profile("villager.life_stamper", () ->
                com.aetherianartificer.townstead.calendar.VillagerLifeStamper.tick(villager));
        profile("villager.life_stage", () -> LifeStageTicker.tick(villager));
        profile("villager.rig_crouch", () ->
                com.aetherianartificer.townstead.root.rig.RigCrouch.tick(villager));
        profile("villager.gene_ability", () ->
                com.aetherianartificer.townstead.root.ability.GeneAbilityTicker.tick(villager));
        profile("villager.disposition", () ->
                com.aetherianartificer.townstead.root.disposition.DispositionReactions.tick(villager));
        profile("villager.gene_attribute", () ->
                com.aetherianartificer.townstead.root.attribute.GeneAttributeApplier.tick(villager));
        profile("villager.active_ability", () ->
                com.aetherianartificer.townstead.root.ability.ActiveAbilities.aiTick(villager));
        profile("villager.glide", () ->
                com.aetherianartificer.townstead.root.ability.GlideAI.tick(villager));
        profile("villager.gene_resource", () ->
                com.aetherianartificer.townstead.root.ability.ResourceValues.tick(villager));
        profile("villager.gene_collection", () ->
                com.aetherianartificer.townstead.root.collection.CollectionValues.tick(villager));
        profile("villager.chronicle_birth", () ->
                com.aetherianartificer.townstead.chronicle.emit.PendingBirths.tick(villager));
        profile("villager.chronicle_marriage", () ->
                com.aetherianartificer.townstead.chronicle.emit.MarriageWatcher.tick(villager, gameTime));
        profile("villager.chronicle_gossip", () ->
                com.aetherianartificer.townstead.chronicle.knowledge.GossipTicker.tick(villager, gameTime));
        profile("villager.chronicle_mood", () ->
                com.aetherianartificer.townstead.chronicle.consumer.ChronicleMoodTicker.tick(villager, gameTime));
    }

    private static void profile(String name, Runnable runnable) {
        if (!TownsteadProfiler.enabled()) {
            runnable.run();
            return;
        }
        long start = System.nanoTime();
        try {
            runnable.run();
        } finally {
            TownsteadProfiler.record(name, System.nanoTime() - start);
        }
    }
}
