package com.aetherianartificer.townstead.client.animation;

import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;

public record McaAnimationParameters(
        float limbAngle,
        float limbDistance,
        float headYaw
) {
    public static <T extends LivingEntity> McaAnimationParameters from(
            T entity,
            HumanoidModel<T> model,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw
    ) {
        if (model instanceof VillagerEntityBaseModelMCA<?> && entity instanceof VillagerLike<?> villager) {
            if (villager.getAgeState() == AgeState.BABY && !entity.isPassenger()) {
                limbDistance = (float) Math.sin(entity.tickCount / 12.0F);
                limbAngle = (float) Math.cos(entity.tickCount / 9.0F) * 3.0F;
                headYaw += (float) Math.sin(entity.tickCount / 2.0F);
            }
            if (entity.isBaby()) {
                limbAngle /= 3.0F;
            }
            limbAngle /= 0.2F + villager.getRawVerticalScaleFactor();
        } else if (model instanceof PlayerEntityExtendedModel<?>) {
            VillagerLike<?> villager = CommonVillagerModel.getVillager(entity);
            if (villager.getAgeState() == AgeState.BABY && !entity.isPassenger()) {
                limbDistance = (float) Math.sin(entity.tickCount / 12.0F);
                limbAngle = (float) Math.cos(entity.tickCount / 9.0F) * 3.0F;
                headYaw += (float) Math.sin(entity.tickCount / 2.0F);
            }
        }

        return new McaAnimationParameters(limbAngle, limbDistance, headYaw);
    }
}
