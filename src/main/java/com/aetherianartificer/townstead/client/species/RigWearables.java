package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.root.rig.RigDefinition;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

/**
 * Re-poses a host humanoid's {@code body} and {@code head} bones onto a non-humanoid rig's real back
 * and head, so worn render layers that anchor to those bones (Backpacked/cape/elytra on the body,
 * helmet/pumpkin/mob-head on the head) land on the rig instead of floating at the default humanoid
 * chest/head. The host bones are invisible for an alternate rig (their layers are suppressed), so
 * moving them only affects those external attachments.
 *
 * <p>Each anchor is placed RELATIVE TO the matching rig bone: the host bone is moved onto the rig
 * bone's rest position (resolved through the rig's bone map — {@code body}-&gt;abdomen, {@code head}),
 * then the authored {@code offset}/{@code rotation} is a small nudge from there. So a pack author tunes
 * a pixel or two off the actual body part instead of typing the whole gap from the humanoid pose. The
 * spaces line up when the rig renders at scale 1.0; a scaled rig would need a scale correction here.</p>
 *
 * <p>The base anchor is applied once in the host model's {@code setupAnim}, which every back layer then
 * reads. A specific back layer can be nudged further: its render mixin calls {@link #applyItem} just
 * before it reads the bone, re-posing it to that layer's base+delta.</p>
 */
public final class RigWearables {

    private RigWearables() {}
    private static final java.util.Map<ModelPart, float[]> SAVED_SCALES = new java.util.WeakHashMap<>();

    private static ModelPart[] parts(HumanoidModel<?> model) {
        return new ModelPart[]{model.head, model.hat, model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg};
    }

    private static void rememberScales(HumanoidModel<?> model) {
        for (var part : parts(model)) SAVED_SCALES.putIfAbsent(part, new float[]{part.xScale, part.yScale, part.zScale});
    }

    /** Undo only our previous wearable fit before the next entity's setupAnim and proportions run. */
    public static void restoreHostScales(HumanoidModel<?> model) {
        for (var part : parts(model)) {
            float[] scale = SAVED_SCALES.remove(part);
            if (scale != null) { part.xScale = scale[0]; part.yScale = scale[1]; part.zScale = scale[2]; }
        }
    }

    // The host body bone last posed by the base anchor this frame, and the rig bone it was anchored to.
    // Rendering is single-threaded and ordered setupAnim -> model -> layers per entity, so these are the
    // current entity's while its back layers render. Held so a layer mixin can re-pose without re-resolving.
    private static ModelPart anchoredBody;
    // The rig back bone's pose composed through its parents (x, y, z, xRot, yRot, zRot), or null.
    private static float[] anchoredBackPose;

    /** Scope an opt-in registered Curios renderer fit to just that item, preserving the other layers. */
    public static Runnable fitCurio(HumanoidModel<?> host, LivingEntity entity, String slot, String item) {
        String rig = RigModels.rigBaseFor(entity);
        if (!RigModels.isGeneric(rig)) return () -> {};
        var def = RigModels.definition(rig);
        if (def == null) return () -> {};
        boolean limbs = slot.equals("feet") || slot.equals("hands");
        var seat = CurioItemSeat.forSlot(slot);
        if (!limbs && seat == null) return () -> {};
        boolean head = !limbs && seat.channel().equals("head");
        var anchor = head ? def.head() : def.back();
        if (anchor == null) return () -> {};
        var adjust = anchor.items().get(item);
        // a namespaced item falls back to its bare key, as the back layers do (backpacked:backpack -> backpack)
        if (adjust == null && !limbs && item.contains(":")) adjust = anchor.items().get(item.substring(item.indexOf(':') + 1));
        if (adjust == null) adjust = anchor.items().get("renderer:" + slot);
        if (adjust == null) return () -> {};
        // Limb entries are relative to each named rig bone; torso entries use the worn anchor.
        String[] channels = slot.equals("feet") ? new String[]{"right_leg", "left_leg"}
                : slot.equals("hands") ? new String[]{"right_arm", "left_arm"}
                : new String[]{head ? "head" : "body"};
        ModelPart[] parts = slot.equals("feet") ? new ModelPart[]{host.rightLeg,host.leftLeg}
                : slot.equals("hands") ? new ModelPart[]{host.rightArm,host.leftArm}
                : new ModelPart[]{head ? host.head : host.body};
        var restores = new java.util.ArrayList<Runnable>();
        // A limb entry is authored for the right limb; the left wears its mirror image.
        for (int i = 0; i < parts.length; i++)
            restores.add(fitPart(parts[i], RigModels.boneModelPose(rig, def.boneFor(channels[i])),
                    limbs ? (i == 1 ? adjust.mirrored() : adjust) : anchor.base().plus(adjust)));
        return () -> { for (var restore : restores) restore.run(); };
    }

