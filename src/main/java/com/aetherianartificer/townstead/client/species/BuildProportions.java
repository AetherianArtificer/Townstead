package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Applies a stocky race's per-part build proportions so its model parts aren't squashed.
 *
 * <p>MCA renders the whole model with one scale {@code (width, height, width)}, applied to every
 * part — so a body that's wider than it is tall (any short, stocky build) gets flattened parts.
 * For a bearer whose origin carries the {@code townstead_roots:proportions} gene, each part the
 * gene lists has that squash <b>neutralized</b>, anchored at the geometric mean of the horizontal and
 * vertical scale ({@code mean = sqrt(horiz*vert)}), then multiplied by the gene's factor for that
 * part. {@code 1.0} = proportioned at the build's own average size (no resize, just un-squashed);
 * {@code >1}/{@code <1} = stylize. The body's overall short-and-wide silhouette is unchanged (that
 * comes from MCA's model scale); only the listed parts' aspect is corrected.</p>
 *
 * <p><b>Opt-in and data-driven.</b> Resolved client-side via the synced origin catalog (the same path
 * the skin-tint layer uses). A bearer with no proportions gene — every base-mod/Overworlder villager,
 * and any part a gene omits — has its part scale reset to {@code (1,1,1)} every frame: MCA's normal
 * proportions, byte-for-byte. The unconditional reset also stops a stocky body's scale leaking onto
 * the next normal one (the renderer reuses one model instance per layer). The head/limb scales
 * propagate to the skin/hair/clothing layers via {@code VillagerLayer.copyPropertiesTo}.</p>
 *
 * <p>Shared by MCA's villager model and its player model (a villager-mode player is drawn with the
 * latter, which is not a villager model), so the editor's villager preview and the player in the
 * world wear the same build.</p>
 */
public final class BuildProportions {

    private BuildProportions() {}

    /**
     * Scale {@code model}'s parts for {@code entity}'s build. {@code genetics}/{@code traits} are the
     * body's MCA genes (a player's come from MCA's stand-in villager), or null when unknown.
     */
    public static void apply(HumanoidModel<?> model, LivingEntity entity, @Nullable Genetics genetics,
                             @Nullable Traits traits) {
        GeneCatalogEntry proportions = proportionsGene(entity);

        // De-squash factors from the body's own genetics × traits build (the same factors MCA's
        // renderer scales the whole model by; gender/dimensions are skipped — minor, and absent on
        // the 1.20.1 entity API). 1.0 when uniform or when there's no proportions gene to apply.
        float xz0 = 1.0f;
        float y0 = 1.0f;
        // Lean↔stout position from the individual's rolled width gene, mapped through the gene's
        // own width range (so the spread covers the race's actual roll, and MCA's width blending
        // makes bulk heritable). Only lean:stout entries read it.
        float bulk = 0.5f;
        if (proportions != null) {
            if (genetics != null && traits != null) {
                float horiz = genetics.getHorizontalScaleFactor() * traits.getHorizontalScaleFactor();
                float vert = genetics.getVerticalScaleFactor() * traits.getVerticalScaleFactor();
                if (horiz > 1.0e-4f && vert > 1.0e-4f) {
                    float mean = (float) Math.sqrt(horiz * vert);
                    xz0 = mean / horiz;
                    y0 = mean / vert;
                }
            }
            if (genetics != null) {
                float width = genetics.getGene(Genetics.WIDTH);
                float[] range = proportions.proportionBulkRange();
                bulk = range != null && range[1] - range[0] > 1.0e-4f
                        ? Math.max(0f, Math.min(1f, (width - range[0]) / (range[1] - range[0])))
                        : width;
            }
        }

        scalePart(model.head, proportions, "head", xz0, y0, bulk);
        scalePart(model.hat, proportions, "head", xz0, y0, bulk);
        scalePart(model.rightArm, proportions, "arms", xz0, y0, bulk);
        scalePart(model.leftArm, proportions, "arms", xz0, y0, bulk);
        scalePart(model.rightLeg, proportions, "legs", xz0, y0, bulk);
        scalePart(model.leftLeg, proportions, "legs", xz0, y0, bulk);
        scalePart(model.body, proportions, "body", xz0, y0, bulk);
        attachLimbs(model);

        // Hidden features (prevent_feature_render): zero the listed groups after proportions.
        com.aetherianartificer.townstead.client.root.HideFeatures.hide(
                model, com.aetherianartificer.townstead.client.root.HideFeatures.hiddenGroups(entity));
        // a root icon is the head alone
        if (com.aetherianartificer.townstead.client.root.IconRender.headOnly()) {
            com.aetherianartificer.townstead.client.root.HideFeatures.hide(model,
                    com.aetherianartificer.townstead.client.root.IconRender.BELOW_THE_NECK);
        }
    }

