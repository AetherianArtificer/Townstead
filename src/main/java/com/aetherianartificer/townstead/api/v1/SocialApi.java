package com.aetherianartificer.townstead.api.v1;

import com.aetherianartificer.townstead.api.v1.model.GeneSnapshot;
import com.aetherianartificer.townstead.api.v1.model.PersonalitySnapshot;
import com.aetherianartificer.townstead.api.v1.model.ReactionCause;
import com.aetherianartificer.townstead.api.v1.model.RootSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;
import java.util.Set;

/** Personality, context, reactions, expressions, and the social notes dialogue mods send. */
public interface SocialApi {

    Optional<PersonalitySnapshot> personality(String personalityId);

    Optional<RootSnapshot> root(ResourceLocation rootId);

    Optional<GeneSnapshot> gene(ResourceLocation geneId);

    /** What Townstead's reaction system currently believes about a villager's situation. */
    Set<String> contextTags(ServerLevel level, LivingEntity villager);

    /** The same, with the player-relationship and held-item tags computed for {@code viewer}. */
    Set<String> contextTags(ServerLevel level, LivingEntity villager, ServerPlayer viewer);

    /** True while a reaction holds the villager and another cannot start. */
    boolean reactionLocked(LivingEntity villager);

    /** False when no animation backend is registered, in which case every reaction is inert. */
    boolean reactionsPlayable();

    Set<ResourceLocation> reactionIds();

    Set<ResourceLocation> expressionCueIds();

    /** Plays a reaction by id, subject to Townstead's own cooldown, lock and chance gates. */
    boolean fireReaction(ServerLevel level, LivingEntity villager, ResourceLocation reactionId, ReactionCause cause);

    /**
     * Notifies reactions bound to a task phase. {@code phase} is free-form and must match what
     * the reaction's trigger lists. Returns how many reactions played.
     */
    int dispatchTaskTransition(ServerLevel level, LivingEntity villager, ResourceLocation taskId, String phase);

    boolean emitExpression(LivingEntity actor, ResourceLocation cueId, LivingEntity counterpart);

    /** Records that a relationship moved by {@code delta} hearts, for the heart-change context tags. */
    void noteHeartChange(LivingEntity villager, int delta);

    /** Marks a dialogue open between a villager and a player. Pair with {@link #dialogueClosed}. */
    void dialogueOpened(LivingEntity villager, ServerPlayer player);

    void dialogueClosed(LivingEntity villager, ServerPlayer player);
}
