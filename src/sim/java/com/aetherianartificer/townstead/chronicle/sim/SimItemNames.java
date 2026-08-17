package com.aetherianartificer.townstead.chronicle.sim;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Real item names offline. In game {@code ChronicleTaps.itemName} asks the item
 * registry, which needs a running Minecraft; here the language files on the
 * classpath answer the same question, so a fabricated feast reads "Mushroom
 * Stew" rather than {@code mushroom_stew}.
 *
 * <p>Falls back to a tidied id when a key is missing, which is honest about not
 * knowing rather than printing a raw path.</p>
 */
public final class SimItemNames {

    private static final Gson GSON = new Gson();
    private static final Map<String, String> LANG = new HashMap<>();
    private static final String[] SOURCES = {
            "/assets/minecraft/lang/en_us.json",
            "/assets/townstead/lang/en_us.json",
    };

    static {
        for (String source : SOURCES) load(source);
    }

    private SimItemNames() {}

    public static String of(ResourceLocation id) {
        // Vanilla keys items as item.<ns>.<path>, but a few (cake, beds) are blocks.
        String path = id.getPath().replace('/', '.');
        String byItem = LANG.get("item." + id.getNamespace() + "." + path);
        if (byItem != null) return byItem;
        String byBlock = LANG.get("block." + id.getNamespace() + "." + path);
        if (byBlock != null) return byBlock;
        return titleCase(id.getPath());
    }

    /** True when the language files were found, so the report can say if names are guesses. */
    public static boolean available() {
        return !LANG.isEmpty();
    }

    private static void load(String resource) {
        try (InputStream stream = SimItemNames.class.getResourceAsStream(resource)) {
            if (stream == null) return;
            JsonObject json = GSON.fromJson(
                    new InputStreamReader(stream, StandardCharsets.UTF_8), JsonObject.class);
            for (String key : json.keySet()) {
                JsonElement value = json.get(key);
                if (value.isJsonPrimitive()) LANG.putIfAbsent(key, value.getAsString());
            }
        } catch (Exception ignored) {
            // No language file on this classpath: names fall back to tidied ids.
        }
    }

    private static String titleCase(String path) {
        StringBuilder out = new StringBuilder(path.length());
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return out.length() == 0 ? path : out.toString();
    }
}
