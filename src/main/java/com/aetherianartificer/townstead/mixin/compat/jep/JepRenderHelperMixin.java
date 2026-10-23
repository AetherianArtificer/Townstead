package com.aetherianartificer.townstead.mixin.compat.jep;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adapts JEP's vanilla-villager framing to MCA's player-shaped preview renderer. */
@Pseudo
@Mixin(targets = "com.mrbysco.justenoughprofessions.RenderHelper", remap = false)
public final class JepRenderHelperMixin {

    private static final int MCA_VERTICAL_OFFSET = 12;

    @ModifyVariable(
            method = "renderVillager",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1,
            require = 0)
    private static int townstead$lowerMcaPreview(int y) {
        return y + MCA_VERTICAL_OFFSET;
    }

    /**
     * JEP's copied inventory renderer scales all three axes positively. Minecraft's current
     * inventory renderer deliberately negates Z; without that handedness correction MCA's
     * translucent, back-face-culled face shell is culled while the opaque skin and clothing still
     * draw. This is why the mannequin had a normal head and wardrobe but no eyes.
     */
    @ModifyArg(
            method = "renderVillager",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V"),
            index = 2,
            require = 0)
    private static float townstead$useInventoryRenderHandedness(float zScale) {
        return -Math.abs(zScale);
    }

    /**
     * Pair the inventory renderer's negative Z scale with its normal 180-degree entity yaw. The
     * handedness change also reverses horizontal rotation, so reflect JEP's mouse yaw around 180
     * rather than merely adding 180; the mannequin then follows the pointer instead of opposing it.
     */
    @Inject(
            method = "renderVillager",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(DDD)V"),
            require = 0)
    private static void townstead$faceMcaPreviewForward(GuiGraphics graphics, int x, int y,
            double scale, double mouseX, double mouseY, Villager villager, CallbackInfo ci) {
        if (!(villager instanceof VillagerEntityMCA)) return;
        villager.yBodyRot = 180.0f - villager.yBodyRot;
        villager.setYRot(180.0f - villager.getYRot());
        villager.yHeadRot = villager.getYRot();
        villager.yHeadRotO = villager.getYRot();
    }

}