    /** Every part back to MCA's own scale (a body drawn without a build, on a reused model). */
    public static void reset(HumanoidModel<?> model) {
        for (ModelPart part : new ModelPart[]{model.head, model.hat, model.rightArm, model.leftArm,
                model.rightLeg, model.leftLeg, model.body}) {
            scalePart(part, null, "", 1f, 1f, 0.5f);
        }
        attachLimbs(model);
    }

    // The player mesh MCA draws with: arms pivot 5 px out with their inner face 1 px in from the
    // pivot, on a torso 4 px either side of centre; legs pivot 1.9 px out.
    private static final float ARM_PIVOT = 5.0f;
    private static final float ARM_INNER = 1.0f;
    private static final float TORSO_HALF = 4.0f;
    private static final float LEG_PIVOT = 1.9f;

    /**
     * Each part scales about its own pivot, so a narrowed torso pulls in from its sides while a
     * narrowed arm shrinks out toward its shoulder pivot, and the two come apart. Draw the arms
     * back in until their inner faces meet the torso (only ever inward: a stout build that already
     * overlaps keeps its look). The hips spread with the legs' width either way, so legs scaled
     * like the torso sit exactly as vanilla's do, resized: every layer's shell then keeps vanilla's
     * spacing where torso and legs overlap at the waist, instead of one layer's torso landing on
     * another's legs and z-fighting.
     */
    private static void attachLimbs(HumanoidModel<?> model) {
        // vanilla setupAnim sets the arm pivots (±5, or swung round by an attack) every frame, so
        // scaling them here never compounds
        float arm = Math.min(ARM_PIVOT, TORSO_HALF * model.body.xScale + ARM_INNER * model.rightArm.xScale) / ARM_PIVOT;
        model.rightArm.x *= arm;
        model.rightArm.z *= arm;
        arm = Math.min(ARM_PIVOT, TORSO_HALF * model.body.xScale + ARM_INNER * model.leftArm.xScale) / ARM_PIVOT;
        model.leftArm.x *= arm;
        model.leftArm.z *= arm;
        // nothing else moves a leg pivot sideways, so these are set outright (and put back by reset())
        model.rightLeg.x = -LEG_PIVOT * model.rightLeg.xScale;
        model.leftLeg.x = LEG_PIVOT * model.leftLeg.xScale;
    }

    /**
     * Set {@code part}'s scale: when the gene lists this group, the de-squash {@code (xz0,y0)} times
     * the part's per-axis factors (clamped); otherwise reset to {@code (1,1,1)} so the part keeps
     * MCA's squash. Per-axis factors stylize without stretching — thick arms that aren't longer,
     * a torso that's deep but not tall.
     */
    private static void scalePart(ModelPart part, @Nullable GeneCatalogEntry proportions,
                                  String group, float xz0, float y0, float bulk) {
        float x = 1.0f;
        float y = 1.0f;
        float z = 1.0f;
        if (proportions != null) {
            float[] factor = proportions.proportionScale(group, bulk);
            if (factor != null) {
                x = Math.max(0.4f, Math.min(2.5f, xz0 * factor[0]));
                y = Math.max(0.4f, Math.min(2.5f, y0 * factor[1]));
                z = Math.max(0.4f, Math.min(2.5f, xz0 * factor[2]));
            }
        }
        part.xScale = x;
        part.yScale = y;
        part.zScale = z;
    }

    /** The proportions gene on the entity's applied origin (synced catalog), or {@code null}. */
    private static @Nullable GeneCatalogEntry proportionsGene(LivingEntity entity) {
        String rootId = RootClientStore.resolve(entity);
        if (rootId.isEmpty()) return null;
        RootCatalogEntry origin = RootCatalogClient.origin(rootId);
        if (origin == null) return null;
        for (RootCatalogEntry.Inherited inherited : origin.inheritedGenes()) {
            GeneCatalogEntry gene = RootCatalogClient.gene(inherited.geneId());
            if (gene != null && gene.isProportions()) return gene;
        }
        return null;
    }
}
