package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.work.job.WorkJobDef;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises sibling TownsteadPacks Career data against this checkout's real parsers.
 *
 * <p>The test is automatically active for the normal sibling-repository layout and can be
 * pointed elsewhere with {@code TOWNSTEAD_PACKS_ROOT}. CI may check out both repositories and set
 * that variable; a Townstead-only checkout skips this one cross-repository contract.</p>
 */
class ExternalCareerPacksContractTest {

    @Test
    void beekeeperUsesTheVanillaHiveFamily() throws IOException {
        Path packsRoot = packsRoot();
        Assumptions.assumeTrue(Files.isDirectory(packsRoot),
                "TownsteadPacks careers are not present beside this checkout");

        Path professionWork = findBySuffix(packsRoot,
                "townstead_beekeeping/profession/beekeeper/work.json");
        Path harvestJob = findBySuffix(packsRoot,
                "townstead_beekeeping/work_job/beehive_harvest.json");
        Path apiary = findBySuffix(packsRoot,
                "mca/building_types/townstead_beekeeping/apiary.json");

        JsonObject work = object(professionWork);
        assertTrue(work.getAsJsonArray("poi").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(poi -> "townstead:building".equals(poi.get("type").getAsString())
                        && "townstead_beekeeping:apiary".equals(
                        poi.get("type_prefix").getAsString())));
        assertTrue(work.getAsJsonArray("poi").asList().stream()
                .map(JsonElement::getAsJsonObject)
                .anyMatch(poi -> "townstead:job_block".equals(poi.get("type").getAsString())
                        && "minecraft:beehive".equals(poi.get("block").getAsString())),
                "the exact vanilla POI remains the building-free fallback");
        assertTrue(work.getAsJsonArray("tasks").get(0).getAsJsonObject()
                .getAsJsonArray("workstations").asList().stream()
                .anyMatch(value -> "#minecraft:beehives".equals(value.getAsString())));
        assertTrue("#minecraft:beehives".equals(
                object(harvestJob).getAsJsonObject("target").get("block").getAsString()));
        assertTrue(object(apiary).getAsJsonObject("blocks").has("#minecraft:beehives"));
    }

    @Test
    void installedCareerPacksMatchCurrentTownsteadContracts() throws IOException {
        Path packsRoot = packsRoot();
        Assumptions.assumeTrue(Files.isDirectory(packsRoot),
                "TownsteadPacks careers are not present beside this checkout");

        List<Path> professionFiles;
        try (var paths = Files.walk(packsRoot)) {
            professionFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals("profession.json"))
                    .filter(path -> hasSegment(path, "profession"))
                    .sorted()
                    .toList();
        }
        assertFalse(professionFiles.isEmpty(),
                "No Career profession.json files found under " + packsRoot);

