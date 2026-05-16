package com.aetherianartificer.townstead.client.animation.emote;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmotecraftEmoteLoader;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmoteReflection;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds the loaded set of Townstead emotes, keyed by {@link ResourceLocation}.
 * Reload is driven by {@link
 * com.aetherianartificer.townstead.client.animation.McaAnimationBridge#onResourcesReloaded()}.
 *
 * <p>Walks the active resource manager for files under {@code
 * assets/<namespace>/townstead_emotes/} ending in {@code .json}, {@code
 * .emotecraft}, or {@code .emote} (Quark-format text animations — Emotecraft's
 * {@code UniversalEmoteSerializer} dispatches to the right reader by filename).
 * Loads each via {@link EmotecraftEmoteLoader}. If Emotecraft isn't installed,
 * every load returns empty and the registry stays empty — no log noise.</p>
 */
public final class EmoteRegistry {
    private static final String SCAN_PATH = "townstead_emotes";
    private static final String EMOTECRAFT_SCAN_PATH = "emotes";
    private static volatile Map<ResourceLocation, ParsedEmote> EMOTES = Map.of();

    private EmoteRegistry() {}

    public static Optional<ParsedEmote> get(ResourceLocation id) {
        return Optional.ofNullable(EMOTES.get(id));
    }

    public static int size() {
        return EMOTES.size();
    }

    public static java.util.Set<ResourceLocation> allIds() {
        return EMOTES.keySet();
    }

    /**
     * Insert a transient {@link ParsedEmote} (one built from an Emotecraft runtime
     * event rather than a resource-pack file). The entry survives until the next
     * resource reload — long enough for the playback to run to completion.
     */
    public static synchronized void putTransient(ParsedEmote emote) {
        if (emote == null) return;
        java.util.HashMap<ResourceLocation, ParsedEmote> next = new java.util.HashMap<>(EMOTES);
        next.put(emote.id(), emote);
        EMOTES = java.util.Map.copyOf(next);
    }

    public static void reload() {
        if (!EmoteReflection.isAvailable()) {
            EMOTES = Map.of();
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        ResourceManager rm = client.getResourceManager();
        if (rm == null) return;

        Map<ResourceLocation, Resource> found = rm.listResources(SCAN_PATH, location -> {
            String path = location.getPath();
            return path.endsWith(".json") || path.endsWith(".emotecraft") || path.endsWith(".emote");
        });

        // Also pick up Emotecraft's built-in emotes at assets/emotecraft/emotes/*,
        // so reactions can reference them by file stem (e.g. emotecraft:waving).
        Map<ResourceLocation, Resource> builtIns = rm.listResources(EMOTECRAFT_SCAN_PATH, location -> {
            if (!"emotecraft".equals(location.getNamespace())) return false;
            String path = location.getPath();
            return path.endsWith(".json") || path.endsWith(".emotecraft") || path.endsWith(".emote");
        });

        com.aetherianartificer.townstead.client.animation.emote.loader.EmoteNameIndex.clear();

        Map<ResourceLocation, ParsedEmote> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : builtIns.entrySet()) {
            loadOne(entry.getKey(), entry.getValue(), loaded);
        }
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            loadOne(entry.getKey(), entry.getValue(), loaded);
        }

        EMOTES = Map.copyOf(loaded);
        if (!loaded.isEmpty()) {
            Townstead.LOGGER.info("[AnimationBridge] emotes source: loaded {} emote(s)", loaded.size());
        }
    }

    private static void loadOne(ResourceLocation file, Resource resource,
            Map<ResourceLocation, ParsedEmote> sink) {
        ResourceLocation baseId = idFromResource(file);
        try (InputStream stream = resource.open()) {
            List<ParsedEmote> parsed = EmotecraftEmoteLoader.load(baseId, stream, file.getPath());
            for (ParsedEmote pe : parsed) {
                sink.put(pe.id(), pe);
            }
        } catch (Exception e) {
            Townstead.LOGGER.debug("[AnimationBridge] emote loader: failed to read {} ({})",
                    file, e.getMessage());
        }
    }

    private static ResourceLocation idFromResource(ResourceLocation file) {
        String path = file.getPath();
        String stripped = path;
        if (stripped.startsWith(SCAN_PATH + "/")) {
            stripped = stripped.substring(SCAN_PATH.length() + 1);
        } else if (stripped.startsWith(EMOTECRAFT_SCAN_PATH + "/")) {
            stripped = stripped.substring(EMOTECRAFT_SCAN_PATH.length() + 1);
        }
        int dot = stripped.lastIndexOf('.');
        if (dot > 0) stripped = stripped.substring(0, dot);
        //? if neoforge {
        return ResourceLocation.fromNamespaceAndPath(file.getNamespace(), stripped);
        //?} else {
        /*return new ResourceLocation(file.getNamespace(), stripped);
        *///?}
    }
}
