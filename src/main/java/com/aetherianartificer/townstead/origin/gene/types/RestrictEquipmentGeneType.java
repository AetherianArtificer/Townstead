package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Prevents the entity from keeping equipment in given slots (a race that can't wear
 * armor, or can't hold a shield). Enforced server-side: restricted items are popped
 * back to the inventory each tick. {@code "slots":"all"} restricts the four armor
 * slots.
 *
 * <p>JSON: {@code { "type":"pheno:restrict_equipment", "slots":["head","chest"] }}</p>
 */
public final class RestrictEquipmentGeneType implements GeneType {

    public static final String KEY = "pheno:restrict_equipment";

    private static final Set<EquipmentSlot> ARMOR =
            EnumSet.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    public record Instance(Set<EquipmentSlot> slots, @Nullable Condition condition) implements GeneInstance {
        public Instance { slots = EnumSet.copyOf(slots); }
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        if (json.has("slots") && json.get("slots").isJsonPrimitive()
                && json.get("slots").getAsString().equalsIgnoreCase("all")) {
            return new Instance(ARMOR, condition);
        }
        EnumSet<EquipmentSlot> slots = EnumSet.noneOf(EquipmentSlot.class);
        JsonArray array = GsonHelper.getAsJsonArray(json, "slots", new JsonArray());
        for (var element : array) {
            EquipmentSlot slot = slotByName(element.getAsString());
            if (slot != null) slots.add(slot);
        }
        if (slots.isEmpty()) return null;
        return new Instance(slots, condition);
    }

    private static EquipmentSlot slotByName(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "head", "helmet" -> EquipmentSlot.HEAD;
            case "chest", "chestplate" -> EquipmentSlot.CHEST;
            case "legs", "leggings" -> EquipmentSlot.LEGS;
            case "feet", "boots" -> EquipmentSlot.FEET;
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            default -> null;
        };
    }
}
