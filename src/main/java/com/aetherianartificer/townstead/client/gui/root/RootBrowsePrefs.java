package com.aetherianartificer.townstead.client.gui.root;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The root browser's memory: the roots recently looked at (newest first, up to {@link #RECENT})
 * and the ones the player pinned. Root ids come from packs, not worlds, so both carry across
 * worlds. Kept in {@code config/townstead/roots_browser.json}.
 */
public final class RootBrowsePrefs {
    private static final Logger LOGGER = LoggerFactory.getLogger("Townstead/RootBrowsePrefs");
    public static final int RECENT = 10;

    private static final List<String> RECENT_IDS = new ArrayList<>();
    private static final Set<String> PINNED = new LinkedHashSet<>();
    private static boolean loaded;

    private RootBrowsePrefs() {}

    public static synchronized List<String> recent() {
        load();
        return List.copyOf(RECENT_IDS);
    }

    public static synchronized Set<String> pinned() {
        load();
        return Set.copyOf(PINNED);
    }

    public static synchronized boolean isPinned(String id) {
        load();
        return PINNED.contains(id);
    }

    /** Pins or unpins a root. Returns whether it is pinned afterward. */
    public static synchronized boolean togglePin(String id) {
        load();
        boolean pinned = !PINNED.remove(id) && PINNED.add(id);
        save();
        return pinned;
    }

    /** Moves a root to the front of the recent list. */
    public static synchronized void viewed(String id) {
        load();
        RECENT_IDS.remove(id);
        RECENT_IDS.add(0, id);
        while (RECENT_IDS.size() > RECENT) RECENT_IDS.remove(RECENT_IDS.size() - 1);
        save();
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        Path file = file();
        if (!Files.isRegularFile(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return;
            JsonObject obj = parsed.getAsJsonObject();
            readInto(obj.get("recent"), RECENT_IDS);
            List<String> pinned = new ArrayList<>();
            readInto(obj.get("pinned"), pinned);
            PINNED.addAll(pinned);
            while (RECENT_IDS.size() > RECENT) RECENT_IDS.remove(RECENT_IDS.size() - 1);
        } catch (Exception e) {
            LOGGER.warn("Could not read {}: {}", file, e.getMessage());
        }
    }

    private static void readInto(JsonElement array, List<String> out) {
        if (array == null || !array.isJsonArray()) return;
        for (JsonElement id : array.getAsJsonArray()) {
            if (id.isJsonPrimitive() && !out.contains(id.getAsString())) out.add(id.getAsString());
        }
    }

    private static void save() {
        JsonObject obj = new JsonObject();
        JsonArray recent = new JsonArray();
        RECENT_IDS.forEach(recent::add);
        JsonArray pinned = new JsonArray();
        PINNED.forEach(pinned::add);
        obj.add("recent", recent);
        obj.add("pinned", pinned);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(obj.toString());
            }
        } catch (Exception e) {
            LOGGER.warn("Could not save {}: {}", file, e.getMessage());
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("townstead")
                .resolve("roots_browser.json");
    }
}
