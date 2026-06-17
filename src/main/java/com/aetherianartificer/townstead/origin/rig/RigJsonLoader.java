package com.aetherianartificer.townstead.origin.rig;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.TownsteadSchema;
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

        boolean hair = GsonHelper.getAsBoolean(obj, "hair", false);

        return new RigDefinition(id, modelType, modelRef, modelLayer, texture, bones, armorType, inner, outer, face, hair);
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
