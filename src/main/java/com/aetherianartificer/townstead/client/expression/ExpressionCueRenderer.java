package com.aetherianartificer.townstead.client.expression;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.expression.ExpressionCue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/** Camera-facing icon/text cues rendered above any entity rig, independent of its model bones. */
public final class ExpressionCueRenderer {
    private ExpressionCueRenderer() {}

    public static void render(Entity entity, PoseStack pose, MultiBufferSource buffers, int packedLight, float partialTick) {
        ExpressionCueClientStore.Active active = ExpressionCueClientStore.get(entity.getId());
        if (active == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity.distanceToSqr(mc.player) > 128d * 128d) return;

        if (active.kind() == ExpressionCue.Kind.ICON) {
            renderIconBurst(entity, active, pose, buffers, packedLight, mc, partialTick);
            return;
        }
        renderTextBurst(entity, active, pose, buffers, packedLight, mc, partialTick);
    }

    private static void renderIconBurst(Entity entity, ExpressionCueClientStore.Active active,
                                        PoseStack pose, MultiBufferSource buffers, int packedLight,
                                        Minecraft mc, float partialTick) {
        float age = active.ageTicks(partialTick);
        int baseSeed = 31 * (31 * entity.getId() + active.cueId().hashCode())
                + Long.hashCode(active.startTick());
        for (int index = 0; index < active.iconCount(); index++) {
            float localAge = age - (float) index * active.iconStaggerTicks();
            if (localAge < 0f || localAge > active.durationTicks()) continue;
            float progress = Math.max(0f, Math.min(1f, localAge / active.durationTicks()));
            float entrance = Math.min(1f, localAge / 6f);
            float fadeIn = entrance * entrance * (3f - 2f * entrance);
            float exit = Math.min(1f, (active.durationTicks() - localAge) / 10f);
            float fadeOut = exit * exit * (3f - 2f * exit);
            int color = fadedColor(active.color(), fadeIn * fadeOut);

            float angle = random01(baseSeed, index, 0) * (float) (Math.PI * 2d);
            float radius = active.iconSpread()
                    * (0.25f + 0.75f * (float) Math.sqrt(random01(baseSeed, index, 1)));
            float outward = radius * (0.25f + 0.75f * progress);
            float swirl = (float) Math.sin(progress * Math.PI * 2d + angle)
                    * active.iconSpread() * 0.12f;
            float x = (float) Math.cos(angle) * outward
                    + (float) Math.cos(angle + Math.PI / 2d) * swirl
                    + active.drift() * progress;
            float z = (float) Math.sin(angle) * outward * 0.65f
                    + (float) Math.sin(angle + Math.PI / 2d) * swirl;
            float startY = (random01(baseSeed, index, 2) - 0.5f) * active.iconVerticalSpread();
            float bob = (float) Math.sin(progress * Math.PI) * 0.025f;
            float variance = (random01(baseSeed, index, 3) * 2f - 1f) * active.iconScaleVariance();
            float pop = 1f - (float) Math.pow(1f - entrance, 3d);
            float scale = active.scale() * (1f + variance) * (0.7f + 0.3f * pop);

            pose.pushPose();
            pose.translate(x, entity.getBbHeight() + 0.5d + startY + active.rise() * progress + bob, z);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            float unit = 0.025f * scale;
            pose.scale(-unit, -unit, unit);
            renderIcon(active.content(), pose, buffers, packedLight, color);
            pose.popPose();
        }
    }

