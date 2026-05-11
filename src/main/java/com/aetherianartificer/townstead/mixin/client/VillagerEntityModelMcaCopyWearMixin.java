package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.animation.emote.loader.EmoteReflection;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Initializes bend mutators on MCA's wear layers ({@code leftArmwear},
 * {@code rightArmwear}, ...) at model construction.
 *
 * <p>Why this is needed: MCA's renderer creates a separate
 * {@code VillagerEntityModelMCA} instance for every render layer ({@code
 * SkinLayer}, {@code FaceLayer}, {@code ClothingLayer}, {@code HairLayer}).
 * Each instance's standard {@code HumanoidModel} parts (head/body/arms/legs)
 * are bend-initialized automatically by playerAnim's {@code
 * BipedEntityModelMixin} at construction, but the wear layers are MCA-added
 * "extras" outside that mixin's scope and stay un-bendable.</p>
 *
 * <p>This matters because {@code VillagerLayer.render} calls
 * {@code parentModel.copyPropertiesTo(layerModel)} which propagates pose +
 * bend state via {@code copyFrom} (bendylib's {@code copyTransformExtended}
 * mixin handles the bend bit). But that propagation requires the target part
 * to have a bend mutator already attached AND its
 * {@code hasMutatedCuboid} flag set — otherwise bendylib's render redirect
 * doesn't fire and the cube renders as the original straight mesh. By
 * calling {@code initBend} on every wear part at construction, we cover all
 * layer instances and ensure the clothing layer's sleeve bends with the
 * villager's inner arm during emotes.</p>
 */
@Mixin(VillagerEntityModelMCA.class)
public abstract class VillagerEntityModelMcaCopyWearMixin<T extends LivingEntity & VillagerLike<T>> {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void townstead$initBendOnWearLayers(net.minecraft.client.model.geom.ModelPart root, CallbackInfo ci) {
        VillagerEntityModelMCA<?> self = (VillagerEntityModelMCA<?>) (Object) this;
        EmoteReflection.attachBendMutator(self.leftArmwear);
        EmoteReflection.attachBendMutator(self.rightArmwear);
        EmoteReflection.attachBendMutator(self.leftLegwear);
        EmoteReflection.attachBendMutator(self.rightLegwear);
        EmoteReflection.attachBendMutator(self.bodyWear);
    }
}
