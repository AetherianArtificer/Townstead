package com.aetherianartificer.townstead.client.animation;

import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

public record McaRigScale(
        float width,
        float height,
        float head
) {
    private static final McaRigScale VANILLA = new McaRigScale(1.0F, 1.0F, 1.0F);

    public static <T extends LivingEntity> McaRigScale from(T entity, HumanoidModel<T> model) {
        if (!(model instanceof CommonVillagerModel<?> commonModel)) return VANILLA;

        VillagerLike<?> villager = CommonVillagerModel.getVillager(entity);
        VillagerDimensions dimensions = commonModel.getDimensions();
        float width = villager == null ? dimensions.getWidth() : villager.getRawHorizontalScaleFactor();
        float height = villager == null ? dimensions.getHeight() : villager.getRawVerticalScaleFactor();
        return new McaRigScale(
                clampScale(width),
                clampScale(height),
                clampScale(dimensions.getHead()));
    }

    public float rotationStrength(String target) {
        float scale = switch (target) {
            case "head" -> height * head;
            case "body" -> (height + width) * 0.5F;
            case "right_arm", "left_arm" -> (height * 0.7F) + (width * 0.3F);
            case "right_leg", "left_leg" -> height;
            default -> height;
        };
        return Mth.clamp(1.0F / (float) Math.sqrt(clampScale(scale)), 0.55F, 1.25F);
    }

    public float translationStrength(String target) {
        float scale = switch (target) {
            case "head" -> height * head;
            case "body" -> (height + width) * 0.5F;
            case "right_arm", "left_arm" -> (height * 0.75F) + (width * 0.25F);
            case "right_leg", "left_leg" -> height;
            default -> height;
        };
        return Mth.clamp((float) Math.sqrt(clampScale(scale)), 0.55F, 1.35F);
    }

    private static float clampScale(float value) {
        if (!Float.isFinite(value)) return 1.0F;
        return Mth.clamp(value, 0.35F, 2.5F);
    }
}
