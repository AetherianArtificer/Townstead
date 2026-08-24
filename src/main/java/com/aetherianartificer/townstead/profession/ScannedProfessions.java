package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.profession.def.ProfessionPathDocument;
import com.aetherianartificer.townstead.profession.def.ProfessionPathsOverlay;
import com.aetherianartificer.townstead.profession.def.ProfessionWorkOverlay;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
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
 * Boot-time scan that turns eligible profession defs bundled in mod jars into real
 * {@link VillagerProfession} registrations, so a data-defined specialization is a full
 * profession like any other. A def whose {@code poi} declares {@code townstead:job_block}
 * gets a real job-site POI: blocks nobody claims become a new {@link PoiType} under the def's
 * id, and blocks an existing POI type already claims (a blockstate may belong to only one
 * type) are accepted through that type instead. Defs with only building/always providers
 * register POI-less like {@code townstead:cook}.
 *
 * <p>Only jar-bundled defs can get this treatment: the profession registry freezes at
 * startup, so defs added by world data packs or {@code /reload} cannot register and are
 * reachable only through the career layer.</p>
 *
 * <p>Eligible: a Townstead profession def with either non-empty {@code acquisition_routes} or
 * explicit {@code "register_profession": true}, which may be supplied by adjacent
 * {@code work.json} in the directory layout. The scanner composes legacy {@code paths.json} and
 * individual {@code path/<id>/path.json} documents first when present, so villager path-to-worksite
 * affinities can still be validated at startup.
 * The explicit form is how a practiced custom career says it owns a new villager profession;
 * otherwise practiced careers extend professions that already exist. Ids already present in the
 * registry are skipped; {@code aliases} exist for converging on those instead.</p>
 */
public final class ScannedProfessions {

    private static final Set<String> SCHEMAS = Set.of(
            "townstead:profession/v1", "townstead:profession/v2");

