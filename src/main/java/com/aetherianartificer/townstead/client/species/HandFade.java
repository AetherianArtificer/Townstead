package com.aetherianartificer.townstead.client.species;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * The first-person hand's share of {@link InvisFade}: the same opacity the body has in third
 * person, applied to whichever arm draws the hand (MCA's, the vanilla player's, or a rig's).
 * A hand pass brackets itself with {@link #begin}/{@link #end} so render-type and colour hooks
 * deep inside it can read the fade without the entity.
 */
public final class HandFade {

    private static float current = 1f;

    private HandFade() {}

    /** This entity's body opacity right now. */
    public static float alpha(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        //? if neoforge {
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
        //?} else {
        /*float partialTick = mc.getFrameTime();
        *///?}
        return InvisFade.alpha(entity, partialTick);
    }

    public static void begin(LivingEntity entity) {
        current = alpha(entity);
    }

    public static void end() {
        current = 1f;
    }

    public static float current() {
        return current;
    }

    /** The translucent counterpart of a hand's render type while it fades; cutout ignores alpha. */
    public static RenderType type(RenderType original, ResourceLocation texture) {
        return current < 1f ? RenderType.entityTranslucent(texture) : original;
    }

    /** An ARGB colour with its alpha scaled by {@code alpha}. */
    public static int color(int argb, float alpha) {
        if (alpha >= 1f) return argb;
        int a = (argb >>> 24) & 0xFF;
        return (Math.round(a * alpha) << 24) | (argb & 0xFFFFFF);
    }

    /** {@code buffer} with every vertex's alpha scaled by the current fade (for draws with no colour argument). */
    public static VertexConsumer wrap(VertexConsumer buffer) {
        return current >= 1f || buffer == null ? buffer : new Faded(buffer, current);
    }

    private static final class Faded implements VertexConsumer {
        private final VertexConsumer target;
        private final float alpha;

        Faded(VertexConsumer target, float alpha) {
            this.target = target;
            this.alpha = alpha;
        }

        //? if neoforge {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            target.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            target.setColor(r, g, b, Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            target.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            target.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            target.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            target.setNormal(x, y, z);
            return this;
        }
        //?} else {
        /*@Override
        public VertexConsumer vertex(double x, double y, double z) {
            target.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            target.color(r, g, b, Math.round(a * alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            target.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            target.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            target.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            target.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            target.endVertex();
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
            target.defaultColor(r, g, b, Math.round(a * alpha));
        }

        @Override
        public void unsetDefaultColor() {
            target.unsetDefaultColor();
        }
        *///?}
    }
}
