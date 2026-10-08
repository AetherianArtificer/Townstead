package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.social.RelationshipQuality;
import com.aetherianartificer.townstead.social.RelationshipQualities;
import com.aetherianartificer.townstead.social.RelationshipScenario;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Runs authored relationship stories through the production ledger without launching Minecraft. */
public final class SocialSim {
    private SocialSim() {}
    public static int run(SimArgs args) throws IOException {
        Path data = args.path("data", "src/main/resources/data");
        loadQualities(data.resolve("townstead/relationship_quality"));
        Path selected = args.path("scenario", data.resolve("townstead/relationship_scenario").toString());
        var paths = new java.util.ArrayList<Path>();
        if (Files.isDirectory(selected)) try (var files = Files.list(selected)) {
            paths.addAll(files.filter(path -> path.toString().endsWith(".json")).sorted().toList());
        } else paths.add(selected);
        if (paths.isEmpty()) { System.out.println("No relationship scenarios found under " + selected.toAbsolutePath()); return 2; }
        int failed = 0;
        for (Path path : paths) {
            RelationshipScenario scenario = RelationshipScenario.parse(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
            RelationshipScenario.Result result = scenario.run();
            SimOutput.heading("social scenario: " + scenario.id());
            result.trace().forEach(line -> System.out.println("  " + line));
            if (!result.passed()) {
                failed++; result.failures().forEach(line -> System.out.println("  FAIL: " + line));
            } else System.out.println("  PASS");
        }
        return failed == 0 ? 0 : 1;
    }

    private static void loadQualities(Path root) throws IOException {
        Map<ResourceLocation, RelationshipQuality> definitions = new LinkedHashMap<>();
        if (Files.isDirectory(root)) try (var files = Files.list(root)) {
            for (Path path : files.filter(file -> file.toString().endsWith(".json")).toList()) {
                String name = path.getFileName().toString().replace(".json", "");
                ResourceLocation id = ResourceLocation.parse("townstead:" + name);
                definitions.put(id, RelationshipQuality.parse(id,
                        JsonParser.parseString(Files.readString(path)).getAsJsonObject(), Map.of()));
            }
        }
        RelationshipQualities.replaceAll(definitions);
    }
}
