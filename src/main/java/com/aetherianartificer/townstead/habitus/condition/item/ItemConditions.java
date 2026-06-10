package com.aetherianartificer.townstead.habitus.condition.item;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses an item-condition JSON into an {@link ItemCondition}. Parity-clean subset:
 * {@code amount}, {@code empty}, {@code durability}, {@code relative_durability},
 * {@code is_damageable}, {@code enchantable}, {@code ingredient} (item id or tag), plus
 * the version-guarded {@code enchantment} (alias {@code base_enchantment}), {@code food},
 * {@code fireproof}; with
 * {@code and}/{@code or}/{@code constant}. {@code "inverted":true} negates.
 * ({@code smeltable}/{@code fuel} need the divergent recipe/fuel API, and
 * {@code nbt}/{@code armor_value}/{@code is_equippable}/{@code meat} hit the
 * NBT-vs-components wall, so they are deferred.)
 */
public final class ItemConditions {

    private ItemConditions() {}

    @Nullable
    public static ItemCondition parse(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        ItemCondition condition = build(stripNamespace(GsonHelper.getAsString(json, "type", "")), json);
        if (condition == null) return null;
        return GsonHelper.getAsBoolean(json, "inverted", false) ? condition.negate() : condition;
    }

    @Nullable
    private static ItemCondition build(String type, JsonObject json) {
        switch (type) {
            case "amount": {
                int min = GsonHelper.getAsInt(json, "min", 1);
                int max = GsonHelper.getAsInt(json, "max", Integer.MAX_VALUE);
                return (level, stack) -> stack.getCount() >= min && stack.getCount() <= max;
            }
            case "empty":
                return (level, stack) -> stack.isEmpty();
            case "is_damageable":
                return (level, stack) -> stack.isDamageableItem();
            case "enchantable":
                return (level, stack) -> stack.isEnchantable();
            case "durability": {
                int min = GsonHelper.getAsInt(json, "min", Integer.MIN_VALUE);
                int max = GsonHelper.getAsInt(json, "max", Integer.MAX_VALUE);
                return (level, stack) -> {
                    int remaining = stack.getMaxDamage() - stack.getDamageValue();
                    return remaining >= min && remaining <= max;
                };
            }
            case "relative_durability": {
                float min = GsonHelper.getAsFloat(json, "min", 0f);
                float max = GsonHelper.getAsFloat(json, "max", 1f);
                return (level, stack) -> {
                    if (stack.getMaxDamage() <= 0) return 1f >= min && 1f <= max;
                    float fraction = (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage();
                    return fraction >= min && fraction <= max;
                };
            }
            case "ingredient": {
                if (json.has("tag")) {
                    ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "tag", ""));
                    if (id == null) return null;
                    TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
                    return (level, stack) -> stack.is(tag);
                }
                ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "item", ""));
                if (id == null) return null;
                Item item = BuiltInRegistries.ITEM.get(id);
                return (level, stack) -> stack.is(item);
            }
            case "food":
                return (level, stack) -> {
                    //? if >=1.21 {
                    return stack.has(net.minecraft.core.component.DataComponents.FOOD);
                    //?} else {
                    /*return stack.isEdible();
                    *///?}
                };
            case "fireproof":
                return (level, stack) -> {
                    //? if >=1.21 {
                    return stack.has(net.minecraft.core.component.DataComponents.FIRE_RESISTANT);
                    //?} else {
                    /*return stack.getItem().isFireResistant();
                    *///?}
                };
            case "enchantment":
            case "base_enchantment": {
                ResourceLocation id = json.has("enchantment")
                        ? DataPackLang.parseId(GsonHelper.getAsString(json, "enchantment", "")) : null;
                int min = GsonHelper.getAsInt(json, "min", 1);
                return (level, stack) -> {
                    if (id == null) return stack.isEnchanted();
                    return enchantmentLevel(stack, id) >= min;
                };
            }
            case "and": {
                List<ItemCondition> all = parseList(json);
                if (all == null) return null;
                return (level, stack) -> all.stream().allMatch(c -> c.test(level, stack));
            }
            case "or": {
                List<ItemCondition> any = parseList(json);
                if (any == null) return null;
                return (level, stack) -> any.stream().anyMatch(c -> c.test(level, stack));
            }
            case "constant": {
                boolean value = GsonHelper.getAsBoolean(json, "value", true);
                return (level, stack) -> value;
            }
            default:
                return null;
        }
    }

    private static int enchantmentLevel(net.minecraft.world.item.ItemStack stack, ResourceLocation id) {
        //? if >=1.21 {
        net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key =
                net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, id);
        for (net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder
                : stack.getEnchantments().keySet()) {
            if (holder.is(key)) return stack.getEnchantments().getLevel(holder);
        }
        return 0;
        //?} else {
        /*net.minecraft.world.item.enchantment.Enchantment enchantment =
                BuiltInRegistries.ENCHANTMENT.get(id);
        if (enchantment == null) return 0;
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
        *///?}
    }

    @Nullable
    private static List<ItemCondition> parseList(JsonObject json) {
        if (!json.has("conditions") || !json.get("conditions").isJsonArray()) return null;
        List<ItemCondition> out = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("conditions")) {
            ItemCondition condition = parse(element);
            if (condition == null) return null;
            out.add(condition);
        }
        return out.isEmpty() ? null : out;
    }

    private static String stripNamespace(String type) {
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1);
    }
}
