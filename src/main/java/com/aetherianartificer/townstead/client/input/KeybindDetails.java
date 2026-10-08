package com.aetherianartificer.townstead.client.input;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a pack has to say about the keybinds the catalogue discovers.
 *
 * <p>Discovery gives us every binding in the game, but a {@code KeyMapping} carries only a name and
 * a category. Both are written to sit in a Controls list, not on a wheel: "Quick Cast Slot 01" says
 * nothing about what is in the slot, and "Iron's Spells 'n Spellbooks - Quick Cast" is a heading no
 * tab strip can hold. This is where a pack fixes that.</p>
 *
 * <p>A RESOURCE pack, not a data pack, and deliberately. The icon a pack most wants to give is one
 * it drew itself, and a texture can only live in {@code assets/}; putting the mapping in
 * {@code data/} would mean shipping two packs to add one picture. It is also purely presentation,
 * so it works in single player and when joining a server that has never heard of it.</p>
 *
 * <pre>
 * assets/&lt;ns&gt;/townstead/keybind/irons_spells.json
 * {
 *   "schema": "townstead:keybind/v1",
 *   "bindings": {
 *     "key.irons_spellbooks.spell_quick_cast_1": {
 *       "icon": "minecraft:blaze_powder",
 *       "name": "Spell 1",
 *       "source": "Iron's Spells"
 *     },
 *     "key.ars_nouveau.qc1": "yourpack:textures/wheel/frostbite.png"
 *   }
 * }
 * </pre>
 *
 * <p>A bare string is shorthand for an icon alone, which is the common case. Icons take either form
 * {@link com.aetherianartificer.townstead.client.gui.common.IconArt} accepts: an item id, or a
 * texture path ending in {@code .png}. Anything a pack leaves out falls back to what the binding
 * itself reports, so a pack only says what it wants to change.</p>
 */
public final class KeybindDetails {

    private KeybindDetails() {}

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/KeybindDetails");
    private static final String DIR = "townstead/keybind";
    private static final String SCHEMA = "townstead:keybind/v1";

    /** Empty strings mean "did not say", never "said nothing". Shared with {@link LiveKeybinds}. */
    public record Detail(String icon, String name, String source) {
        public static final Detail NONE = new Detail("", "", "");
    }

    private static volatile Map<String, Detail> DETAILS = Map.of();

    /** What a pack declared for this binding; every field may be empty. */
    public static Detail of(String keybind) {
        return keybind == null ? Detail.NONE : DETAILS.getOrDefault(keybind, Detail.NONE);
    }

    /** {@code override} when a pack gave one, otherwise what the game reported. */
    public static String pick(String override, String fallback) {
        return override == null || override.isEmpty() ? fallback : override;
    }

    /**
     * Reloads the mapping. Later packs win, which is the resource stack's own rule, so a player can
     * override a pack's choice without editing it.
     */
    public static void reload(ResourceManager manager) {
        Map<String, Detail> merged = new LinkedHashMap<>();
        int files = 0;
        for (Map.Entry<ResourceLocation, Resource> entry
                : manager.listResources(DIR, path -> path.getPath().endsWith(".json")).entrySet()) {
            try (InputStream in = entry.getValue().open();
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) {
                    LOGGER.warn("keybind {} is not an object, skipping", entry.getKey());
                    continue;
                }
                JsonObject json = parsed.getAsJsonObject();
                com.aetherianartificer.townstead.data.TownsteadSchema.validate(json, SCHEMA);
                if (!json.has("bindings") || !json.get("bindings").isJsonObject()) {
                    LOGGER.warn("keybind {} declares no 'bindings' object, skipping", entry.getKey());
                    continue;
                }
                for (Map.Entry<String, JsonElement> pair
                        : json.getAsJsonObject("bindings").entrySet()) {
                    Detail detail = parseDetail(pair.getValue());
                    if (detail != null) merged.put(pair.getKey(), detail);
                }
                files++;
            } catch (Exception ex) {
                // One bad file costs its own bindings, not the whole mapping.
                LOGGER.warn("Failed to read keybind {}: {}", entry.getKey(), ex.getMessage());
            }
        }
        DETAILS = Map.copyOf(merged);
        LOGGER.info("Loaded details for {} keybind(s) from {} file(s)", merged.size(), files);
    }

    private static Detail parseDetail(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return new Detail(element.getAsString(), "", "");
        }
        if (!element.isJsonObject()) return null;
        JsonObject object = element.getAsJsonObject();
        return new Detail(
                GsonHelper.getAsString(object, "icon", ""),
                GsonHelper.getAsString(object, "name", ""),
                GsonHelper.getAsString(object, "source", ""));
    }
}
