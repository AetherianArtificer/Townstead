package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.Townstead;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Boot-time scan that turns advanced-profession defs bundled in mod jars into real
 * {@link VillagerProfession} registrations, so a data-defined specialization is a full
 * profession like any other. A def whose {@code poi} declares {@code townstead:job_block}
 * gets a real job-site POI: blocks nobody claims become a new {@link PoiType} under the def's
 * id, and blocks an existing POI type already claims (a blockstate may belong to only one
 * type) are accepted through that type instead. Defs with only building/always providers
 * register POI-less like {@code townstead:cook}.
 *
 * <p>Only jar-bundled defs can get this treatment: the profession registry freezes at
 * startup, so defs added by world data packs or {@code /reload} cannot register and instead
 * inherit their parent's Minecraft profession through the career layer.</p>
 *
 * <p>Eligible: {@code data/<ns>/profession/*.json} with the Townstead profession schema and a
 * non-empty {@code parents} list (root defs describe professions that already exist), unless
 * the def opts out with {@code "register_profession": false}. Ids already present in the
 * registry are skipped; {@code aliases} exist for converging on those instead.</p>
 */
public final class ScannedProfessions {

    private static final String SCHEMA = "townstead:profession/v1";

    public record ScannedDef(ResourceLocation id, Set<ResourceLocation> jobBlocks,
                             Set<String> providerModIds) {

        /**
         * A def may only register into namespaces its shipping jar owns, or that no installed
         * mod claims. This closes a registration-order race: an advanced def squatting on
         * another installed mod's namespace could otherwise win the RegisterEvent race and
         * make that mod's own profession registration collide at boot.
         */
        boolean ownsNamespace(String namespace) {
            return providerModIds.contains(namespace)
                    || !com.aetherianartificer.townstead.compat.ModCompat.isLoaded(namespace);
        }
    }

    private static volatile List<ScannedDef> scanned = null;
    /** Filled during the POI register event; read during the profession event that follows. */
    private static final Map<ResourceLocation, Set<ResourceKey<PoiType>>> POI_KEYS = new LinkedHashMap<>();

    private ScannedProfessions() {}

    /** Scanned advanced-profession defs, resolved lazily on first use during registration. */
    public static List<ScannedDef> defs() {
        List<ScannedDef> result = scanned;
        if (result == null) {
            synchronized (ScannedProfessions.class) {
                result = scanned;
                if (result == null) scanned = result = scan();
            }
        }
        return result;
    }

    public static List<ResourceLocation> ids() {
        return defs().stream().map(ScannedDef::id).toList();
    }

    //? if neoforge {
    public static void onRegister(net.neoforged.neoforge.registries.RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.POINT_OF_INTEREST_TYPE)) {
            registerPoiTypes((id, poi) -> event.register(Registries.POINT_OF_INTEREST_TYPE, id, () -> poi));
        } else if (event.getRegistryKey().equals(Registries.VILLAGER_PROFESSION)) {
            registerProfessions((id, profession) ->
                    event.register(Registries.VILLAGER_PROFESSION, id, () -> profession));
        }
    }
    //?} else {
    /*public static void onRegister(net.minecraftforge.registries.RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.POINT_OF_INTEREST_TYPE)) {
            registerPoiTypes((id, poi) -> event.register(Registries.POINT_OF_INTEREST_TYPE, id, () -> poi));
        } else if (event.getRegistryKey().equals(Registries.VILLAGER_PROFESSION)) {
            registerProfessions((id, profession) ->
                    event.register(Registries.VILLAGER_PROFESSION, id, () -> profession));
        }
    }
    *///?}

    private interface Sink<T> {
        void accept(ResourceLocation id, T value);
    }

    /**
     * POI phase: for each def's declared job blocks, split into states an existing POI type
     * already claims (accept that type) and unclaimed states (register a new type under the
     * def id). Namespace ownership keeps this race-free: a def never claims states for a
     * block belonging to another installed mod, because that mod may register its own POI
     * later in the same event and a blockstate may belong to only one type.
     */
    private static void registerPoiTypes(Sink<PoiType> sink) {
        POI_KEYS.clear();
        for (ScannedDef def : defs()) {
            if (def.jobBlocks().isEmpty()) continue;
            Set<ResourceKey<PoiType>> keys = new LinkedHashSet<>();
            Set<BlockState> unclaimed = new LinkedHashSet<>();
            for (ResourceLocation blockId : def.jobBlocks()) {
                Block block = BuiltInRegistries.BLOCK.getOptional(blockId).orElse(null);
                if (block == null) continue;
                // Claiming another installed mod's unclaimed block risks colliding with a POI
                // that mod registers later in the same event; only its existing claims are
                // safe to accept. Vanilla and own-jar blocks are race-free.
                boolean mayClaim = "minecraft".equals(blockId.getNamespace())
                        || def.ownsNamespace(blockId.getNamespace());
                for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                    var claimed = PoiTypes.forState(state).flatMap(Holder::unwrapKey);
                    if (claimed.isPresent()) {
                        keys.add(claimed.get());
                    } else if (mayClaim) {
                        unclaimed.add(state);
                    }
                }
                if (!mayClaim) {
                    Townstead.LOGGER.info("Scanned profession {} does not claim job block {} "
                            + "(namespace belongs to an installed mod); villagers can still use "
                            + "it if that mod registers a POI for it", def.id(), blockId);
                }
            }
            if (!unclaimed.isEmpty()) {
                try {
                    sink.accept(def.id(), new PoiType(Set.copyOf(unclaimed), 1, 1));
                    keys.add(ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, def.id()));
                } catch (Exception error) {
                    Townstead.LOGGER.warn("Could not register POI for scanned profession {}", def.id(), error);
                }
            }
            if (!keys.isEmpty()) POI_KEYS.put(def.id(), Set.copyOf(keys));
        }
    }

    private static void registerProfessions(Sink<VillagerProfession> sink) {
        for (ScannedDef def : defs()) {
            ResourceLocation id = def.id();
            if (BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id)) continue;
            if (!def.ownsNamespace(id.getNamespace())) {
                Townstead.LOGGER.info("Scanned profession {} sits in installed mod '{}''s namespace; "
                        + "not registering (extend that mod's profession as a root def or via aliases)",
                        id, id.getNamespace());
                continue;
            }
            try {
                sink.accept(id, create(def));
                Townstead.LOGGER.info("Registered scanned profession {}", id);
            } catch (Exception error) {
                Townstead.LOGGER.warn("Could not register scanned profession {}", id, error);
            }
        }
    }

    private static VillagerProfession create(ScannedDef def) {
        Set<ResourceKey<PoiType>> keys = POI_KEYS.getOrDefault(def.id(), Set.of());
        // Building/always providers have no vanilla POI: the career layer and building slot
        // rules own availability, exactly like townstead:cook.
        Predicate<Holder<PoiType>> jobSite = keys.isEmpty()
                ? PoiType.NONE
                : holder -> keys.stream().anyMatch(holder::is);
        return new VillagerProfession(def.id().getPath(), jobSite, jobSite,
                ImmutableSet.of(), ImmutableSet.of(), null);
    }

    private static List<ScannedDef> scan() {
        Map<ResourceLocation, ScannedDef> out = new LinkedHashMap<>();
        try {
            //? if neoforge {
            var modFiles = net.neoforged.fml.ModList.get().getModFiles();
            //?} else {
            /*var modFiles = net.minecraftforge.fml.ModList.get().getModFiles();
            *///?}
            for (var info : modFiles) {
                try {
                    Path data = info.getFile().findResource("data");
                    if (!Files.isDirectory(data)) continue;
                    Set<String> modIds = new LinkedHashSet<>();
                    for (var mod : info.getMods()) modIds.add(mod.getModId());
                    try (var namespaces = Files.list(data)) {
                        for (Path nsDir : (Iterable<Path>) namespaces::iterator) {
                            collectNamespace(nsDir, Set.copyOf(modIds), out);
                        }
                    }
                } catch (Exception error) {
                    Townstead.LOGGER.debug("Profession scan skipped mod file {}: {}",
                            info.moduleName(), error.toString());
                }
            }
        } catch (Exception error) {
            Townstead.LOGGER.warn("Profession scan failed; advanced professions will inherit "
                    + "their parent's Minecraft profession", error);
        }
        return List.copyOf(out.values());
    }

    private static void collectNamespace(Path nsDir, Set<String> providerModIds,
                                         Map<ResourceLocation, ScannedDef> out) throws Exception {
        Path professions = nsDir.resolve("profession");
        if (!Files.isDirectory(professions)) return;
        String namespace = nsDir.getFileName().toString();
        try (var files = Files.list(professions)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                String fileName = file.getFileName().toString();
                if (!fileName.endsWith(".json")) continue;
                ResourceLocation id = ResourceLocation.tryParse(
                        namespace + ":" + fileName.substring(0, fileName.length() - ".json".length()));
                if (id == null) continue;
                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject() && eligible(parsed.getAsJsonObject())) {
                        out.putIfAbsent(id, new ScannedDef(id, jobBlocks(parsed.getAsJsonObject()),
                                providerModIds));
                    }
                } catch (Exception error) {
                    Townstead.LOGGER.debug("Profession scan could not read {}: {}", file, error.toString());
                }
            }
        }
    }

    /**
     * Advanced Townstead profession defs only: the schema guard keeps other mods' unrelated
     * {@code profession/} data folders out, and root defs describe professions that exist.
     */
    static boolean eligible(JsonObject json) {
        if (!json.has("schema") || !json.get("schema").isJsonPrimitive()
                || !SCHEMA.equals(json.get("schema").getAsString())) return false;
        if (json.has("register_profession") && !json.get("register_profession").getAsBoolean()) return false;
        return json.has("parents") && json.get("parents").isJsonArray()
                && !json.getAsJsonArray("parents").isEmpty();
    }

    /** The block ids of every {@code townstead:job_block} provider in the def's poi list. */
    static Set<ResourceLocation> jobBlocks(JsonObject json) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        if (!json.has("poi") || !json.get("poi").isJsonArray()) return out;
        for (JsonElement entry : json.getAsJsonArray("poi")) {
            if (!entry.isJsonObject()) continue;
            JsonObject provider = entry.getAsJsonObject();
            if (!provider.has("type")
                    || !"townstead:job_block".equals(provider.get("type").getAsString())) continue;
            if (provider.has("block")) {
                ResourceLocation id = ResourceLocation.tryParse(provider.get("block").getAsString());
                if (id != null) out.add(id);
            }
            if (provider.has("blocks") && provider.get("blocks").isJsonArray()) {
                for (JsonElement blockId : provider.getAsJsonArray("blocks")) {
                    ResourceLocation id = ResourceLocation.tryParse(blockId.getAsString());
                    if (id != null) out.add(id);
                }
            }
        }
        return out;
    }
}
