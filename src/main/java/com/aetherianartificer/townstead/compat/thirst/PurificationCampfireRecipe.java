package com.aetherianartificer.townstead.compat.thirst;

import com.aetherianartificer.townstead.Townstead;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * Campfire cooking recipe for LSO canteen purification.
 * Mirrors LSO's PurificationSmeltingRecipe but targets RecipeType.CAMPFIRE_COOKING
 * so skillets and campfires accept canteens.
 */
public class PurificationCampfireRecipe extends CampfireCookingRecipe {

    private static boolean reflectionInitialized;
    private static Class<?> canteenItemClass;
    private static Method getCapacityTagMethod;
    private static Method setCapacityTagMethod;
    private static Method setHydrationEnumTagMethod;
    private static Object hydrationPurified;

    public PurificationCampfireRecipe(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result, float experience, int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        initReflection();
        if (canteenItemClass == null) return false;
        ItemStack stack = input.item();
        if (!canteenItemClass.isInstance(stack.getItem())) return false;
        // Must have water (capacity > 0)
        if (getCapacityTagMethod != null) {
            try {
                int capacity = (int) getCapacityTagMethod.invoke(null, stack);
                return capacity > 0;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public @NotNull ItemStack assemble(SingleRecipeInput input, @NotNull HolderLookup.Provider provider) {
        initReflection();
        ItemStack inputStack = input.item();

        int capacity = 0;
        if (getCapacityTagMethod != null) {
            try { capacity = (int) getCapacityTagMethod.invoke(null, inputStack); } catch (Exception ignored) {}
        }

        // Create result with same item type (preserves large vs regular canteen)
        ItemStack result = new ItemStack(inputStack.getItem());

        // Copy enchantments
        ItemEnchantments enchantments = inputStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!enchantments.isEmpty()) {
            result.set(DataComponents.ENCHANTMENTS, enchantments);
        }

        // Set purified tag + capacity
        if (setHydrationEnumTagMethod != null && hydrationPurified != null) {
            try { setHydrationEnumTagMethod.invoke(null, result, hydrationPurified); } catch (Exception ignored) {}
        }
        if (setCapacityTagMethod != null) {
            try { setCapacityTagMethod.invoke(null, result, capacity); } catch (Exception ignored) {}
        }

        return result;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    @Override
    public @NotNull RecipeType<?> getType() {
        return RecipeType.CAMPFIRE_COOKING;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static synchronized void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            canteenItemClass = Class.forName("sfiomn.legendarysurvivaloverhaul.common.items.drink.CanteenItem");
            Class<?> thirstUtil = Class.forName("sfiomn.legendarysurvivaloverhaul.api.thirst.ThirstUtil");
            Class<?> hydrationEnum = Class.forName("sfiomn.legendarysurvivaloverhaul.api.thirst.HydrationEnum");
            getCapacityTagMethod = thirstUtil.getMethod("getCapacityTag", ItemStack.class);
            setCapacityTagMethod = thirstUtil.getMethod("setCapacityTag", ItemStack.class, int.class);
            setHydrationEnumTagMethod = thirstUtil.getMethod("setHydrationEnumTag", ItemStack.class, hydrationEnum);
            hydrationPurified = Enum.valueOf((Class) hydrationEnum, "PURIFIED");
        } catch (Exception e) {
            Townstead.LOGGER.debug("LSO canteen purification recipe reflection init failed", e);
            canteenItemClass = null;
        }
    }

    public static class Serializer implements RecipeSerializer<PurificationCampfireRecipe> {
        public static final Serializer INSTANCE = new Serializer(100);
        private static final StreamCodec<RegistryFriendlyByteBuf, PurificationCampfireRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, AbstractCookingRecipe::getGroup,
                ByteBufCodecs.fromCodec(CookingBookCategory.CODEC), AbstractCookingRecipe::category,
                Ingredient.CONTENTS_STREAM_CODEC, r -> r.ingredient,
                ItemStack.STREAM_CODEC, r -> r.result,
                ByteBufCodecs.FLOAT, r -> r.experience,
                ByteBufCodecs.VAR_INT, r -> r.cookingTime,
                PurificationCampfireRecipe::new
        );
        private final int defaultCookingTime;

        public Serializer(int cookingTime) {
            this.defaultCookingTime = cookingTime;
        }

        @Override
        public MapCodec<PurificationCampfireRecipe> codec() {
            return RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.STRING.optionalFieldOf("group", "").forGetter(AbstractCookingRecipe::getGroup),
                    CookingBookCategory.CODEC.optionalFieldOf("category", CookingBookCategory.MISC).forGetter(AbstractCookingRecipe::category),
                    Ingredient.CODEC.fieldOf("ingredient").forGetter(r -> r.ingredient),
                    ItemStack.CODEC.fieldOf("result").forGetter(r -> r.result),
                    Codec.FLOAT.optionalFieldOf("experience", 0.0F).forGetter(r -> r.experience),
                    Codec.INT.optionalFieldOf("cookingtime", this.defaultCookingTime).forGetter(r -> r.cookingTime)
            ).apply(instance, PurificationCampfireRecipe::new));
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, PurificationCampfireRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
