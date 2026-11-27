package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.attachment.AttachmentClient;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.combat.ProjectileLooks;
import com.aetherianartificer.townstead.pheno.particle.DataParticleDef;
import com.aetherianartificer.townstead.pheno.particle.DataParticles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A shot marked with a particle look is drawn as that particle's sprite, facing the camera, instead of its item. */
@Mixin(ThrownItemRenderer.class)
public abstract class ThrownItemLookMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void townstead$drawLook(Entity entity, float yaw, float partialTick, PoseStack pose,
                                    MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (!(entity instanceof ItemSupplier supplier)) return;
        String look = ProjectileLooks.lookOf(supplier.getItem());
        if (look.isEmpty()) return;
        ResourceLocation id = ResourceLocation.tryParse(look);
        DataParticleDef def = id == null ? null : DataParticles.get(id);
        if (def == null) return;
        ResourceLocation synced = AttachmentClient.namedTexture(def.texture());
        ResourceLocation texture = synced != null ? synced : DataPackLang.parseId(def.texture());
        if (texture == null) return;
        ci.cancel();
        float size = (def.sizeMin() + def.sizeMax()) / 2f;
        int rgb = def.colors().length > 0 ? def.colors()[0] : 0xFFFFFF;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int a = Math.round(def.alpha() * 255f);
        float v1 = 1f / Math.max(1, def.frames());
        int lit = def.glow() ? 0xF000F0 : light;
        pose.pushPose();
        pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        VertexConsumer out = buffers.getBuffer(RenderType.entityTranslucent(texture));
        PoseStack.Pose last = pose.last();
        townstead$corner(out, last, -size, -size, 0f, v1, r, g, b, a, lit);
        townstead$corner(out, last, size, -size, 1f, v1, r, g, b, a, lit);
        townstead$corner(out, last, size, size, 1f, 0f, r, g, b, a, lit);
        townstead$corner(out, last, -size, size, 0f, 0f, r, g, b, a, lit);
        pose.popPose();
    }

    private static void townstead$corner(VertexConsumer out, PoseStack.Pose pose, float x, float y, float u, float v,
                               int r, int g, int b, int a, int light) {
        //? if neoforge {
        out.addVertex(pose, x, y, 0f).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, 0f, 1f, 0f);
        //?} else {
        /*out.vertex(pose.pose(), x, y, 0f).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(pose.normal(), 0f, 1f, 0f).endVertex();
        *///?}
    }
}
