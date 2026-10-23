package com.aetherianartificer.townstead.switchboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A named set of world setting values. Any setting a preset leaves out means Townstead's default, so a
 * preset gives the same result on every server whatever its config file says. Stored as JSON, and shared
 * as a compact {@code TS1:} code.
 */
public record SwitchboardPreset(String name, String description, Map<String, JsonElement> values) {
    public static final String SCHEMA = "townstead:switchboard_preset/v1";
    public static final String CODE_PREFIX = "TS1:";
    private static final int MAX_BYTES = 1 << 20;

    public SwitchboardPreset {
        name = name == null ? "" : name;
        description = description == null ? "" : description;
        values = Map.copyOf(values);
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("schema", SCHEMA);
        root.addProperty("name", name);
        if (!description.isEmpty()) root.addProperty("description", description);
        JsonObject object = new JsonObject();
        values.forEach(object::add);
        root.add("values", object);
        return root;
    }

    public static SwitchboardPreset fromJson(JsonObject root, String fallbackName) {
        if (!root.has("values") || !root.get("values").isJsonObject()) {
            throw new IllegalArgumentException("not a Townstead preset");
        }
        Map<String, JsonElement> values = new LinkedHashMap<>();
        root.getAsJsonObject("values").entrySet().forEach(e -> values.put(e.getKey(), e.getValue()));
        String name = root.has("name") ? root.get("name").getAsString() : fallbackName;
        String description = root.has("description") ? root.get("description").getAsString() : "";
        return new SwitchboardPreset(name, description, values);
    }

    public String toShareCode() {
        byte[] raw = toJson().toString().getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer));
        deflater.end();
        return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /** Reads a share code or preset JSON. Throws {@link IllegalArgumentException} when it is neither. */
    public static SwitchboardPreset parse(String text, String fallbackName) {
        String trimmed = text == null ? "" : text.trim();
        String json = trimmed.startsWith(CODE_PREFIX) ? inflate(trimmed.substring(CODE_PREFIX.length())) : trimmed;
        try {
            return fromJson(JsonParser.parseString(json).getAsJsonObject(), fallbackName);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("not a Townstead preset", e);
        }
    }

    private static String inflate(String code) {
        byte[] compressed;
        try {
            compressed = Base64.getUrlDecoder().decode(code.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("broken share code", e);
        }
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(buffer);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                out.write(buffer, 0, n);
                if (out.size() > MAX_BYTES) throw new IllegalArgumentException("share code too large");
            }
        } catch (DataFormatException e) {
            throw new IllegalArgumentException("broken share code", e);
        } finally {
            inflater.end();
        }
        return out.toString(StandardCharsets.UTF_8);
    }
}