    /**
     * Draw a Curios hands/feet item where the rig wears boots, as {@link RigBootsRenderer} seats them: a
     * feet item once per {@code boots} entry, a hands item once on each arm bone with the seat of that
     * side's first boot. An entry for the item (or {@code renderer:<slot>}) adds to that seat. Each draw
     * has one host limb laid flat in the seat and the other hidden. False, with nothing drawn, when the
     * rig declares no boots; then {@link #fitCurio} places it.
     */
    public static boolean drawCurioOnSeats(HumanoidModel<?> host, LivingEntity entity, String slot, String item,
                                           com.mojang.blaze3d.vertex.PoseStack pose, Runnable draw) {
        boolean feet = slot.equals("feet");
        if (!feet && !slot.equals("hands")) return false;
        String rig = RigModels.rigBaseFor(entity);
        if (!RigModels.isGeneric(rig)) return false;
        var def = RigModels.definition(rig);
        if (def == null || def.boots().isEmpty()) return false;
        // An entry for the item (or renderer:<slot>) nudges it from the boot seat, authored for the right side.
        RigDefinition.Adjust delta = null;
        if (def.back() != null) {
            delta = def.back().items().get(item);
            if (delta == null) delta = def.back().items().get("renderer:" + slot);
        }
        java.util.List<RigDefinition.Boot> seats = new java.util.ArrayList<>();
        if (feet) {
            seats.addAll(def.boots());
        } else {
            for (boolean left : new boolean[]{false, true}) {
                String bone = def.boneFor(left ? "left_arm" : "right_arm");
                RigDefinition.Boot like = null;
                for (RigDefinition.Boot boot : def.boots()) if (boot.left() == left) { like = boot; break; }
                if (like != null) seats.add(new RigDefinition.Boot(bone, left, like.scale(), like.seat()));
            }
        }
        if (delta != null) {
            for (int i = 0; i < seats.size(); i++) {
                RigDefinition.Boot b = seats.get(i);
                seats.set(i, new RigDefinition.Boot(b.bone(), b.left(), b.scale(),
                        b.seat().plus(b.left() ? delta.mirrored() : delta)));
            }
        }
        // A humanoid leg ends 12px below its pivot, an arm 10px.
        float drop = feet ? RigBootsRenderer.FOOT_DROP : 10f;
        float scale = RigModels.scaleFor(entity);
        for (RigDefinition.Boot seat : seats) {
            ModelPart worn = feet ? (seat.left() ? host.leftLeg : host.rightLeg) : (seat.left() ? host.leftArm : host.rightArm);
            ModelPart other = feet ? (seat.left() ? host.rightLeg : host.leftLeg) : (seat.left() ? host.rightArm : host.leftArm);
            var restoreWorn = fitPart(worn, null, RigDefinition.Adjust.ZERO);
            var restoreOther = fitPart(other, null, RigDefinition.Adjust.ZERO);
            pose.pushPose();
            try {
                // the rig body's own scaled frame, as SpeciesRigLayer draws it
                if (scale != 1f) {
                    pose.translate(0f, 1.501f, 0f);
                    pose.scale(scale, scale, scale);
                    pose.translate(0f, -1.501f, 0f);
                }
                if (!RigBootsRenderer.enterSeat(rig, seat, pose, drop)) continue;
                float s = seat.scale() * seat.seat().scale();
                RigBootsRenderer.flatLeg(worn, s);
                worn.xScale *= seat.seat().scaleAxes()[0];
                worn.yScale *= seat.seat().scaleAxes()[1];
                worn.zScale *= seat.seat().scaleAxes()[2];
                RigBootsRenderer.hideLeg(other);
                draw.run();
            } finally {
                pose.popPose();
                restoreWorn.run();
                restoreOther.run();
            }
        }
        return true;
    }

