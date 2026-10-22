package com.aetherianartificer.townstead.pheno.state;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A persistent state written through an owned backing stores its amount in a resource power of
 * the same id. Without that power the state can be neither read nor set, so it silently does
 * nothing (werewolf villagers never changed form for this reason).
 */
class StateResourceCoverageTest {
    @Test
    void everyShippedPersistentStateHasItsBaselineResource() throws Exception {
        URL url = getClass().getClassLoader().getResource("data/townstead_state/entity_state");
        assertNotNull(url);
        Path states = Path.of(url.toURI());
        Path baselines = states.resolveSibling("baseline_power");
        List<String> missing = new ArrayList<>();
        try (var files = Files.list(states)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                JsonObject state;
                try (Reader reader = Files.newBufferedReader(file)) {
                    state = JsonParser.parseReader(reader).getAsJsonObject();
                }
                String persistence = state.has("persistence") ? state.get("persistence").getAsString() : "persistent";
                if (!"persistent".equals(persistence)) continue;
                if (!Files.isRegularFile(baselines.resolve(file.getFileName()))) missing.add(file.getFileName().toString());
            }
        }
        assertTrue(missing.isEmpty(), "persistent states with no baseline_power resource: " + missing);
    }
}
