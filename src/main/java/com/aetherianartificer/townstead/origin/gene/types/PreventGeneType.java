package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Blocks a vanilla interaction for the bearer (Apoli's {@code prevent_*} family):
 * {@code death} (cancel the killing blow, leaving 1 health), {@code sleep},
 * {@code entity_collision} (mobs no longer shove the bearer), and {@code item_use}
 * (can't start using matching items, e.g. a carnivore that can't eat bread). An
 * optional {@code condition} gates it; {@code item_use} may name an item {@code tag}
 * and/or an {@code items} id list (omit both to cover every item). Enforced by
 * {@code Prevents} off the relevant events / a collision mixin.
 *
 * <p>JSON: {@code { "type":"pheno:prevent", "what":"item_use",
 * "tag":"minecraft:meat" }}</p>
 */
public final class PreventGeneType implements GeneType {

    public static final String KEY = "pheno:prevent";

    public enum What {
        DEATH("death"), SLEEP("sleep"), ENTITY_COLLISION("entity_collision"), ITEM_USE("item_use");

        private final String key;

        What(String key) { this.key = key; }

        @Nullable
        static What byKey(String raw) {
            if (raw == null) return null;
            String needle = raw.toLowerCase(Locale.ROOT);
            for (What w : values()) if (w.key.equals(needle)) return w;
            return null;
        }
    }

    public record Instance(What what, @Nullable TagKey<Item> tag, Set<ResourceLocation> items,
                           @Nullable Condition condition) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }

        /** Whether this gene's (optional) item filter covers the given stack. */
        public boolean matchesItem(ItemStack stack) {
            if (tag == null && items.isEmpty()) return true;
            if (tag != null && stack.is(tag)) return true;
            return items.contains(itemId(stack));
        }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        What what = What.byKey(GsonHelper.getAsString(json, "what", ""));
        if (what == null) return null;
        TagKey<Item> tag = null;
        if (json.has("tag")) {
            ResourceLocation tagId = DataPackLang.parseId(GsonHelper.getAsString(json, "tag", ""));
            if (tagId != null) tag = TagKey.create(Registries.ITEM, tagId);
        }
        Set<ResourceLocation> items = new HashSet<>();
        if (json.has("items") && json.get("items").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("items");
            for (var el : arr) {
                ResourceLocation id = DataPackLang.parseId(el.getAsString());
                if (id != null) items.add(id);
            }
        }
        Condition condition = json.has("condition") ? Conditions.parse(json.get("condition")) : null;
        return new Instance(what, tag, items, condition);
    }

    private static ResourceLocation itemId(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
    }
}
