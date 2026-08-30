package com.aetherianartificer.townstead.compat.pizzadelight;

import com.aetherianartificer.townstead.work.OutputAppraisal;
import com.aetherianartificer.townstead.hunger.FoodSafety;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.VillagerConsumptionManager;
import com.aetherianartificer.townstead.needs.Amenities;
import com.aetherianartificer.townstead.profession.career.CareerProgression;
import com.aetherianartificer.townstead.profession.career.Careers;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pizza Delight (Tiviacz1337, mod id {@code pizzadelight}) career glue. NOTE: an unrelated mod
 * by AidanTilanus ships the same mod id; its classes live under {@code com.aidant} so every
 * class-keyed hook here simply never matches it, and item-id lookups miss — soft no-ops.
 *
 * <p>Pizzas carry their ingredient list on the stack (1.20.1: NBT {@code Inventory}; 1.21.1:
 * the {@code pizzadelight:pizza_ingredients} data component), and taste is a pure function of
 * ingredient uniqueness — so quality is APPRAISED from the product, mirroring
 * {@code TasteHandler}: pointer = unique - 9 → DELICIOUS(4) / TASTY(3) / GOOD(2) /
 * DISGUSTING(1).</p>
 */
public final class PizzaDelightCompat {

    public static final String MOD_ID = "pizzadelight";

    private static final Set<String> PIZZA_ITEMS = Set.of(
            "pizzadelight:pizza", "pizzadelight:raw_pizza", "pizzadelight:pizza_slice");

    private PizzaDelightCompat() {}

    /** Static registrations; safe to call unconditionally (matches by item id at runtime). */
    public static void bootstrap() {
        OutputAppraisal.register(PizzaDelightCompat::appraisePizza);
        PizzaDelightStationAdapters.bootstrap();
        Amenities.registerWorldSource(new PlacedPizzaSource());
    }

    /** A cooked pizza in the world is four real, component-preserving meals. */
    private static final class PlacedPizzaSource implements Amenities.WorldSource {
        private static final ResourceLocation ID = Objects.requireNonNull(
                ResourceLocation.tryParse("townstead:pizzadelight_pizza"));
        private static final ResourceLocation PIZZA = Objects.requireNonNull(
                ResourceLocation.tryParse("pizzadelight:pizza"));

        @Override public ResourceLocation id() { return ID; }
        @Override public Set<ResourceLocation> blocks() { return Set.of(PIZZA); }

        @Override
        public boolean available(ServerLevel level, BlockPos pos) {
            return sliceAt(level, pos) != null;
        }

        @Override public boolean feeds(ServerLevel level, BlockPos pos) { return true; }
        @Override public boolean hydrates(ServerLevel level, BlockPos pos) { return false; }

