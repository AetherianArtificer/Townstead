package com.aetherianartificer.townstead.root.attachment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What an attachment is to the world rather than to the eye. {@code values} feed its morph and
 * visibility channels from live state, so a sac can swell as a meter fills. {@code volume} is the
 * space it takes up. {@code part} says how it can be hit, wounded, and lost: every attachment can be
 * unless its block changes that, and {@code "part": false} leaves it nothing but a place to hit the bearer.
 *
 * <p>JSON: {@code "values": { "fill": { "resource":"examplepack:charge", "from":0.7, "to":2.0 } },
 * "volume": { "mode":"block", "boxes":[[-8,10,2,8,26,20]], "pivot":[0,18,2], "grow":"fill" },
 * "part": { "health":20, "severable":true, "regrow_ticks":48000, "severed":"examplepack:stump" }}.
 * A part's hit boxes come from its model as drawn, so {@code volume} is only about movement.</p>
 */
public record AttachmentBody(Map<String, ValueSource> values, @Nullable Volume volume, @Nullable Part part) {

    public static final AttachmentBody NONE = new AttachmentBody(Map.of(), null, null);

    /** A channel read from live state: the source's fill from 0 to 1 maps onto {@code from}..{@code to}. */
    public record ValueSource(String key, float from, float to) {
        public float map(float fill) {
            return from + (to - from) * fill;
        }
    }

    /**
     * Space the attachment occupies, in model pixels from the bearer's feet at its resting pose, with
     * +z behind the body. {@code mode} {@code block} adds it to what stops the bearer moving,
     * {@code fold} means it folds away and costs the bearer flight when it doesn't fit, and
     * {@code none} takes no part in movement (hit boxes only). {@code grow} names a channel that
     * scales the boxes about {@code pivot} by {@code axes}, as a morph channel does.
     */
    public record Volume(String mode, List<float[]> boxes, float[] pivot, String grow, float[] axes) {
        public static final String BLOCK = "block", FOLD = "fold", NONE = "none";
    }

    /**
     * A part that can be hit. {@code health} is its own pool, separate from its bearer's. It is wounded
     * at {@code woundedAt} of that pool and lost at zero if {@code severable}; {@code severTags} name
     * the damage that can cut it (empty means anything can), and other damage stops at wounded.
     * {@code wounded} and {@code severed} are attachments worn in its place. {@code damageScale} is the
     * share of a hit that reaches the bearer. {@code regrowTicks} is how long a lost part takes to come
     * back (0 never), and {@code healPerSecond} refills the pool. {@code trace} is left where it was
     * hurt. The {@code on*} fields are actions, as JSON, run on the bearer. {@code drop} is an item that
     * falls when the part is lost: a passing prop, unless {@code keepDrop} makes it a real item.
     * {@code sharesWith} names another attachment that's the same part in another pose (folded and spread
     * wings): that one's rules and health apply here, and everything else in this block is ignored.
     * {@code stumpTrace} is left on the bearer when the part comes off with nothing worn in its place.
     * {@code remainsSeconds} is how long a piece that came off lies on the ground before it fades.
     * {@code split} lets each branch of its model come off on its own (each wing, each tusk).
     * {@code bleeds} is the material a blow to this part brings out, whatever its bearer bleeds.
     * Null leaves it to the body, and empty means it bleeds nothing.
     * {@code hasEntities} is true for a part a pack declared, which gets part entities of its own. Those
     * cost every entity search in the level, so a part nobody declared is found from the hit's path instead.
     */
    public record Part(float health, float woundedAt, boolean severable, List<String> severTags, float damageScale,
                       int regrowTicks, float healPerSecond, String wounded, String severed, String drop, boolean keepDrop,
                       String trace, String onHit, String onWounded, String onSevered, String onHealed, String sharesWith,
                       String stumpTrace, boolean split, boolean hasEntities, int remainsSeconds, @org.jetbrains.annotations.Nullable String bleeds) {
        public static final int INTACT = 0, WOUNDED = 1, LOST = 2;
        public static final String STUMP = "bitofamess:wound";
        /** As long as a dropped item stays. */
        public static final int REMAINS = 300;
        public static final Part DEFAULT = new Part(20f, 0.5f, true, List.of(), 1f, 48000, 0.05f, "", "", "", false,
                "", "", "", "", "", "", STUMP, true, false, REMAINS, null);
    }