    private static void renderTextBurst(Entity entity, ExpressionCueClientStore.Active active,
                                        PoseStack pose, MultiBufferSource buffers, int packedLight,
                                        Minecraft mc, float partialTick) {
        float age = active.ageTicks(partialTick);
        int baseSeed = 31 * (31 * entity.getId() + active.cueId().hashCode())
                + Long.hashCode(active.startTick());
        for (int index = 0; index < active.textTranslations().size(); index++) {
            float localAge = age - (float) index * active.textStaggerTicks();
            if (localAge < 0f || localAge > active.durationTicks()) continue;
            float progress = Math.max(0f, Math.min(1f, localAge / active.durationTicks()));
            float fadeIn = Math.min(1f, localAge / 5f);
            float fadeOut = Math.min(1f, (1f - progress) * 5f);
            int color = fadedColor(active.color(), fadeIn * fadeOut);

            float angle = random01(baseSeed, index, 4) * (float) (Math.PI * 2d);
            float radius = active.textSpread() * (0.35f + 0.65f * random01(baseSeed, index, 5));
            float x = (float) Math.cos(angle) * radius + active.drift() * progress;
            float z = (float) Math.sin(angle) * radius * 0.5f;
            float startY = (random01(baseSeed, index, 6) - 0.5f) * active.textVerticalSpread();
            float bob = (float) Math.sin(progress * Math.PI + angle) * 0.06f;
            float variance = (random01(baseSeed, index, 7) * 2f - 1f) * active.textScaleVariance();
            float scale = active.scale() * (1f + variance);

            pose.pushPose();
            pose.translate(x, entity.getBbHeight() + 0.55d + startY + active.rise() * progress + bob, z);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            float unit = 0.025f * scale;
            pose.scale(-unit, -unit, unit);
            renderText(active.textTranslations().get(index), pose, buffers, packedLight, color);
            pose.popPose();
        }
    }

    private static int fadedColor(int source, float fade) {
        int sourceAlpha = (source >>> 24) & 0xFF;
        int alpha = Math.max(0, Math.min(255, Math.round(sourceAlpha * fade)));
        return (alpha << 24) | (source & 0x00FFFFFF);
    }

    private static float random01(int seed, int index, int channel) {
        int value = seed ^ (index * 0x9E3779B9) ^ (channel * 0x85EBCA6B);
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return (value & 0x00FFFFFF) / 16777216f;
    }

    private static void renderText(String key, PoseStack pose, MultiBufferSource buffers,
                                   int packedLight, int color) {
        Component text = Component.translatable(key);
        Font font = Minecraft.getInstance().font;
        float x = -font.width(text) / 2f;
        int background = (((color >>> 24) & 0xFF) * 80 / 255) << 24;
        font.drawInBatch(text, x, 0f, color, false, pose.last().pose(), buffers,
                Font.DisplayMode.SEE_THROUGH, background, packedLight);
    }

    private static void renderIcon(String texture, PoseStack pose, MultiBufferSource buffers,
                                   int packedLight, int color) {
        ResourceLocation id = DataPackLang.parseId(texture);
        if (id == null) return;
        // Alpha blending is required for the entrance/exit envelope; cutout snaps at its threshold.
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(id));
        org.joml.Matrix4f matrix = pose.last().pose();
        // Expressions are UI language, not objects in the scene: keep them legible at night and
        // under roofs just as vanilla nameplates are. The billboard is emitted from the raw
        // RenderLivingEvent.Pre frame, before a custom renderer rotates/scales its model.
        int light = LightTexture.FULL_BRIGHT;
        //? if >=1.21 {
        vertices.addVertex(matrix, -8f, 8f, 0f).setColor(color).setUv(0f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 0f, -1f);
        vertices.addVertex(matrix, 8f, 8f, 0f).setColor(color).setUv(1f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 0f, -1f);
        vertices.addVertex(matrix, 8f, -8f, 0f).setColor(color).setUv(1f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 0f, -1f);
        vertices.addVertex(matrix, -8f, -8f, 0f).setColor(color).setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 0f, -1f);
        //?} else {
        /*float a = ((color >>> 24) & 255) / 255f, r = ((color >>> 16) & 255) / 255f;
        float g = ((color >>> 8) & 255) / 255f, b = (color & 255) / 255f;
        vertices.vertex(matrix, -8f, 8f, 0f).color(r,g,b,a).uv(0f,1f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f,0f,-1f).endVertex();
        vertices.vertex(matrix, 8f, 8f, 0f).color(r,g,b,a).uv(1f,1f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f,0f,-1f).endVertex();
        vertices.vertex(matrix, 8f, -8f, 0f).color(r,g,b,a).uv(1f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f,0f,-1f).endVertex();
        vertices.vertex(matrix, -8f, -8f, 0f).color(r,g,b,a).uv(0f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f,0f,-1f).endVertex();
        *///?}
    }
}