    static Runnable fitPart(ModelPart part, float[] bonePose, RigDefinition.Adjust adjust) {
        var before = part.storePose();
        float sx = part.xScale, sy = part.yScale, sz = part.zScale;
        poseAt(part, bonePose, adjust, false, 0, 0);
        return () -> {
            part.loadPose(before);
            part.xScale = sx; part.yScale = sy; part.zScale = sz;
        };
    }

    /**
     * Hide the host's worn boots on a non-humanoid rig by zeroing the host model's leg scale, which the
     * armor layer copies onto its own legs (so the boots draw at zero size). The helmet/chest/back path
     * is untouched, so the helmet still renders normally. {@code generic} false RESETS the legs to 1,
     * but only when they were left zeroed by a previous generic entity on this shared model — so it does
     * not fight any other per-frame leg scaler (e.g. the proportions gene).
     */
    public static void suppressHostBoots(HumanoidModel<?> host, boolean generic) {
        if (generic) {
            rememberScales(host);
            setLegScale(host, 0f);
        } else if (host.leftLeg.xScale == 0f || host.rightLeg.xScale == 0f) {
            setLegScale(host, 1f);
        }
    }

    private static void setLegScale(HumanoidModel<?> host, float scale) {
        host.leftLeg.xScale = scale;
        host.leftLeg.yScale = scale;
        host.leftLeg.zScale = scale;
        host.rightLeg.xScale = scale;
        host.rightLeg.yScale = scale;
        host.rightLeg.zScale = scale;
    }

    /**
     * Pose the host bones a non-humanoid rig declares anchors for: the body bone onto the rig's back
     * bone (remembered for per-item nudges) and the head bone onto the rig's head bone. The head also
     * takes this frame's look ({@code headYaw}/{@code headPitch}, the head-vs-body angles passed to
     * {@code setupAnim}), so a worn helmet turns with the head exactly like the visible head does.
     * Returns true if it posed anything, so the caller can skip the humanoid animation bridge.
     */
    public static boolean anchor(HumanoidModel<?> host, RigDefinition def, float headYaw, float headPitch) {
        rememberScales(host);
        boolean posed = false;
        RigModels.genericModel(def.id()); // ensure the rig root is baked so its bones resolve
        if (def.back() != null) {
            anchoredBody = host.body;
            anchoredBackPose = RigModels.boneModelPose(def.id(), def.boneFor("body"));
            poseAt(anchoredBody, anchoredBackPose, def.back().base(), false, 0f, 0f);
            posed = true;
        }
        if (def.head() != null) {
            poseAt(host.head, RigModels.boneModelPose(def.id(), def.boneFor("head")), def.head().base(), !def.boneFor("head").equals(def.boneFor("body")) && def.modelType() != RigDefinition.ModelType.GEOMETRY, headYaw, headPitch);
            posed = true;
        }
        if (def.armorType() == RigDefinition.ArmorType.CUSTOM) {
            RigGeometryArmor.poseHostLimbs(host, def);
            host.hat.copyFrom(host.head);
        }
        return posed;
    }

