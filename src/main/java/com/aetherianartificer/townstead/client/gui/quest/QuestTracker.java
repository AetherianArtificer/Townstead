package com.aetherianartificer.townstead.client.gui.quest;

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
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Townstead's own quest tracker, for sources with no tracker of their own (villager stories,
 * Bountiful, advancements). Holds up to {@link #MAX} ledger keys per world, oldest dropped first,
 * and remembers them in {@code config/townstead/quest_tracker.json}.
 */
public final class QuestTracker {
    private static final Logger LOGGER = LoggerFactory.getLogger("Townstead/QuestTracker");
    public static final int MAX = 3;

    private static final LinkedHashSet<String> KEYS = new LinkedHashSet<>();
    private static String world;

    private QuestTracker() {}

    public static synchronized boolean isTracked(String key) {
        ensureWorld();
        return KEYS.contains(key);
    }

    public static synchronized List<String> keys() {
        ensureWorld();
        return List.copyOf(KEYS);
    }

    /** Tracks or stops tracking a quest. Returns whether it is tracked afterward. */
    public static synchronized boolean toggle(String key) {
        ensureWorld();
        boolean tracked;
        if (KEYS.remove(key)) {
            tracked = false;
        } else {
            KEYS.add(key);
            Iterator<String> oldest = KEYS.iterator();
            while (KEYS.size() > MAX && oldest.hasNext()) {
                oldest.next();
                oldest.remove();
            }
            tracked = true;
        }
        save();
        return tracked;
    }

    public static synchronized void untrack(String key) {
        ensureWorld();
        if (KEYS.remove(key)) save();
    }

    private static void ensureWorld() {
        String current = currentWorld();
        if (current == null || current.equals(world)) return;
        world = current;
        KEYS.clear();
        JsonObject all = read();
        JsonElement saved = all.get(current);
        if (saved != null && saved.isJsonArray()) {
            for (JsonElement key : saved.getAsJsonArray()) {
                if (KEYS.size() < MAX && key.isJsonPrimitive()) KEYS.add(key.getAsString());
            }
        }
    }

    private static String currentWorld() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        if (mc.getSingleplayerServer() != null) {
            return "world:" + mc.getSingleplayerServer().getWorldData().getLevelName();
        }
        return mc.getCurrentServer() != null ? "server:" + mc.getCurrentServer().ip : null;
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("townstead")
                .resolve("quest_tracker.json");
    }

    private static JsonObject read() {
        Path file = file();
        if (!Files.isRegularFile(file)) return new JsonObject();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            LOGGER.warn("Could not read {}: {}", file, e.getMessage());
            return new JsonObject();
        }
    }

    private static void save() {
        if (world == null) return;
        JsonObject all = read();
        JsonArray keys = new JsonArray();
        new ArrayList<>(KEYS).forEach(keys::add);
        if (keys.isEmpty()) all.remove(world);
        else all.add(world, keys);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                writer.write(all.toString());
            }
        } catch (Exception e) {
            LOGGER.warn("Could not save {}: {}", file, e.getMessage());
        }
    }
}
