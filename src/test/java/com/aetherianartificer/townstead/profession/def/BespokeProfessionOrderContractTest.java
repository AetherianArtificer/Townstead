package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.farming.cellplan.FieldPostConfig;
import com.aetherianartificer.townstead.work.WorkActivities;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Contracts for vanilla professions whose work is driven by a spatial, bespoke task engine. */
class BespokeProfessionOrderContractTest {

    @Test
    void fishermanOffersItsExistingFishingLoopAsAnActivity() {
        WorkTaskDef fish = tasks("fisherman").get(0);

        assertEquals(WorkTaskTypes.FISH, fish.type());
        assertNotNull(fish.order());
        assertEquals("Fish", fish.order().name());
        assertEquals(id("minecraft:fishing_rod"), fish.order().icon());

        WorkActivities.bootstrap();
        assertTrue(WorkActivities.isKnown(WorkTaskTypes.FISH),
                "the activity catalogue must have an executable fishing probe");
    }

    @Test
    void shepherdOffersShearingButNeverMakesStorageOptional() {
        List<WorkTaskDef> tasks = tasks("shepherd");
        WorkTaskDef shear = tasks.stream()
                .filter(task -> task.type().equals(WorkTaskTypes.SHEAR))
                .findFirst().orElseThrow();
        WorkTaskDef store = tasks.stream()
                .filter(task -> task.type().equals(WorkTaskTypes.STORE))
                .findFirst().orElseThrow();

        assertNotNull(shear.order());
        assertEquals("Shear sheep", shear.order().name());
        assertEquals(id("minecraft:shears"), shear.order().icon());
        assertNull(store.order(),
                "depositing carried wool is mandatory pipeline work, not a pausable activity");

        WorkActivities.bootstrap();
        assertTrue(WorkActivities.isKnown(WorkTaskTypes.SHEAR),
                "the activity catalogue must have an executable Pen/shearing probe");
        assertFalse(WorkActivities.isKnown(WorkTaskTypes.STORE),
                "mandatory storage must not leak into the activity catalogue");
    }

    @Test
    void farmerKeepsTheFieldPostAsItsOrderSheet() {
        WorkTaskDef harvest = tasks("farmer").get(0);
        assertEquals(WorkTaskTypes.HARVEST, harvest.type());
        assertNull(harvest.order(),
                "Farmer must not acquire a second, generic Order Sheet beside the Field Post");

        Set<String> controls = Arrays.stream(FieldPostConfig.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .collect(Collectors.toSet());
        assertTrue(controls.containsAll(Set.of(
                        "patternId", "tierCap", "radius", "priority", "autoSeedMode",
                        "seedFilter", "waterEnabled", "maxWaterCells", "groomEnabled",
                        "groomRadius", "rotationEnabled", "rotationPatterns", "cellPlan")),
                "the specialized surface must retain crop, layout, irrigation, grooming and priority controls");
    }

    private static List<WorkTaskDef> tasks(String profession) {
        JsonObject work = resource("/data/minecraft/profession/" + profession + "/work.json");
        return work.getAsJsonArray("tasks").asList().stream()
                .map(element -> WorkTaskDef.parse(element.getAsJsonObject()))
                .toList();
    }

    private static JsonObject resource(String path) {
        InputStream in = BespokeProfessionOrderContractTest.class.getResourceAsStream(path);
        assertNotNull(in, "missing fixture " + path);
        return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
