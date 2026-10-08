package com.aetherianartificer.townstead.client.story;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * The speech mark over a villager who has something for you: "?" on gold when a quest is ready to
 * hand back, "!" when they are calling you over. Stories come from the server (see {@code StoryCalls}); every quest-ledger
 * source that knows who gave its quests (MCA: Quests) adds its givers here on the client.
 */
public final class StoryCallMarks {
    private static final byte CALLING = 2, READY = 3;
    /** The sprite for a state in the player's chosen style (Bright, or Ember for dark). */
    public static ResourceLocation sprite(byte state) {
        String name = state == READY ? "ready" : "calling";
        return ResourceLocation.tryParse("townstead:textures/gui/story_mark/"
                + TownsteadConfig.storyMarkStyle().resolved().name().toLowerCase(java.util.Locale.ROOT) + "_" + name + ".png");
    }

    private static final Map<Integer, Byte> STATES = new HashMap<>();
    /** Quest givers from the ledger's sources, by entity id, refreshed once a second. */
    private static final Map<Integer, Byte> GIVERS = new HashMap<>();
    private static final double RANGE = 48;
    private static java.util.List<com.aetherianartificer.townstead.quest.QuestProvider> providers;
    private static long refreshedAt = Long.MIN_VALUE;

    private StoryCallMarks() {}

    public static void set(int entityId, byte state) {
        if (state == 0) STATES.remove(entityId);
        else STATES.put(entityId, state);
    }

    public static void clear() {
        STATES.clear();
        GIVERS.clear();
    }

    private static byte state(Entity entity) {
        byte story = STATES.getOrDefault(entity.getId(), (byte) 0);
        byte giver = GIVERS.getOrDefault(entity.getId(), (byte) 0);
        return (byte) Math.max(story, giver);
    }

    /** Asks each ledger source who gave the player their quests, and finds those people nearby. */
    private static void refreshGivers(Minecraft mc) {
        long now = mc.level.getGameTime();
        if (now - refreshedAt < 20 && now >= refreshedAt) return;
        refreshedAt = now;
        GIVERS.clear();
        if (providers == null) {
            providers = java.util.List.of(new com.aetherianartificer.townstead.client.gui.quest.adapter.McaQuestsProvider(),
                    new com.aetherianartificer.townstead.client.gui.quest.adapter.StoryQuestProvider(),
                    new com.aetherianartificer.townstead.client.gui.quest.adapter.BountifulProvider(),
                    new com.aetherianartificer.townstead.client.gui.quest.adapter.FtbQuestsProvider());
        }
        Map<java.util.UUID, Byte> byUuid = new HashMap<>();
        for (com.aetherianartificer.townstead.quest.QuestProvider provider : providers) {
            try {
                if (!provider.isAvailable()) continue;
                provider.giverMarks().forEach((uuid, state) -> byUuid.merge(uuid, state, (a, b) -> (byte) Math.max(a, b)));
            } catch (Throwable ignored) {
                // A source that cannot answer simply marks no one.
            }
        }
        if (byUuid.isEmpty()) return;
        for (Entity entity : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(RANGE))) {
            Byte state = byUuid.get(entity.getUUID());
            if (state != null) GIVERS.put(entity.getId(), state);
        }
    }

    public static void render(Entity entity, PoseStack pose, MultiBufferSource buffers, float partialTick) {
        if (!TownsteadConfig.showStoryMarks()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || entity.distanceToSqr(mc.player) > RANGE * RANGE) return;
        refreshGivers(mc);
        byte state = state(entity);
        if (state != CALLING && state != READY) return;
        // An expression cue already floats there; the mark waits its turn.
        if (com.aetherianartificer.townstead.client.expression.ExpressionCueClientStore.get(entity.getId()) != null) return;
        if (mc.screen instanceof com.aetherianartificer.townstead.client.gui.dialogue.RpgDialogueScreen screen
                && screen.villagerEntityId() == entity.getId()) return;

        float time = entity.tickCount + partialTick;
        float bob = (float) Math.sin(time * 0.12f) * 0.06f;
        pose.pushPose();
        pose.translate(0d, entity.getBbHeight() + 0.55d + bob, 0d);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float unit = 0.025f * (state == READY ? 1.15f : 1.0f);
        pose.scale(-unit, -unit, unit);
        quad(sprite(state), pose, buffers, 0xFFFFFFFF);
        pose.popPose();
    }

    private static void quad(ResourceLocation texture, PoseStack pose, MultiBufferSource buffers, int color) {
        // The billboard is scaled -1 on X, which mirrors it; the U coordinates are swapped to undo that.
        VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(texture));
        org.joml.Matrix4f matrix = pose.last().pose();
        int light = LightTexture.FULL_BRIGHT;
        //? if >=1.21 {
        vertices.addVertex(matrix, -8f, 8f, 0f).setColor(color).setUv(1f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 0f, -1f);
        vertices.addVertex(matrix, 8f, 8f, 0f).setColor(color).setUv(0f, 1f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 0f, -1f);
        vertices.addVertex(matrix, 8f, -8f, 0f).setColor(color).setUv(0f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 0f, -1f);
        vertices.addVertex(matrix, -8f, -8f, 0f).setColor(color).setUv(1f, 0f)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0f, 0f, -1f);
        //?} else {
        /*float a = ((color >>> 24) & 255) / 255f;
        vertices.vertex(matrix, -8f, 8f, 0f).color(1f,1f,1f,a).uv(1f,1f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f,0f,-1f).endVertex();
        vertices.vertex(matrix, 8f, 8f, 0f).color(1f,1f,1f,a).uv(0f,1f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f,0f,-1f).endVertex();
        vertices.vertex(matrix, 8f, -8f, 0f).color(1f,1f,1f,a).uv(0f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f,0f,-1f).endVertex();
        vertices.vertex(matrix, -8f, -8f, 0f).color(1f,1f,1f,a).uv(1f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(0f,0f,-1f).endVertex();
        *///?}
    }
}
