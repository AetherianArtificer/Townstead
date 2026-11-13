package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.client.animation.AnimationTargetMap;
import com.aetherianartificer.townstead.client.attachment.geo.AttachmentGeo;
import com.aetherianartificer.townstead.client.devlink.BenchAnchorCapture;
import com.aetherianartificer.townstead.client.devlink.BenchGizmos;
import com.aetherianartificer.townstead.client.devlink.BenchLinkClient;
import org.joml.Matrix4f;
import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.client.species.InvisFade;
import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.client.species.RigSkinTone;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.aetherianartificer.townstead.root.attachment.AttachmentPointDef;
import com.aetherianartificer.townstead.root.gene.AllelePayload;
import org.jetbrains.annotations.Nullable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Renders an entity's expressed cosmetic attachments (tails, ears, tusks). Resolves
 * each {@code attachment} gene the individual expresses (per-entity from the synced
 * expressed set, falling back to the origin-typical grant list) to a synced
 * {@link AttachmentDef}, anchors its baked geometry to the named model bone via
 * {@link ModelPart#translateAndRotate} so it follows the live pose, then applies the
 * definition's offset/rotation/scale and tint. Geometry and texture come from
 * {@code AttachmentClient} (datapack-synced + cached), so no resource pack is needed.
 */
public class AttachmentRenderLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    public AttachmentRenderLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        // Attachments fade with the body instead of vanilla's hard cut on the invisible
        // flag, so ears/tails/wings ghost out together with the skin they grow from.
        float fade = InvisFade.alpha(entity, partialTick);
        if (fade <= 0f) return;
        int fadeAlpha = Math.round(fade * 255f);
        List<Expressed> attachments = resolve(entity);
        // Bench Link: a capture request or the held tool also needs bones and points drawn
        // or recorded on entities that wear no attachments.
        BenchAnchorCapture.Frame bench = BenchAnchorCapture.wants(entity.getId())
                ? BenchAnchorCapture.begin(entity, pose.last().pose(), RigModels.rigBaseFor(entity)) : null;
        boolean gizmos = BenchLinkClient.gizmos();
        if (attachments.isEmpty() && bench == null && !gizmos) return;

        AnimationTargetMap<T> bones = AnimationTargetMap.forMcaModel(getParentModel());
        if (bench != null || gizmos) benchFrame(entity, pose, buffers, bones, bench, gizmos);
        StageKeys stageKeys = stageKeys(entity);
        // Placed on the first physics attachment, while the pose is still the model root.
        List<com.aetherianartificer.townstead.root.attachment.ChainSim.Capsule> colliders = null;
        for (Expressed expressed : attachments) {
            AttachmentDef def = expressed.def();
            if (hiddenByEquipment(entity, def)) continue;
            if (!AttachmentPoses.defActive(entity, def)) continue;
            // A life-stage override can swap the model, add an offset, and scale (baby ears).
            AttachmentDef.StageOverride stage = def.stages().get(stageKeys.named());
            if (stage == null) stage = def.stages().get(stageKeys.canonical());
            String activeSha = def.geoSha1();
            if (stage != null && stage.geoSha1() != null
                    && AttachmentClient.attachmentGeometry(stage.geoSha1()) != null) {
                activeSha = stage.geoSha1();
            }
            AttachmentGeo geometry = AttachmentClient.geometryFor(def, activeSha, false);
            ResourceLocation texture = AttachmentClient.texture(def.textureSha1());
            if (geometry == null || texture == null) continue;

            // The granted gene's allele payload carries this individual's rolls: the
            // morph/visibility channel values and, for gene-tinted attachments, the
            // heritable colour components.
            AllelePayload payload = AllelePayload.parse(
                    RootClientStore.resolveCarriedVariant(entity, expressed.geneId()));
            int tint = resolveTint(entity, def, payload);
            // Multiply at full strength rides the vertex colour for free; the other blend
            // modes (and faded tints) bake a derived texture through SkinBlend, quantized
            // to 5 bits per channel so bearer-resolved tints don't explode the cache. A tint
            // mask is per-pixel, which a vertex colour cannot express, so it bakes too.
            ResourceLocation drawTexture = texture;
            int vertexTint = tint;
            boolean masked = !def.tintMaskSha1().isEmpty();
            if ((def.tintBlend() != 0 || def.tintStrength() < 1f || masked) && tint != 0xFFFFFF) {
                // A gene tint is picked on an HSV picker whose brightness axis must land in the
                // render, so its "color" blend upgrades to color-value; authored flat/skin/hair
                // tints keep plain "color" (their packs tuned against base-brightness preserve).
                int blendMode = def.tintBlend() == 3 && def.tintSource() == AttachmentDef.TINT_GENE
                        ? com.aetherianartificer.townstead.client.skin.SkinBlend.MODE_COLOR_VALUE
                        : def.tintBlend();
                int packed = com.aetherianartificer.townstead.client.skin.SkinBlend.pack(
                        tint & 0xF8F8F8, blendMode, def.tintStrength());
                ResourceLocation baked = AttachmentClient.blendedTexture(
                        def.textureSha1(), def.tintMaskSha1(), packed);
                if (baked != null) {
                    drawTexture = baked;
                    vertexTint = 0xFFFFFF;
                }
            }
            // The cutout type ignores vertex alpha, so a fading attachment must go translucent.
            RenderType renderType = def.opacity() < 1f ? AttachmentRenderTypes.seeThrough(drawTexture)
                    : (def.translucent() || fade < 1f)
                    ? RenderType.entityTranslucent(drawTexture)
                    : RenderType.entityCutoutNoCull(drawTexture);
            int drawAlpha = Math.round(fadeAlpha * def.opacity());
            ResourceLocation emissive = def.emissiveSha1().isEmpty()
                    ? null : AttachmentClient.texture(def.emissiveSha1());
            com.aetherianartificer.townstead.root.attachment.BodyPoseRules.Morphs morphs =
                    com.aetherianartificer.townstead.root.attachment.BodyPoseRules.morphs(entity, def, payload);
            float[] stageOffset = stage == null ? NO_OFFSET : stage.offset();
            float stageScale = stage == null ? 1f : stage.scale();
            float scale = def.scale() * stageScale;
            AttachmentPoses.Sample poseSample = AttachmentPoses.sample(entity, def, ageInTicks);
            AttachmentAnimations.Sample animSample = AttachmentAnimations.sample(entity, def, ageInTicks);

            // One attachment can fill several points (a tag matching both ears), so render an
            // instance at each resolved anchor. A mirror point renders the mirrored re-bake.
            List<Anchor> anchors = anchorsFor(def, RigModels.rigBaseFor(entity));
            if (colliders == null && !def.physics().isEmpty()) {
                colliders = AttachmentColliders.gather(entity, pose, bones);
                if (gizmos) AttachmentColliders.draw(entity, pose, buffers, colliders);
            }
            for (int anchorIndex = 0; anchorIndex < anchors.size(); anchorIndex++) {
                Anchor anchor = anchors.get(anchorIndex);
                ModelPart bone = bones.resolve(anchor.bone()).orElse(null);
                if (bone == null) continue;
                // Each branch of the model at this anchor is a piece: gone, showing, or about to fall.
                List<String> pieceRoots = com.aetherianartificer.townstead.root.attachment.BodyPose.pieceRoots(entity, def);
                int splits = Math.max(1, pieceRoots.size());
                boolean falling = false, showing = false;
                List<String> goneRoots = null;
                for (int s = 0; s < splits; s++) {
                    int piece = anchorIndex * splits + s;
                    if (FallingPieces.wants(entity.getId(), def.id(), piece)) {
                        falling = true;
                    } else if (FallingPieces.gone(entity.getId(), def.id(), piece)
                            || com.aetherianartificer.townstead.root.attachment.BodyParts.pieceLost(entity, def.id(), piece)) {
                        if (splits > 1) {
                            if (goneRoots == null) goneRoots = new ArrayList<>();
                            goneRoots.add(pieceRoots.get(s));
                        }
                    } else {
                        showing = true;
                    }
                }
                if (!falling && !showing) {
                    LiveBoxes.forget(entity, def, anchorIndex);
                    continue;
                }
                AttachmentGeo geo = anchor.mirror()
                        ? AttachmentClient.geometryFor(def, activeSha, true) : geometry;
                if (geo == null) continue;

                float[] base = anchor.offset();
                pose.pushPose();
                bone.translateAndRotate(pose);
                Matrix4f benchBone = bench == null ? null : new Matrix4f(pose.last().pose());
                // A stage shrinks the attachment about its anchor: geometry authored far from its
                // origin (wings from an avatar's feet) would otherwise sink as it scales.
                pose.translate((base[0] + def.offset()[0] * stageScale + stageOffset[0]) / 16f,
                        (base[1] + def.offset()[1] * stageScale + stageOffset[1]) / 16f,
                        (base[2] + def.offset()[2] * stageScale + stageOffset[2]) / 16f);
                rotateZyx(pose, anchor.rotation());
                // Def rotation and eased state poses mirror on mirror anchors so a symmetric
                // pair sweeps and folds symmetrically.
                if (anchor.mirror()) {
                    float[] r = def.rotation();
                    rotateZyx(pose, new float[]{r[0], -r[1], -r[2]});
                } else {
                    rotateZyx(pose, def.rotation());
                }
                if (poseSample != null) {
                    float[] r = poseSample.rotation();
                    if (anchor.mirror()) rotateZyx(pose, new float[]{r[0], -r[1], -r[2]});
                    else rotateZyx(pose, r);
                }
                if (scale != 1f) pose.scale(scale, scale, scale);
                if (morphs.whole() != null) pose.scale(morphs.whole()[0], morphs.whole()[1], morphs.whole()[2]);
                if (bench != null) {
                    float[] r = poseSample == null ? NO_OFFSET : poseSample.rotation();
                    boolean posed = r[0] != 0f || r[1] != 0f || r[2] != 0f;
                    bench.attachment(def.id(), anchorIndex, anchor.bone(), anchor.mirror(), posed,
                            benchBone, new Matrix4f(pose.last().pose()));
                }
                if (gizmos) BenchGizmos.tripod(pose, buffers, BenchGizmos.ATTACHMENT);

                // Morph channels with named bones scale each about its own pivot (an ear
                // shrinks toward the head surface it grows from); bakes are shared across
                // entities, so set and restore around the draw. Channels without bones
                // scaled the whole attachment at its anchor above. Visibility rules hide
                // threshold-gated bones (a rounded human ear tip below the elven length).
                for (java.util.Map.Entry<String, float[]> entry : morphs.boneScales().entrySet()) {
                    AttachmentGeo.Bone morphBone = geo.bone(entry.getKey());
                    if (morphBone == null) continue;
                    morphBone.xScale = entry.getValue()[0];
                    morphBone.yScale = entry.getValue()[1];
                    morphBone.zScale = entry.getValue()[2];
                }
                List<AttachmentGeo.Bone> hidden = hideBones(geo, morphs.hiddenBones());
                if (goneRoots != null) {
                    hidden = new ArrayList<>(hidden);
                    hidden.addAll(hideBones(geo, goneRoots));
                }
                // Morph rotations, poses, and keyframe clips share the bone-delta contract and
                // compose in that order into the animated pose. Physics then springs each chain
                // toward that pose (a drooped ear still swings, a wagging tail still follows through).
                java.util.Map<String, float[]> boneRotations = mergeBoneDeltas(
                        mergeBoneDeltas(morphs.rotations(),
                                poseSample == null ? java.util.Map.of() : poseSample.boneRotations()),
                        animSample == null ? java.util.Map.of() : animSample.rotations());
                List<SavedRotation> posed = applyBonePose(geo, boneRotations, anchor.mirror());
                List<SavedOffset> offset = animSample == null ? List.of()
                        : applyBoneOffsets(geo, animSample.positions(), anchor.mirror());
                List<SavedScale> scaled = animSample == null ? List.of()
                        : applyBoneScales(geo, animSample.scales());
                List<AttachmentGeo.Bone> simulated = AttachmentPhysics.apply(entity, def, anchorIndex, geo,
                        pose.last().pose(), partialTick, limbSwing, limbSwingAmount,
                        colliders == null ? List.of() : colliders);
                if (gizmos && !simulated.isEmpty()) AttachmentPhysics.drawGizmos(entity, def, anchorIndex, pose, buffers);
                LiveBoxes.capture(entity, def, activeSha, anchorIndex, anchors.size(), geo, pose.last().pose());
                VertexConsumer buffer = buffers.getBuffer(renderType);
                geo.render(pose, buffer, light, OverlayTexture.NO_OVERLAY, (drawAlpha << 24) | vertexTint);
                for (int s = 0; falling && s < splits; s++) {
                    int piece = anchorIndex * splits + s;
                    if (FallingPieces.wants(entity.getId(), def.id(), piece)) {
                        boolean solid = def.opacity() >= 1f && !def.translucent();
                        FallingPieces.capture(entity, def, anchorIndex, piece, pieceRoots, s, geo,
                                solid ? RenderType.entityCutoutNoCull(drawTexture) : renderType, solid,
                                Math.round(255f * def.opacity()), vertexTint, emissive,
                                com.aetherianartificer.townstead.compat.mess.MessBridge.active()
                                        ? com.aetherianartificer.townstead.compat.mess.MessClientLink.copyMarks(entity, geo, def.id(), anchorIndex, geo::render, texture)
                                        : List.of());
                    }
                }
                // The emissive layer re-draws the same posed geometry full-bright, so
                // glowing markings ride every morph, pose, clip, and physics swing of the
                // base pass. entityTranslucentEmissive (the warden's glow type) draws the
                // mask's own colour at full luminance in the world, day or night — the
                // additive eyes type washes out against a sunlit base texture.
                if (emissive != null) {
                    geo.render(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(emissive)),
                            light, OverlayTexture.NO_OVERLAY, (fadeAlpha << 24) | 0xFFFFFF);
                }
                if (com.aetherianartificer.townstead.compat.mess.MessBridge.active()) com.aetherianartificer.townstead.compat.mess.MessClientLink.drawOnPart(entity, geo, def.id(), anchorIndex, geo::render, texture, pose, buffers, light, fadeAlpha);
                for (AttachmentGeo.Bone simulatedBone : simulated) simulatedBone.physicsRotation = null;
                for (SavedScale savedScale : scaled) savedScale.restore();
                for (SavedOffset savedOffset : offset) savedOffset.restore();
                for (SavedRotation savedRotation : posed) savedRotation.restore();
                for (AttachmentGeo.Bone hiddenBone : hidden) hiddenBone.visible = true;
                for (String scaledBone : morphs.boneScales().keySet()) {
                    AttachmentGeo.Bone morphBone = geo.bone(scaledBone);
                    if (morphBone == null) continue;
                    morphBone.xScale = 1f;
                    morphBone.yScale = 1f;
                    morphBone.zScale = 1f;
                }
                pose.popPose();
            }
        }
        if (bench != null) BenchAnchorCapture.finish(bench);
    }

    private static final String[] BENCH_CHANNELS =
            {"head", "headwear", "body", "right_arm", "left_arm", "right_leg", "left_leg"};

    /**
     * Records (for a Bench Link capture) and draws (while the tool is held) the channel bones and
     * every attachment point that applies to this rig, using the same bone frames and point math
     * the attachment loop uses.
     */
    private static <T extends LivingEntity> void benchFrame(T entity, PoseStack pose, MultiBufferSource buffers,
                                                            AnimationTargetMap<T> bones,
                                                            @Nullable BenchAnchorCapture.Frame bench, boolean gizmos) {
        if (bench != null) {
            for (String channel : BENCH_CHANNELS) {
                ModelPart part = bones.resolve(channel).orElse(null);
                if (part == null) continue;
                pose.pushPose();
                part.translateAndRotate(pose);
                bench.bone(channel, part, new Matrix4f(pose.last().pose()));
                pose.popPose();
            }
        }
        String rig = RigModels.rigBaseFor(entity);
        for (AttachmentPointDef point : AttachmentClient.allPoints()) {
            if (!point.rig().isEmpty() && !point.rig().equals(rig)) continue;
            ModelPart part = bones.resolve(point.bone()).orElse(null);
            if (part == null) continue;
            pose.pushPose();
            part.translateAndRotate(pose);
            float[] offset = point.offset();
            pose.translate(offset[0] / 16f, offset[1] / 16f, offset[2] / 16f);
            rotateZyx(pose, point.rotation());
            if (bench != null) bench.point(point.id(), point.bone(), new Matrix4f(pose.last().pose()));
            if (gizmos) BenchGizmos.tripod(pose, buffers, BenchGizmos.POINT);
            pose.popPose();
        }
    }

    /**
     * The entity's canonical life-stage key for stage overrides: {@code senior} from the
     * synced life snapshot, else MCA's age state ({@code baby}/{@code toddler}/{@code child}/
     * {@code teen}/{@code adult}); players and unknown entities read as adults.
     */
    private record StageKeys(String named, String canonical) {}

    static String[] stageNames(LivingEntity entity) {
        StageKeys keys = stageKeys(entity);
        return new String[]{keys.named(), keys.canonical()};
    }

    /** The size values rolled on the gene that gives this entity this attachment. */
    static AllelePayload rolled(LivingEntity entity, AttachmentDef def) {
        for (Expressed expressed : resolve(entity)) {
            if (expressed.def().id().equals(def.id())) {
                return AllelePayload.parse(RootClientStore.resolveCarriedVariant(entity, expressed.geneId()));
            }
        }
        return AllelePayload.parse("");
    }

    private static StageKeys stageKeys(LivingEntity entity) {
        if (entity instanceof net.conczin.mca.entity.VillagerEntityMCA villager) {
            com.aetherianartificer.townstead.calendar.LifeClientStore.Snapshot snap =
                    com.aetherianartificer.townstead.calendar.LifeClientStore.get(entity.getId());
            String canonical = snap != null && snap.isSenior() ? "senior"
                    : villager.getAgeState().name().toLowerCase(java.util.Locale.ROOT);
            String named = snap == null ? "" : snap.currentStageId();
            return new StageKeys(named, canonical);
        }
        return new StageKeys("", "adult");
    }

    private static void rotateZyx(PoseStack pose, float[] degrees) {
        if (degrees[2] != 0f) pose.mulPose(Axis.ZP.rotationDegrees(degrees[2]));
        if (degrees[1] != 0f) pose.mulPose(Axis.YP.rotationDegrees(degrees[1]));
        if (degrees[0] != 0f) pose.mulPose(Axis.XP.rotationDegrees(degrees[0]));
    }

    /**
     * The attachment's tint for this bearer: the flat colour, or a colour resolved
     * from the entity — skin tone (rig pipeline), rendered hair colour (captured
     * from MCA's hair layer; flat tint until it has rendered once or the rig hides
     * hair), expressed eye colour (matching the face overlay), or the heritable
     * colour rolled on the granting gene's allele (tint_r/g/b channels).
     */
    private static int resolveTint(LivingEntity entity, AttachmentDef def, AllelePayload payload) {
        return switch (def.tintSource()) {
            // Prefer the skin colour the face was actually drawn with this frame
            // (melanin gradient + origin tint, captured at MCA's SkinLayer); the
            // resolved tint is the fallback until the skin has rendered once.
            case AttachmentDef.TINT_SKIN -> com.aetherianartificer.townstead.client.species.RigSkinColor.get(
                    entity.getId(), RigSkinTone.forEntity(entity) & 0xFFFFFF);
            case AttachmentDef.TINT_HAIR ->
                    com.aetherianartificer.townstead.client.species.RigHairColor.get(entity.getId(), def.tint());
            case AttachmentDef.TINT_COAT -> hasCoat(entity)
                    ? com.aetherianartificer.townstead.client.species.RigSkinColor.get(
                            entity.getId(), RigSkinTone.forEntity(entity) & 0xFFFFFF)
                    : com.aetherianartificer.townstead.client.species.RigHairColor.get(entity.getId(), def.tint());
            case AttachmentDef.TINT_EYES -> {
                int eye = com.aetherianartificer.townstead.client.species.RigEyeColor.forEntity(entity);
                yield eye >= 0 ? eye : def.tint();
            }
            case AttachmentDef.TINT_GENE -> {
                Float r = payload.channels().get("tint_r");
                Float g = payload.channels().get("tint_g");
                Float b = payload.channels().get("tint_b");
                if (r == null || g == null || b == null) yield def.tint();   // unrolled: untinted
                yield (component(r) << 16) | (component(g) << 8) | component(b);
            }
            default -> def.tint();
        };
    }

    /** Whether the bearer shows a coat: an expressed skin-tone gene (fur, scales) over its skin. */
    private static boolean hasCoat(LivingEntity entity) {
        for (String geneId : RootClientStore.appearanceGenes(entity)) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            if (gene != null && gene.isSkinTone()) return true;
        }
        return false;
    }

    private static int component(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255f)));
    }

    /** Whether the bearer wears something in a slot this attachment hides under. */
    private static boolean hiddenByEquipment(LivingEntity entity, AttachmentDef def) {
        for (String name : def.hidesUnder()) {
            var slot = AttachmentDef.equipmentSlot(name);
            if (slot != null && !entity.getItemBySlot(slot).isEmpty()) return true;
        }
        return false;
    }

    /** Hides the named bones on the shared bake, returning the ones actually flipped for restore. */
    private static List<AttachmentGeo.Bone> hideBones(AttachmentGeo geo, List<String> names) {
        if (names.isEmpty()) return List.of();
        List<AttachmentGeo.Bone> hidden = new ArrayList<>(names.size());
        for (String name : names) {
            AttachmentGeo.Bone bone = geo.bone(name);
            if (bone == null || !bone.visible) continue;
            bone.visible = false;
            hidden.add(bone);
        }
        return hidden;
    }

    /** A bone's pre-pose rotation, held so the shared bake can be restored after the draw. */
    private record SavedRotation(AttachmentGeo.Bone bone, float x, float y, float z) {
        void restore() {
            bone.xRot = x;
            bone.yRot = y;
            bone.zRot = z;
        }
    }

    /** A bone's pre-animation translation offset (model px), restored after the draw. */
    private record SavedOffset(AttachmentGeo.Bone bone, float x, float y, float z) {
        void restore() {
            bone.xOff = x;
            bone.yOff = y;
            bone.zOff = z;
        }
    }

    /** A bone's pre-animation scale (possibly morphed), restored after the draw. */
    private record SavedScale(AttachmentGeo.Bone bone, float x, float y, float z) {
        void restore() {
            bone.xScale = x;
            bone.yScale = y;
            bone.zScale = z;
        }
    }

    /**
     * Adds keyframe position deltas to the named bones. Values are authored in the
     * animation file's Bedrock convention (pixels, Y up): Java keeps X/Z and negates
     * Y; a mirrored bake additionally flips X.
     */
    private static List<SavedOffset> applyBoneOffsets(AttachmentGeo geo, java.util.Map<String, float[]> positions,
                                                      boolean mirror) {
        if (positions.isEmpty()) return List.of();
        List<SavedOffset> saved = new ArrayList<>(positions.size());
        for (java.util.Map.Entry<String, float[]> entry : positions.entrySet()) {
            AttachmentGeo.Bone bone = geo.bone(entry.getKey());
            if (bone == null) continue;
            saved.add(new SavedOffset(bone, bone.xOff, bone.yOff, bone.zOff));
            float[] px = entry.getValue();
            bone.xOff += mirror ? -px[0] : px[0];
            bone.yOff += -px[1];
            bone.zOff += px[2];
        }
        return saved;
    }

    /** Multiplies keyframe scale onto the named bones (composes with a morph's set scale). */
    private static List<SavedScale> applyBoneScales(AttachmentGeo geo, java.util.Map<String, float[]> scales) {
        if (scales.isEmpty()) return List.of();
        List<SavedScale> saved = new ArrayList<>(scales.size());
        for (java.util.Map.Entry<String, float[]> entry : scales.entrySet()) {
            AttachmentGeo.Bone bone = geo.bone(entry.getKey());
            if (bone == null) continue;
            saved.add(new SavedScale(bone, bone.xScale, bone.yScale, bone.zScale));
            float[] scale = entry.getValue();
            bone.xScale *= scale[0];
            bone.yScale *= scale[1];
            bone.zScale *= scale[2];
        }
        return saved;
    }

    /** Two bone-delta maps summed per bone (either may be empty). */
    private static java.util.Map<String, float[]> mergeBoneDeltas(java.util.Map<String, float[]> base,
                                                                  java.util.Map<String, float[]> extra) {
        if (extra.isEmpty()) return base;
        if (base.isEmpty()) return extra;
        java.util.Map<String, float[]> merged = new java.util.LinkedHashMap<>(base);
        extra.forEach((name, delta) -> merged.merge(name, delta,
                (a, b) -> new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}));
        return merged;
    }

    /**
     * Adds the eased pose deltas to the named bones. Pose bone rotations are authored
     * in the .geo.json's convention (Java space negates Y/Z, matching the geometry
     * loader); on a mirrored bake the mirror flips Y/Z back.
     */
    private static List<SavedRotation> applyBonePose(AttachmentGeo geo, java.util.Map<String, float[]> rotations,
                                                     boolean mirror) {
        if (rotations.isEmpty()) return List.of();
        List<SavedRotation> saved = new ArrayList<>(rotations.size());
        for (java.util.Map.Entry<String, float[]> entry : rotations.entrySet()) {
            AttachmentGeo.Bone bone = geo.bone(entry.getKey());
            if (bone == null) continue;
            saved.add(new SavedRotation(bone, bone.xRot, bone.yRot, bone.zRot));
            float[] degrees = entry.getValue();
            bone.xRot += (float) Math.toRadians(degrees[0]);
            bone.yRot += (float) Math.toRadians(mirror ? degrees[1] : -degrees[1]);
            bone.zRot += (float) Math.toRadians(mirror ? degrees[2] : -degrees[2]);
        }
        return saved;
    }

    /** A resolved anchor: a model bone, base offset (pixels), base orientation, and mirroring. */
    private record Anchor(String bone, float[] offset, float[] rotation, boolean mirror) {}

    private static final float[] NO_OFFSET = {0, 0, 0};

    /** Tag (every matching point, rig-filtered) -> explicit point -> direct bone. */
    private static List<Anchor> anchorsFor(AttachmentDef def, String rig) {
        if (def.targetTag() != null) {
            List<Anchor> out = new ArrayList<>();
            for (AttachmentPointDef point : pointsFor(def.targetTag(), rig)) {
                out.add(new Anchor(point.bone(), point.offset(), point.rotation(), point.mirror()));
            }
            return out;
        }
        if (def.targetPoint() != null) {
            AttachmentPointDef point = AttachmentClient.slot(def.targetPoint());
            if (point != null && (point.rig().isEmpty() || point.rig().equals(rig))) {
                return List.of(new Anchor(point.bone(), point.offset(), point.rotation(), point.mirror()));
            }
            if (point != null) return List.of();   // scoped to another rig: nothing to render here
        }
        return List.of(new Anchor(def.bone(), NO_OFFSET, NO_OFFSET, false));
    }

    /**
     * The points a tag resolves to on this rig: rig-scoped points that match, plus the
     * universal ones — unless any rig-scoped point carries the tag, in which case the
     * rig's own placement replaces the universal set entirely (a spider puts its "ear"
     * points where spider anatomy wants them, not where the humanoid head is).
     */
    private static List<AttachmentPointDef> pointsFor(String tag, String rig) {
        List<AttachmentPointDef> matched = new ArrayList<>();
        boolean rigSpecific = false;
        for (AttachmentPointDef point : AttachmentClient.allPoints()) {
            if (!point.tags().contains(tag)) continue;
            if (!point.rig().isEmpty() && !point.rig().equals(rig)) continue;
            if (!point.rig().isEmpty()) rigSpecific = true;
            matched.add(point);
        }
        if (rigSpecific) matched.removeIf(point -> point.rig().isEmpty());
        return matched;
    }

    /** An expressed attachment paired with the gene that granted it (the gene's allele carries the size roll). */
    private record Expressed(String geneId, AttachmentDef def) {}

    /** The attachment defs the entity expresses (per-entity set first, origin grant list as fallback). */
    /** The definitions this entity wears right now, as the render pass resolves them. */
    public static List<AttachmentDef> worn(LivingEntity entity) {
        List<AttachmentDef> out = new ArrayList<>();
        for (Expressed expressed : resolve(entity)) out.add(expressed.def());
        return out;
    }

    private static List<Expressed> resolve(LivingEntity entity) {
        List<Expressed> out = new ArrayList<>();
        Set<String> expressed = RootClientStore.expressedGenes(entity);
        if (RootClientStore.hasExpressionSync(entity) || !expressed.isEmpty()) {
            for (String geneId : expressed) collect(entity, geneId, out);
            return out;
        }
        String rootId = RootClientStore.resolve(entity);
        if (rootId.isEmpty()) return out;
        RootCatalogEntry origin = RootCatalogClient.origin(rootId);
        if (origin == null) return out;
        for (RootCatalogEntry.Inherited inherited : origin.inheritedGenes()) collect(entity, inherited.geneId(), out);
        return out;
    }

    private static void collect(LivingEntity entity, String geneId, List<Expressed> out) {
        GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
        if (gene == null || !gene.grantsAttachment()) return;
        // A variant-swapped gene wears the attachment of the carried option (three tail
        // styles, one gene); a composite grant wears every definition in its set, all
        // sharing the gene's channel rolls.
        String carried = AllelePayload.parse(
                RootClientStore.resolveCarriedVariant(entity, geneId)).variant();
        for (String attachmentId : gene.attachmentsFor(carried)) {
            AttachmentDef def = AttachmentClient.def(attachmentId);
            if (def == null) continue;
            // A wounded or lost part shows as whatever its definition puts in its place.
            AttachmentDef shown = com.aetherianartificer.townstead.root.attachment.BodyParts.shown(
                    entity, def, AttachmentClient::def);
            if (shown != null) out.add(new Expressed(geneId, shown));
            // Drawn once more, to be copied as it falls.
            else if (FallingPieces.wantsAny(entity.getId(), def.id())) out.add(new Expressed(geneId, def));
        }
    }
}
