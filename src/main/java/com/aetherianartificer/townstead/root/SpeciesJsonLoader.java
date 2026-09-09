package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.personality.Personalities;
import com.aetherianartificer.townstead.root.personality.PersonalityPolicies;
import com.aetherianartificer.townstead.root.personality.PersonalityPolicyRegistry;
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
        Map<ResourceLocation, com.aetherianartificer.townstead.root.appearance.HairPolicy> hairPolicies = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            try {
                JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), file.toString());
                Component displayName = DataPackLang.parseComponent(obj.get("display_name"), file.toString(), lang);
                Rig rig = parseRig(obj);
                Animations animations = parseAnimations(obj);
                // Breasts default to the rig: MCA's villager body has the part and shows it, so a
                // villager-derived species keeps it without saying so; any other rig defaults to none.
                // An explicit "breasts" always wins (e.g. a humanoid custom rig that wants them).
                boolean breasts = obj.has("breasts")
                        ? GsonHelper.getAsBoolean(obj, "breasts", true)
                        : rig.base().equals(Rig.VILLAGER.base());
                float admixture = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(obj, "admixture_chance", 0f)));
                Genome genome = RootJsonParsing.genes(obj, file.toString(), LOGGER);
                CharacterEditorLayout characterEditor = parseCharacterEditor(obj, file.toString(), lang);
                parsed.put(file, new Species(file, displayName, rig, animations, breasts, admixture, genome,
                        characterEditor));
                policies.put(file, PersonalityPolicies.parse(obj));
                hairPolicies.put(file, com.aetherianartificer.townstead.root.appearance.HairPolicy.parse(obj));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse species {}: {}", file, ex.getMessage());
            }
        }
        SpeciesRegistry.replaceAll(parsed);
        PersonalityPolicyRegistry.setSpecies(policies);
        com.aetherianartificer.townstead.root.appearance.HairPolicyRegistry.setSpecies(hairPolicies);
        LOGGER.info("Loaded {} origin species", parsed.size());
    }

    /**
     * Parse the optional {@code "character_editor"} block (null when absent → opt-out, keeping MCA's
     * full native Character tab). {@code "native"} is the list of MCA-native groups to keep;
     * {@code "tabs"} is an optional explicit tab list (else the client auto-derives from genes).
     */
    private static CharacterEditorLayout parseCharacterEditor(JsonObject obj, String file, Map<String, String> lang) {
        if (!obj.has("character_editor") || !obj.get("character_editor").isJsonObject()) return null;
        JsonObject ce = obj.getAsJsonObject("character_editor");
        List<String> nativeGroups = new ArrayList<>();
        if (ce.has("native") && ce.get("native").isJsonArray()) {
            for (JsonElement e : ce.getAsJsonArray("native")) if (e.isJsonPrimitive()) nativeGroups.add(e.getAsString());
        }
        List<CharacterEditorLayout.Tab> tabs = new ArrayList<>();
        if (ce.has("tabs") && ce.get("tabs").isJsonArray()) {
            for (JsonElement te : ce.getAsJsonArray("tabs")) {
                if (!te.isJsonObject()) continue;
                JsonObject t = te.getAsJsonObject();
                String id = GsonHelper.getAsString(t, "id", "");
                if (id.isEmpty()) continue;
                Component labelC = t.has("label") ? DataPackLang.parseComponent(t.get("label"), file, lang) : null;
                String label = labelC != null ? labelC.getString() : "";
                String labelKey = labelC != null && labelC.getContents()
                        instanceof net.minecraft.network.chat.contents.TranslatableContents tc ? tc.getKey() : "";
                List<String> fields = new ArrayList<>();
                if (t.has("fields") && t.get("fields").isJsonArray()) {
                    for (JsonElement f : t.getAsJsonArray("fields")) if (f.isJsonPrimitive()) fields.add(f.getAsString());
                }
                tabs.add(new CharacterEditorLayout.Tab(id, label, labelKey, fields));
            }
        }
        return new CharacterEditorLayout(nativeGroups, tabs);
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
}
