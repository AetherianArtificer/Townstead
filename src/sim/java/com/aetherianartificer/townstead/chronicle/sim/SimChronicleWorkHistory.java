package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.chronicle.pregen.ChronicleWorkHistories;
import com.aetherianartificer.townstead.chronicle.pregen.ChronicleWorkHistory;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Loads Chronicle work histories off disk into the registry the server fills. */
public final class SimChronicleWorkHistory {

    private static final Gson GSON = new Gson();

    private SimChronicleWorkHistory() {}

    public static List<String> load(Path dataRoot) throws IOException {
        Map<ResourceLocation, ChronicleWorkHistory> parsed = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        if (!Files.isDirectory(dataRoot)) return warnings;
        try (Stream<Path> namespaces = Files.list(dataRoot)) {
            for (Path namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
                Path dir = namespaceDir.resolve("chronicle_work_history");
                if (!Files.isDirectory(dir)) continue;
                String namespace = namespaceDir.getFileName().toString();
                try (Stream<Path> files = Files.walk(dir)) {
                    for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                        String path = dir.relativize(file).toString().replace('\\', '/');
                        path = path.substring(0, path.length() - ".json".length());
                        ResourceLocation id = DataPackLang.parseId(namespace + ":" + path);
                        if (id == null) continue;
                        try {
                            parsed.put(id, ChronicleWorkHistory.parse(id, GsonHelper.convertToJsonObject(
                                    GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                                            JsonElement.class), id.toString())));
                        } catch (Exception ex) {
                            warnings.add("failed to parse Chronicle work history "
                                    + id + ": " + ex.getMessage());
                        }
                    }
                }
            }
        }
        ChronicleWorkHistories.replaceAll(parsed);
        return warnings;
    }
}
