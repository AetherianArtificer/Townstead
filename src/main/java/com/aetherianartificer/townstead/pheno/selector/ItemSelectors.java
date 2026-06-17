package com.aetherianartificer.townstead.pheno.selector;

import com.aetherianartificer.townstead.pheno.condition.item.ItemCondition;
import com.aetherianartificer.townstead.pheno.condition.item.ItemConditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses the {@code on} value of an item action into an {@link ItemSelector}: a slot name
 * ({@code held}/{@code offhand}/{@code head}/{@code chest}/{@code legs}/{@code feet}) is that one
 * stack, {@code inventory} (a string, or an object with an item {@code where}) is the holder's
 * matching inventory stacks, an array is the union. Empty stacks are skipped.
 */
public final class ItemSelectors {

    private ItemSelectors() {}

    @Nullable
    public static ItemSelector parse(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String name = element.getAsString();
            if ("inventory".equalsIgnoreCase(name)) return inventory(null);
            EquipmentSlot slot = slot(name);
            return slot == null ? null : slot(slot);
        }
        if (element.isJsonArray()) {
            List<ItemSelector> parts = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                ItemSelector part = parse(child);
                if (part == null) return null;
                parts.add(part);
            }
            return holder -> {
                List<ItemStack> out = new ArrayList<>();
                for (ItemSelector part : parts) out.addAll(part.select(holder));
                return out;
            };
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("slot")) {
                EquipmentSlot slot = slot(GsonHelper.getAsString(obj, "slot", ""));
                return slot == null ? null : slot(slot);
            }
            ItemCondition where = null;
            if (obj.has("where")) {
                where = ItemConditions.parse(obj.get("where"));
                if (where == null) return null;
            }
            return inventory(where);
        }
        return null;
    }

    private static ItemSelector slot(EquipmentSlot slot) {
        return holder -> {
            ItemStack stack = holder.getItemBySlot(slot);
            return stack.isEmpty() ? List.of() : List.of(stack);
        };
    }

    private static ItemSelector inventory(@Nullable ItemCondition where) {
        return holder -> {
            if (!(holder instanceof Player player)) return List.of();
            List<ItemStack> out = new ArrayList<>();
            var inventory = player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack stack = inventory.getItem(i);
                if (stack.isEmpty()) continue;
                if (where == null || where.test(holder.level(), stack)) out.add(stack);
            }
            return out;
        };
    }

    @Nullable
    private static EquipmentSlot slot(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "held", "main_hand", "mainhand", "hand" -> EquipmentSlot.MAINHAND;
            case "off_hand", "offhand" -> EquipmentSlot.OFFHAND;
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            default -> null;
        };
    }
}
