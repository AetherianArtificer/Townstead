package com.aetherianartificer.townstead.pheno.seek;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * A pack's or server's say over any seek, by id, without a datapack: {@code config/pheno/seeks.json} maps a seek id to
 * the fields to use instead, for example {@code {"malrem:round":{"limit":"all","within":{"place":"townstead:village"}}}}.
 * {@code within} and {@code radius} replace the seek's search place; any other field replaces the same field of the seek.
 * Read again when the file changes.
 */
public final class SeekOverrides {

    private static final Path FILE = Path.of("config", "pheno", "seeks.json");
    private static volatile JsonObject loaded = new JsonObject();
    private static volatile long stamp = -1, checkedAt;

    private SeekOverrides() {}

    /** The seek's own JSON with this file's fields for its id laid over it, or null when the file has none. */
    static @Nullable JsonObject apply(ResourceLocation id, JsonObject seek) {
        JsonObject all = current();
        JsonElement over = all.get(id.toString());
        if (over == null || !over.isJsonObject()) return null;
        JsonObject out = seek.deepCopy();
        for (Map.Entry<String, JsonElement> e : over.getAsJsonObject().entrySet()) {
            if (e.getKey().equals("within") || e.getKey().equals("radius")) {
                JsonObject find = out.has("find") && out.get("find").isJsonObject() ? out.getAsJsonObject("find") : new JsonObject();
                find.remove("radius");
                find.remove("within");
                find.add(e.getKey(), e.getValue().deepCopy());
                out.add("find", find);
            } else {
                out.add(e.getKey(), e.getValue().deepCopy());
            }
        }
        return out;
    }

    private static JsonObject current() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < 5000) return loaded;
        checkedAt = now;
        try {
            long modified = Files.exists(FILE) ? Files.getLastModifiedTime(FILE).toMillis() : 0;
            if (modified != stamp) {
                stamp = modified;
                JsonElement json = modified == 0 ? null : JsonParser.parseString(Files.readString(FILE));
                loaded = json != null && json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
            }
        } catch (Exception e) {
            loaded = new JsonObject();
        }
        return loaded;
    }
}
