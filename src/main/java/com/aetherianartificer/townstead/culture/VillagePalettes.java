package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Block swaps for culture villages, from {@code data/<ns>/village_palette/<path>.json}. A palette
 * names the villages it dresses (structures, or every village of some cultures), optionally the
 * biomes it is for, and the swaps: whole material families, which carry over to their stairs,
 * slabs, walls, fences and so on, and single blocks. Palettes that apply are layered by priority,
 * lowest first, so a culture's base look can be reworked by a village type and then by a biome.
 *
 * <pre>{@code
 * {
 *   "schema": "townstead:village_palette/v1",
 *   "cultures": ["townstead_cavalrosia:cavalrosia"],
 *   "biomes": ["#minecraft:is_taiga", "minecraft:snowy_plains"],
 *   "priority": 10,
 *   "families": { "oak": "spruce", "stone_bricks": "deepslate_bricks" },
 *   "blocks": { "minecraft:red_banner": "minecraft:blue_banner" }
 * }
 * }</pre>
 *
 * <p>A village reads its biome once, at its centre, so every building of it is dressed alike. A swap
 * that would change what a building is recognized as is dropped: blocks villagers work or sleep at,
 * and blocks a building type counts where their replacement would not.</p>
 */
public final class VillagePalettes {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/VillagePalettes");
    private static final String SCHEMA = "townstead:village_palette/v1";
    private static final List<String> WOOD_SUFFIXES = List.of("_stairs", "_slab", "_fence", "_fence_gate", "_door",
            "_trapdoor", "_button", "_pressure_plate", "_sign", "_wall_sign", "_hanging_sign", "_wall_hanging_sign");
    private static final List<String> STONE_SUFFIXES = List.of("_stairs", "_slab", "_wall");
    private static volatile List<Palette> palettes = List.of();
    private static final Map<String, Map<Block, Block>> RESOLVED = new ConcurrentHashMap<>();

    /** One palette, its swaps already resolved to blocks. */
    public record Palette(ResourceLocation id, List<ResourceLocation> villages, List<ResourceLocation> cultures,
                          List<String> biomes, int priority, Map<Block, Block> swaps) {
        boolean dresses(ResourceLocation structure, @Nullable ResourceLocation culture) {
            if (villages.isEmpty() && cultures.isEmpty()) return false;
            if (villages.contains(structure)) return true;
            for (ResourceLocation listed : cultures) {
                if (culture != null && Cultures.within(culture, listed)) return true;
            }
            return false;
        }

        boolean suits(@Nullable Holder<Biome> biome) {
            if (biomes.isEmpty()) return true;
            if (biome == null) return false;
            for (String entry : biomes) {
                if (entry.startsWith("#")) {
                    ResourceLocation tag = ResourceLocation.tryParse(entry.substring(1));
                    if (tag != null && biome.is(TagKey.create(Registries.BIOME, tag))) return true;
                } else if (biome.unwrapKey().map(key -> key.location().toString().equals(entry)).orElse(false)) {
                    return true;
                }
            }
            return false;
        }
    }

    private VillagePalettes() {}

    public static boolean any() {
        return !palettes.isEmpty();
    }

    /** Whether any palette could dress this village structure, whatever its biome. */
    public static boolean dresses(ResourceLocation structure) {
        ResourceLocation culture = CultureVillages.culture(structure);
        for (Palette palette : palettes) if (palette.dresses(structure, culture)) return true;
        return false;
    }

    /** The swaps for one village, every palette that suits it layered in order; empty when none. */
    public static Map<Block, Block> swapsFor(ResourceLocation structure, @Nullable Holder<Biome> biome) {
        String key = structure + "|" + (biome == null ? "" : biome.unwrapKey().map(k -> k.location().toString()).orElse(""));
        return RESOLVED.computeIfAbsent(key, ignored -> {
            ResourceLocation culture = CultureVillages.culture(structure);
            Map<Block, Block> swaps = new LinkedHashMap<>();
            for (Palette palette : palettes) {
                if (palette.dresses(structure, culture) && palette.suits(biome)) swaps = layer(swaps, palette.swaps());
            }
            return Map.copyOf(com.aetherianartificer.townstead.compat.mca.RecognitionSafeSwaps.filter(swaps));
        });
    }

    /** Applies {@code over} on top of {@code under}: under's results are swapped again, and over's own swaps join. */
    static <T> Map<T, T> layer(Map<T, T> under, Map<T, T> over) {
        Map<T, T> out = new LinkedHashMap<>();
        under.forEach((from, to) -> out.put(from, over.getOrDefault(to, to)));
        over.forEach(out::putIfAbsent);
        out.entrySet().removeIf(entry -> entry.getKey().equals(entry.getValue()));
        return out;
    }

