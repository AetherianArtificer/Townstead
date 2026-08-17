package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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

/**
 * Loads {@code bond_kind} definitions off disk into the same registry the
 * server fills, so the harness honours a pack's own arity and pairing rules
 * rather than assuming Townstead's.
 */
public final class SimBondKinds {

    private static final Gson GSON = new Gson();

    private SimBondKinds() {}

    public static List<String> load(Path dataRoot, Map<String, String> lang) throws IOException {
        Map<ResourceLocation, BondKind> kinds = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();
        if (!Files.isDirectory(dataRoot)) return warnings;

        try (Stream<Path> namespaces = Files.list(dataRoot)) {
            for (Path namespaceDir : namespaces.filter(Files::isDirectory).toList()) {
                Path dir = namespaceDir.resolve("bond_kind");
                if (!Files.isDirectory(dir)) continue;
                String namespace = namespaceDir.getFileName().toString();
                try (Stream<Path> files = Files.walk(dir)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                        String path = dir.relativize(file).toString().replace('\\', '/');
                        path = path.substring(0, path.length() - ".json".length());
                        ResourceLocation id = DataPackLang.parseId(namespace + ":" + path);
                        if (id == null) continue;
                        try {
                            JsonObject json = GsonHelper.convertToJsonObject(
                                    GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                                            JsonElement.class), id.toString());
                            kinds.put(id, BondKind.parse(id, json, lang));
                        } catch (Exception ex) {
                            warnings.add("failed to parse bond_kind " + id + ": " + ex.getMessage());
                        }
                    }
                }
            }
        }
        BondKinds.replaceAll(kinds);
        return warnings;
    }
}
