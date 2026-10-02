package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.root.rig.RigDefinition;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AnimationTargetMap<T extends LivingEntity> {
    // Render-thread caches, bounded even if a preview repeatedly creates fresh models.
    // Strong, bounded keys avoid weak-key/value cycles through custom ModelPart graphs.
    private static final int CACHE_LIMIT = 128;
    private static final Map<HumanoidModel<?>, AnimationTargetMap<?>> MODEL_CACHE = new IdentityHashMap<>();
    private static final Map<ModelPart, RigEntry> RIG_CACHE = new IdentityHashMap<>();

    private final Map<String, ModelPart> targets = new HashMap<>();
    /**
     * Extra ModelParts to apply bend to alongside the primary. MCA's villager
     * model has wear layers ({@code leftArmwear}, {@code rightArmwear}, etc.)
     * whose meshes follow the inner arm via {@code copyFrom}, but bend state
     * isn't carried — so the wear layer renders straight on a bent arm,
     * producing the visible "extra arm" doubling.
     */
    private final Map<String, List<ModelPart>> bendCompanions = new HashMap<>();

    private AnimationTargetMap(HumanoidModel<T> model) {
        targets.put("head", model.head);
        targets.put("headwear", model.hat);
        targets.put("body", model.body);
        targets.put("right_arm", model.rightArm);
        targets.put("left_arm", model.leftArm);
        targets.put("right_leg", model.rightLeg);
        targets.put("left_leg", model.leftLeg);

        if (model instanceof VillagerEntityModelMCA<?> mca) {
            bendCompanions.put("left_arm", List.of(mca.leftArmwear));
            bendCompanions.put("right_arm", List.of(mca.rightArmwear));
            bendCompanions.put("left_leg", List.of(mca.leftLegwear));
            bendCompanions.put("right_leg", List.of(mca.rightLegwear));
        }
    }

    /**
     * Build a target map for an alternate species rig by resolving each animation channel to the
     * bone the rig's definition names for it (arbitrary author names supported). For a vanilla body
     * the bone map is the identity (channel == bone, {@code headwear -> hat}), so this resolves to the
     * same parts {@link #forMcaModel} would, keeping the existing rigs pixel-identical.
     */
    private AnimationTargetMap(ModelPart root, RigDefinition def) {
        for (String channel : RigDefinition.CHANNELS) {
            String bone = def.boneFor(channel);
            if (root.hasChild(bone)) targets.put(channel, root.getChild(bone));
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends LivingEntity> AnimationTargetMap<T> forMcaModel(HumanoidModel<T> model) {
        AnimationTargetMap<?> cached = MODEL_CACHE.get(model);
        if (cached == null || !cached.matchesModel(model)) {
            cached = new AnimationTargetMap<>(model);
            if (MODEL_CACHE.size() >= CACHE_LIMIT) MODEL_CACHE.clear();
            MODEL_CACHE.put(model, cached);
        }
        return (AnimationTargetMap<T>) cached;
    }

    @SuppressWarnings("unchecked")
    public static <T extends LivingEntity> AnimationTargetMap<T> forRig(ModelPart root, RigDefinition def) {
        RigEntry cached = RIG_CACHE.get(root);
        if (cached == null || cached.definition() != def || !cached.targets().matchesRig(root, def)) {
            cached = new RigEntry(def, new AnimationTargetMap<>(root, def));
            if (RIG_CACHE.size() >= CACHE_LIMIT) RIG_CACHE.clear();
            RIG_CACHE.put(root, cached);
        }
        return (AnimationTargetMap<T>) cached.targets();
    }

    public static void clearCache() {
        MODEL_CACHE.clear();
        RIG_CACHE.clear();
    }

    private boolean matchesModel(HumanoidModel<?> model) {
        return targets.get("head") == model.head && targets.get("headwear") == model.hat
                && targets.get("body") == model.body && targets.get("left_arm") == model.leftArm
                && targets.get("right_arm") == model.rightArm && targets.get("left_leg") == model.leftLeg
                && targets.get("right_leg") == model.rightLeg
                && (!(model instanceof VillagerEntityModelMCA<?> mca)
                    || bendCompanions.get("left_arm").get(0) == mca.leftArmwear
                    && bendCompanions.get("right_arm").get(0) == mca.rightArmwear
                    && bendCompanions.get("left_leg").get(0) == mca.leftLegwear
                    && bendCompanions.get("right_leg").get(0) == mca.rightLegwear);
    }

    private boolean matchesRig(ModelPart root, RigDefinition def) {
        for (String channel : RigDefinition.CHANNELS) {
            String bone = def.boneFor(channel);
            ModelPart current = root.hasChild(bone) ? root.getChild(bone) : null;
            if (targets.get(channel) != current) return false;
        }
        return true;
    }

    private record RigEntry(RigDefinition definition, AnimationTargetMap<?> targets) {}

    public Optional<ModelPart> resolve(String target) {
        // Emotecraft/Bedrock convention calls the humanoid body bone "torso" while
        // Minecraft's model calls it "body". Native clips deliberately accept both.
        return Optional.ofNullable(targets.get("torso".equals(target) ? "body" : target));
    }

    public List<ModelPart> bendCompanionsFor(String target) {
        List<ModelPart> list = bendCompanions.get(target);
        return list != null ? list : List.of();
    }
}