    /**
     * Every block id of material family {@code from} paired with the same member of {@code to}, by
     * name: a wood family ({@code oak}) holds its planks, logs, wood and their stripped forms, leaves,
     * stairs, slabs, fences, doors and signs; any other family ({@code stone_bricks}) holds the block
     * itself and its stairs, slabs and walls. Members either family lacks are left out.
     */
    static Map<String, String> family(String from, String to, Predicate<String> exists) {
        Map<String, String> out = new LinkedHashMap<>();
        boolean wood = exists.test(from + "_planks") && exists.test(to + "_planks");
        List<String[]> members = new ArrayList<>();
        if (wood) {
            for (String member : List.of("_planks", "_leaves")) members.add(new String[]{from + member, to + member});
            // Nether woods grow stems and hyphae where overworld woods have logs and wood.
            String fromLog = exists.test(from + "_log") ? "_log" : "_stem";
            String toLog = exists.test(to + "_log") ? "_log" : "_stem";
            String fromWood = fromLog.equals("_log") ? "_wood" : "_hyphae";
            String toWood = toLog.equals("_log") ? "_wood" : "_hyphae";
            members.add(new String[]{from + fromLog, to + toLog});
            members.add(new String[]{from + fromWood, to + toWood});
            members.add(new String[]{"stripped_" + from + fromLog, "stripped_" + to + toLog});
            members.add(new String[]{"stripped_" + from + fromWood, "stripped_" + to + toWood});
            for (String suffix : WOOD_SUFFIXES) members.add(new String[]{from + suffix, to + suffix});
        } else {
            members.add(new String[]{from, to});
            String fromStem = stem(from), toStem = stem(to);
            for (String suffix : STONE_SUFFIXES) members.add(new String[]{fromStem + suffix, toStem + suffix});
        }
        for (String[] pair : members) {
            if (exists.test(pair[0]) && exists.test(pair[1])) out.put(pair[0], pair[1]);
        }
        return out;
    }

    /** The name a material's stairs and slabs are built on: {@code stone_bricks} gives {@code stone_brick}. */
    static String stem(String family) {
        return family.endsWith("bricks") || family.endsWith("tiles") ? family.substring(0, family.length() - 1) : family;
    }

    static void replace(List<Palette> loaded) {
        palettes = List.copyOf(loaded);
        RESOLVED.clear();
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        private static final Gson GSON = new Gson();

        public Loader() {
            super(GSON, "village_palette");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resources,
                             ProfilerFiller profiler) {
            List<Palette> loaded = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    loaded.add(parse(entry.getKey(), GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString())));
                } catch (RuntimeException error) {
                    LOGGER.warn("Village palette {} rejected: {}", entry.getKey(), error.getMessage());
                }
            }
            loaded.sort(Comparator.comparingInt(Palette::priority).thenComparing(palette -> palette.id().toString()));
            replace(loaded);
            LOGGER.info("Loaded {} village palette(s)", loaded.size());
        }

        private static Palette parse(ResourceLocation id, JsonObject root) {
            TownsteadSchema.validate(root, SCHEMA);
            List<String> biomes = strings(root, "biomes");
            int priority = GsonHelper.getAsInt(root, "priority", biomes.isEmpty() ? 0 : 10);
            Map<Block, Block> swaps = new LinkedHashMap<>();
            List<String> missing = new ArrayList<>();
            JsonObject families = GsonHelper.getAsJsonObject(root, "families", new JsonObject());
            for (Map.Entry<String, JsonElement> family : families.entrySet()) {
                String from = family.getKey(), to = GsonHelper.convertToString(family.getValue(), from);
                Map<String, String> members = family(path(from), path(to), name -> block(namespace(from), name) != null);
                if (members.isEmpty()) missing.add(from + " -> " + to);
                members.forEach((a, b) -> swaps.put(block(namespace(from), a), block(namespace(to), b)));
            }
            JsonObject blocks = GsonHelper.getAsJsonObject(root, "blocks", new JsonObject());
            for (Map.Entry<String, JsonElement> single : blocks.entrySet()) {
                Block from = block(single.getKey()), to = block(GsonHelper.convertToString(single.getValue(), single.getKey()));
                if (from == null || to == null) missing.add(single.getKey());
                else swaps.put(from, to);
            }
            if (!missing.isEmpty()) LOGGER.warn("Village palette {}: no such block or family: {}", id, String.join(", ", missing));
            return new Palette(id, ids(root, "villages"), ids(root, "cultures"), biomes, priority, swaps);
        }

        private static List<String> strings(JsonObject root, String key) {
            List<String> out = new ArrayList<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(root, key, new JsonArray())) out.add(element.getAsString());
            return out;
        }

        private static List<ResourceLocation> ids(JsonObject root, String key) {
            List<ResourceLocation> out = new ArrayList<>();
            for (String raw : strings(root, key)) {
                ResourceLocation id = DataPackLang.parseId(raw);
                if (id == null) throw new IllegalArgumentException("'" + raw + "' is not an id");
                out.add(id);
            }
            return out;
        }

        private static String namespace(String id) {
            int colon = id.indexOf(':');
            return colon < 0 ? "minecraft" : id.substring(0, colon);
        }

        private static String path(String id) {
            int colon = id.indexOf(':');
            return colon < 0 ? id : id.substring(colon + 1);
        }

        private static @Nullable Block block(String namespace, String path) {
            return block(namespace + ":" + path);
        }

        private static @Nullable Block block(String id) {
            ResourceLocation location = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
            if (location == null || !BuiltInRegistries.BLOCK.containsKey(location)) return null;
            Block block = BuiltInRegistries.BLOCK.get(location);
            return block == Blocks.AIR && !location.getPath().equals("air") ? null : block;
        }
    }
}