    public record ScannedDef(ResourceLocation id, Set<ResourceLocation> jobBlocks,
                             @org.jetbrains.annotations.Nullable ResourceLocation workSound,
                             Set<ResourceLocation> taskTypes,
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

    /** Scanned profession defs, resolved lazily on first use during registration. */
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

    /** Boot-safe profession ids whose authored work sidecar declares this task type. */
    public static Set<ResourceLocation> idsForTask(ResourceLocation taskType) {
        if (taskType == null) return Set.of();
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (ScannedDef def : defs()) {
            if (def.taskTypes().contains(taskType)) ids.add(def.id());
        }
        return Set.copyOf(ids);
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
        SoundEvent workSound = def.workSound() == null ? null
                : BuiltInRegistries.SOUND_EVENT.getOptional(def.workSound()).orElse(null);
        return new VillagerProfession(def.id().getPath(), jobSite, jobSite,
                ImmutableSet.of(), ImmutableSet.of(), workSound);
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
            Townstead.LOGGER.warn("Profession scan failed; gated careers will not register "
                    + "their own professions this session", error);
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
                if (Files.isDirectory(file)) {
                    // Per-profession directory layout: <name>/profession.json is the def,
                    // id = <ns>:<name>. Skill subdirectories never register professions.
                    Path def = file.resolve("profession.json");
                    if (Files.isRegularFile(def)) {
                        Path paths = file.resolve("paths.json");
                        Path pathDirectory = file.resolve("path");
                        Path work = file.resolve("work.json");
                        readCandidate(def, Files.isRegularFile(paths) ? paths : null,
                                Files.isDirectory(pathDirectory) ? pathDirectory : null,
                                Files.isRegularFile(work) ? work : null,
                                ResourceLocation.tryParse(namespace + ":" + fileName), providerModIds, out);
                    }
                    continue;
                }
                if (!fileName.endsWith(".json")) continue;
                readCandidate(file, null, null, null, ResourceLocation.tryParse(
                                namespace + ":" + fileName.substring(0, fileName.length() - ".json".length())),
                        providerModIds, out);
            }
        }
    }

    private static void readCandidate(Path file, @org.jetbrains.annotations.Nullable Path pathsFile,
                                      @org.jetbrains.annotations.Nullable Path pathDirectory,
                                      @org.jetbrains.annotations.Nullable Path workFile,
                                      @org.jetbrains.annotations.Nullable ResourceLocation id,
                                      Set<String> providerModIds, Map<ResourceLocation, ScannedDef> out) {
        if (id == null) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return;
            JsonObject profession = parsed.getAsJsonObject();
            if (pathsFile != null) {
                try (Reader pathsReader = Files.newBufferedReader(pathsFile)) {
                    JsonElement paths = JsonParser.parseReader(pathsReader);
                    if (!paths.isJsonObject()) throw new IllegalArgumentException("paths.json must be an object");
                    ProfessionPathsOverlay.apply(profession, paths.getAsJsonObject());
                }
            }
            if (pathDirectory != null) {
                try (var pathDirectories = Files.list(pathDirectory)) {
                    for (Path pathDir : pathDirectories.filter(Files::isDirectory)
                            .sorted().toList()) {
                        String pathId = pathDir.getFileName().toString();
                        Path pathFile = pathDir.resolve("path.json");
                        if (!Files.isRegularFile(pathFile)) continue;
                        try (Reader pathReader = Files.newBufferedReader(pathFile)) {
                            JsonElement path = JsonParser.parseReader(pathReader);
                            if (!path.isJsonObject()) {
                                throw new IllegalArgumentException(pathFile + " must be an object");
                            }
                            ProfessionPathDocument.apply(profession, pathId,
                                    path.getAsJsonObject());
                        }
                    }
                }
            }
            if (workFile != null) {
                try (Reader workReader = Files.newBufferedReader(workFile)) {
                    JsonElement work = JsonParser.parseReader(workReader);
                    if (!work.isJsonObject()) throw new IllegalArgumentException("work.json must be an object");
                    ProfessionWorkOverlay.apply(profession, work.getAsJsonObject());
                }
            }
            if (eligible(profession)) {
                out.putIfAbsent(id, new ScannedDef(id, jobBlocks(profession), workSound(profession),
                        taskTypes(profession),
                        providerModIds));
            }
        } catch (Exception error) {
            Townstead.LOGGER.debug("Profession scan could not read {}: {}", file, error.toString());
        }
    }

    /**
     * Townstead profession defs that need a profession of their own. The schema guard keeps
     * other mods' unrelated {@code profession/} data folders out. Gated careers register by
     * default; a practiced career can opt in with {@code register_profession: true}, or opt out
     * explicitly with {@code false}. The scan cannot use the full parser because it runs before
     * common setup registers the Pheno condition types needed by {@code requirements}. A def
     * whose {@code mods} gate is unmet (or malformed) never registers a profession.
     */
    static boolean eligible(JsonObject json) {
        if (!json.has("schema") || !json.get("schema").isJsonPrimitive()
                || !SCHEMAS.contains(json.get("schema").getAsString())) return false;
        if (json.has("register_profession")) {
            if (!json.get("register_profession").getAsBoolean()) return false;
            if (json.has("mods") && !Boolean.TRUE.equals(
                    com.aetherianartificer.townstead.data.ModGate.evaluate(json.get("mods")))) return false;
            return true;
        }
        if (json.has("mods") && !Boolean.TRUE.equals(
                com.aetherianartificer.townstead.data.ModGate.evaluate(json.get("mods")))) return false;
        return com.aetherianartificer.townstead.profession.def.ProfessionDef.declaresAcquisitionRoutes(json);
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

    /** Optional work sound used when the bundled definition registers a real profession. */
    static @org.jetbrains.annotations.Nullable ResourceLocation workSound(JsonObject json) {
        if (!json.has("work_sound") || !json.get("work_sound").isJsonPrimitive()
                || !json.getAsJsonPrimitive("work_sound").isString()) return null;
        return ResourceLocation.tryParse(json.get("work_sound").getAsString());
    }

    /** Task ids composed from the adjacent work sidecar during the same boot scan. */
    static Set<ResourceLocation> taskTypes(JsonObject json) {
        if (!json.has("work_tasks") || !json.get("work_tasks").isJsonArray()) return Set.of();
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray("work_tasks")) {
            if (!element.isJsonObject()) continue;
            String raw = net.minecraft.util.GsonHelper.getAsString(element.getAsJsonObject(), "type", "");
            ResourceLocation id = ResourceLocation.tryParse(raw.contains(":")
                    ? raw : com.aetherianartificer.townstead.profession.def.WorkTaskTypes.NAMESPACE + ":" + raw);
            if (id != null) out.add(id);
        }
        return Set.copyOf(out);
    }
}
