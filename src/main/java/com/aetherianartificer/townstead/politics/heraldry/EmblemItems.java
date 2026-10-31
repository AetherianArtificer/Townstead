package com.aetherianartificer.townstead.politics.heraldry;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.BannerBlock;
import java.util.*;

/** Shared vanilla rendering on banners, shields, civic previews and lectern cloth. */
public final class EmblemItems {
    private EmblemItems() {}
    public static List<String> patterns(RegistryAccess access) {
        return access.registryOrThrow(Registries.BANNER_PATTERN).keySet().stream().map(Object::toString).sorted().toList();
    }
    public static boolean valid(RegistryAccess access, EmblemRecipe recipe) {
        var registry = access.registryOrThrow(Registries.BANNER_PATTERN);
        return (recipe.division().isEmpty() || registry.containsKey(ResourceLocation.tryParse(recipe.division())))
                && (recipe.symbol().isEmpty() || registry.containsKey(ResourceLocation.tryParse(recipe.symbol())));
    }
    public static ItemStack banner(RegistryAccess access, EmblemRecipe recipe) {
        ItemStack stack = new ItemStack(BannerBlock.byColor(DyeColor.byId(recipe.field())).asItem());
        decorate(access, stack, recipe); return stack;
    }
    public static ItemStack shield(RegistryAccess access, EmblemRecipe recipe) {
        ItemStack stack = new ItemStack(Items.SHIELD); decorate(access, stack, recipe); return stack;
    }
    public static boolean canDecorate(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() instanceof BannerItem || stack.is(Items.SHIELD));
    }
    /** Copy one item, retaining its custom data while replacing the whole heraldic design. */
    public static ItemStack stampedCopy(RegistryAccess access, ItemStack original, EmblemRecipe recipe) {
        if (!canDecorate(original)) return ItemStack.EMPTY;
        ItemStack result = original.copy();
        result.setCount(1);
        if (original.getItem() instanceof BannerItem) {
            var item = BannerBlock.byColor(DyeColor.byId(recipe.field())).asItem();
            //? if >=1.21 {
            result = original.transmuteCopy(item, 1);
            //?} else {
            /*var tag = result.save(new net.minecraft.nbt.CompoundTag());
            tag.putString("id", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
            result = ItemStack.of(tag);
            *///?}
        }
        decorate(access, result, recipe);
        return result;
    }
    public static void decorate(RegistryAccess access, ItemStack stack, EmblemRecipe recipe) {
        var registry = access.registryOrThrow(Registries.BANNER_PATTERN);
        //? if >=1.21 {
        var builder = new net.minecraft.world.level.block.entity.BannerPatternLayers.Builder();
        String[] ids = {recipe.division(), recipe.symbol()};
        int[] colors = {recipe.divisionColor(), recipe.symbolColor()};
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].isEmpty()) continue;
            var holder = registry.getHolder(net.minecraft.resources.ResourceKey.create(Registries.BANNER_PATTERN, ResourceLocation.tryParse(ids[i])));
            if (holder.isPresent()) builder.add(holder.get(), DyeColor.byId(colors[i]));
        }
        stack.set(net.minecraft.core.component.DataComponents.BANNER_PATTERNS, builder.build());
        if (stack.is(Items.SHIELD)) stack.set(net.minecraft.core.component.DataComponents.BASE_COLOR, DyeColor.byId(recipe.field()));
        //?} else {
        /*net.minecraft.nbt.ListTag layers = new net.minecraft.nbt.ListTag();
        String[] ids = {recipe.division(), recipe.symbol()};
        int[] colors = {recipe.divisionColor(), recipe.symbolColor()};
        for (int i = 0; i < ids.length; i++) {
            if (ids[i].isEmpty()) continue;
            var pattern = registry.get(ResourceLocation.tryParse(ids[i]));
            if (pattern == null) continue;
            var layer = new net.minecraft.nbt.CompoundTag();
            layer.putString("Pattern", pattern.getHashname()); layer.putInt("Color", colors[i]); layers.add(layer);
        }
        var tag = stack.getOrCreateTagElement("BlockEntityTag");
        tag.put("Patterns", layers);
        if (stack.is(Items.SHIELD)) tag.putInt("Base", recipe.field());
        *///?}
    }
}
