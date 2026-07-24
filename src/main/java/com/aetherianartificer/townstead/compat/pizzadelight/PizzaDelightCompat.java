package com.aetherianartificer.townstead.compat.pizzadelight;

import com.aetherianartificer.townstead.ai.work.OutputAppraisal;
import com.aetherianartificer.townstead.profession.career.CareerProgression;
import com.aetherianartificer.townstead.profession.career.Careers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Map;
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
