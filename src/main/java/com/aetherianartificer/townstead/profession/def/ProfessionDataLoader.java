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
 * Loads data-driven professions and skills together, so the cross-references between them are
 * populated and validated atomically each reload. The canonical layout is a directory per
 * profession: {@code data/<ns>/profession/<name>/profession.json} (reserved filename) with its
 * skills beside it in {@code data/<ns>/profession/<name>/skill/<skill>.json}, which register as
 * {@code <ns>:<name>/<skill>} and derive their {@code profession} from location. An optional
 * {@code levels.json} beside the def supplies the progression ({@code levels},
 * {@code daily_cap}, {@code max_xp}), overriding anything inline, so tuning packs can replace a
 * profession's pacing without copying its identity. The flat forms
 * ({@code profession/<name>.json}, {@code data/<ns>/skill/*.json} with an explicit
 * {@code profession} field) still load for older packs. Validation findings (unreachable tiers,
 * dangling refs, cycles) are stored under the "profession" source for {@code /pheno validate}.
 */
public final class ProfessionDataLoader extends SimplePreparableReloadListener<ProfessionDataLoader.Prepared> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/ProfessionDataLoader");

    public record Prepared(Map<ResourceLocation, JsonObject> professions,
                           Map<ResourceLocation, JsonObject> skills,
                           Map<ResourceLocation, JsonObject> levelOverlays) {}

    @Override
    protected Prepared prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonObject> skills = read(resourceManager, "skill");
        Map<ResourceLocation, JsonObject> professions = new LinkedHashMap<>();
        Map<ResourceLocation, JsonObject> levelOverlays = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : resourceManager
                .listResources("profession", loc -> loc.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = e.getKey();
            String subpath = file.getPath().substring("profession/".length(),
                    file.getPath().length() - ".json".length());
            JsonObject json = readJson(e.getValue(), file);
            if (json == null) continue;
            int skillDir = subpath.lastIndexOf("/skill/");
            if (skillDir > 0) {
                String professionPath = subpath.substring(0, skillDir);
                String skillName = subpath.substring(skillDir + "/skill/".length());
                ResourceLocation skillId = ResourceLocation.tryParse(
                        file.getNamespace() + ":" + professionPath + "/" + skillName);
                if (skillId == null) continue;
                if (!json.has("profession")) {
                    json.addProperty("profession", file.getNamespace() + ":" + professionPath);
                }
                skills.put(skillId, json);
            } else if (subpath.endsWith("/profession")) {
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                        + subpath.substring(0, subpath.length() - "/profession".length()));
                if (id != null) professions.put(id, json);
            } else if (subpath.endsWith("/levels")) {
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                        + subpath.substring(0, subpath.length() - "/levels".length()));
                if (id != null) levelOverlays.put(id, json);
            } else {
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":" + subpath);
                if (id != null) professions.put(id, json);
            }
        }
        return new Prepared(professions, skills, levelOverlays);
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, String> lang = DataPackLang.loadLangIndex(resourceManager);
        Diagnostics diagnostics = new Diagnostics();

        // Mod gates first: a def whose "mods" expression is unmet simply does not exist this
        // session (nor do its levels overlay or skills). A malformed gate refuses to load too,
        // with a diagnostic: broken gates must never read as satisfied.
        Map<ResourceLocation, JsonObject> activeDefs = new LinkedHashMap<>();
        Set<ResourceLocation> gated = new java.util.LinkedHashSet<>();
        for (Map.Entry<ResourceLocation, JsonObject> e : prepared.professions().entrySet()) {
            JsonObject obj = e.getValue();
            if (obj.has("mods")) {
                Boolean met = com.aetherianartificer.townstead.data.ModGate.evaluate(obj.get("mods"));
                if (met == null) {
                    diagnostics.forResource(e.getKey());
                    diagnostics.error(JsonPath.ROOT.field("mods"),
                            "Invalid mods gate expression.",
                            "Use a mod id, an array (all required), or {\"all\"/\"any\"/\"not\"} objects.");
                    gated.add(e.getKey());
                    continue;
                }
                if (!met) {
                    LOGGER.debug("Profession {} skipped: mods gate unmet", e.getKey());
                    gated.add(e.getKey());
                    continue;
                }
            }
            activeDefs.put(e.getKey(), obj);
        }

        for (Map.Entry<ResourceLocation, JsonObject> e : prepared.levelOverlays().entrySet()) {
            if (gated.contains(e.getKey())) continue;
            JsonObject def = activeDefs.get(e.getKey());
            diagnostics.forResource(e.getKey());
            if (def == null) {
                diagnostics.warning(JsonPath.ROOT,
                        "levels.json has no matching profession.json in this directory; ignored.",
                        "Add profession.json beside it (or remove the orphan).");
                continue;
            }
            applyLevelsOverlay(def, e.getValue());
        }

        Map<ResourceLocation, ProfessionDef> professions = new LinkedHashMap<>();
        Map<ResourceLocation, SkillDef> skills = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> e : activeDefs.entrySet()) {
            diagnostics.forResource(e.getKey());
            try {
                // Inline level skills land in the same registry; a sidecar with the same id
                // overrides them, which is how packs extend or retune another def's pool.
                ProfessionDef profession = parseProfession(e.getKey(), e.getValue(), lang,
                        diagnostics, skills);
                if (profession != null) professions.put(e.getKey(), profession);
            } catch (Exception ex) {
                diagnostics.error(JsonPath.ROOT, "Failed to parse profession: " + ex.getMessage(),
                        "Fix the JSON structure.");
            }
        }

        for (Map.Entry<ResourceLocation, JsonObject> e : prepared.skills().entrySet()) {
            ResourceLocation skillProfession = ResourceLocation.tryParse(
                    GsonHelper.getAsString(e.getValue(), "profession", ""));
            if (skillProfession != null && gated.contains(skillProfession)) continue;
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
        ProfessionTitles.replaceAll(parseAllTitles(activeDefs, professions.keySet(), lang, diagnostics));
        ProfessionPaths.replaceAll(parseAllPaths(activeDefs, professions.keySet(), diagnostics));

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
        return parseProfession(id, obj, lang, diag, new LinkedHashMap<>());
    }

    static ProfessionDef parseProfession(ResourceLocation id, JsonObject obj, Map<String, String> lang,
                                                 Diagnostics diag,
                                                 Map<ResourceLocation, SkillDef> inlineSkillsOut) {
        boolean v2 = "townstead:profession/v2".equals(GsonHelper.getAsString(obj, "schema", ""))
                || obj.has("levels");
        TownsteadSchema.validate(obj, v2 ? "townstead:profession/v2" : "townstead:profession/v1");
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

        List<WorkTaskDef> workTasks = new ArrayList<>();
        if (obj.has("work_tasks") && obj.get("work_tasks").isJsonArray()) {
            JsonArray taskArray = obj.getAsJsonArray("work_tasks");
            for (int i = 0; i < taskArray.size(); i++) {
                if (!taskArray.get(i).isJsonObject()) continue;
                WorkTaskDef task = WorkTaskDef.parse(taskArray.get(i).getAsJsonObject());
                if (task == null) {
                    diag.error(JsonPath.ROOT.field("work_tasks").index(i),
                            "Invalid work task (missing type, bad workstation entry, or broken requirements).",
                            "Give each entry a type and valid workstation block ids or #tags.");
                    continue;
                }
                if (!WorkTaskTypes.knows(task.type())) {
                    diag.error(JsonPath.ROOT.field("work_tasks").index(i).field("type"),
                            "Unknown work task type '" + task.type() + "'.",
                            "Use a registered type, e.g. townstead_work:cook, townstead_work:chop,"
                                    + " townstead_work:brew (bare ids resolve to townstead_work).");
                    continue;
                }
                workTasks.add(task);
            }
        }

        List<LevelDef> levels = new ArrayList<>();
        Map<Integer, List<TradeDef>> levelTrades = new LinkedHashMap<>();
        List<ResourceLocation> inlineSkillIds = new ArrayList<>();
        if (obj.has("levels") && obj.get("levels").isJsonArray()) {
            JsonArray levelArray = obj.getAsJsonArray("levels");
            for (int i = 0; i < levelArray.size(); i++) {
                if (!levelArray.get(i).isJsonObject()) continue;
                JsonObject entry = levelArray.get(i).getAsJsonObject();
                int levelNumber = i + 1;
                Component levelName = entry.has("name")
                        ? DataPackLang.parseComponent(entry.get("name"),
                                id + ".level." + levelNumber, lang) : null;
                List<TradeDef> tradeList = new ArrayList<>();
                if (entry.has("trades") && entry.get("trades").isJsonArray()) {
                    JsonArray tradeArray = entry.getAsJsonArray("trades");
                    for (int j = 0; j < tradeArray.size(); j++) {
                        if (!tradeArray.get(j).isJsonObject()) continue;
                        TradeDef trade = TradeDef.parse(tradeArray.get(j).getAsJsonObject(), id);
                        if (trade == null) {
                            diag.warning(JsonPath.ROOT.field("levels").index(i)
                                            .field("trades").index(j),
                                    "Trade needs 'cost' and 'result' item objects; entry ignored.",
                                    "Add { \"item\": ..., \"count\": ... } for both.");
                            continue;
                        }
                        tradeList.add(trade);
                    }
                }
                if (!tradeList.isEmpty()) levelTrades.put(levelNumber, List.copyOf(tradeList));
                List<ResourceLocation> levelSkills = new ArrayList<>();
                if (entry.has("skills") && entry.get("skills").isJsonArray()) {
                    JsonArray skillArray = entry.getAsJsonArray("skills");
                    for (int j = 0; j < skillArray.size(); j++) {
                        if (skillArray.get(j).isJsonPrimitive()) {
                            // Bare id: a reference to a skill in the profession's own directory
                            // (<ns>:<profession>/<name>); the graph validator flags dangling refs.
                            String rawRef = skillArray.get(j).getAsString();
                            ResourceLocation refId = resolveSkillRef(id, rawRef);
                            if (refId == null) {
                                diag.error(JsonPath.ROOT.field("levels").index(i)
                                                .field("skills").index(j),
                                        "Invalid skill reference '" + rawRef + "'.",
                                        "Use a bare path (namespaced to the profession) or a full id.");
                                continue;
                            }
                            levelSkills.add(refId);
                            inlineSkillIds.add(refId);
                            continue;
                        }
                        if (!skillArray.get(j).isJsonObject()) continue;
                        JsonObject skillJson = skillArray.get(j).getAsJsonObject().deepCopy();
                        String rawId = GsonHelper.getAsString(skillJson, "id", "");
                        ResourceLocation skillId = resolveSkillRef(id, rawId);
                        if (skillId == null) {
                            diag.error(JsonPath.ROOT.field("levels").index(i)
                                            .field("skills").index(j).field("id"),
                                    "Missing or invalid inline skill id.",
                                    "Use a bare path (scoped to the profession) or a full id.");
                            continue;
                        }
                        skillJson.remove("schema");
                        skillJson.addProperty("profession", id.toString());
                        if (!skillJson.has("tier") && !skillJson.has("level")) {
                            skillJson.addProperty("tier", levelNumber);
                        }
                        SkillDef skill = parseSkill(skillId, skillJson, lang, diag);
                        if (skill == null) continue;
                        inlineSkillsOut.put(skillId, skill);
                        levelSkills.add(skillId);
                        inlineSkillIds.add(skillId);
                    }
                }
                levels.add(new LevelDef(levelName,
                        GsonHelper.getAsInt(entry, "xp", 0),
                        GsonHelper.getAsInt(entry, "skill_points", 1),
                        List.copyOf(tradeList), List.copyOf(levelSkills)));
            }
        }
        if (!levels.isEmpty()) {
            // Thresholds derive from per-level xp spans; the final level's span is open-ended.
            tiers = new ArrayList<>();
            int accumulated = 0;
            tiers.add(0);
            for (int i = 0; i < levels.size() - 1; i++) {
                accumulated += Math.max(0, levels.get(i).xp());
                tiers.add(accumulated);
            }
            dailyCap = GsonHelper.getAsInt(obj, "daily_cap", dailyCap);
            maxXp = GsonHelper.getAsInt(obj, "max_xp", maxXp);
        }

        Map<Integer, List<TradeDef>> trades = new LinkedHashMap<>(parseTrades(obj, diag));
        levelTrades.forEach((level, list) -> trades.merge(level, list, (a, b) -> {
            List<TradeDef> merged = new ArrayList<>(a);
            merged.addAll(b);
            return List.copyOf(merged);
        }));
        List<ResourceLocation> skillIds = new ArrayList<>(parseSkillRefList(obj, "skills", id));
        for (ResourceLocation inline : inlineSkillIds) {
            if (!skillIds.contains(inline)) skillIds.add(inline);
        }

        return new ProfessionDef(id, name, description,
                new ProgressionTrack(List.copyOf(tiers), dailyCap, maxXp),
                UnlockModel.fromString(unlock),
                GsonHelper.getAsInt(obj, "points_per_tier", 1),
                RetrainingPolicy.fromString(retraining),
                List.copyOf(skillIds),
                List.copyOf(historyCounters),
                GsonHelper.getAsBoolean(obj, "hidden", false),
                requirements,
                List.copyOf(routes),
                List.copyOf(jobSites),
                parseIdList(obj, "aliases"),
                Map.copyOf(trades),
                obj.has("requirements") ? RequirementHint.extract(obj.get("requirements")) : List.of(),
                obj.has("icon") ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "icon", "")) : null,
                List.copyOf(levels),
                List.copyOf(workTasks));
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
        // A mods gate on a skill works like one on a profession: unmet means the skill
        // silently does not exist this session (mod-specific branches of a tree).
        if (obj.has("mods") && !Boolean.TRUE.equals(
                com.aetherianartificer.townstead.data.ModGate.evaluate(obj.get("mods")))) {
            return null;
        }
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

        List<SkillGrant> grants = parseGrants(obj, diag);
        ResourceLocation animation = obj.has("animation")
                ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "animation", "")) : null;
        ResourceLocation skillGroup = obj.has("skill_group")
                ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "skill_group", "")) : null;

        return new SkillDef(id, name, description, profession,
                GsonHelper.getAsInt(obj, "tier", GsonHelper.getAsInt(obj, "level", 1)),
                // Bare requires/exclusive_with entries name siblings in the same profession.
                parseSkillRefList(obj, "requires", profession),
                parseSkillRefList(obj, "exclusive_with", profession),
                GsonHelper.getAsInt(obj, "cost", 1),
                List.copyOf(grants),
                animation,
                skillGroup,
                parsePower(obj, lang, diag),
                obj.has("icon") ? ResourceLocation.tryParse(GsonHelper.getAsString(obj, "icon", "")) : null);
    }

    /**
     * Build titles from each def's {@code titles} array: {@code {id, name, skills}} where bare
     * skill refs resolve to the owning profession's path-scoped ids. A title is flavour over
     * the base profession ("Rotisseur (Cook)"), earned by completing the named skill build.
     */
    private static Map<ResourceLocation, List<ProfessionTitles.Title>> parseAllTitles(
            Map<ResourceLocation, JsonObject> activeDefs, Set<ResourceLocation> loaded,
            Map<String, String> lang, Diagnostics diagnostics) {
        Map<ResourceLocation, List<ProfessionTitles.Title>> out = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> e : activeDefs.entrySet()) {
            if (!loaded.contains(e.getKey())) continue;
            JsonObject obj = e.getValue();
            if (!obj.has("titles") || !obj.get("titles").isJsonArray()) continue;
            diagnostics.forResource(e.getKey());
            List<ProfessionTitles.Title> titles = new ArrayList<>();
            JsonArray arr = obj.getAsJsonArray("titles");
            for (int i = 0; i < arr.size(); i++) {
                if (!arr.get(i).isJsonObject()) continue;
                JsonObject t = arr.get(i).getAsJsonObject();
                String titleId = GsonHelper.getAsString(t, "id", "");
                List<ResourceLocation> skillIds = new ArrayList<>();
                for (JsonElement raw : GsonHelper.getAsJsonArray(t, "skills", new JsonArray())) {
                    if (!raw.isJsonPrimitive()) continue;
                    ResourceLocation skillId = resolveSkillRef(e.getKey(), raw.getAsString());
                    if (skillId != null) skillIds.add(skillId);
                }
                if (titleId.isBlank() || skillIds.isEmpty()) {
                    diagnostics.warning(JsonPath.ROOT.field("titles").index(i),
                            "A title needs an id and at least one skill; ignored.",
                            "Use {\"id\": ..., \"name\": ..., \"skills\": [...]}.");
                    continue;
                }
                Component name = t.has("name")
                        ? DataPackLang.parseComponent(t.get("name"),
                                e.getKey() + ".title." + titleId, lang)
                        : Component.literal(titleId);
                titles.add(new ProfessionTitles.Title(e.getKey(), titleId, name, skillIds));
            }
            if (!titles.isEmpty()) out.put(e.getKey(), List.copyOf(titles));
        }
        return out;
    }

    /**
     * A def's {@code paths} block: specialization branches opened by buying a gateway skill.
     * Skill refs resolve path-scoped like title refs; worksites are full block ids because
     * they usually belong to other mods.
     */
    private static Map<ResourceLocation, List<ProfessionPaths.Path>> parseAllPaths(
            Map<ResourceLocation, JsonObject> activeDefs, Set<ResourceLocation> loaded,
            Diagnostics diagnostics) {
        Map<ResourceLocation, List<ProfessionPaths.Path>> out = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> e : activeDefs.entrySet()) {
            if (!loaded.contains(e.getKey())) continue;
            JsonObject obj = e.getValue();
            if (!obj.has("paths") || !obj.get("paths").isJsonArray()) continue;
            diagnostics.forResource(e.getKey());
            List<ProfessionPaths.Path> paths = new ArrayList<>();
            JsonArray arr = obj.getAsJsonArray("paths");
            for (int i = 0; i < arr.size(); i++) {
                if (!arr.get(i).isJsonObject()) continue;
                JsonObject p = arr.get(i).getAsJsonObject();
                String pathId = GsonHelper.getAsString(p, "id", "");
                ResourceLocation gateway = resolveSkillRef(e.getKey(),
                        GsonHelper.getAsString(p, "gateway", ""));
                if (pathId.isBlank() || gateway == null) {
                    diagnostics.warning(JsonPath.ROOT.field("paths").index(i),
                            "A path needs an id and a gateway skill; ignored.",
                            "Use {\"id\": ..., \"gateway\": ..., \"skills\": [...], \"worksites\": [...]}.");
                    continue;
                }
                List<ResourceLocation> skillIds = new ArrayList<>();
                for (JsonElement raw : GsonHelper.getAsJsonArray(p, "skills", new JsonArray())) {
                    if (!raw.isJsonPrimitive()) continue;
                    ResourceLocation skillId = resolveSkillRef(e.getKey(), raw.getAsString());
                    if (skillId != null) skillIds.add(skillId);
                }
                List<ResourceLocation> worksites = parseIdList(p, "worksites");
                paths.add(new ProfessionPaths.Path(e.getKey(), pathId, gateway, skillIds, worksites));
            }
            if (!paths.isEmpty()) out.put(e.getKey(), List.copyOf(paths));
        }
        return out;
    }

    /** Shared grants block parser: ordinary skills and Combo Skills carry identical grants. */
    static List<SkillGrant> parseGrants(JsonObject obj, Diagnostics diag) {
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
        return List.copyOf(grants);
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

    /**
     * Overlays a {@code levels.json} sidecar onto its profession def. Only the progression keys
     * cross over, so a sidecar can never mutate identity or hooks.
     */
    static void applyLevelsOverlay(JsonObject def, JsonObject overlay) {
        if (overlay.has("levels")) def.add("levels", overlay.get("levels"));
        if (overlay.has("daily_cap")) def.add("daily_cap", overlay.get("daily_cap"));
        if (overlay.has("max_xp")) def.add("max_xp", overlay.get("max_xp"));
    }

    /**
     * A skill reference: a full {@code ns:path} id passes through; a bare path is scoped to the
     * owner's directory ({@code <owner ns>:<owner path>/<raw>}). Null when blank or malformed.
     */
    private static ResourceLocation resolveSkillRef(ResourceLocation owner, String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.contains(":")
                ? ResourceLocation.tryParse(raw)
                : ResourceLocation.tryParse(owner.getNamespace() + ":" + owner.getPath() + "/" + raw);
    }

    /** Like {@link #parseIdList} but bare entries resolve through {@link #resolveSkillRef}. */
    private static List<ResourceLocation> parseSkillRefList(JsonObject obj, String key,
                                                            ResourceLocation owner) {
        List<ResourceLocation> out = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            for (JsonElement e : obj.getAsJsonArray(key)) {
                if (!e.isJsonPrimitive()) continue;
                ResourceLocation id = resolveSkillRef(owner, e.getAsString());
                if (id != null) out.add(id);
            }
        }
        return List.copyOf(out);
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
            JsonObject json = readJson(e.getValue(), file);
            if (json != null) out.put(id, json);
        }
        return out;
    }

    private static JsonObject readJson(Resource resource, ResourceLocation file) {
        try (Reader reader = resource.openAsReader()) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception ex) {
            LOGGER.warn("Failed to read {}: {}", file, ex.getMessage());
            return null;
        }
    }
}
