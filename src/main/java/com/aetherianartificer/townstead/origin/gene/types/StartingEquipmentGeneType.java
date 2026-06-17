package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Grants items once when a player first takes the origin (player-only; villagers
 * carry the gene but don't receive the kit). An item with a {@code slot} is equipped
 * if that slot is free, otherwise added to the inventory.
 *
 * <p>JSON: {@code { "type":"pheno:starting_equipment",
 * "items":[ { "item":"minecraft:trident", "count":1 },
 *           { "item":"minecraft:turtle_helmet", "slot":"head" } ] }}</p>
 */
public final class StartingEquipmentGeneType implements GeneType {

    public static final String KEY = "pheno:starting_equipment";

    public record Entry(ResourceLocation item, int count, @Nullable EquipmentSlot slot) {}

    public record Instance(List<Entry> items) implements GeneInstance {
        public Instance { items = List.copyOf(items); }
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        List<Entry> items = new ArrayList<>();
        for (var element : GsonHelper.getAsJsonArray(json, "items", new JsonArray())) {
            if (!element.isJsonObject()) continue;
            JsonObject entry = element.getAsJsonObject();
            ResourceLocation item = DataPackLang.parseId(GsonHelper.getAsString(entry, "item", ""));
            if (item == null) continue;
            int count = Math.max(1, GsonHelper.getAsInt(entry, "count", 1));
            EquipmentSlot slot = entry.has("slot") ? slotByName(GsonHelper.getAsString(entry, "slot", "")) : null;
            items.add(new Entry(item, count, slot));
        }
        if (items.isEmpty()) return null;
        return new Instance(items);
    }

    private static EquipmentSlot slotByName(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "head" -> EquipmentSlot.HEAD;
            case "chest" -> EquipmentSlot.CHEST;
            case "legs" -> EquipmentSlot.LEGS;
            case "feet" -> EquipmentSlot.FEET;
            case "mainhand" -> EquipmentSlot.MAINHAND;
            case "offhand" -> EquipmentSlot.OFFHAND;
            default -> null;
        };
    }
}
