package com.aetherianartificer.townstead.compat.thirst;

//? if >=1.21 {
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
//?} else {
/*import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
*///?}
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;


/**
 * A campfire boil for ThirstWasTaken2 water bottles: Dirty or Murky in, Clean out, the grade the
 * mod's own furnace and smoker give. The grade lives on the stack, so matching and the result go
 * through the bridge instead of the JSON ingredient.
 */
public final class Twt2WaterPurificationRecipe extends CampfireCookingRecipe {
    //? if >=1.21 {
    public Twt2WaterPurificationRecipe(String group, CookingBookCategory category, Ingredient ingredient,
                                     ItemStack result, float experience, int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
    }
    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item()) && ThirstWasTaken2Bridge.boilable(input.item());
    }
    @Override
    public ItemStack assemble(SingleRecipeInput input, net.minecraft.core.HolderLookup.Provider registries) {
        return clean(super.assemble(input, registries));
    }
    @Override
    public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider registries) {
        return clean(super.getResultItem(registries));
    }
    //?} else {
    /*public Twt2WaterPurificationRecipe(net.minecraft.resources.ResourceLocation id, String group,
                                     CookingBookCategory category, Ingredient ingredient,
                                     ItemStack result, float experience, int cookingTime) {
        super(id, group, category, ingredient, result, experience, cookingTime);
    }
    @Override
    public boolean matches(net.minecraft.world.Container input, Level level) {
        return ingredient.test(input.getItem(0)) && ThirstWasTaken2Bridge.boilable(input.getItem(0));
    }
    @Override
    public ItemStack assemble(net.minecraft.world.Container input, net.minecraft.core.RegistryAccess registries) {
        return clean(super.assemble(input, registries));
    }
    @Override
    public ItemStack getResultItem(net.minecraft.core.RegistryAccess registries) {
        return clean(super.getResultItem(registries));
    }
    *///?}
    private static ItemStack clean(ItemStack fallback) {
        ItemStack bottle = ThirstWasTaken2Bridge.INSTANCE.cleanBottle();
        return bottle.isEmpty() ? fallback : bottle;
    }
    @Override
    public RecipeSerializer<?> getSerializer() { return Serializer.INSTANCE; }

    //? if >=1.21 {
    public static class Serializer implements RecipeSerializer<Twt2WaterPurificationRecipe> {
        public static final Serializer INSTANCE = new Serializer(100);
        private static final StreamCodec<RegistryFriendlyByteBuf, Twt2WaterPurificationRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, AbstractCookingRecipe::getGroup,
                ByteBufCodecs.fromCodec(CookingBookCategory.CODEC), AbstractCookingRecipe::category,
                Ingredient.CONTENTS_STREAM_CODEC, r -> r.ingredient,
                ItemStack.STREAM_CODEC, r -> r.result,
                ByteBufCodecs.FLOAT, r -> r.experience,
                ByteBufCodecs.VAR_INT, r -> r.cookingTime,
                Twt2WaterPurificationRecipe::new
        );
        private final int defaultCookingTime;

        public Serializer(int cookingTime) {
            this.defaultCookingTime = cookingTime;
        }

        @Override
        public MapCodec<Twt2WaterPurificationRecipe> codec() {
            return RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter(AbstractCookingRecipe::getGroup),
                    CookingBookCategory.CODEC.optionalFieldOf("category", CookingBookCategory.MISC).forGetter(AbstractCookingRecipe::category),
                    Ingredient.CODEC.fieldOf("ingredient").forGetter(r -> r.ingredient),
                    ItemStack.CODEC.fieldOf("result").forGetter(r -> r.result),
                    Codec.FLOAT.optionalFieldOf("experience", 0.0F).forGetter(r -> r.experience),
                    Codec.INT.optionalFieldOf("cookingtime", this.defaultCookingTime).forGetter(r -> r.cookingTime)
            ).apply(instance, Twt2WaterPurificationRecipe::new));
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Twt2WaterPurificationRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
    //?} else {
    /*public static class Serializer implements RecipeSerializer<Twt2WaterPurificationRecipe> {
        public static final Serializer INSTANCE = new Serializer(100);
        private final int defaultCookingTime;

        public Serializer(int cookingTime) {
            this.defaultCookingTime = cookingTime;
        }

        @Override
        public @NotNull Twt2WaterPurificationRecipe fromJson(@NotNull net.minecraft.resources.ResourceLocation id, @NotNull JsonObject json) {
            String group = GsonHelper.getAsString(json, "group", "");
            CookingBookCategory category = CookingBookCategory.CODEC.byName(
                    GsonHelper.getAsString(json, "category", "misc"), CookingBookCategory.MISC);
            Ingredient ingredient = Ingredient.fromJson(json.get("ingredient"));
            ItemStack result = net.minecraftforge.common.crafting.CraftingHelper.getItemStack(
                    GsonHelper.getAsJsonObject(json, "result"), true, true);
            float experience = GsonHelper.getAsFloat(json, "experience", 0.0f);
            int cookingTime = GsonHelper.getAsInt(json, "cookingtime", this.defaultCookingTime);
            return new Twt2WaterPurificationRecipe(id, group, category, ingredient, result, experience, cookingTime);
        }

        @Override
        public @NotNull Twt2WaterPurificationRecipe fromNetwork(@NotNull net.minecraft.resources.ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CookingBookCategory category = buffer.readEnum(CookingBookCategory.class);
            Ingredient ingredient = Ingredient.fromNetwork(buffer);
            ItemStack result = buffer.readItem();
            float experience = buffer.readFloat();
            int cookingTime = buffer.readVarInt();
            return new Twt2WaterPurificationRecipe(id, group, category, ingredient, result, experience, cookingTime);
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull Twt2WaterPurificationRecipe recipe) {
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.category());
            recipe.ingredient.toNetwork(buffer);
            buffer.writeItem(recipe.result);
            buffer.writeFloat(recipe.experience);
            buffer.writeVarInt(recipe.cookingTime);
        }
    }
    *///?}
}
