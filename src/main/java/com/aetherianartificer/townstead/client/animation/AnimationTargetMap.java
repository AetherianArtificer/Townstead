package com.aetherianartificer.townstead.client.animation;

import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AnimationTargetMap<T extends LivingEntity> {
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

    public static <T extends LivingEntity> AnimationTargetMap<T> forMcaModel(HumanoidModel<T> model) {
        return new AnimationTargetMap<>(model);
    }

    public Optional<ModelPart> resolve(String target) {
        return Optional.ofNullable(targets.get(target));
    }

    public List<ModelPart> bendCompanionsFor(String target) {
        List<ModelPart> list = bendCompanions.get(target);
        return list != null ? list : List.of();
    }
}
