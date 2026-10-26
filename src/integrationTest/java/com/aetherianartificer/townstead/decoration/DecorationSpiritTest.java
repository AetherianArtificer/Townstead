package com.aetherianartificer.townstead.decoration;

import com.aetherianartificer.townstead.spirit.*;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DecorationSpiritTest {
    // TagKey.create touches Registries, which on 1.20.1 requires a bootstrapped registry root.
    // NeoForge's bootstrap fails outside the game, and 1.21 does not need it.
    @BeforeAll static void bootstrap() {
        //? if <1.21 {
        /*SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        *///?}
    }

    @Test void everyBundledDecorationHasOneSmallContribution() throws Exception {
        for (String name : List.of("cool_spot", "flower_bed", "fountain", "haystack", "hearth", "lamp_post", "well")) {
            try (var stream = getClass().getResourceAsStream("/data/townstead/decoration/" + name + ".json")) {
                assertNotNull(stream);
                var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                var definition = DecorationDefinition.parse(ResourceLocation.tryParse("townstead:" + name), json);
                assertNotNull(definition, name);
                assertEquals(1, definition.spirits().values().stream().mapToInt(Integer::intValue).sum(), name);
            }
        }
    }

    @Test void builtCountsAffectTotalsAndContributorsAndDisappearWhenRemoved() {
        var well = ResourceLocation.tryParse("townstead:well");
        var base = new VillageSpiritAggregator.Snapshot(new SpiritTotals(Map.of("nautical", 24), 24, 1),
                Map.of("nautical", List.of(new ContributorRow("dock", 1, 24))));
        var built = DecorationSpiritContributions.addTo(base, Map.of(well, 2), ignored -> Map.of("nautical", 1));
        assertEquals(26, built.totals().total());
        assertEquals(26, built.totals().pointsFor("nautical"));
        assertEquals(1, built.totals().contributingBuildings());
        assertEquals(1, VillageSpiritAggregator.readoutFor(built.totals()).tierIndex());
        var row = built.contributors().get("nautical").get(1);
        assertEquals(2, row.count()); assertEquals(2, row.points());
        assertEquals(well, DecorationSpiritContributions.decorationId(row.buildingType()));
        assertNull(DecorationSpiritContributions.decorationId("dock"));
        assertEquals(base, DecorationSpiritContributions.addTo(base, Map.of(), ignored -> Map.of("nautical", 1)));
        assertEquals(24, base.totals().total());
    }

    @Test void spiritDefinitionsRejectUnknownNegativeAndFractionalPoints() {
        var id = ResourceLocation.tryParse("test:decoration");
        for (String spirits : List.of("{\"unknown\":1}", "{\"natural\":-1}", "{\"natural\":0.5}", "[]")) {
            var json = JsonParser.parseString("{\"anchor\":\"minecraft:stone\",\"spirits\":" + spirits + "}").getAsJsonObject();
            assertNull(DecorationDefinition.parse(id, json));
        }
        var json = JsonParser.parseString("{\"anchor\":\"minecraft:stone\"}").getAsJsonObject();
        assertTrue(DecorationDefinition.parse(id, json).spirits().isEmpty());
    }
}
