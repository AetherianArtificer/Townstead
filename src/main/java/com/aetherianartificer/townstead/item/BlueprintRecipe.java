package com.aetherianartificer.townstead.item;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
//? if >=1.21 {
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.CraftingInput;
//?} else {
/*import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.inventory.CraftingContainer;
*///?}
import org.jetbrains.annotations.NotNull;

/**
 * A shapeless recipe for an MCA blueprint that stamps a {@link BlueprintStyle}: the recipe's surface
 * plus whichever item from {@code townstead:blueprint_ink} went in. Not special, so the recipe book
 * and recipe viewers list it like any shapeless recipe.
 */
public class BlueprintRecipe extends ShapelessRecipe {
    private static final TagKey<Item> INK = TagKey.create(Registries.ITEM,
            //? if >=1.21 {
            ResourceLocation.parse("townstead:blueprint_ink"));
            //?} else {
            /*new ResourceLocation("townstead:blueprint_ink"));
            *///?}

    private final ResourceLocation surface;
    private ItemStack display;

    //? if >=1.21 {
    public BlueprintRecipe(String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients, ResourceLocation surface) {
        super(group, category, result, ingredients);
        this.surface = surface;
    }

    private BlueprintRecipe(ShapelessRecipe base, ResourceLocation surface) {
        this(base.getGroup(), base.category(), base.getResultItem(null), base.getIngredients(), surface);
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
    /*public BlueprintRecipe(ResourceLocation id, String group, CraftingBookCategory category, ItemStack result, NonNullList<Ingredient> ingredients, ResourceLocation surface) {
        super(id, group, category, result, ingredients);
        this.surface = surface;
    }

    private BlueprintRecipe(ShapelessRecipe base, ResourceLocation surface) {
        this(base.getId(), base.getGroup(), base.category(), base.getResultItem(null), base.getIngredients(), surface);
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
                RecipeSerializer.SHAPELESS_RECIPE.codec().forGetter(r -> r),
                ResourceLocation.CODEC.fieldOf("surface").forGetter(r -> r.surface)
        ).apply(instance, BlueprintRecipe::new));
        private static final StreamCodec<RegistryFriendlyByteBuf, BlueprintRecipe> STREAM_CODEC = StreamCodec.composite(
                ShapelessRecipe.Serializer.STREAM_CODEC, r -> r,
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
            return new BlueprintRecipe(RecipeSerializer.SHAPELESS_RECIPE.fromJson(id, json),
                    new ResourceLocation(GsonHelper.getAsString(json, "surface")));
        }

        @Override
        public @NotNull BlueprintRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            ShapelessRecipe base = RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id, buffer);
            return new BlueprintRecipe(base, buffer.readResourceLocation());
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull BlueprintRecipe recipe) {
            RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buffer, recipe);
            buffer.writeResourceLocation(recipe.surface);
        }
    }
    *///?}
}
