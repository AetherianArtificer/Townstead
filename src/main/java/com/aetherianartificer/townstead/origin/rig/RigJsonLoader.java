package com.aetherianartificer.townstead.origin.rig;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.origin.Hold;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@link RigDefinition}s from {@code data/<ns>/rig/*.json} (server data pack), so a species'
 * {@code rig.base} resolves to a body model + texture + bone map + armor. Registered as a server
 * reload listener; the definitions are synced to clients with the origin catalog.
 */
public final class RigJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/RigJsonLoader");
    private static final Gson GSON = new Gson();

    public RigJsonLoader() {
        super(GSON, "rig");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, RigDefinition> parsed = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            String id = file.getNamespace() + ":" + file.getPath();
            try {
                parsed.put(id, parse(id, GsonHelper.convertToJsonObject(entry.getValue(), file.toString())));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse rig {}: {}", file, ex.getMessage());
            }
        }
        RigRegistry.replaceAll(parsed);
        LOGGER.info("Loaded {} rigs", parsed.size());
    }

    private static RigDefinition parse(String id, JsonObject obj) {
        TownsteadSchema.validate(obj, "townstead:rig/v1");
        JsonObject model = GsonHelper.getAsJsonObject(obj, "model");
        boolean geometry = "geometry".equals(GsonHelper.getAsString(model, "type", "entity_layer"));
        RigDefinition.ModelType modelType = geometry
                ? RigDefinition.ModelType.GEOMETRY : RigDefinition.ModelType.ENTITY_LAYER;
        // For an entity layer the reference is "ns:path#layer" (default layer "main"); for geometry
        // it is the file path, kept whole in modelRef for the (later) geometry loader.
        String ref = geometry
                ? GsonHelper.getAsString(model, "file", "")
                : GsonHelper.getAsString(model, "layer", "");
        String modelRef = ref;
        String modelLayer = "main";
        int hash = ref.indexOf('#');
        if (!geometry && hash >= 0) {
            modelRef = ref.substring(0, hash);
            modelLayer = ref.substring(hash + 1);
        }

        String texture = GsonHelper.getAsString(obj, "texture", "");

        Map<String, String> bones = new LinkedHashMap<>();
        if (obj.has("bones") && obj.get("bones").isJsonObject()) {
            for (Map.Entry<String, JsonElement> b : obj.getAsJsonObject("bones").entrySet()) {
                bones.put(b.getKey(), b.getValue().getAsString());
            }
        }

        RigDefinition.ArmorType armorType = RigDefinition.ArmorType.NONE;
        String inner = null;
        String outer = null;
        if (obj.has("armor") && obj.get("armor").isJsonObject()) {
            JsonObject armor = obj.getAsJsonObject("armor");
            armorType = switch (GsonHelper.getAsString(armor, "type", "none")) {
                case "layers" -> RigDefinition.ArmorType.LAYERS;
                case "custom" -> RigDefinition.ArmorType.CUSTOM;
                default -> RigDefinition.ArmorType.NONE;
            };
            inner = armor.has("inner") ? GsonHelper.getAsString(armor, "inner") : null;
            outer = armor.has("outer") ? GsonHelper.getAsString(armor, "outer") : null;
        }

        RigDefinition.Face face = null;
        if (obj.has("face") && obj.get("face").isJsonObject()) {
            JsonObject f = obj.getAsJsonObject("face");
            face = new RigDefinition.Face(
                    GsonHelper.getAsString(f, "bone", "head"),
                    vec(f, "center", 3, new float[]{0f, -4f, -4f}),
                    vec(f, "size", 2, new float[]{8f, 8f}),
                    GsonHelper.getAsFloat(f, "forward", -1f));
        }

        // Worn-equipment anchors (head/back/boots) live under a "wearables" object; a top-level block
        // is still read as a fallback so older rigs keep working.
        JsonObject worn = obj.has("wearables") && obj.get("wearables").isJsonObject()
                ? obj.getAsJsonObject("wearables") : obj;

        // head/back are worn-anchors (base re-pose + per-item placement deltas keyed by item id).
        RigDefinition.WornAnchor back = wornAnchor(worn, "back");
        RigDefinition.WornAnchor head = wornAnchor(worn, "head");

        java.util.List<RigDefinition.Boot> boots = new java.util.ArrayList<>();
        if (worn.has("boots") && worn.get("boots").isJsonArray()) {
            for (JsonElement e : worn.getAsJsonArray("boots")) {
                if (!e.isJsonObject()) continue;
                JsonObject bo = e.getAsJsonObject();
                String bone = GsonHelper.getAsString(bo, "bone", "");
                if (bone.isEmpty()) continue;
                boolean left = "left".equalsIgnoreCase(GsonHelper.getAsString(bo, "boot", "right"));
                boots.add(new RigDefinition.Boot(bone, left, GsonHelper.getAsFloat(bo, "scale", 1f), adjust(bo)));
            }
        }

        // Held-item grips: which bone each hand holds from, plus third- and first-person nudges. A body
        // property (the grip is a function of the rig's bones), so it lives in the rig, not the species.
        Hold hold = parseHold(obj);

        boolean hair = GsonHelper.getAsBoolean(obj, "hair", false);

        // Per-state bone poses: { "<state>": [ { "bone": ..., "rotation": [x,y,z], "offset": [x,y,z] } ] }.
        Map<String, java.util.List<RigDefinition.PoseBone>> poses = new LinkedHashMap<>();
        if (obj.has("poses") && obj.get("poses").isJsonObject()) {
            for (Map.Entry<String, JsonElement> state : obj.getAsJsonObject("poses").entrySet()) {
                if (!state.getValue().isJsonArray()) continue;
                java.util.List<RigDefinition.PoseBone> bonePoses = new java.util.ArrayList<>();
                for (JsonElement el : state.getValue().getAsJsonArray()) {
                    if (!el.isJsonObject()) continue;
                    JsonObject po = el.getAsJsonObject();
                    String bone = GsonHelper.getAsString(po, "bone", "");
                    if (bone.isEmpty()) continue;
                    bonePoses.add(new RigDefinition.PoseBone(bone,
                            vec(po, "rotation", 3, new float[]{0f, 0f, 0f}),
                            vec(po, "offset", 3, new float[]{0f, 0f, 0f})));
                }
                poses.put(state.getKey(), java.util.List.copyOf(bonePoses));
            }
        }

        // Optional collision/interaction box: { "hitbox": { "width": 1.0, "height": 1.0 } }, in blocks.
        RigDefinition.Hitbox hitbox = null;
        if (obj.has("hitbox") && obj.get("hitbox").isJsonObject()) {
            JsonObject hb = obj.getAsJsonObject("hitbox");
            float w = GsonHelper.getAsFloat(hb, "width", 0.6f);
            float h = GsonHelper.getAsFloat(hb, "height", 2.0f);
            if (w > 0f && h > 0f) hitbox = new RigDefinition.Hitbox(w, h);
        }

        // Equipment slots this body refuses: { "equipment": { "disabled": ["head","chest", ...] } }.
        java.util.Set<net.minecraft.world.entity.EquipmentSlot> disabledSlots = parseDisabledSlots(obj);

        // First-person camera anchor: { "camera": { "bone": "head" } }. The eye height is derived from
        // that bone client-side; absent = keep the height-proportional default.
        String cameraBone = "";
        if (obj.has("camera") && obj.get("camera").isJsonObject()) {
            cameraBone = GsonHelper.getAsString(obj.getAsJsonObject("camera"), "bone", "");
        }

        return new RigDefinition(id, modelType, modelRef, modelLayer, texture, bones, armorType, inner, outer, face, back, head, java.util.List.copyOf(boots), hold, hair, Map.copyOf(poses), hitbox, disabledSlots, cameraBone);
    }

    /** Parse {@code equipment.disabled} into a set of vanilla equipment slots (unknown names skipped). */
    private static java.util.Set<net.minecraft.world.entity.EquipmentSlot> parseDisabledSlots(JsonObject obj) {
        if (!obj.has("equipment") || !obj.get("equipment").isJsonObject()) return java.util.Set.of();
        JsonObject equipment = obj.getAsJsonObject("equipment");
        if (!equipment.has("disabled") || !equipment.get("disabled").isJsonArray()) return java.util.Set.of();
        java.util.EnumSet<net.minecraft.world.entity.EquipmentSlot> slots =
                java.util.EnumSet.noneOf(net.minecraft.world.entity.EquipmentSlot.class);
        for (JsonElement e : equipment.getAsJsonArray("disabled")) {
            if (!e.isJsonPrimitive()) continue;
            try {
                slots.add(net.minecraft.world.entity.EquipmentSlot.byName(
                        e.getAsString().toLowerCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Unknown slot name in the pack; skip it rather than fail the whole rig.
            }
        }
        return java.util.Set.copyOf(slots);
    }

    /**
     * {@code "hold": { "mainhand": { "bone": "right_front_leg", "offset": [x,y,z], "rotation": [x,y,z],
     * "first_person": { "offset": [x,y,z], "rotation": [x,y,z] } }, "offhand": { ... } }} -> per-hand
     * grip bone + third-person nudge + first-person seating. A hand key omitted (null grip) means that
     * hand cannot hold; an absent {@code hold} block means the rig holds nothing.
     */
    private static Hold parseHold(JsonObject obj) {
        if (!obj.has("hold") || !obj.get("hold").isJsonObject()) return Hold.NONE;
        JsonObject hold = obj.getAsJsonObject("hold");
        return new Hold(parseGrip(hold, "mainhand"), parseGrip(hold, "offhand"));
    }

    /** One hand's grip, or null when the hand is absent (cannot hold). */
    private static Hold.Grip parseGrip(JsonObject hold, String key) {
        if (!hold.has(key) || !hold.get(key).isJsonObject()) return null;
        JsonObject grip = hold.getAsJsonObject(key);
        String bone = GsonHelper.getAsString(grip, "bone", "");
        JsonObject fp = grip.has("first_person") && grip.get("first_person").isJsonObject()
                ? grip.getAsJsonObject("first_person") : new JsonObject();
        float[] zero = {0f, 0f, 0f};
        return new Hold.Grip(bone, vec(grip, "offset", 3, zero), vec(grip, "rotation", 3, zero),
                vec(fp, "offset", 3, zero), vec(fp, "rotation", 3, zero));
    }

    /** Read an offset+rotation transform (both optional vec3, default zero) from an object. */
    private static RigDefinition.Adjust adjust(JsonObject obj) {
        return new RigDefinition.Adjust(
                vec(obj, "offset", 3, new float[]{0f, 0f, 0f}),
                vec(obj, "rotation", 3, new float[]{0f, 0f, 0f}));
    }

    /** Parse a worn-anchor ({@code base} offset/rotation + per-item-id {@code items} deltas) under {@code key}. */
    private static RigDefinition.WornAnchor wornAnchor(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) return null;
        JsonObject o = parent.getAsJsonObject(key);
        Map<String, RigDefinition.Adjust> items = new LinkedHashMap<>();
        if (o.has("items") && o.get("items").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("items").entrySet()) {
                if (e.getValue().isJsonObject()) items.put(e.getKey(), adjust(e.getValue().getAsJsonObject()));
            }
        }
        return new RigDefinition.WornAnchor(adjust(o), Map.copyOf(items));
    }

    /** Read a fixed-length float array from a JSON array key, falling back to {@code def}. */
    private static float[] vec(JsonObject obj, String key, int len, float[] def) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return def;
        var arr = obj.getAsJsonArray(key);
        float[] out = def.clone();
        for (int i = 0; i < len && i < arr.size(); i++) out[i] = arr.get(i).getAsFloat();
        return out;
    }
}
