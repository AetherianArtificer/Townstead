package com.aetherianartificer.townstead.performance;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PerformanceMappingsTest {
    @Test
    void targetsUseExplicitPriorityThenAuthorOrder() {
        ResourceLocation semantic = id("test:toast");
        List<PerformanceMappings.Target> targets = PerformanceMappings.parse(semantic,
                JsonParser.parseString("""
                    {"schema":"townstead:performance_mapping/v1","targets":[
                      {"provider":"test:late","performance":"test:b","priority":2},
                      {"provider":"test:first","performance":"test:a","priority":10},
                      {"provider":"test:second","performance":"test:c","priority":10}
                    ]}
                    """).getAsJsonObject());
        PerformanceMappings.replaceForTest(Map.of(semantic, targets));

        assertEquals("test:first", targets.get(0).provider());
        assertEquals("test:second", targets.get(1).provider());
        assertEquals("test:late", targets.get(2).provider());
        assertEquals(targets, PerformanceMappings.targets(semantic));
    }

    @Test
    void rejectsUnknownMappingFields() {
        assertThrows(IllegalArgumentException.class, () -> PerformanceMappings.parse(id("test:bad"),
                JsonParser.parseString("""
                    {"schema":"townstead:performance_mapping/v1","targets":[
                      {"provider":"test:p","performance":"test:a","mystery":true}
                    ]}
                    """).getAsJsonObject()));
    }

    private static ResourceLocation id(String raw) { return ResourceLocation.tryParse(raw); }
}
