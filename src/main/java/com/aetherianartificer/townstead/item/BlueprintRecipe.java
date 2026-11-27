package com.aetherianartificer.townstead.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
//? if >=1.21 {
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
//?} else {
/*import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
*///?}
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A shaped recipe for an MCA blueprint that stamps a {@link BlueprintStyle}: the recipe's surface
 * plus whichever item from {@code townstead:blueprint_ink} went in. Not special, so the recipe book
 * and recipe viewers list it like any shaped recipe.
 */
public class BlueprintRecipe extends ShapedRecipe {
    private static final TagKey<Item> INK = TagKey.create(Registries.ITEM,
            //? if >=1.21 {
            ResourceLocation.parse("townstead:blueprint_ink"));
            //?} else {
            /*new ResourceLocation("townstead:blueprint_ink"));
            *///?}

    private final ResourceLocation surface;
    private ItemStack display;

    //? if >=1.21 {
    public BlueprintRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, boolean showNotification, ResourceLocation surface) {
        super(group, category, pattern, result, showNotification);
        this.surface = surface;
    }

    private BlueprintRecipe(ShapedRecipe base, ResourceLocation surface) {
        this(base.getGroup(), base.category(), base.pattern, base.getResultItem(null), base.showNotification(), surface);
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingInput input, @NotNull HolderLookup.Provider provider) {
        String ink = BlueprintStyle.CHARCOAL;
        for (ItemStack stack : input.items()) {
            if (stack.is(INK)) ink = BlueprintStyle.inkOf(stack);
        }
        return new BlueprintStyle(surface, ink).applyTo(super.assemble(input, provider));
    }

    @Override
    public @NotNull ItemStack getResultItem(@NotNull HolderLookup.Provider provider) {
        if (display == null) display = displayStack(super.getResultItem(provider));
        return display;
    }
    //?} else {
    /*public BlueprintRecipe(ResourceLocation id, String group, CraftingBookCategory category, int width, int height, NonNullList<Ingredient> ingredients, ItemStack result, boolean showNotification, ResourceLocation surface) {
        super(id, group, category, width, height, ingredients, result, showNotification);
        this.surface = surface;
    }

    private BlueprintRecipe(ShapedRecipe base, ResourceLocation surface) {
        this(base.getId(), base.getGroup(), base.category(), base.getWidth(), base.getHeight(), base.getIngredients(), base.getResultItem(null), base.showNotification(), surface);
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull CraftingContainer input, @NotNull RegistryAccess access) {
        String ink = BlueprintStyle.CHARCOAL;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(INK)) ink = BlueprintStyle.inkOf(stack);
        }
        return new BlueprintStyle(surface, ink).applyTo(super.assemble(input, access));
    }

    @Override
    public @NotNull ItemStack getResultItem(@NotNull RegistryAccess access) {
        if (display == null) display = displayStack(super.getResultItem(access));
        return display;
    }
    *///?}

    /** Every ink item, in a fixed order, so a viewer can line each one up with {@link #resultFor}. */
    public static List<ItemStack> inks() {
        List<ItemStack> inks = new ArrayList<>();
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(INK)) inks.add(new ItemStack(holder.value()));
        return inks;
    }

    public static boolean isInk(Ingredient ingredient) {
        ItemStack[] items = ingredient.getItems();
        return items.length > 0 && items[0].is(INK);
    }

    /** The blueprint this recipe makes with {@code ink}. */
    public ItemStack resultFor(ItemStack ink) {
        return new BlueprintStyle(surface, BlueprintStyle.inkOf(ink)).applyTo(new ItemStack(BlueprintStyle.blueprintItem()));
    }

    /** The ingredients as a 3x3 grid, top-left aligned, for viewers. */
    public List<Ingredient> grid() {
        List<Ingredient> grid = new ArrayList<>(Collections.nCopies(9, Ingredient.EMPTY));
        List<Ingredient> ingredients = getIngredients();
        for (int i = 0; i < ingredients.size(); i++) {
            grid.set(i / getWidth() * 3 + i % getWidth(), ingredients.get(i));
        }
        return grid;
    }

    /** Shown in the recipe book and viewers: paper keeps MCA's blue, other surfaces show charcoal. */
    private ItemStack displayStack(ItemStack result) {
        String ink = BlueprintStyle.PAPER.equals(surface) ? DyeColor.BLUE.getName() : BlueprintStyle.CHARCOAL;
        return new BlueprintStyle(surface, ink).applyTo(result.copy());
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    //? if >=1.21 {
    public static class Serializer implements RecipeSerializer<BlueprintRecipe> {
        public static final Serializer INSTANCE = new Serializer();
        private static final MapCodec<BlueprintRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                RecipeSerializer.SHAPED_RECIPE.codec().forGetter(r -> r),
                ResourceLocation.CODEC.fieldOf("surface").forGetter(r -> r.surface)
        ).apply(instance, BlueprintRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, BlueprintRecipe> STREAM_CODEC = StreamCodec.composite(
                ShapedRecipe.Serializer.STREAM_CODEC, r -> r,
                ResourceLocation.STREAM_CODEC, r -> r.surface,
                BlueprintRecipe::new
        );

        @Override
        public @NotNull MapCodec<BlueprintRecipe> codec() {
            return CODEC;
        }

        @Override
        public @NotNull StreamCodec<RegistryFriendlyByteBuf, BlueprintRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
    //?} else {
    /*public static class Serializer implements RecipeSerializer<BlueprintRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public @NotNull BlueprintRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            return new BlueprintRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json),
                    new ResourceLocation(GsonHelper.getAsString(json, "surface")));
        }

        @Override
        public @NotNull BlueprintRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            ShapedRecipe base = RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buffer);
            return new BlueprintRecipe(base, buffer.readResourceLocation());
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull BlueprintRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
            buffer.writeResourceLocation(recipe.surface);
        }
    }
    *///?}
}
