package com.aetherianartificer.townstead.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Renders a Townstead {@code catalog.node_item} for an MCA building type.
 *
 * <p>The building type is the stable identity shared by MCA and Townstead. Atlas
 * coordinates are deliberately not part of this contract: they differ between
 * MCA releases and Townstead's icon is already declared as an item in the
 * extended-building sidecar.
 */
public final class BuildingIconSwap {
    private BuildingIconSwap() {}

    private static Optional<ItemStack> resolve(String buildingType) {
        Optional<ResourceLocation> itemId = BuildingIconResolver.nodeItemForType(buildingType);
        if (itemId.isEmpty() || !BuiltInRegistries.ITEM.containsKey(itemId.get())) {
            return Optional.empty();
        }
        Item item = BuiltInRegistries.ITEM.get(itemId.get());
        if (item == null) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(item);
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
    }

    private static Optional<ItemStack> resolve(int u, int v) {
        Optional<ResourceLocation> itemId = BuildingIconResolver.nodeItemForIconUv(u, v);
        if (itemId.isEmpty() || !BuiltInRegistries.ITEM.containsKey(itemId.get())) {
            return Optional.empty();
        }
        Item item = BuiltInRegistries.ITEM.get(itemId.get());
        if (item == null) return Optional.empty();
        ItemStack stack = new ItemStack(item);
        return stack.isEmpty() ? Optional.empty() : Optional.of(stack);
    }

    /**
     * Draws the node item at {@code (x, y)}, mirroring MCA's unscaled icon transform.
     */
    public static boolean render(GuiGraphics context, int x, int y, String buildingType) {
        Optional<ItemStack> stack = resolve(buildingType);
        if (stack.isEmpty()) {
            return false;
        }
        context.pose().pushPose();
        context.pose().translate(x - 6.0, y - 6.0, 0.0);
        context.pose().scale(0.75f, 0.75f, 1.0f);
        context.renderItem(stack.get(), 0, 0);
        context.pose().popPose();
        return true;
    }

    /** Legacy MCA draw bridge: the native call supplies only its atlas address. */
    public static boolean render(GuiGraphics context, int x, int y, int u, int v) {
        Optional<ItemStack> stack = resolve(u, v);
        if (stack.isEmpty()) return false;
        context.pose().pushPose();
        context.pose().translate(x - 6.0, y - 6.0, 0.0);
        context.pose().scale(0.75f, 0.75f, 1.0f);
        context.renderItem(stack.get(), 0, 0);
        context.pose().popPose();
        return true;
    }

    /**
     * Scaled variant matching MCA's {@code drawScaledBuildingIcon}: footprint
     * icons carry a per-layer {@code scale} and floating-point map coordinates.
     */
    public static boolean renderScaled(GuiGraphics context, double x, double y,
                                       String buildingType, float scale) {
        Optional<ItemStack> stack = resolve(buildingType);
        if (stack.isEmpty()) {
            return false;
        }
        context.pose().pushPose();
        context.pose().translate(x, y, 0.0);
        context.pose().scale(scale, scale, 1.0f);
        context.pose().translate(-6.0, -6.0, 0.0);
        context.pose().scale(0.75f, 0.75f, 1.0f);
        context.renderItem(stack.get(), 0, 0);
        context.pose().popPose();
        return true;
    }
}
