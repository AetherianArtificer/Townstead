package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Lowers one {@code path/<id>/path.json} document into the established Career model. */
public final class ProfessionPathDocument {

    public static final String SCHEMA = "townstead:profession_path/v1";
    static final String CONTRIBUTION_ORIGIN = "__townstead_path_contribution";
    static final String REQUIRED_PATH = "required_path";

    private ProfessionPathDocument() {
    }

    /** Skill tiers derived from positions in a path's {@code skills} array. */
    public record Applied(Map<String, Integer> skillTiers) {
        public Applied {
            skillTiers = Map.copyOf(skillTiers);
        }
    }

    public static Applied apply(JsonObject profession, String pathId, JsonObject document) {
        TownsteadSchema.validate(document, SCHEMA);
        if (pathId == null || pathId.isBlank()) {
            throw new IllegalArgumentException("Path directory must provide a non-empty id");
        }
        if (document.has("id") && (!document.get("id").isJsonPrimitive()
                || !pathId.equals(document.get("id").getAsString()))) {
            throw new IllegalArgumentException("Path id comes from the directory; remove mismatched 'id'");
        }
        if (!document.has("skills") || !document.get("skills").isJsonArray()) {
            throw new IllegalArgumentException("'skills' must be an array");
        }

        Map<String, Integer> tiers = parseSkills(pathId, document.getAsJsonArray("skills"));
        if (tiers.isEmpty()) throw new IllegalArgumentException("A path needs at least one skill");
        rejectMembershipConflicts(profession, pathId, tiers.keySet());

        JsonObject normalized = new JsonObject();
        normalized.addProperty("id", pathId);
        copy(document, normalized, "name");
        copy(document, normalized, "title");
        copy(document, normalized, "color");
        copy(document, normalized, "backdrop");
        copy(document, normalized, "worksites");
        copy(document, normalized, "powers");
        copy(document, normalized, "clothing");

        String gateway = tiers.keySet().iterator().next();
        normalized.addProperty("gateway", gateway);
        JsonArray members = new JsonArray();
        tiers.keySet().stream().skip(1).forEach(members::add);
        normalized.add("skills", members);
        normalized.add("skill_levels", normalizedSkillLevels(pathId,
                document.getAsJsonArray("skills")));

        JsonArray paths = profession.has("paths") && profession.get("paths").isJsonArray()
                ? profession.getAsJsonArray("paths").deepCopy() : new JsonArray();
        for (int i = paths.size() - 1; i >= 0; i--) {
            JsonElement existing = paths.get(i);
            if (existing.isJsonObject()
                    && pathId.equals(string(existing.getAsJsonObject(), "id"))) {
                paths.remove(i);
            }
        }
        paths.add(normalized);
        profession.add("paths", paths);
        ProfessionPathsOverlay.deriveCompletionTitles(profession, paths);
        mergeProfessionSkills(profession, tiers.keySet());
        applyWorkContributions(profession, pathId, document);
        return new Applied(tiers);
    }

