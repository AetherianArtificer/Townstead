package com.aetherianartificer.townstead.compat.curios;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;

/** Client half of the Curios gate: adds Curios' own render layer to a renderer, when Curios is present. */
public final class CuriosClientCompat {

    private CuriosClientCompat() {}

    /**
     * Curios only wires its render layer onto player renderers. Villagers get the same layer here, so
     * curio renderers (rings, backpacks, capes) draw on them exactly as on a player. No-op without Curios.
     */
    public static <T extends LivingEntity, M extends EntityModel<T>> void addLayer(LivingEntityRenderer<T, M> renderer) {
        if (!CuriosCompat.present()) return;
        Layers.add(renderer);
    }

    /** Whether a mod registered a Curios renderer for this item, so Curios' layer draws it itself. */
    public static boolean hasRenderer(net.minecraft.world.item.ItemStack stack) {
        return CuriosCompat.present() && Layers.hasRenderer(stack);
    }

    /** Typed references to Curios client classes, kept here so they are never resolved without Curios. */
    private static final class Layers {
        static <T extends LivingEntity, M extends EntityModel<T>> void add(LivingEntityRenderer<T, M> renderer) {
            renderer.addLayer(new top.theillusivec4.curios.client.render.CuriosLayer<>(renderer));
        }

        static boolean hasRenderer(net.minecraft.world.item.ItemStack stack) {
            return top.theillusivec4.curios.api.client.CuriosRendererRegistry.getRenderer(stack.getItem()).isPresent();
        }
    }
}
