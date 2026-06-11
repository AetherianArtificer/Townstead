package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Severity;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads data-driven professions ({@code data/<ns>/profession/*.json}) and skills
 * ({@code data/<ns>/skill/*.json}) together, so the cross-references between them are populated
 * and validated atomically each reload. Validation findings (unreachable tiers, dangling refs,
 * cycles) are stored under the "profession" source for {@code /pheno validate}.
 */
public final class ProfessionDataLoader extends SimplePreparableReloadListener<ProfessionDataLoader.Prepared> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/ProfessionDataLoader");

    public record Prepared(Map<ResourceLocation, JsonObject> professions,
                           Map<ResourceLocation, JsonObject> skills) {}

    @Override
    protected Prepared prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return new Prepared(read(resourceManager, "profession"), read(resourceManager, "skill"));
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);

        Map<ResourceLocation, ProfessionDef> professions = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> e : prepared.professions().entrySet()) {
            try {
                professions.put(e.getKey(), parseProfession(e.getKey(), e.getValue(), lang));
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse profession {}: {}", e.getKey(), ex.getMessage());
            }
        }

        Map<ResourceLocation, SkillDef> skills = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> e : prepared.skills().entrySet()) {
            try {
                SkillDef skill = parseSkill(e.getKey(), e.getValue(), lang);
                if (skill != null) skills.put(e.getKey(), skill);
            } catch (Exception ex) {
                LOGGER.warn("Failed to parse skill {}: {}", e.getKey(), ex.getMessage());
            }
        }

        ProfessionDefs.replaceAll(professions);
        SkillDefs.replaceAll(skills);

        Diagnostics diagnostics = new Diagnostics();
        SkillGraphValidator.validate(professions, skills, diagnostics);
        PhenoDiagnostics.replace("profession", diagnostics.all());
        for (Diagnostic d : diagnostics.all()) {
            if (d.severity() == Severity.ERROR) LOGGER.warn("pheno: {}", d.render());
        }
        LOGGER.info("Loaded {} professions, {} skills ({} diagnostic{})",
                professions.size(), skills.size(), diagnostics.all().size(),
                diagnostics.all().size() == 1 ? "" : "s");
    }

    private static ProfessionDef parseProfession(ResourceLocation id, JsonObject obj, Map<String, String> lang) {
        Component name = obj.has("display_name")
                ? DataPackLang.parseComponent(obj.get("display_name"), id.toString(), lang)
                : Component.literal(id.getPath());
        Component description = obj.has("description")
                ? DataPackLang.parseComponent(obj.get("description"), id + ".description", lang) : null;

        List<Integer> tiers = new ArrayList<>();
        int dailyCap = 0;
        if (obj.has("progression") && obj.get("progression").isJsonObject()) {
            JsonObject prog = obj.getAsJsonObject("progression");
            if (prog.has("tiers") && prog.get("tiers").isJsonArray()) {
                for (JsonElement t : prog.getAsJsonArray("tiers")) {
                    if (t.isJsonPrimitive()) tiers.add(t.getAsInt());
                }
            }
            dailyCap = GsonHelper.getAsInt(prog, "daily_cap", 0);
        }
        if (tiers.isEmpty()) tiers.add(0);

        return new ProfessionDef(id, name, description,
                new ProgressionTrack(List.copyOf(tiers), dailyCap),
                UnlockModel.fromString(GsonHelper.getAsString(obj, "unlock_model", "experiential")),
                GsonHelper.getAsInt(obj, "points_per_tier", 1),
                RetrainingPolicy.fromString(GsonHelper.getAsString(obj, "retraining", "free")),
                parseIdList(obj, "skills"));
    }

    private static SkillDef parseSkill(ResourceLocation id, JsonObject obj, Map<String, String> lang) {
        ResourceLocation profession = ResourceLocation.tryParse(GsonHelper.getAsString(obj, "profession", ""));
        if (profession == null) {
            LOGGER.warn("Skipping skill {} - missing or invalid 'profession'", id);
            return null;
        }
        Component name = obj.has("display_name")
                ? DataPackLang.parseComponent(obj.get("display_name"), id.toString(), lang)
                : Component.literal(id.getPath());
        Component description = obj.has("description")
                ? DataPackLang.parseComponent(obj.get("description"), id + ".description", lang) : null;

        List<SkillGrant> grants = new ArrayList<>();
        if (obj.has("grants") && obj.get("grants").isJsonArray()) {
            for (JsonElement g : obj.getAsJsonArray("grants")) {
                if (g.isJsonObject()) {
                    SkillGrant grant = SkillGrant.parse(g.getAsJsonObject());
                    if (grant != null) grants.add(grant);
                }
            }
        }
        ResourceLocation animation = obj.has("animation")
                ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "animation", "")) : null;

        return new SkillDef(id, name, description, profession,
                GsonHelper.getAsInt(obj, "tier", 1),
                parseIdList(obj, "requires"),
                parseIdList(obj, "exclusive_with"),
                GsonHelper.getAsInt(obj, "cost", 1),
                List.copyOf(grants),
                animation);
    }

    private static List<ResourceLocation> parseIdList(JsonObject obj, String key) {
        List<ResourceLocation> out = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray(key)) {
                if (e.isJsonPrimitive()) {
                    ResourceLocation id = ResourceLocation.tryParse(e.getAsString());
                    if (id != null) out.add(id);
                }
            }
        }
        return List.copyOf(out);
    }

    private static Map<ResourceLocation, JsonObject> read(ResourceManager resourceManager, String dir) {
        Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
        String prefix = dir + "/";
        for (Map.Entry<ResourceLocation, Resource> e :
                resourceManager.listResources(dir, loc -> loc.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = e.getKey();
            String path = file.getPath();
            String idPath = path.substring(prefix.length(), path.length() - ".json".length());
            ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":" + idPath);
            if (id == null) continue;
            try (Reader reader = e.getValue().openAsReader()) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
            } catch (Exception ex) {
                LOGGER.warn("Failed to read {} {}: {}", dir, file, ex.getMessage());
            }
        }
        return out;
    }
}
