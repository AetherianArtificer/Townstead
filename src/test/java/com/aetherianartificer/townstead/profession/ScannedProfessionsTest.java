package com.aetherianartificer.townstead.profession;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scan's eligibility and composition rules for installed Townstead profession definitions. */
class ScannedProfessionsTest {

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void gatedCareerIsEligible() {
        // The mods-gate path is untestable here (ModGate's default predicate needs ModList);
        // ModGateTest covers the grammar via the predicate overload.
        assertTrue(ScannedProfessions.eligible(obj(
                "{ 'schema': 'townstead:profession/v1', 'acquisition_routes': ['mentor'] }")),
                "gated careers register their own professions");
        assertTrue(ScannedProfessions.eligible(obj(
                "{ 'schema': 'townstead:profession/v2', 'acquisition_routes': ['mentor'] }")),
                "dir-layout v2 defs register too");
    }

    @Test
    void practicedCareerIsNotEligible() {
        assertFalse(ScannedProfessions.eligible(obj(
                "{ 'schema': 'townstead:profession/v1' }")),
                "practiced careers extend professions that already exist");
        assertFalse(ScannedProfessions.eligible(obj(
                "{ 'schema': 'townstead:profession/v2', 'acquisition_routes': [] }")));
        assertFalse(ScannedProfessions.eligible(obj(
                "{ 'schema': 'townstead:profession/v1', 'parents': ['townstead:cook'] }")),
                "parents is dead schema; it neither gates nor grants registration");
    }

    @Test
    void practicedCustomCareerCanExplicitlyRegister() {
        assertTrue(ScannedProfessions.eligible(obj(
                "{ 'schema': 'townstead:profession/v2', 'register_profession': true }")),
                "an early-installed practiced career may explicitly own a new villager profession");
    }

    @Test
    void globalCareerPackDirectorySuppliesRegistrationMetadata(@TempDir Path root) throws Exception {
        Path profession = root.resolve(
                "data/townstead_example/profession/beekeeper/profession.json");
        Path work = profession.resolveSibling("work.json");
        Files.createDirectories(profession.getParent());
        Files.writeString(profession,
                "{\"schema\":\"townstead:profession/v2\",\"display_name\":\"Beekeeper\"}");
        Files.writeString(work,
                "{\"schema\":\"townstead:profession_work/v1\"," +
                        "\"register_profession\":true," +
                        "\"poi\":[{\"type\":\"townstead:job_block\"," +
                        "\"block\":\"minecraft:beehive\"}]}");

        var found = new LinkedHashMap<net.minecraft.resources.ResourceLocation,
                ScannedProfessions.ScannedDef>();
        ScannedProfessions.collectDataRoot(root.resolve("data"), Set.of(), found);

        var id = net.minecraft.resources.ResourceLocation.tryParse("townstead_example:beekeeper");
        assertTrue(found.containsKey(id));
        assertTrue(found.get(id).jobBlocks().contains(
                net.minecraft.resources.ResourceLocation.tryParse("minecraft:beehive")));
    }

    @Test
    void kubeJsDataRootCanOwnARegisteredKubeJsProfession(@TempDir Path data) throws Exception {
        Path profession = data.resolve("kubejs/profession/herbalist/profession.json");
        Files.createDirectories(profession.getParent());
        Files.writeString(profession,
                "{\"schema\":\"townstead:profession/v2\"," +
                        "\"register_profession\":true}");

        var found = new LinkedHashMap<net.minecraft.resources.ResourceLocation,
                ScannedProfessions.ScannedDef>();
        ScannedProfessions.collectDataRoot(data, Set.of("kubejs"), found);

        var id = net.minecraft.resources.ResourceLocation.tryParse("kubejs:herbalist");
        assertTrue(found.containsKey(id));
        assertTrue(found.get(id).ownsNamespace("kubejs"),
                "the KubeJS source owns its conventional namespace during early registration");
    }

    @Test
    void registrationMetadataIncludesWorkSound() {
        assertEquals(net.minecraft.resources.ResourceLocation.tryParse(
                        "minecraft:entity.villager.work_butcher"),
                ScannedProfessions.workSound(obj(
                        "{ 'work_sound': 'minecraft:entity.villager.work_butcher' }")));
        assertEquals(null, ScannedProfessions.workSound(obj("{ 'work_sound': 'not an id' }")));
    }

