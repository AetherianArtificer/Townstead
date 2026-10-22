package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.root.rig.BodyScale;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Draws a body scale gene's size on 1.20.1, which has no scale attribute for the renderer to read.
 * The pose is scaled around the entity's feet before the model draws and restored after. On 1.21
 * the vanilla scale attribute does this and these hooks are not registered.
 */
public final class BodyScaleRender {

    private static final Set<LivingEntity> SCALED = Collections.newSetFromMap(new WeakHashMap<>());

    private BodyScaleRender() {}

    //? if neoforge {
    public static void onRenderLivingPre(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
    //?} else {
    /*public static void onRenderLivingPre(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
    *///?}
        LivingEntity entity = event.getEntity();
        float scale = BodyScale.of(entity);
        if (scale == 1f) return;
        event.getPoseStack().pushPose();
        event.getPoseStack().scale(scale, scale, scale);
        SCALED.add(entity);
    }

    //? if neoforge {
    public static void onRenderLivingPost(net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?> event) {
    //?} else {
    /*public static void onRenderLivingPost(net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?> event) {
    *///?}
        if (SCALED.remove(event.getEntity())) event.getPoseStack().popPose();
    }
}
