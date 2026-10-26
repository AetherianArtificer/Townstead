package com.aetherianartificer.townstead.chronicle.knowledge;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.chronicle.model.Account;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.reaction.*;
import com.aetherianartificer.townstead.reaction.trigger.types.ChronicleLearnedTriggerType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Synchronous, bounded post-learn notification. Reactions cannot recursively deliver more notifications. */
public final class ChronicleLearnedReactions {
    private static boolean dispatching;
    private ChronicleLearnedReactions() {}
    public static void onLearned(MinecraftServer server, ChronicleEventTemplate template, ChronicleEvent event,
                                 Account account, DistortionOverlay overlay) {
        if (dispatching) return;
        String key = ChronicleLearnedTriggerType.KEY;
        Set<ResourceLocation> candidates = new LinkedHashSet<>(ReactionRegistry.triggers().matchesFor(key, template.id().toString()));
        candidates.addAll(ReactionRegistry.triggers().matchesFor(key, "*"));
        if (candidates.isEmpty()) return;
        dispatching = true;
        try {
            for (ServerLevel level : server.getAllLevels()) {
                if (!(level.getEntity(account.knower()) instanceof VillagerEntityMCA knower) || !knower.isAlive()) continue;
                UUID primary = event.participations().stream().filter(p -> p.role().equals(template.primaryRole().id()))
                        .map(p -> p.ref().uuid()).filter(java.util.Objects::nonNull).findFirst().orElse(null);
                UUID believed = overlay.believedUuid(template.primaryRole().id(), primary);
                LivingEntity counterpart = believed == null || believed.equals(account.knower()) ? null
                        : level.getEntity(believed) instanceof LivingEntity living
                            && living.distanceToSqr(knower) <= 256 && knower.hasLineOfSight(living) ? living : null;
                ReactionContext context = new ReactionContext(ReactionContext.TriggerSource.CHRONICLE_LEARNED, null,
                        knower.blockPosition(), Set.of("chronicle:" + template.id(), "learned_via:" + account.channel()), 0, counterpart);
                var parser = new ChronicleLearnedTriggerType();
                for (ResourceLocation id : candidates) {
                    Reaction reaction = ReactionRegistry.get(id).orElse(null);
                    if (reaction == null) continue;
                    boolean match = reaction.rawTriggers().stream().filter(raw -> raw.has("type") && key.equals(raw.get("type").getAsString()))
                            .map(parser::parse).anyMatch(instance -> instance instanceof ChronicleLearnedTriggerType.Instance trigger
                                    && trigger.matches(template.id().toString(), account.channel(), account.fidelity()));
                    if (match) ReactionDispatcher.fire(level, knower, reaction, context);
                }
                break;
            }
        } catch (RuntimeException ex) {
            Townstead.LOGGER.warn("Chronicle learned reaction failed after account {} was recorded", account.accountId(), ex);
        } finally { dispatching = false; }
    }
}