    @Test
    void workSidecarCanSupplyRegistrationAndJobSite() {
        JsonObject profession = obj("{ 'schema': 'townstead:profession/v2' }");
        com.aetherianartificer.townstead.profession.def.ProfessionWorkOverlay.apply(profession,
                obj("{ 'schema': 'townstead:profession_work/v1', 'register_profession': true,"
                        + " 'poi': [{ 'type': 'townstead:job_block', 'block': 'minecraft:beehive' }],"
                        + " 'tasks': [{ 'type': 'townstead_work:interact' }] }"));

        assertTrue(ScannedProfessions.eligible(profession));
        assertTrue(ScannedProfessions.jobBlocks(profession).contains(
                net.minecraft.resources.ResourceLocation.tryParse("minecraft:beehive")));
        assertTrue(ScannedProfessions.taskTypes(profession).contains(
                net.minecraft.resources.ResourceLocation.tryParse("townstead_work:interact")));
    }

    @Test
    void scannedProfessionMatchesAnExistingPoiByItsAuthoredBlockId() {
        var beehive = net.minecraft.resources.ResourceLocation.tryParse("minecraft:beehive");
        var lectern = net.minecraft.resources.ResourceLocation.tryParse("minecraft:lectern");

        assertTrue(ScannedProfessions.matchesAuthoredBlock(
                Set.of(beehive), Set.of(beehive)));
        assertFalse(ScannedProfessions.matchesAuthoredBlock(
                Set.of(beehive), Set.of(lectern)));
    }

    @Test
    void pathSidecarComposesBeforeWorksiteAffinities() {
        JsonObject profession = obj("{ 'schema': 'townstead:profession/v2' }");
        com.aetherianartificer.townstead.profession.def.ProfessionPathsOverlay.apply(profession,
                obj("{ 'schema': 'townstead:profession_paths/v1',"
                        + " 'paths': [{ 'id': 'hive_keeper', 'gateway': 'smoker_use' }] }"));
        com.aetherianartificer.townstead.profession.def.ProfessionWorkOverlay.apply(profession,
                obj("{ 'schema': 'townstead:profession_work/v1',"
                        + " 'path_worksites': { 'hive_keeper': ['minecraft:beehive'] } }"));

        assertTrue(profession.getAsJsonArray("paths").get(0).getAsJsonObject()
                .getAsJsonArray("worksites").get(0).getAsString().equals("minecraft:beehive"));
    }

    @Test
    void individualPathDocumentComposesBeforeWorksiteAffinities() {
        JsonObject profession = obj("{ 'schema': 'townstead:profession/v2' }");
        com.aetherianartificer.townstead.profession.def.ProfessionPathDocument.apply(profession,
                "hive_keeper", obj("{ 'schema': 'townstead:profession_path/v1',"
                        + " 'skills': ['smoker_use'] }"));
        com.aetherianartificer.townstead.profession.def.ProfessionWorkOverlay.apply(profession,
                obj("{ 'schema': 'townstead:profession_work/v1',"
                        + " 'path_worksites': { 'hive_keeper': ['minecraft:beehive'] } }"));

        assertTrue(profession.getAsJsonArray("paths").get(0).getAsJsonObject()
                .getAsJsonArray("worksites").get(0).getAsString().equals("minecraft:beehive"));
    }

    @Test
    void foreignProfessionFolderIsNotEligible() {
        assertFalse(ScannedProfessions.eligible(obj(
                "{ 'acquisition_routes': ['mentor'] }")),
                "another mod's profession/ data folder must not be swept in");
        assertFalse(ScannedProfessions.eligible(obj(
                "{ 'schema': 'othermod:profession/v9', 'acquisition_routes': ['mentor'] }")));
    }

    @Test
    void jobBlocksComeFromJobBlockProvidersOnly() {
        var blocks = ScannedProfessions.jobBlocks(obj(
                "{ 'poi': [ { 'type': 'townstead:job_block', 'block': 'minecraft:lectern' },"
                        + " { 'type': 'townstead:job_block', 'blocks': ['minecraft:loom', 'minecraft:barrel'] },"
                        + " { 'type': 'townstead:building', 'type_prefix': 'compat/x/lab_l' } ] }"));
        assertTrue(blocks.contains(net.minecraft.resources.ResourceLocation.tryParse("minecraft:lectern")));
        assertTrue(blocks.contains(net.minecraft.resources.ResourceLocation.tryParse("minecraft:loom")));
        assertTrue(blocks.contains(net.minecraft.resources.ResourceLocation.tryParse("minecraft:barrel")));
        assertTrue(blocks.size() == 3, "building providers contribute no job blocks");
    }

    @Test
    void optOutIsRespected() {
        assertFalse(ScannedProfessions.eligible(obj(
                "{ 'schema': 'townstead:profession/v1', 'parents': ['townstead:cook'],"
                        + " 'register_profession': false }")));
    }
}
