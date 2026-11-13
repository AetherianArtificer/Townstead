package com.aetherianartificer.townstead.compat.mess;

import com.aetherianartificer.bitofamess.client.trace.TraceLayer;
import com.aetherianartificer.bitofamess.client.trace.TraceRender;
import com.aetherianartificer.townstead.client.species.RigModels;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/** A body on a custom rig draws through its rig's model, not the one its renderer has, and a part draws itself. Client only. */
public final class MessClientLink {

    @FunctionalInterface
    public interface Draw {
        void draw(PoseStack pose, VertexConsumer buffer, int light, int overlay, int argb);
    }

    private MessClientLink() {}

    /**
     * Opens Bit of a Mess's bathing wear picker for a villager, its gender's tab first; Done hands back the wear and color as
     * {@code wear|color}. Says whether it could.
     */
    public static boolean pickBathingWear(LivingEntity villager, String gender, java.util.function.Consumer<String> picked, net.minecraft.client.gui.screens.Screen back) {
        try {
            com.aetherianartificer.bitofamess.client.ChangingPicker.openFor(villager, gender, choice -> picked.accept(choice.wear() + "|" + choice.color()), back);
            return true;
        } catch (LinkageError older) {
            return false;
        }
    }

    static void init() {
        TraceLayer.setDrawnElsewhere(entity -> RigModels.isAlternate(RigModels.rigBaseFor(entity)));
    }

    /** Whatever has landed on this body, on the model it's being drawn with, faded with the body under it. */
    public static <E extends LivingEntity> void drawOnModel(LivingEntity entity, EntityModel<E> model, PoseStack pose,
                                                            MultiBufferSource buffers, int light, float fade) {
        TraceRender.draw(entity, model, (p, buffer, l, overlay, argb) -> {
            //? if neoforge {
            model.renderToBuffer(p, buffer, l, overlay, argb);
            //?} else {
            /*model.renderToBuffer(p, buffer, l, overlay, 1f, 1f, 1f, ((argb >>> 24) & 0xFF) / 255f);
            *///?}
        }, null, pose, buffers, light, Math.round(fade * 255f), true);
    }

    /** What this body carries, and what has landed on this attachment at this place it fills. */
    public static void drawOnPart(LivingEntity entity, Object shape, String attachment, int anchor, Draw draw, ResourceLocation texture, PoseStack pose,
                                  MultiBufferSource buffers, int light, int alpha) {
        TraceRender.drawPart(entity, shape, MessLink.partKey(attachment, anchor), draw::draw, TraceRender.imageOf(texture), pose, buffers, light, alpha);
    }

    /** A texture of marks to draw over a piece, and its color. Whoever asks for one lets go of it. */
    public record Mark(ResourceLocation id, int argb) {}

    /** What's on this attachment now, to go with a piece that has come off it. */
    public static List<Mark> copyMarks(LivingEntity entity, Object shape, String attachment, int anchor, Draw draw, ResourceLocation texture) {
        List<Mark> out = new ArrayList<>();
        for (TraceRender.Copied copied : TraceRender.copyPart(entity, shape, MessLink.partKey(attachment, anchor), draw::draw, TraceRender.imageOf(texture))) {
            out.add(new Mark(copied.id(), copied.argb()));
        }
        return out;
    }
}
