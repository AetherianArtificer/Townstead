package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.aetherianartificer.townstead.pheno.condition.types.ConstantConditionType;
import com.aetherianartificer.townstead.pheno.value.ValueTypes;
import com.aetherianartificer.townstead.pheno.value.types.StandingValueType;
import com.aetherianartificer.townstead.pheno.value.types.VillageNeedsValueType;
import com.aetherianartificer.townstead.pheno.value.types.VillageSpiritTierValueType;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Loads the bundled bond and faction kinds the way the reload listener does. */
final class PoliticsFixtures {
    private PoliticsFixtures() {}

    static void registerPheno() {
        ConditionTypes.register(new ConstantConditionType());
        ValueTypes.register(new StandingValueType());
        ValueTypes.register(new VillageNeedsValueType());
        ValueTypes.register(new VillageSpiritTierValueType());
    }

    static void load() {
        registerPheno();
        Map<ResourceLocation, BondKind> bonds = bonds();
        BondKinds.replaceAll(bonds);
        PoliticalDefinitions.replace(kinds());
    }

    static void clear() {
        BondKinds.replaceAll(Map.of());
        PoliticalDefinitions.replace(Map.of());
    }

    static Map<ResourceLocation, BondKind> bonds() {
        return parse("bond", (id, json) -> BondKind.parse(id, json, Map.of()));
    }

    static Map<ResourceLocation, FactionKind> kinds() {
        return parse("faction", (id, json) -> FactionKind.parse(id, json, Map.of()));
    }

    static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    static JsonObject resource(String path) throws Exception {
        Path file = Path.of(Objects.requireNonNull(PoliticsFixtures.class.getClassLoader().getResource(path)).toURI());
        return json(Files.readString(file));
    }

    static ResourceLocation id(String value) {
        ResourceLocation parsed = DataPackLang.parseId(value);
        if (parsed == null) throw new IllegalArgumentException(value);
        return parsed;
    }

    private static <T> Map<ResourceLocation, T> parse(String directory, Parser<T> parser) {
        try {
            Path root = Path.of(Objects.requireNonNull(PoliticsFixtures.class.getClassLoader()
                    .getResource("data/townstead/" + directory)).toURI());
            Map<ResourceLocation, T> out = new LinkedHashMap<>();
            try (var files = Files.list(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                    String name = file.getFileName().toString();
                    ResourceLocation id = id("townstead:" + name.substring(0, name.length() - 5));
                    out.put(id, parser.parse(id, json(Files.readString(file))));
                }
            }
            return out;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @FunctionalInterface
    private interface Parser<T> {
        T parse(ResourceLocation id, JsonObject json);
    }
}
