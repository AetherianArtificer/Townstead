package com.aetherianartificer.townstead.switchboard;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The modpack's Switchboard preset, {@code config/townstead/switchboard.json}: default values for
 * every world, keys the pack locks, and whether new worlds open the setup screen.
 *
 * <pre>{
 *   "values": { "needs.hunger.enableVillagerHunger": false },
 *   "locked": [ "needs.hunger.enableVillagerHunger" ],
 *   "showOnNewWorld": true
 * }</pre>
 */
public final class SwitchboardPack {
    private SwitchboardPack() {}

    public static final String FILE = "switchboard.json";

    private static volatile Map<String, JsonElement> values = Map.of();
    private static volatile Set<String> locked = Set.of();
    private static volatile boolean showOnNewWorld = true;

    public static Map<String, JsonElement> values() { return values; }
    public static Set<String> locked() { return locked; }
    public static boolean isLocked(String key) { return locked.contains(key); }
    public static boolean showOnNewWorld() { return showOnNewWorld; }

    public static void reload() {
        Map<String, JsonElement> nextValues = new LinkedHashMap<>();
        Set<String> nextLocked = new LinkedHashSet<>();
        boolean nextShow = true;
        Path file = configDirectory().resolve(Townstead.MOD_ID).resolve(FILE);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("values") && root.get("values").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("values").entrySet()) {
                        if (Switchboard.parse(e.getKey(), e.getValue()) == null) {
                            Townstead.LOGGER.warn("[Switchboard] {}: ignoring {} (unknown setting or invalid value)", FILE, e.getKey());
                            continue;
                        }
                        nextValues.put(e.getKey(), e.getValue());
                    }
                }
                if (root.has("locked") && root.get("locked").isJsonArray()) {
                    for (JsonElement key : root.getAsJsonArray("locked")) {
                        String k = key.getAsString();
                        if (!Switchboard.isKnown(k)) {
                            Townstead.LOGGER.warn("[Switchboard] {}: cannot lock unknown setting {}", FILE, k);
                            continue;
                        }
                        nextLocked.add(k);
                    }
                }
                if (root.has("showOnNewWorld")) nextShow = root.get("showOnNewWorld").getAsBoolean();
            } catch (Exception e) {
                Townstead.LOGGER.error("[Switchboard] Could not read {}; using no modpack defaults", file, e);
                nextValues.clear();
                nextLocked.clear();
                nextShow = true;
            }
        }
        values = Collections.unmodifiableMap(nextValues);
        locked = Collections.unmodifiableSet(nextLocked);
        showOnNewWorld = nextShow;
    }

    private static Path configDirectory() {
        //? if >=1.21 {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        //?} else {
        /*return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
        *///?}
    }
}