    /** Prepare this entity before external layers read its anchors. */
    public static void prepare(LivingEntity entity, RigDefinition def, float swing, float amount,
                               float age, float yaw, float pitch) {
        if (def.modelType() != RigDefinition.ModelType.GEOMETRY) return;
        var model = RigModels.genericModel(def.id(), entity);
        if (!(model instanceof net.minecraft.client.model.HierarchicalModel<?> hierarchy)) return;
        RigModels.beginRender(def.id(), hierarchy.root());
        RigModels.setupAnim(model, entity, swing, amount, age, yaw, pitch);
        RigClips.apply(entity, def, hierarchy.root(), age);
        RigClips.applyGrips(entity, def, hierarchy.root(), net.minecraft.util.Mth.clamp(age - entity.tickCount, 0f, 1f));
        RigOutfitState.applyScales(entity, hierarchy.root(), net.minecraft.util.Mth.clamp(age - entity.tickCount, 0f, 1f));
        if (!def.boneFor("head").equals(def.boneFor("body"))) {
            var head = RigModels.bakedBone(def.id(), def.boneFor("head"));
            if (head != null) {
                head.xRot += (float) Math.toRadians(pitch);
                head.yRot += (float) Math.toRadians(yaw);
            }
        }
        com.aetherianartificer.townstead.client.animation.emote.GenericEmoteApplier.apply(
                entity, hierarchy.root(), def, net.minecraft.util.Mth.clamp(age - entity.tickCount, 0f, 1f));
    }

    /**
     * Re-pose the stashed body bone to a specific layer's base+delta, called from that layer's render
     * mixin. A no-op unless the entity actually has a generic rig with a back anchor this frame, so a
     * normal player's cape/elytra/backpack is never touched.
     */
    public static void applyItem(LivingEntity entity, String key) {
        if (anchoredBody == null) return;
        String rigBase = RigModels.rigBaseFor(entity);
        if (!RigModels.isGeneric(rigBase)) return;
        RigDefinition def = RigModels.definition(rigBase);
        if (def == null || def.back() == null) return;
        poseAt(anchoredBody, anchoredBackPose, def.back().forItem(key), false, 0f, 0f);
    }

    // How the rig body was last drawn for an entity: its scale and whole-body offset (model pixels), so
    // other mods' layers drawn after it this frame line up with it.
    private static final java.util.Map<Integer, float[]> DRAWN = new java.util.concurrent.ConcurrentHashMap<>();

    /** Called as a generic rig body draws, before other layers run for the entity. */
    public static void noteDrawn(LivingEntity entity, float scale, float[] bodyOffset) {
        DRAWN.put(entity.getId(), new float[]{scale, bodyOffset[0], bodyOffset[1], bodyOffset[2]});
    }

    /**
     * How many times another mod's layer draws for this entity: once per placement of the part with the
     * most (a cyber leg on each of four spider legs), or 0 when the entity's rig declares no fit and the
     * layer draws as it would.
     */
    public static int fitPasses(LivingEntity entity) {
        RigDefinition def = fitted(entity);
        if (def == null) return 0;
        int passes = 1;
        for (var placements : def.fit().values()) passes = Math.max(passes, placements.size());
        return passes;
    }

    @org.jetbrains.annotations.Nullable
    private static RigDefinition fitted(LivingEntity entity) {
        String rig = RigModels.rigBaseFor(entity);
        if (rig == null || !RigModels.isGeneric(rig)) return null;
        RigDefinition def = RigModels.definition(rig);
        return def == null || def.fit() == null ? null : def;
    }

