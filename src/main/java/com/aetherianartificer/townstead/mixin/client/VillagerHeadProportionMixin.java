package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.origin.OriginCatalogClient;
import com.aetherianartificer.townstead.client.origin.OriginClientStore;
import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies a stocky race's per-part build proportions so its model parts aren't squashed.
 *
 * <p>MCA renders the whole model with one scale {@code (width, height, width)}, applied to every
 * part — so a villager that's wider than it is tall (any short, stocky build) gets flattened parts.
 * For a villager whose origin carries the {@code townstead_origins:proportions} gene, each part the
 * gene lists has that squash <b>neutralized</b>, anchored at the geometric mean of the horizontal and
 * vertical scale ({@code mean = sqrt(horiz*vert)}), then multiplied by the gene's factor for that
 * part. {@code 1.0} = proportioned at the build's own average size (no resize, just un-squashed);
 * {@code >1}/{@code <1} = stylize. The body's overall short-and-wide silhouette is unchanged (that
 * comes from MCA's model scale); only the listed parts' aspect is corrected.</p>
 *
 * <p><b>Opt-in and data-driven.</b> Resolved client-side via the synced origin catalog (the same path
 * the skin-tint layer uses). A villager with no proportions gene — every base-mod/Overworlder
 * villager, and any part a gene omits — has its part scale reset to {@code (1,1,1)} every frame:
 * MCA's normal proportions, byte-for-byte. The unconditional reset also stops a stocky villager's
 * scale leaking onto the next normal one (the renderer reuses one model instance per layer). The
 * head/limb scales propagate to the skin/hair/clothing layers via {@code VillagerLayer.copyPropertiesTo}.</p>
 */
@Mixin(VillagerEntityBaseModelMCA.class)
public abstract class VillagerHeadProportionMixin<T extends LivingEntity & VillagerLike<T>> {

    //? if neoforge {
    @Inject(method = "setupAnim", remap = false, at = @At("TAIL"), require = 1)
    //?} else {
    /*@Inject(method = "m_6973_", remap = false, at = @At("TAIL"), require = 1)
    *///?}
    private void townstead$proportionParts(T entity, float limbAngle, float limbDistance,
            float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        GeneCatalogEntry proportions = townstead$proportionsGene(entity);

        // De-squash factors from the entity's own genetics × traits build (the same factors MCA's
        // renderer scales the whole model by; gender/dimensions are skipped — minor, and absent on
        // the 1.20.1 entity API). 1.0 when uniform or when there's no proportions gene to apply.
        float xz0 = 1.0f;
        float y0 = 1.0f;
        if (proportions != null) {
            Genetics genetics = entity.getGenetics();
            Traits traits = entity.getTraits();
            if (genetics != null && traits != null) {
                float horiz = genetics.getHorizontalScaleFactor() * traits.getHorizontalScaleFactor();
                float vert = genetics.getVerticalScaleFactor() * traits.getVerticalScaleFactor();
                if (horiz > 1.0e-4f && vert > 1.0e-4f) {
                    float mean = (float) Math.sqrt(horiz * vert);
                    xz0 = mean / horiz;
                    y0 = mean / vert;
                }
            }
        }

        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        townstead$scalePart(model.head, proportions, "head", xz0, y0);
        townstead$scalePart(model.hat, proportions, "head", xz0, y0);
        townstead$scalePart(model.rightArm, proportions, "arms", xz0, y0);
        townstead$scalePart(model.leftArm, proportions, "arms", xz0, y0);
        townstead$scalePart(model.rightLeg, proportions, "legs", xz0, y0);
        townstead$scalePart(model.leftLeg, proportions, "legs", xz0, y0);
        townstead$scalePart(model.body, proportions, "body", xz0, y0);

        // Hidden features (prevent_feature_render): zero the listed groups after proportions.
        com.aetherianartificer.townstead.client.origin.HideFeatures.hide(
                model, com.aetherianartificer.townstead.client.origin.HideFeatures.hiddenGroups(entity));
    }

    /**
     * Set {@code part}'s scale: when the gene lists this group, the de-squash {@code (xz0,y0)} times
     * the part's factor (clamped); otherwise reset to {@code (1,1,1)} so the part keeps MCA's squash.
     */
    private static void townstead$scalePart(ModelPart part, GeneCatalogEntry proportions,
            String group, float xz0, float y0) {
        float xz = 1.0f;
        float y = 1.0f;
        if (proportions != null) {
            float factor = proportions.proportionScale(group);
            if (!Float.isNaN(factor)) {
                xz = Math.max(0.4f, Math.min(2.5f, xz0 * factor));
                y = Math.max(0.4f, Math.min(2.5f, y0 * factor));
            }
        }
        part.xScale = xz;
        part.yScale = y;
        part.zScale = xz;
    }

    /** The proportions gene on the entity's applied origin (synced catalog), or {@code null}. */
    private static GeneCatalogEntry townstead$proportionsGene(LivingEntity entity) {
        String originId = OriginClientStore.resolve(entity);
        if (originId.isEmpty()) return null;
        OriginCatalogEntry origin = OriginCatalogClient.origin(originId);
        if (origin == null) return null;
        for (OriginCatalogEntry.Inherited inherited : origin.inheritedGenes()) {
            GeneCatalogEntry gene = OriginCatalogClient.gene(inherited.geneId());
            if (gene != null && gene.isProportions()) return gene;
        }
        return null;
    }
}
