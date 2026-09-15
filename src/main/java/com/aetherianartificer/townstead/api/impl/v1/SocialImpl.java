package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.TownsteadAPI;
import com.aetherianartificer.townstead.api.v1.SocialApi;
import com.aetherianartificer.townstead.api.v1.model.GeneSnapshot;
import com.aetherianartificer.townstead.api.v1.model.PersonalitySnapshot;
import com.aetherianartificer.townstead.api.v1.model.ReactionCause;
import com.aetherianartificer.townstead.api.v1.model.RootSnapshot;
import com.aetherianartificer.townstead.expression.ExpressionCues;
import com.aetherianartificer.townstead.expression.ExpressionService;
import com.aetherianartificer.townstead.reaction.Reaction;
import com.aetherianartificer.townstead.reaction.ReactionContext;
import com.aetherianartificer.townstead.reaction.ReactionDispatcher;
import com.aetherianartificer.townstead.reaction.ReactionLockTracker;
import com.aetherianartificer.townstead.reaction.ReactionRegistry;
import com.aetherianartificer.townstead.reaction.backend.ReactionBackends;
import com.aetherianartificer.townstead.reaction.trigger.event.ContextResolver;
import com.aetherianartificer.townstead.reaction.trigger.event.DialogueStateTracker;
import com.aetherianartificer.townstead.reaction.trigger.event.SocialInteractionTracker;
import com.aetherianartificer.townstead.root.personality.PersonalityDef;
import com.aetherianartificer.townstead.root.personality.PersonalityResolver;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

final class SocialImpl implements SocialApi {

    @Override
    public Optional<PersonalitySnapshot> personality(String personalityId) {
        try {
            PersonalityDef def = PersonalityResolver.def(personalityId);
            if (def == null) return Optional.empty();
            return Optional.of(new PersonalitySnapshot(def.id().toString(),
                    def.base() == null ? "" : def.base().toLowerCase(Locale.ROOT), def.displayName(), def.description()));
        } catch (Throwable t) {
            ApiSupport.swallow("social.personality", t);
            return Optional.empty();
        }
    }

    @Override
    public Optional<RootSnapshot> root(ResourceLocation rootId) {
        try {
            return rootId == null ? Optional.empty() : Optional.ofNullable(ApiSnapshots.root(TownsteadAPI.origin(rootId)));
        } catch (Throwable t) {
            ApiSupport.swallow("social.root", t);
            return Optional.empty();
        }
    }

    @Override
    public Optional<GeneSnapshot> gene(ResourceLocation geneId) {
        try {
            return geneId == null ? Optional.empty() : Optional.ofNullable(ApiSnapshots.gene(TownsteadAPI.gene(geneId)));
        } catch (Throwable t) {
            ApiSupport.swallow("social.gene", t);
            return Optional.empty();
        }
    }

    @Override
    public Set<String> contextTags(ServerLevel level, LivingEntity villager) {
        try {
            VillagerEntityMCA mca = ApiSupport.villager(villager);
            return mca == null ? Set.of() : new HashSet<>(ContextResolver.tagsFor(level, mca));
        } catch (Throwable t) {
            ApiSupport.swallow("social.contextTags", t);
            return Set.of();
        }
    }

    @Override
    public Set<String> contextTags(ServerLevel level, LivingEntity villager, ServerPlayer viewer) {
        try {
            VillagerEntityMCA mca = ApiSupport.villager(villager);
            return mca == null ? Set.of() : new HashSet<>(ContextResolver.tagsFor(level, mca, viewer));
        } catch (Throwable t) {
            ApiSupport.swallow("social.contextTags", t);
            return Set.of();
        }
    }

    @Override
    public boolean reactionLocked(LivingEntity villager) {
        return villager != null && ReactionLockTracker.isLocked(villager, ApiSupport.gameTime(villager));
    }

    @Override
    public boolean reactionsPlayable() {
        return !ReactionBackends.all().isEmpty();
    }

    @Override
    public Set<ResourceLocation> reactionIds() {
        Set<ResourceLocation> out = new HashSet<>();
        for (Reaction reaction : ReactionRegistry.all()) out.add(reaction.id());
        return out;
    }

    @Override
    public Set<ResourceLocation> expressionCueIds() {
        return Set.copyOf(ExpressionCues.all().keySet());
    }

    @Override
    public boolean fireReaction(ServerLevel level, LivingEntity villager, ResourceLocation reactionId, ReactionCause cause) {
        try {
            if (level == null || villager == null || reactionId == null) return false;
            ReactionCause safe = cause == null ? ReactionCause.context(null) : cause;
            ReactionContext context = new ReactionContext(source(safe.source()), safe.player().orElse(null),
                    safe.location().orElse(villager.blockPosition()), safe.contextTags(), 0);
            return ReactionDispatcher.fire(level, villager, reactionId, context);
        } catch (Throwable t) {
            ApiSupport.swallow("social.fireReaction", t);
            return false;
        }
    }

    @Override
    public int dispatchTaskTransition(ServerLevel level, LivingEntity villager, ResourceLocation taskId, String phase) {
        try {
            if (level == null || villager == null) return 0;
            return ReactionDispatcher.onTaskTransition(level, villager, taskId, phase);
        } catch (Throwable t) {
            ApiSupport.swallow("social.dispatchTaskTransition", t);
            return 0;
        }
    }

    @Override
    public boolean emitExpression(LivingEntity actor, ResourceLocation cueId, LivingEntity counterpart) {
        try {
            return actor != null && cueId != null && ExpressionService.emit(actor, cueId, counterpart);
        } catch (Throwable t) {
            ApiSupport.swallow("social.emitExpression", t);
            return false;
        }
    }

    @Override
    public void noteHeartChange(LivingEntity villager, int delta) {
        try {
            if (villager != null) SocialInteractionTracker.markHeartChange(villager, delta, ApiSupport.gameTime(villager));
        } catch (Throwable t) {
            ApiSupport.swallow("social.noteHeartChange", t);
        }
    }

    @Override
    public void dialogueOpened(LivingEntity villager, ServerPlayer player) {
        try {
            if (villager != null && player != null) DialogueStateTracker.onOpen(villager, player, ApiSupport.gameTime(villager));
        } catch (Throwable t) {
            ApiSupport.swallow("social.dialogueOpened", t);
        }
    }

    @Override
    public void dialogueClosed(LivingEntity villager, ServerPlayer player) {
        try {
            if (villager != null && player != null) DialogueStateTracker.onClose(villager, player, ApiSupport.gameTime(villager));
        } catch (Throwable t) {
            ApiSupport.swallow("social.dialogueClosed", t);
        }
    }

    private static ReactionContext.TriggerSource source(String id) {
        try {
            return ReactionContext.TriggerSource.valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ReactionContext.TriggerSource.CONTEXT;
        }
    }
}