    /**
     * Fit the host humanoid parts onto a non-humanoid rig's bones for one draw ({@code pass}) of another
     * mod's layer (a cyber arm, a worn add-on), per the rig's {@code wearables.fit}: each part goes onto
     * its {@code pass}-th placement, live this frame, then the authored nudge; a part with no placement
     * for this pass, or none at all, is hidden. Returns how to put the parts back, or null when the
     * entity's rig declares no fit.
     */
    @org.jetbrains.annotations.Nullable
    public static Runnable fitForeign(LivingEntity entity, HumanoidModel<?> host, int pass) {
        RigDefinition def = fitted(entity);
        if (def == null) return null;
        String rig = RigModels.rigBaseFor(entity);
        ModelPart[] main = {host.head, host.body, host.rightArm, host.leftArm, host.rightLeg, host.leftLeg};
        java.util.List<ModelPart> touched = new java.util.ArrayList<>(java.util.List.of(main));
        touched.add(host.hat);
        if (host instanceof net.minecraft.client.model.PlayerModel<?> player) {
            touched.addAll(java.util.List.of(player.jacket, player.rightSleeve, player.leftSleeve,
                    player.rightPants, player.leftPants));
        }
        java.util.List<Runnable> restores = new java.util.ArrayList<>(touched.size());
        for (ModelPart part : touched) {
            var before = part.storePose();
            float sx = part.xScale, sy = part.yScale, sz = part.zScale;
            restores.add(() -> { part.loadPose(before); part.xScale = sx; part.yScale = sy; part.zScale = sz; });
        }
        float[] drawn = DRAWN.getOrDefault(entity.getId(), new float[]{RigModels.scaleFor(entity), 0f, 0f, 0f});
        for (int i = 0; i < main.length; i++) {
            String channel = RigDefinition.FIT_PARTS.get(i);
            var placements = def.fit().get(channel);
            RigDefinition.Fit fit = placements == null || pass >= placements.size() ? null : placements.get(pass);
            float[] bone = fit == null ? null
                    : RigModels.boneModelPose(rig, fit.bone().isEmpty() ? def.boneFor(channel) : fit.bone());
            if (bone == null) {
                main[i].xScale = main[i].yScale = main[i].zScale = 0f;
                continue;
            }
            fitPose(main[i], bone, fit.adjust());
            liftOffSurface(main[i], PART_CENTERS[i]);
            inDrawnFrame(main[i], drawn);
        }
        copyPart(host.hat, host.head);
        if (host instanceof net.minecraft.client.model.PlayerModel<?> player) {
            copyPart(player.jacket, host.body);
            copyPart(player.rightSleeve, host.rightArm);
            copyPart(player.leftSleeve, host.leftArm);
            copyPart(player.rightPants, host.rightLeg);
            copyPart(player.leftPants, host.leftLeg);
        }
        return () -> { for (Runnable restore : restores) restore.run(); };
    }

    /**
     * Put {@code part} on a rig bone: the authored rotation turns it in the bone's own frame (so a
     * humanoid arm can lie along a sideways spider leg), and the offset moves it along the turned axes.
     */
    private static void fitPose(ModelPart part, float[] bone, RigDefinition.Adjust a) {
        part.resetPose();
        float[] r = a.rotation();
        org.joml.Quaternionf turn = new org.joml.Quaternionf().rotationZYX(bone[5], bone[4], bone[3])
                .mul(new org.joml.Quaternionf().rotationZYX((float) Math.toRadians(r[2]),
                        (float) Math.toRadians(r[1]), (float) Math.toRadians(r[0])));
        org.joml.Vector3f offset = turn.transform(new org.joml.Vector3f(a.offset()[0], a.offset()[1], a.offset()[2]));
        org.joml.Vector3f angles = turn.getEulerAnglesZYX(new org.joml.Vector3f());
        part.x = bone[0] + offset.x();
        part.y = bone[1] + offset.y();
        part.z = bone[2] + offset.z();
        part.xRot = angles.x();
        part.yRot = angles.y();
        part.zRot = angles.z();
        part.xScale = a.scale() * a.scaleAxes()[0];
        part.yScale = a.scale() * a.scaleAxes()[1];
        part.zScale = a.scale() * a.scaleAxes()[2];
    }

    // Centre of each humanoid part's cube relative to its pivot (head, body, right arm, left arm, legs).
    private static final float[][] PART_CENTERS = {
            {0f, -4f, 0f}, {0f, 6f, 0f}, {-1f, 4f, 0f}, {1f, 4f, 0f}, {0f, 6f, 0f}, {0f, 6f, 0f}};
    // A part fitted to the size of the rig part under it would share its faces and flicker; drawing it a
    // little larger around its own centre keeps it just above the surface.
    private static final float SURFACE_LIFT = 1.04f;

