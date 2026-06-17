package com.aetherianartificer.townstead.client.origin;

import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared resolution and application for {@code hide_feature} genes, used by both the
 * villager model mixin and the player (genetics) model mixin so a hidden part
 * disappears on either render. Resolves the entity's expressed hide-feature genes
 * (per-entity set first, origin-typical grant list as fallback) into the part groups
 * to zero, then scales those parts (and their wear layers on a player) to nothing.
 */
public final class HideFeatures {

    private HideFeatures() {}

    /** Part groups ({@code head}/{@code body}/{@code arms}/{@code legs}) the entity hides. */
    public static Set<String> hiddenGroups(LivingEntity entity) {
        Set<String> groups = new HashSet<>();
        Set<String> expressed = OriginClientStore.expressedGenes(entity);
        if (!expressed.isEmpty()) {
            for (String geneId : expressed) collect(OriginCatalogClient.gene(geneId), groups);
            return groups;
        }
        String originId = OriginClientStore.resolve(entity);
        if (originId.isEmpty()) return groups;
        OriginCatalogEntry origin = OriginCatalogClient.origin(originId);
        if (origin == null) return groups;
        for (OriginCatalogEntry.Inherited inherited : origin.inheritedGenes()) {
            collect(OriginCatalogClient.gene(inherited.geneId()), groups);
        }
        return groups;
    }

    /** Zero the model parts for the hidden groups; also the matching wear parts on a player model. */
    public static void hide(HumanoidModel<?> model, Set<String> groups) {
        if (groups.isEmpty()) return;
        if (groups.contains("head")) { zero(model.head); zero(model.hat); }
        if (groups.contains("body")) zero(model.body);
        if (groups.contains("arms")) { zero(model.rightArm); zero(model.leftArm); }
        if (groups.contains("legs")) { zero(model.rightLeg); zero(model.leftLeg); }
        if (model instanceof PlayerModel<?> player) {
            if (groups.contains("body")) zero(player.jacket);
            if (groups.contains("arms")) { zero(player.rightSleeve); zero(player.leftSleeve); }
            if (groups.contains("legs")) { zero(player.rightPants); zero(player.leftPants); }
        }
    }

    private static void collect(GeneCatalogEntry gene, Set<String> groups) {
        if (gene == null || !gene.isHideFeature()) return;
        for (String group : new String[]{"head", "body", "arms", "legs"}) {
            if (gene.hidesPart(group)) groups.add(group);
        }
    }

    private static void zero(ModelPart part) {
        part.xScale = 0f;
        part.yScale = 0f;
        part.zScale = 0f;
    }
}
