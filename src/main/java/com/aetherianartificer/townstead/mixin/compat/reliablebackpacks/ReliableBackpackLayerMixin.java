package com.aetherianartificer.townstead.mixin.compat.reliablebackpacks;

import com.aetherianartificer.townstead.client.species.RigWearables;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Soft compat with Reliable Backpacks. Unlike the cape/elytra/Backpacked back layers (which read
 * {@code getParentModel().body} live and so only need a {@link RigWearables#applyItem} nudge), Reliable
 * Backpacks' {@code BackpackLayer} caches the parent body bone <b>in its constructor</b>
 * ({@code parentBody = getParentModel().body}). It is built at {@code AddLayers} time, when the player
 * renderer's model is still the vanilla {@code PlayerModel} &mdash; so its cached bone is the vanilla
 * body, forever.
 *
 * <p>MCA swaps the renderer's model to its {@code PlayerEntityExtendedModel} inside {@code scale()},
 * which runs <i>before</i> {@code setupAnim} in {@code LivingEntityRenderer.render}. For a Root/genetics
 * player only that swapped model is animated (by the vanilla gait, by FA/PlayerAnimator, and by our own
 * animation bridge); the vanilla body the backpack copies from never gets {@code setupAnim} and sits at
 * rest. The pack still tracks the entity's world position and body-yaw (those live on the shared
 * {@code PoseStack}), so it looks attached but ignores the body bone's local lean/twist/sway &mdash; the
 * "doesn't follow the body animation" desync.</p>
 *
 * <p>Two injectors fix it: {@link #townstead$anchorBackpack} nudges a non-humanoid rig's back anchor
 * onto the item (parity with the other back layers), and {@link #townstead$copyFromLiveBody} redirects
 * the cached-body copy to read the live (swapped, animated) parent model instead. For a plain vanilla
 * player the swapped and cached models are the same object, so the redirect is a no-op there.</p>
 *
 * <p>Extends {@code RenderLayer} only to inherit its {@code protected getParentModel()}; the mixin's own
 * constructor is never applied to the target. {@code @Pseudo} + {@code require = 0} keep this inert when
 * Reliable Backpacks is absent, and let a future rename of its private {@code renderBaseLayer} fall back
 * to the mod's own behavior rather than crash. Reliable Backpacks ships only for 1.21+ (NeoForge/Fabric),
 * so the un-remapped vanilla names below resolve against the runtime's official mappings.</p>
 */
@Pseudo
@Mixin(targets = "com.evandev.reliable_backpacks.client.rendering.BackpackLayer", remap = false)
public abstract class ReliableBackpackLayerMixin
        extends RenderLayer<LivingEntity, HumanoidModel<LivingEntity>> {

    private ReliableBackpackLayerMixin(RenderLayerParent<LivingEntity, HumanoidModel<LivingEntity>> renderer) {
        super(renderer);
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), remap = false, require = 0)
    private void townstead$anchorBackpack(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity,
                                          float limbSwing, float limbSwingAmount, float partialTick,
                                          float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        RigWearables.applyItem(entity, "reliable_backpacks:backpack");
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("RETURN"), remap = false, require = 0)
    private void townstead$anchorBackpackRestore(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity,
                                          float limbSwing, float limbSwingAmount, float partialTick,
                                          float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        RigWearables.applyItem(entity, "");
    }

    @Redirect(method = "renderBaseLayer(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FLnet/minecraft/world/item/ItemStack;Z)V",
            //? if neoforge {
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;copyFrom(Lnet/minecraft/client/model/geom/ModelPart;)V"),
            //?} else {
            /*at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/geom/ModelPart;m_104315_(Lnet/minecraft/client/model/geom/ModelPart;)V"),
            *///?}
            remap = false, require = 0)
    private void townstead$copyFromLiveBody(ModelPart backpackModel, ModelPart cachedParentBody) {
        // Copy from the renderer's live model (the swapped, animated one) rather than the body bone the
        // layer cached at construction. Fall back to the cached bone if the parent has no body.
        HumanoidModel<LivingEntity> live = getParentModel();
        ModelPart source = live != null && live.body != null ? live.body : cachedParentBody;
        backpackModel.copyFrom(source);
    }
}
