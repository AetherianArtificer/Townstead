package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.JsonPath;
import com.aetherianartificer.townstead.pheno.lang.compile.Severity;
import com.google.gson.JsonArray;
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
import java.util.Set;

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
        Diagnostics diagnostics = new Diagnostics();

        Map<ResourceLocation, ProfessionDef> professions = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> e : prepared.professions().entrySet()) {
            diagnostics.forResource(e.getKey());
            try {
                ProfessionDef profession = parseProfession(e.getKey(), e.getValue(), lang, diagnostics);
                if (profession != null) professions.put(e.getKey(), profession);
            } catch (Exception ex) {
                diagnostics.error(JsonPath.ROOT, "Failed to parse profession: " + ex.getMessage(),
                        "Fix the JSON structure.");
            }
        }

        Map<ResourceLocation, SkillDef> skills = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> e : prepared.skills().entrySet()) {
            diagnostics.forResource(e.getKey());
            try {
                SkillDef skill = parseSkill(e.getKey(), e.getValue(), lang, diagnostics);
                if (skill != null) skills.put(e.getKey(), skill);
            } catch (Exception ex) {
                diagnostics.error(JsonPath.ROOT, "Failed to parse skill: " + ex.getMessage(),
                        "Fix the JSON structure.");
            }
        }

        validateAliases(professions, diagnostics);
        ProfessionDefs.replaceAll(professions);
        SkillDefs.replaceAll(skills);

        SkillGraphValidator.validate(professions, skills, diagnostics);
        PhenoDiagnostics.replace("profession", diagnostics.all());
        for (Diagnostic d : diagnostics.all()) {
            if (d.severity() == Severity.ERROR) LOGGER.warn("pheno: {}", d.render());
        }
        LOGGER.info("Loaded {} professions, {} skills ({} diagnostic{})",
                professions.size(), skills.size(), diagnostics.all().size(),
                diagnostics.all().size() == 1 ? "" : "s");
    }

    private static final Set<String> UNLOCK_MODELS = Set.of("points", "experiential", "hybrid");
    private static final Set<String> RETRAINING = Set.of("free", "costly", "locked");
    private static final Set<String> GRANT_OPS = Set.of("add", "multiply", "min", "max", "replace", "set", "deny");

    static ProfessionDef parseProfession(ResourceLocation id, JsonObject obj, Map<String, String> lang,
                                                 Diagnostics diag) {
        TownsteadSchema.validate(obj, "townstead:profession/v1");
        Component name = obj.has("display_name")
                ? DataPackLang.parseComponent(obj.get("display_name"), id.toString(), lang)
                : Component.literal(id.getPath());
        Component description = obj.has("description")
                ? DataPackLang.parseComponent(obj.get("description"), id + ".description", lang) : null;

        List<Integer> tiers = new ArrayList<>();
        int dailyCap = 0;
        int maxXp = ProgressionTrack.DEFAULT_MAX_XP;
        if (obj.has("progression") && obj.get("progression").isJsonObject()) {
            JsonObject prog = obj.getAsJsonObject("progression");
            if (prog.has("tiers") && prog.get("tiers").isJsonArray()) {
                for (JsonElement t : prog.getAsJsonArray("tiers")) {
                    if (t.isJsonPrimitive()) tiers.add(t.getAsInt());
                }
            }
            dailyCap = GsonHelper.getAsInt(prog, "daily_cap", 0);
            maxXp = GsonHelper.getAsInt(prog, "max_xp", ProgressionTrack.DEFAULT_MAX_XP);
        }
        if (tiers.isEmpty()) tiers.add(0);

        String unlock = GsonHelper.getAsString(obj, "unlock_model", "experiential");
        if (!UNLOCK_MODELS.contains(unlock.toLowerCase())) {
            diag.warning(JsonPath.ROOT.field("unlock_model"),
                    "Unknown unlock_model '" + unlock + "'; defaulting to experiential.",
                    "Use points, experiential, or hybrid.");
        }
        String retraining = GsonHelper.getAsString(obj, "retraining", "free");
        if (!RETRAINING.contains(retraining.toLowerCase())) {
            diag.warning(JsonPath.ROOT.field("retraining"),
                    "Unknown retraining '" + retraining + "'; defaulting to free.",
                    "Use free, costly, or locked.");
        }

        List<String> historyCounters = new ArrayList<>();
        if (obj.has("history_counters") && obj.get("history_counters").isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray("history_counters")) {
                if (e.isJsonPrimitive()) historyCounters.add(e.getAsString());
            }
        }

        // A present-but-unparseable requirements condition drops the def: a broken gate must
        // never read as "always eligible".
        com.aetherianartificer.townstead.pheno.condition.Condition requirements =
                com.aetherianartificer.townstead.pheno.condition.Conditions.ALWAYS;
        if (obj.has("requirements")) {
            requirements = com.aetherianartificer.townstead.pheno.condition.Conditions.parse(obj.get("requirements"));
            if (requirements == null) {
                diag.error(JsonPath.ROOT.field("requirements"),
                        "Invalid requirements condition (unknown or malformed type).",
                        "Use registered pheno conditions, e.g. pheno:chronicle_count, pheno:career_xp.");
                return null;
            }
        }

        List<String> routes = new ArrayList<>();
        if (obj.has("acquisition_routes") && obj.get("acquisition_routes").isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray("acquisition_routes")) {
                if (e.isJsonPrimitive()) routes.add(e.getAsString());
            }
        }

        List<JobSiteProvider> jobSites = new ArrayList<>();
        if (obj.has("poi") && obj.get("poi").isJsonArray()) {
            JsonArray poiArray = obj.getAsJsonArray("poi");
            for (int i = 0; i < poiArray.size(); i++) {
                if (!poiArray.get(i).isJsonObject()) continue;
                JsonObject entry = poiArray.get(i).getAsJsonObject();
                String type = GsonHelper.getAsString(entry, "type", "");
                if (!JobSiteProviders.knows(type)) {
                    diag.error(JsonPath.ROOT.field("poi").index(i).field("type"),
                            "Unknown job-site provider type '" + type + "'.",
                            "Use townstead:job_block, townstead:building, or townstead:always.");
                    continue;
                }
                JobSiteProvider provider = JobSiteProviders.parse(entry);
                if (provider == null) {
                    diag.error(JsonPath.ROOT.field("poi").index(i),
                            "Invalid config for job-site provider '" + type + "'.",
                            "Fix the fields this provider requires.");
                    continue;
                }
                jobSites.add(provider);
            }
        }

        return new ProfessionDef(id, name, description,
                new ProgressionTrack(List.copyOf(tiers), dailyCap, maxXp),
                UnlockModel.fromString(unlock),
                GsonHelper.getAsInt(obj, "points_per_tier", 1),
                RetrainingPolicy.fromString(retraining),
                parseIdList(obj, "skills"),
                List.copyOf(historyCounters),
                parseIdList(obj, "parents"),
                GsonHelper.getAsBoolean(obj, "hidden", false),
                requirements,
                List.copyOf(routes),
                List.copyOf(jobSites),
                parseIdList(obj, "aliases"),
                parseTrades(obj, diag),
                obj.has("requirements") ? RequirementHint.extract(obj.get("requirements")) : List.of(),
                obj.has("icon") ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "icon", "")) : null);
    }

    /** {@code trades} maps merchant level ("1".."5") to a list of {@link TradeDef}s. */
    private static Map<Integer, List<TradeDef>> parseTrades(JsonObject obj, Diagnostics diag) {
        if (!obj.has("trades") || !obj.get("trades").isJsonObject()) return Map.of();
        Map<Integer, List<TradeDef>> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> level : obj.getAsJsonObject("trades").entrySet()) {
            int merchantLevel;
            try {
                merchantLevel = Integer.parseInt(level.getKey());
            } catch (NumberFormatException ignored) {
                diag.warning(JsonPath.ROOT.field("trades"),
                        "Trade level '" + level.getKey() + "' is not a number; entry ignored.",
                        "Use merchant levels \"1\" through \"5\".");
                continue;
            }
            if (!level.getValue().isJsonArray()) continue;
            List<TradeDef> defs = new ArrayList<>();
            JsonArray entries = level.getValue().getAsJsonArray();
            for (int i = 0; i < entries.size(); i++) {
                if (!entries.get(i).isJsonObject()) continue;
                TradeDef trade = TradeDef.parse(entries.get(i).getAsJsonObject());
                if (trade == null) {
                    diag.warning(JsonPath.ROOT.field("trades").field(level.getKey()).index(i),
                            "Trade needs 'cost' and 'result' item objects; entry ignored.",
                            "Add { \"item\": ..., \"count\": ... } for both.");
                    continue;
                }
                defs.add(trade);
            }
            if (!defs.isEmpty()) out.put(merchantLevel, List.copyOf(defs));
        }
        return Map.copyOf(out);
    }

    /** Alias hygiene: an alias may not shadow a primary def id or be claimed by two defs. */
    private static void validateAliases(Map<ResourceLocation, ProfessionDef> professions,
                                        Diagnostics diagnostics) {
        Map<ResourceLocation, ResourceLocation> claimed = new LinkedHashMap<>();
        for (ProfessionDef def : professions.values()) {
            diagnostics.forResource(def.id());
            for (ResourceLocation alias : def.aliases()) {
                if (professions.containsKey(alias)) {
                    diagnostics.warning(JsonPath.ROOT.field("aliases"),
                            "Alias '" + alias + "' shadows a primary profession def and is ignored.",
                            "Remove the alias or the conflicting def.");
                    continue;
                }
                ResourceLocation first = claimed.putIfAbsent(alias, def.id());
                if (first != null && !first.equals(def.id())) {
                    diagnostics.warning(JsonPath.ROOT.field("aliases"),
                            "Alias '" + alias + "' is already claimed by '" + first + "'; first claim wins.",
                            "Give each alias exactly one owning profession.");
                }
            }
        }
    }

    static SkillDef parseSkill(ResourceLocation id, JsonObject obj, Map<String, String> lang,
                                       Diagnostics diag) {
        TownsteadSchema.validate(obj, "townstead:skill/v1");
        String professionRaw = GsonHelper.getAsString(obj, "profession", "");
        ResourceLocation profession = professionRaw.isBlank() ? null : ResourceLocation.tryParse(professionRaw);
        if (profession == null) {
            diag.error(JsonPath.ROOT.field("profession"),
                    "Missing or invalid 'profession' id.", "Set it to namespace:profession_id.");
            return null;
        }
        Component name = obj.has("display_name")
                ? DataPackLang.parseComponent(obj.get("display_name"), id.toString(), lang)
                : Component.literal(id.getPath());
        Component description = obj.has("description")
                ? DataPackLang.parseComponent(obj.get("description"), id + ".description", lang) : null;

        List<SkillGrant> grants = new ArrayList<>();
        if (obj.has("grants") && obj.get("grants").isJsonArray()) {
            JsonArray arr = obj.getAsJsonArray("grants");
            for (int i = 0; i < arr.size(); i++) {
                if (!arr.get(i).isJsonObject()) continue;
                JsonObject g = arr.get(i).getAsJsonObject();
                JsonPath gPath = JsonPath.ROOT.field("grants").index(i);
                if (ResourceLocation.tryParse(GsonHelper.getAsString(g, "capability", "")) == null) {
                    diag.error(gPath.field("capability"), "Missing or invalid capability id.", "Use namespace:path.");
                    continue;
                }
                if (!g.has("flag") && g.has("op")) {
                    String op = GsonHelper.getAsString(g, "op", "add");
                    if (!GRANT_OPS.contains(op.toLowerCase())) {
                        diag.warning(gPath.field("op"), "Unknown op '" + op + "'; defaulting to add.",
                                "Use add, multiply, min, max, replace, or deny.");
                    }
                }
                SkillGrant grant = SkillGrant.parse(g);
                if (grant != null) grants.add(grant);
            }
        }
        ResourceLocation animation = obj.has("animation")
                ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "animation", "")) : null;
        ResourceLocation skillGroup = obj.has("skill_group")
                ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "skill_group", "")) : null;

        return new SkillDef(id, name, description, profession,
                GsonHelper.getAsInt(obj, "tier", 1),
                parseIdList(obj, "requires"),
                parseIdList(obj, "exclusive_with"),
                GsonHelper.getAsInt(obj, "cost", 1),
                List.copyOf(grants),
                animation,
                skillGroup,
                parsePower(obj, lang, diag),
                obj.has("icon") ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "icon", "")) : null);
    }

    /**
     * A skill's optional {@code "power"} block is a full pheno component parsed through the same
     * type registry genes use, so skills draw on the whole power vocabulary. A bad block drops
     * only the power (with a diagnostic), never the skill: learned history must keep resolving.
     */
    @org.jetbrains.annotations.Nullable
    private static com.aetherianartificer.townstead.pheno.power.PowerComponent parsePower(
            JsonObject obj, Map<String, String> lang, Diagnostics diag) {
        if (!obj.has("power")) return null;
        JsonPath path = JsonPath.ROOT.field("power");
        if (!obj.get("power").isJsonObject()) {
            diag.error(path, "'power' must be an object.", "Use { \"type\": \"pheno:...\", ... }.");
            return null;
        }
        JsonObject powerJson = obj.getAsJsonObject("power");
        String type = GsonHelper.getAsString(powerJson, "type", "");
        var geneType = com.aetherianartificer.townstead.root.gene.GeneTypes.get(type);
        if (geneType.isEmpty()) {
            diag.error(path.field("type"), "Unknown power type '" + type + "'.",
                    "Use a registered pheno component type (see /pheno dump).");
            return null;
        }
        var instance = geneType.get().parse(powerJson, lang);
        if (instance == null) {
            diag.error(path, "Invalid config for power type '" + type + "'.",
                    "Fix the fields this type requires.");
        }
        return instance;
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
