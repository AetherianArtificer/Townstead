package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.animation.McaAnimationBridge;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmoteReflection;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityExtendedModel.class)
public abstract class PlayerEntityExtendedModelAnimationMixin<T extends LivingEntity> {
    // Runs BEFORE super.setupAnim (which is where playerAnim applies emote bend). playerAnim's
    // BipedEntityModelMixin registers bend mutators on this model's parts once, at construction.
    // When EMF is active it wraps the parts and swaps each one's active cube per model variant —
    // and EmfCompat forces EMF's vanilla variant during emotes, so the cube playerAnim registered
    // on is no longer the active one. playerAnim's own emote bend then calls
    // getAndActivateMutator("bend") on a cube with no mutator and NPEs (crash-2026-07-17_11.15.27).
    // Re-register on whichever cube is active this frame; idempotent, no-op without bendylib.
    // This is the same self-healing re-init Townstead's own applyBend does for villager layers.
    //? if neoforge {
    @Inject(method = "setupAnim", remap = false, at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "m_6973_", remap = false, at = @At("HEAD"))
    *///?}
    private void townstead$ensureBendMutators(
            T player,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
        // The MCA/EMF model variant can differ from the one PlayerRenderer primed.
        // Set this before vanilla setupAnim so the body AND arms receive the crouch pose.
        model.crouching = player.isCrouching();
        if (!EmoteReflection.isBendylibAvailable()) return;
        EmoteReflection.attachBendMutator(model.body);
        EmoteReflection.attachBendMutator(model.leftArm);
        EmoteReflection.attachBendMutator(model.rightArm);
        EmoteReflection.attachBendMutator(model.leftLeg);
        EmoteReflection.attachBendMutator(model.rightLeg);
    }

    //? if neoforge {
    @Inject(method = "setupAnim", remap = false, at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_6973_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$applyAnimationBridge(
            T player,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch,
            CallbackInfo ci
    ) {
        McaAnimationBridge.apply(
                player,
                (PlayerEntityExtendedModel<T>) (Object) this,
                limbAngle,
                limbDistance,
                animationProgress,
                headYaw,
                headPitch);
        // hide_feature genes: zero the hidden parts on the player's genetics model too
        // (the villager model handles its own; this covers the player render).
        com.aetherianartificer.townstead.client.root.HideFeatures.hide(
                (net.minecraft.client.model.HumanoidModel<?>) (Object) this,
                com.aetherianartificer.townstead.client.root.HideFeatures.hiddenGroups(player));
        String rigBase = com.aetherianartificer.townstead.client.species.RigModels.rigBaseFor(player);
        boolean generic = com.aetherianartificer.townstead.client.species.RigModels.isGeneric(rigBase);
        // Hide the host's mis-fitting boots on a non-humanoid rig by zeroing the leg scale (the worn
        // armor copies it); reset it for a normal player so the shared model does not leak the zero.
        com.aetherianartificer.townstead.client.species.RigWearables.suppressHostBoots(
                (net.minecraft.client.model.HumanoidModel<?>) (Object) this, generic);
        // For a non-humanoid rig, re-pose the body bone onto the rig's back so back-worn layers
        // (Backpacked, cape, elytra) sit on the creature's back instead of the default humanoid chest.
        if (generic) {
            com.aetherianartificer.townstead.root.rig.RigDefinition def =
                    com.aetherianartificer.townstead.client.species.RigModels.definition(rigBase);
            if (def != null) {
                com.aetherianartificer.townstead.client.species.RigWearables.anchor(
                        (net.minecraft.client.model.HumanoidModel<?>) (Object) this, def, headYaw, headPitch);
            }
        }
    }
}
