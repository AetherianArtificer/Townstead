package com.aetherianartificer.townstead.persona;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * How a Persona takes one kind of gift, from {@code persona.json}'s {@code gifts} list. Items are
 * ids or {@code #tags}; {@code $name} is a variable the world rolled, such as {@code $gift}. An id
 * can require a potion, as in {@code minecraft:potion[potion=minecraft:water]} for a water bottle.
 * <pre>
 * { "items": ["$gift"], "response": "loves", "knot": "gift_loved",
 *   "relationship": { "affection": 5 },
 *   "first": { "knot": "gift_from_home", "relationship": { "affection": 10 } } }
 * </pre>
 * {@code satisfaction} works like an MCA gift's (hearts, mood, saturation); it defaults to 30
 * for loves, 15 for likes and -15 for dislikes. A disliked gift is handed back.
 */
public record PersonaGift(List<String> items, Response response, int satisfaction, Map<String, Float> relationship,
                          @Nullable String knot, @Nullable First first) {

    public enum Response { LOVES, LIKES, DISLIKES }

    /** What happens the first time a player gives this kind of gift, instead of the usual. */
    public record First(@Nullable String knot, Map<String, Float> relationship) {}

    /** Whether the stack is one of these items, with {@code $name} read from {@code vars}. */
    public boolean matches(ItemStack stack, Map<String, Object> vars) {
        for (String raw : items) {
            String entry = raw.startsWith("$") ? String.valueOf(vars.getOrDefault(raw.substring(1), "")) : raw;
            if (entry.isEmpty()) continue;
            if (entry.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
                if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) return true;
            } else {
                String potion = null;
                int open = entry.indexOf('[');
                if (open > 0 && entry.endsWith("]")) {
                    String property = entry.substring(open + 1, entry.length() - 1).trim();
                    if (property.startsWith("potion=")) potion = property.substring("potion=".length()).trim();
                    entry = entry.substring(0, open);
                }
                ResourceLocation id = ResourceLocation.tryParse(entry);
                if (id == null || !id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) continue;
                if (potion == null || potion.equals(potionOf(stack))) return true;
            }
        }
        return false;
    }

    /** The potion id in a potion, splash potion, lingering potion or tipped arrow, or null. */
    static @Nullable String potionOf(ItemStack stack) {
        //? if >=1.21 {
        var contents = stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()) return null;
        return contents.potion().get().unwrapKey().map(key -> key.location().toString()).orElse(null);
        //?} else {
        /*net.minecraft.world.item.alchemy.Potion potion = net.minecraft.world.item.alchemy.PotionUtils.getPotion(stack);
        ResourceLocation id = BuiltInRegistries.POTION.getKey(potion);
        return id == null ? null : id.toString();
        *///?}
    }

    /** Knots this gift plays, to check against the compiled Ink. */
    public List<String> knots() {
        List<String> out = new ArrayList<>();
        if (knot != null) out.add(knot);
        if (first != null && first.knot() != null) out.add(first.knot());
        return out;
    }

    static @Nullable PersonaGift parse(JsonElement element, int index, List<String> issues) {
        String where = "gifts[" + index + "]";
        if (!element.isJsonObject()) {
            issues.add("error: " + where + ": must be an object");
            return null;
        }
        JsonObject json = element.getAsJsonObject();
        List<String> items = new ArrayList<>();
        JsonElement raw = json.get("items");
        if (raw != null && raw.isJsonArray()) raw.getAsJsonArray().forEach(e -> items.add(e.getAsString()));
        else if (raw != null) items.add(raw.getAsString());
        if (items.isEmpty()) {
            issues.add("error: " + where + ": needs \"items\"");
            return null;
        }
        Response response;
        try {
            response = Response.valueOf(json.has("response") ? json.get("response").getAsString().toUpperCase(Locale.ROOT) : "LIKES");
        } catch (IllegalArgumentException e) {
            issues.add("error: " + where + ".response must be \"loves\", \"likes\" or \"dislikes\"");
            return null;
        }
        int fallback = switch (response) {
            case LOVES -> 30;
            case LIKES -> 15;
            case DISLIKES -> -15;
        };
        int satisfaction = json.has("satisfaction") ? json.get("satisfaction").getAsInt() : fallback;
        First first = null;
        if (json.has("first") && json.get("first").isJsonObject()) {
            JsonObject f = json.getAsJsonObject("first");
            first = new First(f.has("knot") ? f.get("knot").getAsString() : null, relationship(f, where + ".first", issues));
        }
        return new PersonaGift(List.copyOf(items), response, satisfaction, relationship(json, where, issues),
                json.has("knot") ? json.get("knot").getAsString() : null, first);
    }

    private static Map<String, Float> relationship(JsonObject json, String where, List<String> issues) {
        Map<String, Float> out = new LinkedHashMap<>();
        if (!json.has("relationship")) return out;
        if (!json.get("relationship").isJsonObject()) {
            issues.add("error: " + where + ".relationship must map qualities to amounts");
            return out;
        }
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("relationship").entrySet()) {
            out.put(e.getKey(), e.getValue().getAsFloat());
        }
        return Map.copyOf(out);
    }
}
