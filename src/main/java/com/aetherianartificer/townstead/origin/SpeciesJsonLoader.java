package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.personality.Personalities;
import com.aetherianartificer.townstead.origin.personality.PersonalityPolicies;
import com.aetherianartificer.townstead.origin.personality.PersonalityPolicyRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads {@link Species} from {@code data/<ns>/species/*.json}. */
public final class SpeciesJsonLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/SpeciesJsonLoader");
    private static final Gson GSON = new Gson();

    public SpeciesJsonLoader() {
        super(GSON, "species");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Map<ResourceLocation, Species> parsed = new LinkedHashMap<>();
        Map<ResourceLocation, Personalities> policies = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                Component displayName = DataPackLang.parseComponent(obj.get("display_name"), file.toString(), lang);
                Rig rig = parseRig(obj);
                Hold hold = parseHold(obj);
                Animations animations = parseAnimations(obj);
                // Breasts default to the rig: MCA's villager body has the part and shows it, so a
                // villager-derived species keeps it without saying so; any other rig defaults to none.
                // An explicit "breasts" always wins (e.g. a humanoid custom rig that wants them).
                boolean breasts = obj.has("breasts")
                        ? GsonHelper.getAsBoolean(obj, "breasts", true)
                        : rig.base().equals(Rig.VILLAGER.base());
                float admixture = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(obj, "admixture_chance", 0f)));
                Genome genome = OriginJsonParsing.genes(obj, file.toString(), LOGGER);
                parsed.put(file, new Species(file, displayName, rig, hold, animations, breasts, admixture, genome));
                policies.put(file, PersonalityPolicies.parse(obj));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse species {}: {}", file, ex.getMessage());
            }
        }
        SpeciesRegistry.replaceAll(parsed);
        PersonalityPolicyRegistry.setSpecies(policies);
        LOGGER.info("Loaded {} origin species", parsed.size());
    }

    /**
     * The species' rig. v2: {@code "rig": { "base": "minecraft:spider" }}. v1 fallback: the legacy
     * {@code "shape"} string, where {@code humanoid} maps to the MCA villager model.
     */
    private static Rig parseRig(JsonObject obj) {
        if (obj.has("rig") && obj.get("rig").isJsonObject()) {
            JsonObject rig = obj.getAsJsonObject("rig");
            return new Rig(GsonHelper.getAsString(rig, "base", "mca:villager"),
                    GsonHelper.getAsFloat(rig, "scale", 1.0f));
        }
        String shape = GsonHelper.getAsString(obj, "shape", "humanoid");
        return "humanoid".equals(shape) ? Rig.VILLAGER : new Rig(shape, 1.0f);
    }

    /**
     * {@code "hold": { "mainhand": { "bone": "right_arm", "offset": [x,y,z], "rotation": [x,y,z] }, ... }}
     * -> per-hand anchor bone + offset (pixels) + rotation (degrees). A hand key omitted (null grip)
     * means that hand cannot hold; an absent {@code hold} block means the rig holds nothing.
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
        return new Hold.Grip(bone, readVec3(grip, "offset"), readVec3(grip, "rotation"));
    }

    /**
     * {@code "animations": { "crouch": "humanoid", "sleep": "humanoid", "fly": "humanoid" }} -> a
     * per-state source. Unknown states/sources are skipped; unlisted states default to humanoid
     * (opt-out), so the block is written to disable a state or redirect it.
     */
    private static Animations parseAnimations(JsonObject obj) {
        if (!obj.has("animations") || !obj.get("animations").isJsonObject()) return Animations.DEFAULT;
        JsonObject a = obj.getAsJsonObject("animations");
        Map<Animations.State, Animations.Source> map = new EnumMap<>(Animations.State.class);
        for (Map.Entry<String, JsonElement> e : a.entrySet()) {
            Animations.State state = Animations.State.byKey(e.getKey());
            if (state == null || !e.getValue().isJsonPrimitive()) continue;
            map.put(state, Animations.Source.byKey(e.getValue().getAsString(), Animations.Source.HUMANOID));
        }
        List<String> providers = new ArrayList<>();
        if (a.has("providers") && a.get("providers").isJsonArray()) {
            for (JsonElement p : a.getAsJsonArray("providers")) if (p.isJsonPrimitive()) providers.add(p.getAsString());
        }
        return map.isEmpty() && providers.isEmpty() ? Animations.DEFAULT
                : new Animations(Map.copyOf(map), List.copyOf(providers));
    }

    private static float[] readVec3(JsonObject obj, String key) {
        float[] out = new float[]{0f, 0f, 0f};
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            var arr = obj.getAsJsonArray(key);
            for (int i = 0; i < 3 && i < arr.size(); i++) out[i] = arr.get(i).getAsFloat();
        }
        return out;
    }
}