    private static void liftOffSurface(ModelPart part, float[] center) {
        org.joml.Vector3f c = new org.joml.Vector3f(center[0] * part.xScale, center[1] * part.yScale,
                center[2] * part.zScale);
        new org.joml.Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(c);
        part.x += c.x() * (1f - SURFACE_LIFT);
        part.y += c.y() * (1f - SURFACE_LIFT);
        part.z += c.z() * (1f - SURFACE_LIFT);
        part.xScale *= SURFACE_LIFT;
        part.yScale *= SURFACE_LIFT;
        part.zScale *= SURFACE_LIFT;
    }

    /** Carry a part posed in the rig's own space into the frame the rig body was drawn in. */
    private static void inDrawnFrame(ModelPart part, float[] drawn) {
        // The body draws offset by drawn[1..3] px, then scaled by drawn[0] about the feet pivot (1.501 blocks).
        float scale = drawn[0], pivot = 1.501f * 16f;
        part.x = (part.x + drawn[1]) * scale;
        part.y = (part.y + drawn[2] - pivot) * scale + pivot;
        part.z = (part.z + drawn[3]) * scale;
        part.xScale *= scale;
        part.yScale *= scale;
        part.zScale *= scale;
    }

    private static void copyPart(ModelPart target, ModelPart source) {
        target.copyFrom(source);
        target.xScale = source.xScale;
        target.yScale = source.yScale;
        target.zScale = source.zScale;
    }

    /**
     * Pose {@code target} onto {@code source}'s rest position, with rotation = (this frame's look when
     * {@code trackLook}, else the source bone's rest rotation) plus the adjust's rotation delta. The
     * adjust's offset is applied in the bone's LOCAL (rotated) frame — so a wearable stays rigidly glued
     * to the bone and swings with it (a helmet on the head's look), instead of sitting on the rotation
     * axis and being left behind. With {@code source} null (rig bone not found) it falls back to the
     * host bone's own rest position.
     */
    private static void poseAt(ModelPart target, float[] source, RigDefinition.Adjust a,
                               boolean trackLook, float headYaw, float headPitch) {
        target.resetPose();
        float baseX, baseY, baseZ, baseXRot, baseYRot, baseZRot;
        if (source != null) {
            baseX = source[0]; baseY = source[1]; baseZ = source[2];
            baseXRot = source[3]; baseYRot = source[4]; baseZRot = source[5];
        } else {
            baseX = target.x; baseY = target.y; baseZ = target.z;
            baseXRot = baseYRot = baseZRot = 0f;
        }
        // The head turns with this frame's look (the rig bone's own rotation is a frame stale); other
        // bones keep their rest rotation.
        if (trackLook) {
            baseXRot = (float) Math.toRadians(headPitch);
            baseYRot = (float) Math.toRadians(headYaw);
            baseZRot = 0f;
        }
        float[] rotation = a.rotation();
        float xRot = baseXRot + (float) Math.toRadians(rotation[0]);
        float yRot = baseYRot + (float) Math.toRadians(rotation[1]);
        float zRot = baseZRot + (float) Math.toRadians(rotation[2]);
        // Offset in the bone's local frame: rotate it by the bone's orientation (ModelPart's own ZYX
        // order) before adding to the pivot, so the wearable rides the rotation.
        float[] offset = a.offset();
        org.joml.Vector3f local = new org.joml.Vector3f(offset[0], offset[1], offset[2]);
        new org.joml.Quaternionf().rotationZYX(zRot, yRot, xRot).transform(local);
        target.x = baseX + local.x();
        target.y = baseY + local.y();
        target.z = baseZ + local.z();
        target.xRot = xRot;
        target.yRot = yRot;
        target.zRot = zRot;
        target.xScale = a.scale() * a.scaleAxes()[0];
        target.yScale = a.scale() * a.scaleAxes()[1];
        target.zScale = a.scale() * a.scaleAxes()[2];
    }
}
