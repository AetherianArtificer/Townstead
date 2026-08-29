package com.aetherianartificer.townstead.compat;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConditionalCompatIndexTest {
    @Test
    void processedResourcesIndexEveryRelocatedCompatBuilding() throws Exception {
        var input = getClass().getResourceAsStream("/townstead_compat/index.txt");
        assertNotNull(input, "processResources must generate the compat building index");
        List<String> entries;
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            entries = reader.lines().filter(line -> !line.isBlank()).toList();
        }
        assertFalse(entries.isEmpty());
        assertEquals(entries.size(), entries.stream().distinct().count());
        assertTrue(entries.contains("building_types/compat/butchery/tannery.json"));
        assertTrue(entries.contains("building_types/compat/farmersdelight/kitchen_l1.json"));
    }
}
