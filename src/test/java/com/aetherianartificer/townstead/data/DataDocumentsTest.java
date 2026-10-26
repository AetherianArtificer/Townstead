package com.aetherianartificer.townstead.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shared pre-parse pass: gates drop documents, met overrides merge in order, patches apply by
 * priority, and the control keys never reach a loader.
 */
class DataDocumentsTest {

    @BeforeEach
    void namespaces() {
        DataNamespaces.setForTesting(Set.of("townstead_classic", "runic_roots"));
    }

    @AfterEach
    void reset() {
        DataNamespaces.setForTesting(Set.of());
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static ResourceLocation id(String s) {
        return DataPackLang.parseId(s);
    }

    @Test
    void unmetGateDropsDocument() {
        assertNull(DataDocuments.applyOverrides(json("{\"requires\": {\"namespace\": \"missing\"}}"), "t"));
        assertNull(DataDocuments.applyOverrides(json("{\"requires\": {\"bogus\": 1}}"), "t"));
        JsonObject kept = DataDocuments.applyOverrides(json("{\"requires\": {\"namespace\": \"runic_roots\"}, \"a\": 1}"), "t");
        assertNotNull(kept);
        assertFalse(kept.has("requires"));
    }

    @Test
    void metOverridesMergeInOrder() {
        JsonObject resolved = DataDocuments.applyOverrides(json("""
                {
                  "ancestry": "runic_roots:elven",
                  "demonym": {"singular": "High Elf", "plural": "High Elves"},
                  "genes": ["a", "b"],
                  "overrides": [
                    {"requires": {"namespace": "townstead_classic"}, "ancestry": "townstead_classic:elf",
                     "demonym": {"plural": "High Folk"}, "genes": ["c"]},
                    {"requires": {"namespace": "missing"}, "ancestry": "nope"},
                    {"genes": null}
                  ]
                }"""), "t");
        assertNotNull(resolved);
        assertEquals("townstead_classic:elf", resolved.get("ancestry").getAsString());
        assertEquals("High Elf", resolved.getAsJsonObject("demonym").get("singular").getAsString(),
                "objects merge key by key");
        assertEquals("High Folk", resolved.getAsJsonObject("demonym").get("plural").getAsString());
        assertFalse(resolved.has("genes"), "null removes; a later override sees the earlier result");
        assertFalse(resolved.has("overrides"));
    }

    @Test
    void patchesApplyByPriorityAndUnionLists() {
        DataPatches.Patch low = DataPatches.parse(id("p:low"), json("""
                {"schema": "townstead:patch/v1", "target": {"ancestry": "townstead_classic:elf"},
                 "priority": 0, "set": {"spawn_bias": {"default": 0.5}},
                 "add": {"genes": ["x", {"gene": "a", "occurrence": 0.2}]}}"""));
        DataPatches.Patch high = DataPatches.parse(id("p:high"), json("""
                {"schema": "townstead:patch/v1", "target": [{"ancestry": ["townstead_classic:elf"]}],
                 "priority": 5, "set": {"spawn_bias": {"default": 0.9}},
                 "remove": {"genes": ["b"]},
                 "replace": {"genes": {"a": "z"}},
                 "multiply": {"spawn_bias.default": 2}}"""));
        DataPatches.Patch other = DataPatches.parse(id("p:other"), json("""
                {"schema": "townstead:patch/v1", "target": {"lineage": "townstead_classic:elf"},
                 "set": {"display_name": "wrong kind"}}"""));
        DataPatches.Patch gated = DataPatches.parse(id("p:gated"), json("""
                {"schema": "townstead:patch/v1", "requires": {"namespace": "missing"},
                 "target": {"ancestry": "townstead_classic:elf"}, "set": {"x": 1}}"""));
        assertNull(gated);
        assertNull(DataPatches.parse(id("p:bad"), json("{\"schema\": \"townstead:patch/v1\", \"target\": 3}")));
        assertNull(DataPatches.parse(id("p:noschema"), json("{\"target\": {\"ancestry\": \"a:b\"}}")));

        DataPatches patches = new DataPatches(List.of(high, other, low));
        JsonObject base = json("{\"genes\": [{\"gene\": \"a\", \"occurrence\": 0.7}, \"b\"], \"display_name\": \"Elf\"}");
        JsonObject patched = patches.apply("ancestry", id("townstead_classic:elf"), base);

        assertNotSame(base, patched, "patching never mutates the input");
        assertEquals("Elf", patched.get("display_name").getAsString());
        assertEquals(1.8, patched.getAsJsonObject("spawn_bias").get("default").getAsDouble(), 1e-9,
                "high priority set wins, then multiply");
        assertEquals(List.of("z", "x"), patched.getAsJsonArray("genes").asList().stream()
                .map(DataDocuments::identity).toList(), "add is a union; replace keeps occurrence; remove drops b");
        assertEquals(0.7, patched.getAsJsonArray("genes").get(0).getAsJsonObject().get("occurrence").getAsDouble());
    }

    @Test
    void originTargetsAreRoots() {
        DataPatches.Patch patch = DataPatches.parse(id("p:o"), json("""
                {"schema": "townstead:patch/v1", "target": {"origin": "townstead_classic:high_elf"},
                 "set": {"x": 1}}"""));
        DataPatches patches = new DataPatches(List.of(patch));
        assertTrue(patches.apply("root", id("townstead_classic:high_elf"), json("{}")).has("x"));
        assertTrue(patches.apply("origin", id("townstead_classic:high_elf"), json("{}")).has("x"));
    }

    @Test
    void nonArrayAddOnlyFillsAbsentKeys() {
        JsonObject target = json("{\"spawn_bias\": {\"biomes\": {\"minecraft:plains\": 2}}}");
        DataPatches.applyPatch(json("""
                {"add": {"spawn_bias.biomes.minecraft:plains": 9, "spawn_bias.biomes.minecraft:forest": 3}}"""),
                target, "t");
        JsonObject biomes = target.getAsJsonObject("spawn_bias").getAsJsonObject("biomes");
        assertEquals(2, biomes.get("minecraft:plains").getAsInt());
        assertEquals(3, biomes.get("minecraft:forest").getAsInt());
    }
}
