package com.aetherianartificer.townstead.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * What an MCA blueprint was drawn on and with. Cosmetic only: MCA never reads it. Lives in the
 * stack's custom data under {@code townstead:blueprint}; an MCA-crafted blueprint has none and
 * keeps MCA's look.
 */
public record BlueprintStyle(ResourceLocation surface, String ink) {
    private static final String KEY = "townstead:blueprint";
    public static final String CHARCOAL = "charcoal";
    public static final ResourceLocation MCA_BLUEPRINT = id("mca:blueprint");
    public static final ResourceLocation PAPER = id("townstead:paper");
    private static final Set<ResourceLocation> KNOWN_SURFACES = new ConcurrentSkipListSet<>();

    public static Item blueprintItem() {
        return BuiltInRegistries.ITEM.get(MCA_BLUEPRINT);
    }

    /** Surfaces with a model, filled on the client when models load. */
    public static Set<ResourceLocation> knownSurfaces() {
        return KNOWN_SURFACES;
    }

    /** Ink name for a stack used as ink: its dye color, or charcoal for coal, charcoal and ink sacs. */
    public static String inkOf(ItemStack stack) {
        DyeColor color = DyeColor.getColor(stack);
        return color != null ? color.getName() : CHARCOAL;
    }

    /** MCA's own art already shows blue on paper, so that pair needs no model swap. */
    public boolean matchesMcaArt() {
        return PAPER.equals(surface) && DyeColor.BLUE.getName().equals(ink);
    }

    public int inkColor() {
        DyeColor color = CHARCOAL.equals(ink) ? null : DyeColor.byName(ink, null);
        if (color == null) return 0x3A3A3A;
        //? if >=1.21 {
        return color.getTextureDiffuseColor() & 0xFFFFFF;
        //?} else {
        /*float[] c = color.getTextureDiffuseColors();
        return ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
        *///?}
    }

    public ItemStack applyTo(ItemStack stack) {
        CompoundTag mark = new CompoundTag();
        mark.putString("surface", surface.toString());
        mark.putString("ink", ink);
        //? if >=1.21 {
        CompoundTag root = customData(stack);
        root.put(KEY, mark);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(root));
        //?} else {
        /*stack.getOrCreateTag().put(KEY, mark);
        *///?}
        return stack;
    }

    public static @Nullable BlueprintStyle of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CompoundTag root = customData(stack);
        if (!root.contains(KEY, 10)) return null;
        CompoundTag mark = root.getCompound(KEY);
        ResourceLocation surface = ResourceLocation.tryParse(mark.getString("surface"));
        if (surface == null) return null;
        String ink = mark.getString("ink");
        return new BlueprintStyle(surface, ink.isEmpty() ? CHARCOAL : ink);
    }

    public static void tooltip(ItemStack stack, List<Component> lines) {
        BlueprintStyle style = of(stack);
        if (style == null) return;
        Component surface = Component.translatable("blueprint_surface." + style.surface.getNamespace() + "." + style.surface.getPath());
        Component ink = CHARCOAL.equals(style.ink)
                ? Component.translatable("townstead.blueprint.ink.charcoal")
                : Component.translatable("townstead.blueprint.ink.dye", Component.translatable("color.minecraft." + style.ink));
        lines.add(Component.translatable("townstead.blueprint.style", surface, ink).withStyle(ChatFormatting.GRAY));
    }

    private static CompoundTag customData(ItemStack stack) {
        //? if >=1.21 {
        net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? new CompoundTag() : data.copyTag();
        //?} else {
        /*return stack.getTag() == null ? new CompoundTag() : stack.getTag().copy();
        *///?}
    }

    private static ResourceLocation id(String name) {
        //? if >=1.21 {
        return ResourceLocation.parse(name);
        //?} else {
        /*return new ResourceLocation(name);
        *///?}
    }
}