        List<String> failures = new ArrayList<>();
        registerWorkJobTasks(packsRoot, failures);
        for (Path professionFile : professionFiles) {
            try {
                validateCareer(professionFile);
            } catch (Exception error) {
                failures.add(packsRoot.relativize(professionFile) + ": "
                        + error.getMessage());
            }
        }
        assertTrue(failures.isEmpty(), "TownsteadPacks Career contract failures:\n - "
                + String.join("\n - ", failures));
    }

    /**
     * Runtime reloads Jobs before professions, and Job-authored task ids are part of the valid
     * profession vocabulary. Mirror that order here. The plain unit-test JVM cannot safely run
     * the world-aware action/condition parser, so this checks the stable Job envelope and leaves
     * its detailed Pheno primitives to their focused parser tests.
     */
    private static void registerWorkJobTasks(Path packsRoot, List<String> failures)
            throws IOException {
        List<Path> jobFiles;
        try (var paths = Files.walk(packsRoot)) {
            jobFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> hasSegment(path, "work_job"))
                    .sorted().toList();
        }
        for (Path jobFile : jobFiles) {
            try {
                DataLocation location = locateDataResource(jobFile, "work_job");
                JsonObject json = object(jobFile);
                TownsteadSchema.validateRequired(json, WorkJobDef.SCHEMA);
                ResourceLocation task = resource(json, "task");
                ResourceLocation type = resource(json, "type");
                if (task == null) throw new IllegalArgumentException("invalid or missing task");
                if (type == null) throw new IllegalArgumentException("invalid or missing type");
                if (WorkJobDef.BLOCK_INTERACTION.equals(type)) {
                    if (!json.has("target") || !json.get("target").isJsonObject()) {
                        throw new IllegalArgumentException("block interaction Job requires target");
                    }
                } else if (WorkJobDef.ENTITY_DELIVERY.equals(type)) {
                    if (!json.has("source") || !json.get("source").isJsonObject()
                            || !json.has("destination")
                            || !json.get("destination").isJsonObject()) {
                        throw new IllegalArgumentException(
                                "entity delivery Job requires source and destination");
                    }
                } else {
                    throw new IllegalArgumentException("unknown Job type '" + type + "'");
                }
                WorkTaskTypes.register(task);
            } catch (Exception error) {
                failures.add(packsRoot.relativize(jobFile) + ": " + error.getMessage());
            }
        }
    }

    private static void validateCareer(Path professionFile) throws IOException {
        CareerLocation location = locate(professionFile);
        JsonObject profession = object(professionFile).deepCopy();
        Map<String, Integer> skillTiers = new LinkedHashMap<>();

        Path progression = location.careerRoot().resolve("progression.json");
        if (Files.isRegularFile(progression)) {
            ProfessionProgressionOverlay.apply(profession, object(progression));
        }

        Path pathsRoot = location.careerRoot().resolve("path");
        if (Files.isDirectory(pathsRoot)) {
            List<Path> pathDocuments;
            try (var paths = Files.walk(pathsRoot)) {
                pathDocuments = paths.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().equals("path.json"))
                        .sorted().toList();
            }
            for (Path pathDocument : pathDocuments) {
                String pathId = pathsRoot.relativize(pathDocument.getParent())
                        .toString().replace('\\', '/');
                ProfessionPathDocument.Applied applied = ProfessionPathDocument.apply(
                        profession, pathId, object(pathDocument));
                skillTiers.putAll(applied.skillTiers());
            }
        }

        Path work = location.careerRoot().resolve("work.json");
        if (!Files.isRegularFile(work)) {
            throw new IllegalArgumentException("missing work.json beside profession.json");
        }
        ProfessionWorkOverlay.apply(profession, object(work));

        applyTrades(location.careerRoot(), profession);

        Map<String, String> language = language(location.packRoot(), location.namespace());
        Diagnostics diagnostics = new Diagnostics(location.id());
        ProfessionDef parsed = ProfessionDataLoader.parseProfession(
                location.id(), profession, language, diagnostics);
        assertNotNull(parsed, "profession parser returned null for " + location.id());
        if (diagnostics.hasErrors()) {
            throw new IllegalArgumentException(diagnostics.all().stream()
                    .map(Diagnostic::render).reduce((a, b) -> a + "; " + b).orElse("invalid Career"));
        }

        validateSkills(location, language, skillTiers);
        validateTranslationReferences(location.careerRoot(), language);
    }

    private static void applyTrades(Path careerRoot, JsonObject profession) throws IOException {
        List<Path> tradeFiles;
        try (var paths = Files.walk(careerRoot)) {
            tradeFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getParent() != null
                            && path.getParent().getFileName().toString().equals("trade"))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().toList();
        }
        for (Path trade : tradeFiles) {
            String relative = careerRoot.relativize(trade).toString().replace('\\', '/');
            String pathId = null;
            if (relative.startsWith("path/")) {
                String remainder = relative.substring("path/".length());
                pathId = remainder.substring(0, remainder.indexOf('/'));
            }
            ProfessionTradeDocument.apply(profession, object(trade), pathId);
        }
    }

    private static void validateSkills(CareerLocation location, Map<String, String> language,
                                       Map<String, Integer> skillTiers) throws IOException {
        List<Path> skillFiles;
        try (var paths = Files.walk(location.careerRoot())) {
            skillFiles = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getParent() != null
                            && path.getParent().getFileName().toString().equals("skill"))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted().toList();
        }
        for (Path skillFile : skillFiles) {
            JsonObject skill = object(skillFile).deepCopy();
            // Skill powers use the live Gene registry, which is initialized by the mod loader and
            // is intentionally unavailable to plain JUnit. Validate their stable envelope here,
            // then let the real skill parser exercise every loader-independent field.
            if (skill.has("power")) {
                if (!skill.get("power").isJsonObject()) {
                    throw new IllegalArgumentException(skillFile + " power must be an object");
                }
                ResourceLocation powerType = resource(skill.getAsJsonObject("power"), "type");
                if (powerType == null) {
                    throw new IllegalArgumentException(skillFile
                            + " power requires a valid resource-id type");
                }
                skill.remove("power");
            }
            String relative = location.careerRoot().relativize(skillFile)
                    .toString().replace('\\', '/');
            String pathId = relative.startsWith("path/")
                    ? relative.substring("path/".length(), relative.indexOf('/', "path/".length()))
                    : "";
            String localId = stripJson(skillFile.getFileName().toString());
            String authoredId = pathId.isBlank() ? localId : pathId + "/" + localId;
            skill.addProperty("profession", location.id().toString());
            if (!pathId.isBlank()) skill.addProperty("__townstead_path", pathId);
            Integer tier = skillTiers.get(authoredId);
            if (tier != null) skill.addProperty("tier", tier);

            //? if >=1.21 {
            ResourceLocation skillId = ResourceLocation.fromNamespaceAndPath(
                    location.namespace(), location.id().getPath() + "/" + authoredId);
            //?} else {
            /*ResourceLocation skillId = new ResourceLocation(
                    location.namespace(), location.id().getPath() + "/" + authoredId);
            *///?}
            Diagnostics diagnostics = new Diagnostics(skillId);
            SkillDef parsed = ProfessionDataLoader.parseSkill(skillId, skill, language, diagnostics);
            assertNotNull(parsed, "skill parser returned null for " + skillId);
            if (diagnostics.hasErrors()) {
                throw new IllegalArgumentException(diagnostics.all().stream()
                        .map(Diagnostic::render).reduce((a, b) -> a + "; " + b)
                        .orElse("invalid skill " + skillId));
            }
        }
    }

    private static void validateTranslationReferences(Path careerRoot, Map<String, String> language)
            throws IOException {
        Set<String> referenced = new LinkedHashSet<>();
        try (var paths = Files.walk(careerRoot)) {
            for (Path json : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                collectTranslations(object(json), referenced);
            }
        }
        List<String> missing = referenced.stream()
                .filter(key -> !language.containsKey(key)
                        && language.keySet().stream().noneMatch(candidate ->
                        candidate.startsWith(key + "/")))
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("missing en_us translation(s): "
                    + String.join(", ", missing));
        }
    }

    private static void collectTranslations(JsonElement element, Set<String> into) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectTranslations(child, into);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (object.has("translate") && object.get("translate").isJsonPrimitive()) {
            into.add(object.get("translate").getAsString());
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            collectTranslations(entry.getValue(), into);
        }
    }

    private static Map<String, String> language(Path packRoot, String namespace) throws IOException {
        Map<String, String> language = new LinkedHashMap<>();
        mergeLanguage(packRoot.resolve("assets").resolve(namespace)
                .resolve("lang").resolve("en_us.json"), language);
        mergeLanguage(packRoot.resolve("data").resolve(namespace)
                .resolve("lang").resolve("en_us.json"), language);
        return language;
    }

    private static void mergeLanguage(Path file, Map<String, String> into) throws IOException {
        if (!Files.isRegularFile(file)) return;
        for (Map.Entry<String, JsonElement> entry : object(file).entrySet()) {
            if (entry.getValue().isJsonPrimitive()) {
                into.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
    }

    private static CareerLocation locate(Path professionFile) {
        List<String> parts = new ArrayList<>();
        for (Path part : professionFile.toAbsolutePath().normalize()) parts.add(part.toString());
        int data = parts.lastIndexOf("data");
        int profession = parts.lastIndexOf("profession");
        if (data < 0 || profession != data + 2 || profession + 1 >= parts.size() - 1) {
            throw new IllegalArgumentException("expected data/<namespace>/profession/<career>/profession.json");
        }
        String namespace = parts.get(data + 1);
        String careerPath = String.join("/", parts.subList(profession + 1, parts.size() - 1));
        ResourceLocation id = ResourceLocation.tryBuild(namespace, careerPath);
        if (id == null) throw new IllegalArgumentException("invalid Career id " + namespace + ":" + careerPath);
        Path dataRoot = professionFile.getRoot();
        for (int i = 0; i < data; i++) dataRoot = dataRoot.resolve(parts.get(i));
        Path packRoot = dataRoot;
        Path careerRoot = professionFile.getParent();
        return new CareerLocation(packRoot, careerRoot, namespace, id);
    }

    private static DataLocation locateDataResource(Path file, String directory) {
        List<String> parts = new ArrayList<>();
        for (Path part : file.toAbsolutePath().normalize()) parts.add(part.toString());
        int data = parts.lastIndexOf("data");
        int resource = parts.lastIndexOf(directory);
        if (data < 0 || resource != data + 2 || resource + 1 >= parts.size()) {
            throw new IllegalArgumentException("expected data/<namespace>/" + directory
                    + "/<resource>.json");
        }
        String namespace = parts.get(data + 1);
        List<String> resourceParts = new ArrayList<>(
                parts.subList(resource + 1, parts.size()));
        int last = resourceParts.size() - 1;
        resourceParts.set(last, stripJson(resourceParts.get(last)));
        ResourceLocation id = ResourceLocation.tryBuild(namespace, String.join("/", resourceParts));
        if (id == null) throw new IllegalArgumentException("invalid resource id in " + file);
        return new DataLocation(id);
    }

    private static Path packsRoot() {
        String configured = System.getenv("TOWNSTEAD_PACKS_ROOT");
        if (configured != null && !configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        return Path.of(System.getProperty("user.dir"), "..", "TownsteadPacks", "careers")
                .toAbsolutePath().normalize();
    }

    private static boolean hasSegment(Path path, String segment) {
        for (Path part : path) if (part.toString().equals(segment)) return true;
        return false;
    }

    private static Path findBySuffix(Path root, String suffix) throws IOException {
        String normalizedSuffix = suffix.replace('\\', '/');
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().replace('\\', '/').endsWith(normalizedSuffix))
                    .findFirst()
                    .orElseThrow(() -> new IOException("Missing TownsteadPacks resource " + suffix));
        }
    }

    private static JsonObject object(Path path) throws IOException {
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("malformed JSON in " + path + ": " + error.getMessage(), error);
        }
    }

    private static String stripJson(String name) {
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    private static ResourceLocation resource(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return null;
        return ResourceLocation.tryParse(json.get(key).getAsString());
    }

    private record CareerLocation(Path packRoot, Path careerRoot, String namespace,
                                  ResourceLocation id) {}

    private record DataLocation(ResourceLocation id) {}
}