    public boolean empty() {
        return values.isEmpty() && volume == null && part == null;
    }

    public static AttachmentBody parse(JsonObject json) {
        Map<String, ValueSource> values = new LinkedHashMap<>();
        if (json.has("values") && json.get("values").isJsonObject()) {
            for (var entry : json.getAsJsonObject("values").entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject source = entry.getValue().getAsJsonObject();
                String resource = GsonHelper.getAsString(source, "resource", "");
                if (resource.isEmpty()) continue;
                values.put(entry.getKey(), new ValueSource(BodyValues.resourceKey(resource),
                        GsonHelper.getAsFloat(source, "from", 0f), GsonHelper.getAsFloat(source, "to", 1f)));
            }
        }
        Volume volume = json.has("volume") && json.get("volume").isJsonObject()
                ? parseVolume(json.getAsJsonObject("volume")) : null;
        Part part = !json.has("part") || GsonHelper.isBooleanValue(json, "part") && GsonHelper.getAsBoolean(json, "part") ? Part.DEFAULT
                : json.get("part").isJsonObject() ? parsePart(json.getAsJsonObject("part")) : null;
        return values.isEmpty() && volume == null && part == null ? NONE : new AttachmentBody(Map.copyOf(values), volume, part);
    }

    @Nullable
    private static Volume parseVolume(JsonObject json) {
        List<float[]> boxes = new ArrayList<>();
        if (json.has("boxes") && json.get("boxes").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("boxes")) {
                if (!element.isJsonArray() || element.getAsJsonArray().size() != 6) continue;
                JsonArray array = element.getAsJsonArray();
                float[] box = new float[6];
                for (int i = 0; i < 6; i++) box[i] = array.get(i).getAsFloat();
                boxes.add(box);
            }
        }
        if (boxes.isEmpty()) return null;
        String mode = GsonHelper.getAsString(json, "mode", Volume.BLOCK);
        if (!List.of(Volume.BLOCK, Volume.FOLD, Volume.NONE).contains(mode)) mode = Volume.BLOCK;
        return new Volume(mode, List.copyOf(boxes), vec(json, "pivot", 0f), GsonHelper.getAsString(json, "grow", ""),
                vec(json, "axes", 1f));
    }

    private static Part parsePart(JsonObject json) {
        List<String> tags = new ArrayList<>();
        if (json.has("sever_tags") && json.get("sever_tags").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("sever_tags")) tags.add(element.getAsString());
        }
        return new Part(GsonHelper.getAsFloat(json, "health", 20f), GsonHelper.getAsFloat(json, "wounded_at", 0.5f),
                GsonHelper.getAsBoolean(json, "severable", true), List.copyOf(tags),
                GsonHelper.getAsFloat(json, "damage_scale", 1f), GsonHelper.getAsInt(json, "regrow_ticks", 48000),
                GsonHelper.getAsFloat(json, "heal_per_second", 0.05f), GsonHelper.getAsString(json, "wounded", ""),
                GsonHelper.getAsString(json, "severed", ""), GsonHelper.getAsString(json, "drop", ""),
                GsonHelper.getAsBoolean(json, "keep_drop", false),
                GsonHelper.getAsString(json, "trace", ""), action(json, "on_hit"), action(json, "on_wounded"),
                action(json, "on_severed"), action(json, "on_healed"), GsonHelper.getAsString(json, "shares_with", ""),
                GsonHelper.getAsString(json, "stump_trace", Part.STUMP), GsonHelper.getAsBoolean(json, "split", true), true,
                GsonHelper.getAsInt(json, "remains_seconds", Part.REMAINS),
                json.has("bleeds") ? json.get("bleeds").getAsString() : null);
    }

    private static String action(JsonObject json, String key) {
        return json.has(key) ? json.get(key).toString() : "";
    }

    private static float[] vec(JsonObject json, String key, float fallback) {
        if (json.has(key) && json.get(key).isJsonArray() && json.getAsJsonArray(key).size() == 3) {
            JsonArray array = json.getAsJsonArray(key);
            return new float[]{array.get(0).getAsFloat(), array.get(1).getAsFloat(), array.get(2).getAsFloat()};
        }
        return new float[]{fallback, fallback, fallback};
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(values.size());
        values.forEach((name, source) -> {
            buf.writeUtf(name);
            buf.writeUtf(source.key());
            buf.writeFloat(source.from());
            buf.writeFloat(source.to());
        });
        buf.writeBoolean(volume != null);
        if (volume != null) {
            buf.writeUtf(volume.mode());
            buf.writeVarInt(volume.boxes().size());
            for (float[] box : volume.boxes()) {
                for (float value : box) buf.writeFloat(value);
            }
            for (float value : volume.pivot()) buf.writeFloat(value);
            buf.writeUtf(volume.grow());
            for (float value : volume.axes()) buf.writeFloat(value);
        }
        buf.writeBoolean(part != null);
        if (part != null) {
            buf.writeFloat(part.health());
            buf.writeFloat(part.woundedAt());
            buf.writeBoolean(part.severable());
            buf.writeVarInt(part.severTags().size());
            for (String tag : part.severTags()) buf.writeUtf(tag);
            buf.writeFloat(part.damageScale());
            buf.writeVarInt(part.regrowTicks());
            buf.writeFloat(part.healPerSecond());
            for (String text : List.of(part.wounded(), part.severed(), part.drop())) buf.writeUtf(text);
            buf.writeBoolean(part.keepDrop());
            for (String text : List.of(part.trace(), part.onHit(), part.onWounded(), part.onSevered(), part.onHealed(),
                    part.sharesWith(), part.stumpTrace())) {
                buf.writeUtf(text);
            }
            buf.writeBoolean(part.split());
            buf.writeBoolean(part.hasEntities());
            buf.writeVarInt(part.remainsSeconds());
            buf.writeBoolean(part.bleeds() != null);
            if (part.bleeds() != null) buf.writeUtf(part.bleeds());
        }
    }

    public static AttachmentBody read(FriendlyByteBuf buf) {
        int valueCount = buf.readVarInt();
        Map<String, ValueSource> values = new LinkedHashMap<>();
        for (int i = 0; i < valueCount; i++) {
            String name = buf.readUtf();
            values.put(name, new ValueSource(buf.readUtf(), buf.readFloat(), buf.readFloat()));
        }
        Volume volume = null;
        if (buf.readBoolean()) {
            String mode = buf.readUtf();
            int boxCount = buf.readVarInt();
            List<float[]> boxes = new ArrayList<>(boxCount);
            for (int i = 0; i < boxCount; i++) {
                float[] box = new float[6];
                for (int j = 0; j < 6; j++) box[j] = buf.readFloat();
                boxes.add(box);
            }
            float[] pivot = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
            String grow = buf.readUtf();
            float[] axes = {buf.readFloat(), buf.readFloat(), buf.readFloat()};
            volume = new Volume(mode, boxes, pivot, grow, axes);
        }
        Part part = null;
        if (buf.readBoolean()) {
            float health = buf.readFloat();
            float woundedAt = buf.readFloat();
            boolean severable = buf.readBoolean();
            int tagCount = buf.readVarInt();
            List<String> tags = new ArrayList<>(tagCount);
            for (int i = 0; i < tagCount; i++) tags.add(buf.readUtf());
            float damageScale = buf.readFloat();
            int regrowTicks = buf.readVarInt();
            float healPerSecond = buf.readFloat();
            String wounded = buf.readUtf(), severed = buf.readUtf(), drop = buf.readUtf();
            boolean keepDrop = buf.readBoolean();
            part = new Part(health, woundedAt, severable, tags, damageScale, regrowTicks, healPerSecond, wounded,
                    severed, drop, keepDrop, buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readUtf(), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readBoolean() ? buf.readUtf() : null);
        }
        if (valueCount == 0 && volume == null && part == null) return NONE;
        return new AttachmentBody(Map.copyOf(values), volume, part);
    }
}
