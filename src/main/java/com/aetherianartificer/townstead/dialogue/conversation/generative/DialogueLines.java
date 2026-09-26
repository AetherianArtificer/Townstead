package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Reads {@code dialogue_pool} and {@code dialogue_line} files. A line takes its fields from its pool,
 * then from each group around it, then from itself. A nearer value replaces a farther one, except
 * {@code state} and {@code requires}, which add up.
 */
public final class DialogueLines {
    public static final String LINE_SCHEMA = "townstead:dialogue_line/v1";
    public static final String POOL_SCHEMA = "townstead:dialogue_pool/v1";
    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");
    private static final Set<String> FIELDS = Set.of(DialoguePart.FIELDS);

    private DialogueLines() {}

    /** A pool's own fields, and the weight of its empty part (0 for none). */
    public record Pool(ResourceLocation id, JsonObject fields, double empty) {}

    static Pool parsePool(ResourceLocation id, JsonObject json) {
        JsonObject fields = new JsonObject();
        for (var e : json.entrySet()) {
            String key = e.getKey();
            if (key.equals("schema") || key.equals("mods") || key.equals("empty")) continue;
            if (!FIELDS.contains(key)) throw Json.bad(key, "unknown field");
            fields.add(key, e.getValue());
        }
        return new Pool(id, fields, Json.number(json, "empty", 0, 0, 100));
    }

    /** The pool's empty part, when it has one. Every voice shares it. */
    static Optional<DialoguePart> emptyPart(Pool pool) {
        if (pool.empty() <= 0) return Optional.empty();
        JsonObject fields = pool.fields().deepCopy();
        fields.addProperty("weight", pool.empty());
        return Optional.of(DialoguePart.parse(pool.id(), GenerativeDialogue.COMMON_VOICE, null, null, fields));
    }

    static List<DialoguePart> parseFile(JsonObject json, Map<ResourceLocation, Pool> pools) {
        ResourceLocation pool = Json.requiredId(json, "pool");
        ResourceLocation voice = json.has("voice") ? Json.requiredId(json, "voice") : GenerativeDialogue.COMMON_VOICE;
        Pool definition = pools.get(pool);
        JsonObject base = definition == null ? new JsonObject() : definition.fields().deepCopy();
        String prefix = "dialogue." + voice.getNamespace() + "." + voice.getPath().replace('/', '.') + "."
                + pool.getPath().replace('/', '.') + ".";
        List<DialoguePart> out = new ArrayList<>();
        walk(json, base, pool, voice, prefix, "", true, out);
        return out;
    }

    private static void walk(JsonObject group, JsonObject inherited, ResourceLocation pool, ResourceLocation voice,
                             String prefix, String where, boolean root, List<DialoguePart> out) {
        JsonObject fields = inherited.deepCopy();
        for (var e : group.entrySet()) {
            String key = e.getKey();
            if (key.equals("lines") || key.equals("groups") || key.equals("empty")) continue;
            if (root && (key.equals("schema") || key.equals("pool") || key.equals("voice") || key.equals("mods"))) continue;
            if (!FIELDS.contains(key)) throw Json.bad(where + key, "unknown field");
            merge(fields, key, e.getValue());
        }
        if (group.has("empty")) {
            JsonObject empty = fields.deepCopy();
            empty.addProperty("weight", Json.number(group, "empty", 1, 0.0001, 100));
            out.add(DialoguePart.parse(pool, voice, null, null, empty));
        }
        if (group.has("lines")) {
            for (var e : GsonHelper.getAsJsonObject(group, "lines").entrySet()) {
                String id = e.getKey();
                String at = where + "lines." + id;
                if (!ID.matcher(id).matches()) throw Json.bad(at, "ids use lowercase letters, digits and underscores");
                try {
                    JsonObject merged = fields.deepCopy();
                    String text;
                    if (e.getValue().isJsonPrimitive()) {
                        text = e.getValue().getAsString();
                    } else {
                        JsonObject line = e.getValue().getAsJsonObject();
                        text = GsonHelper.getAsString(line, "text", null);
                        for (var f : line.entrySet()) {
                            if (f.getKey().equals("text")) continue;
                            if (!FIELDS.contains(f.getKey())) throw Json.bad(f.getKey(), "unknown field");
                            merge(merged, f.getKey(), f.getValue());
                        }
                    }
                    out.add(DialoguePart.parse(pool, voice, prefix + id, text, merged));
                } catch (RuntimeException ex) {
                    throw Json.bad(at, ex.getMessage());
                }
            }
        }
        if (group.has("groups")) {
            JsonArray groups = GsonHelper.getAsJsonArray(group, "groups");
            for (int i = 0; i < groups.size(); i++)
                walk(groups.get(i).getAsJsonObject(), fields, pool, voice, prefix, where + "groups[" + i + "].", false, out);
        }
    }

    private static void merge(JsonObject into, String key, JsonElement value) {
        if (key.equals("state") && into.has("state") && value.isJsonObject()) {
            JsonObject state = into.getAsJsonObject("state").deepCopy();
            for (var e : value.getAsJsonObject().entrySet()) state.add(e.getKey(), e.getValue());
            into.add("state", state);
        } else if (key.equals("requires") && into.has("requires")) {
            JsonArray requires = new JsonArray();
            for (String r : Json.strings(into, "requires")) requires.add(r);
            JsonObject holder = new JsonObject();
            holder.add("requires", value);
            for (String r : Json.strings(holder, "requires")) requires.add(r);
            into.add("requires", requires);
        } else {
            // variant and variants are one field in two spellings, so a nearer one replaces both.
            if (key.equals("variant")) into.remove("variants");
            if (key.equals("variants")) into.remove("variant");
            into.add(key, value);
        }
    }
}
