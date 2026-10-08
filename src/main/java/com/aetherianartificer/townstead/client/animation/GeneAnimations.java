package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import com.aetherianartificer.townstead.root.Animations;

/** Reads server-evaluated expression, including temporary form companions. */
public final class GeneAnimations {
    private GeneAnimations() {}

    public static GeneCatalogEntry active(LivingEntity entity) {
        // Stable precedence when more than one independent animation gene is expressed.
        return RootClientStore.appearanceGenes(entity).stream().sorted()
                .map(RootCatalogClient::gene)
                .filter(g -> g != null && g.displayKind() == GeneDisplay.Kind.ANIMATIONS.ordinal())
                .findFirst().orElse(null);
    }

    public static boolean isZombie(GeneCatalogEntry gene) {
        return gene != null && gene.targetId().split(";", -1)[0].equals("minecraft:zombie");
    }

    /** Authored identities are evaluated by our bridge instead of MCA's implicit player CEM. */
    public static boolean ownsProviderSelection(LivingEntity entity) {
        return active(entity) != null || !com.aetherianartificer.townstead.client.species.RigModels
                .animations(entity).providers().isEmpty();
    }

    /** State poses authored as humanoid use humanoid EMF with MCA/the rig base as fallback. */
    public static boolean usesHumanoidStatePose(LivingEntity entity) {
        return usesHumanoidStatePose(entity.isCrouching(), entity.isSleeping(), entity.isFallFlying(),
                entity instanceof net.minecraft.world.entity.player.Player player && player.getAbilities().flying,
                com.aetherianartificer.townstead.client.species.RigModels.animations(entity));
    }

    static boolean usesHumanoidStatePose(boolean crouching, boolean sleeping, boolean gliding,
                                         boolean creativeFlying, Animations animations) {
        return usesHumanoidStatePose(crouching, sleeping, gliding || creativeFlying, animations);
    }

    static boolean usesHumanoidStatePose(boolean crouching, boolean sleeping, Animations animations) {
        return usesHumanoidStatePose(crouching, sleeping, false, animations);
    }

    static boolean usesHumanoidStatePose(boolean crouching, boolean sleeping, boolean flying, Animations animations) {
        return crouching && animations.isHumanoid(Animations.State.CROUCH)
                || sleeping && animations.isHumanoid(Animations.State.SLEEP)
                || flying && animations.isHumanoid(Animations.State.FLY);
    }

    static List<String> providerChain(String formAnimation, List<String> speciesProviders) {
        return providerChain(formAnimation, speciesProviders, false);
    }

    static List<String> providerChain(String formAnimation, List<String> speciesProviders, boolean humanoidState) {
        if (humanoidState) return List.of("humanoid");
        return formAnimation == null || formAnimation.isEmpty() ? List.copyOf(speciesProviders)
                : List.of(formAnimation.split(";"));
    }
}
