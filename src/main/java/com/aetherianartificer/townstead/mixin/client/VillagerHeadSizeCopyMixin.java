package com.aetherianartificer.townstead.mixin.client;

import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.model.VillagerEntityBaseModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Carries a villager's head size into models that copy its pose. MCA grows a child's head at draw
 * time, as a scale around its head parts inside its own render, never in the part pose, so a stock
 * humanoid model copied from it (a Curios hat renderer, say) draws an adult-sized head that sits on
 * top of the child's larger one. MCA's own copies render through the same draw code and must not get
 * the factor twice; only foreign copies have it multiplied into their head and hat parts.
 */
@Mixin(VillagerEntityBaseModelMCA.class)
public abstract class VillagerHeadSizeCopyMixin<T extends LivingEntity & VillagerLike<T>> {

    //? if neoforge {
    @Inject(method = "copyPropertiesTo", remap = false, at = @At("TAIL"), require = 1)
    //?} else {
    /*@Inject(method = "m_102872_", remap = false, at = @At("TAIL"), require = 1)
    *///?}
    private void townstead$copyHeadSize(HumanoidModel<T> target, CallbackInfo ci) {
        if (target instanceof CommonVillagerModel) return;
        float head = ((VillagerEntityBaseModelMCA<T>) (Object) this).getDimensions().getHead();
        if (head == 1f) return;
        townstead$scale(target.head, head);
        townstead$scale(target.hat, head);
    }

    private static void townstead$scale(ModelPart part, float factor) {
        part.xScale *= factor;
        part.yScale *= factor;
        part.zScale *= factor;
    }
}