        @Override
        public boolean use(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
            ItemStack slice = sliceAt(level, pos);
            if (slice == null || !FoodSafety.isSafeNutritiousFood(slice, villager)) return false;
            var needs = TownsteadVillagers.get(villager).needs();
            if (needs.hunger() >= HungerData.MAX_HUNGER) return false;
            if (!consumeSlice(level, pos)) return false;
            VillagerConsumptionManager.applyConsumption(villager, villager, slice, needs, pos);
            villager.swing(villager.getDominantHand());
            level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.8F, 0.8F);
            return true;
        }
    }

    /** Ask Pizza Delight for the exact slice represented by this placed pizza. */
    private static ItemStack sliceAt(ServerLevel level, BlockPos pos) {
        try {
            BlockState state = level.getBlockState(pos);
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (blockId == null || !"pizzadelight:pizza".equals(blockId.toString())) return null;
            Object result = state.getBlock().getClass()
                    .getMethod("getPizzaSliceItem", net.minecraft.world.level.Level.class, BlockPos.class)
                    .invoke(state.getBlock(), level, pos);
            return result instanceof ItemStack stack && !stack.isEmpty() ? stack : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Builds Pizza Delight's real dynamic slice from a component-bearing whole pizza item. */
    public static ItemStack sliceFromPizzaItem(ItemStack pizza) {
        if (pizza == null || pizza.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(pizza.getItem());
        if (id == null || !"pizzadelight:pizza".equals(id.toString())) return ItemStack.EMPTY;
        //? if >=1.21 {
        try {
            // 1.21 Pizza Delight stores its ten ingredients in a data component. Keeping this
            // reflective makes Townstead load normally when the optional mod is absent.
            ResourceLocation componentId = ResourceLocation.tryParse("pizzadelight:pizza_ingredients");
            Object componentType = BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentId);
            if (!(componentType instanceof net.minecraft.core.component.DataComponentType<?> type)) {
                return fallbackSlice(pizza);
            }
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object ingredients = pizza.get((net.minecraft.core.component.DataComponentType) type);
            if (ingredients == null) return fallbackSlice(pizza);
            Object list = ingredients.getClass().getMethod("getIngredients").invoke(ingredients);
            Class<?> handlerClass;
            try { handlerClass = Class.forName("net.neoforged.neoforge.items.ItemStackHandler"); }
            catch (ClassNotFoundException absent) {
                handlerClass = Class.forName("net.minecraftforge.items.ItemStackHandler");
            }
            Object handler = handlerClass.getConstructor(net.minecraft.core.NonNullList.class)
                    .newInstance(list);
            java.util.List<?> stacks = (java.util.List<?>) list;
            ItemStack base = stacks.isEmpty() ? ItemStack.EMPTY : ((ItemStack) stacks.get(0)).copy();
            ItemStack sauce = stacks.size() <= 9 ? ItemStack.EMPTY : ((ItemStack) stacks.get(9)).copy();
            Class<?> calculatorClass = Class.forName("com.tiviacz.pizzadelight.common.PizzaCalculator");
            Object calculator = calculatorClass.getConstructor(
                    ItemStack.class, ItemStack.class, handlerClass).newInstance(base, sauce, handler);
            ItemStack slice = new ItemStack(BuiltInRegistries.ITEM.get(
                    ResourceLocation.tryParse("pizzadelight:pizza_slice")));
            Object calculated = calculatorClass.getMethod("getResultSlice", ItemStack.class)
                    .invoke(calculator, slice);
            if (calculated instanceof ItemStack result && !result.isEmpty()) {
                //? if >=1.21 {
                if (pizza.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
                    result.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                            pizza.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
                }
                //?}
                return result;
            }
        } catch (Throwable ignored) {
            // Older Pizza Delight revisions use NBT rather than the component calculator.
        }
        //?}
        return fallbackSlice(pizza);
    }

    private static ItemStack fallbackSlice(ItemStack pizza) {
        // Some revisions attach food directly to the whole item; retain that safe fallback.
        //? if >=1.21 {
        return pizza.get(net.minecraft.core.component.DataComponents.FOOD) != null
                ? pizza.copyWithCount(1) : ItemStack.EMPTY;
        //?} else {
        /*return pizza.getFoodProperties(null) != null ? pizza.copyWithCount(1) : ItemStack.EMPTY;
        *///?}
    }

    /** Mirror PizzaBlock.consumeSlice: advance its slice counter, removing the fourth serving. */
    private static boolean consumeSlice(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        IntegerProperty slices = null;
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty integer && "slices".equals(integer.getName())) {
                slices = integer;
                break;
            }
        }
        if (slices == null) return false;
        int used = state.getValue(slices);
        int max = slices.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(used);
        return used < max
                ? level.setBlock(pos, state.setValue(slices, used + 1), 3)
                : level.removeBlock(pos, false);
    }

    /** Player assembled a pizza at the pizza station (result slot take). */
    public static void onPizzaAssembled(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer sp) || stack.isEmpty()) return;
        OutputAppraisal.Appraisal appraisal = appraisePizza(stack);
        int quality = appraisal != null ? appraisal.quality() : 1;
        CareerProgression.completeWork(sp, Careers.COOK, quality, sp.serverLevel().getGameTime(),
                "townstead:pizza", BuiltInRegistries.ITEM.getKey(stack.getItem()),
                "pizza", quality,
                Map.of("station", "pizza_station",
                        "taste", appraisal != null ? appraisal.label() : "unknown"));
    }

    public static OutputAppraisal.Appraisal appraisePizza(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id == null || !PIZZA_ITEMS.contains(id.toString())) return null;
        int unique = uniqueIngredients(stack);
        if (unique < 0) return null;
        return tasteFromUniqueness(unique);
    }

    /**
     * Whether a raw-pizza stack has actually passed through Pizza Delight's assembly station.
     * Blank bases and prepared pizzas share one item id, so order counting must distinguish them.
     */
    public static boolean isAssembledPizza(ItemStack stack) {
        ResourceLocation id = stack == null || stack.isEmpty()
                ? null : BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && "pizzadelight:raw_pizza".equals(id.toString())
                && uniqueIngredients(stack) >= 1;
    }

    /** Mirrors Pizza Delight's TasteHandler thresholds (size = 9 ingredient slots). */
    static OutputAppraisal.Appraisal tasteFromUniqueness(int unique) {
        int pointer = unique - 9;
        if (pointer == 0) return new OutputAppraisal.Appraisal(4, "delicious");
        if (pointer >= -3 && pointer < 0) return new OutputAppraisal.Appraisal(3, "tasty");
        if (pointer >= -6 && pointer < -3) return new OutputAppraisal.Appraisal(2, "good");
        return new OutputAppraisal.Appraisal(1, "disgusting");
    }

    /** Distinct ingredient item ids saved on the pizza stack, or -1 when unreadable. */
    private static int uniqueIngredients(ItemStack stack) {
        try {
            Set<String> unique = new HashSet<>();
            //? if >=1.21 {
            ResourceLocation componentId = ResourceLocation.parse("pizzadelight:pizza_ingredients");
            var type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(componentId);
            if (type == null) return -1;
            Object ingredients = stack.get(type);
            if (ingredients == null) return -1;
            Object items = ingredients.getClass().getMethod("getIngredients").invoke(ingredients);
            for (Object element : (Iterable<?>) items) {
                if (element instanceof ItemStack ingredient && !ingredient.isEmpty()) {
                    unique.add(BuiltInRegistries.ITEM.getKey(ingredient.getItem()).toString());
                }
            }
            //?} else {
            /*net.minecraft.nbt.CompoundTag tag = stack.getTag();
            if (tag == null || !tag.contains("Inventory")) return -1;
            net.minecraft.nbt.ListTag items = tag.getCompound("Inventory").getList("Items", 10);
            for (int i = 0; i < items.size(); i++) {
                String itemId = items.getCompound(i).getString("id");
                if (!itemId.isBlank() && !"minecraft:air".equals(itemId)) unique.add(itemId);
            }
            *///?}
            return unique.size();
        } catch (Throwable t) {
            return -1;
        }
    }
}
