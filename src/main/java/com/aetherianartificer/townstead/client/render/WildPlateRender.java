package com.aetherianartificer.townstead.client.render;

import com.aetherianartificer.townstead.replace.WildPlateS2CPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * A costumed mob's nameplate: the person's name, coloured by how the mob regards the viewer (red
 * hostile, amber neutral, plain friendly), over a grey line naming what it is.
 */
public final class WildPlateRender {
    private WildPlateRender() {}

    /** MCA's own rule for a villager's name: its setting is on, and the viewer is within its distance. */
    public static boolean visible(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || entity.isInvisibleTo(minecraft.player)) return false;
        net.conczin.mca.Config config = net.conczin.mca.Config.getInstance();
        return config.showNameTags && minecraft.player.distanceToSqr(entity) < config.nameTagDistance * config.nameTagDistance;
    }

    /** The name line, or null when the entity has no plate. */
    public static @Nullable Component name(Entity entity) {
        WildPlateS2CPayload plate = WildPlateS2CPayload.of(entity.getId());
        if (plate == null) return null;
        MutableComponent name = Component.literal(shownName(entity, plate));
        return switch (plate.disposition()) {
            case 2 -> name.withStyle(ChatFormatting.RED);
            case 1 -> name.withStyle(ChatFormatting.GOLD);
            default -> name;
        };
    }

    /**
     * The person's name, unless the mob has taken on one of its own since it was costumed (a name
     * tag). The mob's name is synced entity state, so a rename shows on the next frame.
     */
    public static String shownName(Entity entity, WildPlateS2CPayload plate) {
        // The renderer may hold the person standing in for the mob; the mob is what the world has.
        Minecraft minecraft = Minecraft.getInstance();
        Entity mob = minecraft.level == null ? null : minecraft.level.getEntity(entity.getId());
        String own = mob == null ? null : com.aetherianartificer.townstead.replace.WildCostume.ownName(mob);
        return own == null ? plate.name() : own;
    }

    /** Draws the line under the name: the type of the mob the person stands in for. */
    public static void drawAffiliation(Entity entity, PoseStack pose, MultiBufferSource buffers, int light, float partial) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || entity.isDiscrete()) return;
        Entity mob = minecraft.level.getEntity(entity.getId());
        if (mob == null || mob == entity) return;
        Component line = mob.getType().getDescription().copy().withStyle(ChatFormatting.GRAY);
        pose.pushPose();
        //? if >=1.21 {
        net.minecraft.world.phys.Vec3 at = entity.getAttachments().getNullable(
                net.minecraft.world.entity.EntityAttachment.NAME_TAG, 0, entity.getViewYRot(partial));
        if (at == null) {
            pose.popPose();
            return;
        }
        pose.translate(at.x, at.y + 0.5, at.z);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(0.025f, -0.025f, 0.025f);
        //?} else {
        /*pose.translate(0.0, entity.getNameTagOffsetY(), 0.0);
        pose.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025f, -0.025f, 0.025f);
        *///?}
        Font font = minecraft.font;
        float x = -font.width(line) / 2f;
        int background = (int) (minecraft.options.getBackgroundOpacity(0.25f) * 255f) << 24;
        font.drawInBatch(line, x, 10, 0x20FFFFFF, false, pose.last().pose(), buffers, Font.DisplayMode.SEE_THROUGH, background, light);
        font.drawInBatch(line, x, 10, 0xFFFFFFFF, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}