    /**
     * Path locality is authorship, not an implicit career gate. Work placed beside a Path joins
     * the parent profession unless the contribution explicitly says {@code "access":"path"}.
     * This lets Pizzaiolo own the Pizza Delight integration without taking pizza away from an
     * ordinary Cook, while a genuinely exclusive Dark Knight action can opt into exclusivity.
     */
    private static void applyWorkContributions(JsonObject profession, String pathId,
                                               JsonObject document) {
        JsonArray existing = profession.has("work_tasks")
                && profession.get("work_tasks").isJsonArray()
                ? profession.getAsJsonArray("work_tasks").deepCopy() : new JsonArray();
        for (int i = existing.size() - 1; i >= 0; i--) {
            JsonElement element = existing.get(i);
            if (element.isJsonObject() && pathId.equals(
                    string(element.getAsJsonObject(), CONTRIBUTION_ORIGIN))) {
                existing.remove(i);
            }
        }
        if (!document.has("work")) {
            profession.add("work_tasks", existing);
            return;
        }
        if (!document.get("work").isJsonArray()) {
            throw new IllegalArgumentException("'work' must be an array of task contributions");
        }
        for (JsonElement element : document.getAsJsonArray("work")) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Every Path work contribution must be an object");
            }
            JsonObject task = element.getAsJsonObject().deepCopy();
            if (task.has(CONTRIBUTION_ORIGIN) || task.has(REQUIRED_PATH)) {
                throw new IllegalArgumentException("Path work uses reserved internal fields");
            }
            if (task.has("access") && (!task.get("access").isJsonPrimitive()
                    || !task.getAsJsonPrimitive("access").isString())) {
                throw new IllegalArgumentException(
                        "Path work 'access' must be 'profession' or 'path'");
            }
            String access = task.has("access")
                    ? task.get("access").getAsString() : "profession";
            if (!"profession".equals(access) && !"path".equals(access)) {
                throw new IllegalArgumentException(
                        "Path work 'access' must be 'profession' or 'path'");
            }
            task.remove("access");
            task.addProperty(CONTRIBUTION_ORIGIN, pathId);
            if ("path".equals(access)) task.addProperty(REQUIRED_PATH, pathId);
            existing.add(task);
        }
        profession.add("work_tasks", existing);
    }

    private static Map<String, Integer> parseSkills(String pathId, JsonArray skills) {
        Map<String, Integer> tiers = new LinkedHashMap<>();
        for (int i = 0; i < skills.size(); i++) {
            JsonElement level = skills.get(i);
            if (level.isJsonPrimitive() && level.getAsJsonPrimitive().isString()) {
                addSkill(pathId, tiers, level, i + 1);
            } else if (level.isJsonArray() && !level.getAsJsonArray().isEmpty()) {
                for (JsonElement skill : level.getAsJsonArray()) {
                    addSkill(pathId, tiers, skill, i + 1);
                }
            } else {
                throw new IllegalArgumentException("Skill position " + (i + 1)
                        + " must be a skill id or a non-empty array of skill ids");
            }
        }
        return tiers;
    }

    private static void addSkill(String pathId, Map<String, Integer> tiers,
                                 JsonElement rawSkill, int level) {
        if (!rawSkill.isJsonPrimitive() || !rawSkill.getAsJsonPrimitive().isString()
                || rawSkill.getAsString().isBlank()) {
            throw new IllegalArgumentException("Skill position " + level
                    + " contains an invalid skill reference");
        }
        String authored = rawSkill.getAsString();
        String skill = authored.contains(":") ? authored : pathId + "/" + authored;
        Integer previous = tiers.putIfAbsent(skill, level);
        if (previous != null) {
            throw new IllegalArgumentException("Skill '" + skill
                    + "' appears at both path levels " + previous + " and " + level);
        }
    }

    /** Preserve the authored one-of-many rows for completion titles after the path is flattened. */
    private static JsonArray normalizedSkillLevels(String pathId, JsonArray authored) {
        JsonArray levels = new JsonArray();
        for (JsonElement level : authored) {
            JsonArray options = new JsonArray();
            if (level.isJsonArray()) {
                for (JsonElement option : level.getAsJsonArray()) {
                    options.add(normalizedSkillId(pathId, option.getAsString()));
                }
            } else {
                options.add(normalizedSkillId(pathId, level.getAsString()));
            }
            levels.add(options);
        }
        return levels;
    }

    private static String normalizedSkillId(String pathId, String authored) {
        return authored.contains(":") ? authored : pathId + "/" + authored;
    }

    private static void rejectMembershipConflicts(JsonObject profession, String pathId,
                                                   Set<String> proposed) {
        if (!profession.has("paths") || !profession.get("paths").isJsonArray()) return;
        for (JsonElement element : profession.getAsJsonArray("paths")) {
            if (!element.isJsonObject()) continue;
            JsonObject path = element.getAsJsonObject();
            String existingId = string(path, "id");
            if (pathId.equals(existingId)) continue;
            Set<String> existing = new LinkedHashSet<>();
            String gateway = string(path, "gateway");
            if (!gateway.isBlank()) existing.add(gateway);
            if (path.has("skills") && path.get("skills").isJsonArray()) {
                for (JsonElement skill : path.getAsJsonArray("skills")) {
                    if (skill.isJsonPrimitive()) existing.add(skill.getAsString());
                }
            }
            for (String skill : proposed) {
                if (existing.contains(skill)) {
                    throw new IllegalArgumentException("Skill '" + skill
                            + "' already belongs to path '" + existingId + "'");
                }
            }
        }
    }

    private static void mergeProfessionSkills(JsonObject profession, Set<String> pathSkills) {
        Set<String> merged = new LinkedHashSet<>();
        if (profession.has("skills") && profession.get("skills").isJsonArray()) {
            for (JsonElement skill : profession.getAsJsonArray("skills")) {
                if (skill.isJsonPrimitive() && skill.getAsJsonPrimitive().isString()) {
                    merged.add(skill.getAsString());
                }
            }
        }
        merged.addAll(pathSkills);
        JsonArray skills = new JsonArray();
        merged.forEach(skills::add);
        profession.add("skills", skills);
    }

    private static void copy(JsonObject from, JsonObject to, String key) {
        if (from.has(key)) to.add(key, from.get(key).deepCopy());
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive()
                && object.getAsJsonPrimitive(key).isString()
                ? object.get(key).getAsString() : "";
    }
}
