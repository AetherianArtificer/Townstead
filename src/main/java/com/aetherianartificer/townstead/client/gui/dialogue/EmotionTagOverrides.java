package com.aetherianartificer.townstead.client.gui.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads Townstead's emotion-tagged dialogue overrides directly from our lang files,
 * bypassing Minecraft's lang system which may not merge cross-mod on Forge 1.20.1.
 * Provides a lookup from translation key → tagged text.
 */
public final class EmotionTagOverrides {
    private static final Logger LOGGER = LoggerFactory.getLogger("TownsteadEmotionTags");
    private static final Map<String, String> OVERRIDES = new HashMap<>();
    private static boolean loaded = false;

    private static final String[] NAMESPACES = {
            "mca_dialogue",
            "mca_dialogue_athletic", "mca_dialogue_confident", "mca_dialogue_witty",
            "mca_dialogue_crabby", "mca_dialogue_flirty", "mca_dialogue_gloomy",
            "mca_dialogue_greedy", "mca_dialogue_grumpy", "mca_dialogue_introverted",
            "mca_dialogue_lazy", "mca_dialogue_odd", "mca_dialogue_peppy",
            "mca_dialogue_playful", "mca_dialogue_relaxed", "mca_dialogue_sensitive",
            "mca_dialogue_shy"
    };

    private EmotionTagOverrides() {}

    /** Ensure overrides are loaded. Safe to call multiple times. */
    public static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        loadAll();
    }

    /**
     * Get the emotion-tagged version of a translation value, or null if no override exists.
     * The key can be a full translation key like "dialogue.hug.success/3" or
     * "confident.dialogue.hug.success/3".
     */
    public static String getTaggedText(String key) {
        ensureLoaded();
        return OVERRIDES.get(key);
    }

    /**
     * Apply emotion tags to already-resolved text by searching our overrides.
     * Checks if any override value (without tags) matches the plain text, and if so
     * returns the tagged version.
     */
    public static String applyTagsToResolvedText(String plainText) {
        ensureLoaded();
        // Direct matching — check if any override's stripped version equals the plain text
        for (Map.Entry<String, String> entry : OVERRIDES.entrySet()) {
            String tagged = entry.getValue();
            if (!tagged.contains("<")) continue;
            String stripped = com.aetherianartificer.townstead.client.gui.dialogue.effect.EffectTagParser
                    .stripTags(tagged);
            if (stripped.equals(plainText)) {
                return tagged;
            }
        }
        return null;
    }

    private static void loadAll() {
        for (String namespace : NAMESPACES) {
            loadNamespace(namespace);
        }
        LOGGER.info("Loaded {} emotion tag overrides", OVERRIDES.size());
    }

    private static void loadNamespace(String namespace) {
        try {
            //? if >=1.21 {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(namespace, "lang/en_us.json");
            //?} else {
            /*ResourceLocation loc = new ResourceLocation(namespace, "lang/en_us.json");
            *///?}
            ResourceManager resourceManager = Minecraft.getInstance().getResourceManager();
            // Get ALL resources (from all packs) for this location — last one wins
            //? if >=1.21 {
            List<Resource> resources = resourceManager.getResourceStack(loc);
            //?} else {
            /*List<Resource> resources = resourceManager.getResourceStack(loc);
            *///?}
            for (Resource resource : resources) {
                try (InputStream is = resource.open();
                     InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                        String value = entry.getValue().getAsString();
                        if (value.contains("<") && value.contains(">")) {
                            OVERRIDES.put(entry.getKey(), value);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Namespace not found or not loadable — skip silently
        }
    }
}
