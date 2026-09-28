package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.diagnostics.TownsteadProfiler;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.storage.EmptyContainerDropoff;
import net.conczin.mca.entity.VillagerEntityMCA;
import com.aetherianartificer.townstead.switchboard.Systems;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

public final class VillagerServerTickDispatcher {
    private VillagerServerTickDispatcher() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;

        long gameTime = villager.level().getGameTime();

        // Clean up dead/removed entities
        if (!villager.isAlive() || villager.isRemoved()) {
            FatigueVillagerTicker.forget(villager);
            com.aetherianartificer.townstead.performance.CollapsePlayback.stop(villager);
            TemperatureVillagerTicker.forget(villager.getId());
            WardrobeVillagerTicker.forget(villager.getId());
            WorkToolTicker.forget(villager);
            EmptyContainerDropoff.forget(villager);
            com.aetherianartificer.townstead.profession.ProfessionSites.forget(villager);
            com.aetherianartificer.townstead.work.WorkActivities.forget(villager);
            com.aetherianartificer.townstead.hangout.HangoutEngine.forget(villager);
            com.aetherianartificer.townstead.dialogue.conversation.ConversationEngine.forget(villager);
            com.aetherianartificer.townstead.pheno.state.EntityStates.forget(villager);
            return;
        }

        // Recreational drinking must finish even with hunger/thirst simulation disabled.
        com.aetherianartificer.townstead.hunger.VillagerConsumptionManager.tickAndFinalize(villager,
                com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).needs());

        if (!TownsteadProfiler.enabled()) {
            runSteps(villager, gameTime, false);
            com.aetherianartificer.townstead.performance.CollapsePlayback.tick(villager);
            return;
        }

        runSteps(villager, gameTime, true);
        com.aetherianartificer.townstead.performance.CollapsePlayback.tick(villager);
    }

    /**
     * One per-villager system, with the switch that turns it off (null when it has none of its own).
     * A {@code resident} step only matters to someone living in a village, so wild villagers skip it.
     */
    private record Step(String name, @Nullable String system, boolean resident, BiConsumer<VillagerEntityMCA, Long> action) {
        Step(String name, @Nullable String system, BiConsumer<VillagerEntityMCA, Long> action) {
            this(name, system, false, action);
        }
    }

    private static Step resident(String name, @Nullable String system, BiConsumer<VillagerEntityMCA, Long> action) {
        return new Step(name, system, true, action);
    }

    private static final List<Step> STEPS = List.of(
            resident("villager.conversation", null, (v, t) ->
                    com.aetherianartificer.townstead.dialogue.conversation.ConversationEngine.consider(v)),
            resident("villager.profession_auto_assign", Systems.WORK, (v, t) -> ProfessionAutoAssignTicker.tick(v)),
            resident("villager.profession_trade_backfill", Systems.CAREERS, (v, t) -> ProfessionTradeBackfillTicker.tick(v)),
            new Step("villager.hunger", null, (v, t) -> HungerVillagerTicker.tick(v)),
            resident("villager.thirst", null, (v, t) -> {
                if (ThirstBridgeResolver.isActive()) ThirstVillagerTicker.tick(v);
            }),
            resident("villager.fatigue", null, (v, t) -> FatigueVillagerTicker.tick(v)),
            resident("villager.temperature", null, (v, t) -> TemperatureVillagerTicker.tick(v)),
            resident("villager.wardrobe", Systems.CLOTHING, (v, t) -> WardrobeVillagerTicker.tick(v)),
            resident("villager.container_dropoff", null, (v, t) -> EmptyContainerDropoff.tick(v)),
            resident("villager.profession_memory", Systems.CAREERS, (v, t) -> ProfessionProgressMemoryTicker.tick(v)),
            resident("villager.guard_rest", Systems.WORK, (v, t) -> GuardRestEnforcerTicker.tick(v)),
            resident("villager.work_tool", Systems.WORK, (v, t) -> WorkToolTicker.tick(v)),
            resident("villager.work_feedback", Systems.WORK, (v, t) ->
                    com.aetherianartificer.townstead.work.feedback.WorkFeedbackTicker.tick(v)),
            // Always ticks so a reaction lock still releases after reactions are switched off.
            new Step("villager.reaction_lock", null, (v, t) ->
                    com.aetherianartificer.townstead.reaction.ReactionLockTracker.tickFreeze(v, t)),
            resident("villager.reaction_context", Systems.REACTIONS, (v, t) ->
                    com.aetherianartificer.townstead.reaction.trigger.event.ContextTickHook.tick(v, t)),
            new Step("villager.life_stamper", null, (v, t) ->
                    com.aetherianartificer.townstead.calendar.VillagerLifeStamper.tick(v)),
            new Step("villager.life_stage", null, (v, t) -> LifeStageTicker.tick(v)),
            new Step("villager.rig_crouch", Systems.ROOTS, (v, t) ->
                    com.aetherianartificer.townstead.root.rig.RigCrouch.tick(v)),
            // Gene and skill tickers stay on: Roots and Careers switch off their powers at the source.
            new Step("villager.gene_ability", null, (v, t) ->
                    com.aetherianartificer.townstead.root.ability.GeneAbilityTicker.tick(v)),
            new Step("villager.disposition", Systems.ROOTS, (v, t) ->
                    com.aetherianartificer.townstead.root.disposition.DispositionReactions.tick(v)),
            new Step("villager.gene_attribute", null, (v, t) ->
                    com.aetherianartificer.townstead.root.attribute.GeneAttributeApplier.tick(v)),
            new Step("villager.active_ability", null, (v, t) ->
                    com.aetherianartificer.townstead.root.ability.ActiveAbilities.aiTick(v)),
            new Step("villager.glide", null, (v, t) -> com.aetherianartificer.townstead.root.ability.GlideAI.tick(v)),
            new Step("villager.gene_resource", null, (v, t) ->
                    com.aetherianartificer.townstead.root.ability.ResourceValues.tick(v)),
            new Step("villager.gene_collection", null, (v, t) ->
                    com.aetherianartificer.townstead.root.collection.CollectionValues.tick(v)),
            resident("villager.hangout", Systems.HANGOUTS, (v, t) ->
                    com.aetherianartificer.townstead.hangout.HangoutEngine.tick(v)),
            new Step("villager.pheno_state", null, (v, t) ->
                    com.aetherianartificer.townstead.pheno.state.EntityStates.tick(v)),
            new Step("villager.vampire_cure", null, (v, t) ->
                    com.aetherianartificer.townstead.compat.vampirism.VampireVillagers.tick(v)),
            new Step("villager.wild", null, (v, t) ->
                    com.aetherianartificer.townstead.replace.MobReplacer.tick(v)),
            resident("villager.chronicle_birth", Systems.CHRONICLES, (v, t) ->
                    com.aetherianartificer.townstead.chronicle.emit.PendingBirths.tick(v)),
            resident("villager.chronicle_marriage", Systems.CHRONICLES, (v, t) ->
                    com.aetherianartificer.townstead.chronicle.emit.MarriageWatcher.tick(v, t)),
            resident("villager.resident_register", null, (v, t) ->
                    com.aetherianartificer.townstead.village.ResidentRegister.onVillagerTick(v, t)),
            resident("villager.chronicle_gossip", Systems.CHRONICLES, (v, t) ->
                    com.aetherianartificer.townstead.chronicle.knowledge.GossipTicker.tick(v, t)),
            resident("villager.chronicle_mood", Systems.CHRONICLES, (v, t) ->
                    com.aetherianartificer.townstead.chronicle.consumer.ChronicleMoodTicker.tick(v, t))
    );

    private static void runSteps(VillagerEntityMCA villager, long gameTime, boolean profiled) {
        boolean wild = com.aetherianartificer.townstead.replace.MobReplacer.isWild(villager);
        for (Step step : STEPS) {
            if (wild && step.resident()) continue;
            if (step.system() != null && !Systems.on(step.system())) continue;
            if (profiled) profile(step.name(), () -> step.action().accept(villager, gameTime));
            else step.action().accept(villager, gameTime);
        }
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
