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
 * Boot-time scan that turns eligible profession defs bundled in mod jars or installed as
 * global Townstead Career packs into real
 * {@link VillagerProfession} registrations, so a data-defined specialization is a full
 * profession like any other. A def whose {@code poi} declares {@code townstead:job_block}
 * gets a real job-site POI: blocks nobody claims become a new {@link PoiType} under the def's
 * id, and blocks an existing POI type already claims (a blockstate may belong to only one
 * type) are accepted through that type instead. Defs with only building/always providers
 * register POI-less like {@code townstead:cook}.
 *
 * <p>The profession registry freezes at startup. Ordinary world data packs and
 * {@code /reload} therefore cannot add registry entries. Townstead Career packs installed in
 * the profile-level {@code datapacks} directory or under
 * {@code config/townstead/career-packs}, or the loose {@code kubejs/data} tree are deliberately
 * scanned before that freeze and can opt in to real profession registration without shipping a
 * mod.</p>
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
        for (ScannedDef def : defs()) {
            if (def.jobBlocks().isEmpty()) continue;
            Set<ResourceKey<PoiType>> existingKeys = new LinkedHashSet<>();
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
                    Set<ResourceKey<PoiType>> owners = existingPoiKeys(state);
                    existingKeys.addAll(owners);
                    if (owners.isEmpty() && mayClaim) {
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
                } catch (Exception error) {
                    Townstead.LOGGER.warn("Could not register POI for scanned profession {}", def.id(), error);
                }
            }
            if (!existingKeys.isEmpty()) {
                Townstead.LOGGER.info("Bound scanned profession {} to existing POI type(s): {}",
                        def.id(), existingKeys.stream()
                                .map(key -> key.location().toString()).sorted().toList());
            }
        }
    }

    /**
     * Finds existing owners without relying solely on PoiTypes' block-state cache. During a
     * mod registry event that cache can lag behind the registry even though the vanilla POI is
     * already present. Treating such a state as unclaimed registers a second POI that the world
     * never uses.
     */
    private static Set<ResourceKey<PoiType>> existingPoiKeys(BlockState state) {
        Set<ResourceKey<PoiType>> keys = new LinkedHashSet<>();
        PoiTypes.forState(state).flatMap(Holder::unwrapKey).ifPresent(keys::add);
        for (PoiType poi : BuiltInRegistries.POINT_OF_INTEREST_TYPE) {
            if (!poi.matchingStates().contains(state)) continue;
            BuiltInRegistries.POINT_OF_INTEREST_TYPE.getResourceKey(poi).ifPresent(keys::add);
        }
        return keys;
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
        // Building/always providers have no vanilla POI: the career layer and building slot
        // rules own availability, exactly like townstead:cook.
        Predicate<Holder<PoiType>> jobSite = jobSitePredicate(def);
        SoundEvent workSound = def.workSound() == null ? null
                : BuiltInRegistries.SOUND_EVENT.getOptional(def.workSound()).orElse(null);
        return new VillagerProfession(def.id().getPath(), jobSite, jobSite,
                ImmutableSet.of(), ImmutableSet.of(), workSound);
    }

    /**
     * Matches the authored blocks at evaluation time. Existing vanilla POIs (notably beehives)
     * can have an id unrelated to the custom profession, while a mod-supplied POI may register
     * after Townstead's scan. The block states are the stable contract in both cases.
     */
    static Predicate<Holder<PoiType>> jobSitePredicate(ScannedDef def) {
        if (def.jobBlocks().isEmpty()) return PoiType.NONE;
        return holder -> holder != null && matchesAuthoredBlock(def.jobBlocks(),
                holder.value().matchingStates().stream()
                        .map(state -> BuiltInRegistries.BLOCK.getKey(state.getBlock()))
                        .toList());
    }

    static boolean matchesAuthoredBlock(Set<ResourceLocation> authored,
                                        Iterable<ResourceLocation> poiBlocks) {
        for (ResourceLocation blockId : poiBlocks) {
            if (blockId != null && authored.contains(blockId)) return true;
        }
        return false;
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
        CareerPackSource.visitDataRoots(data -> collectDataRoot(data, Set.of(), out));
        // KubeJS mounts its loose data tree during normal resource loading, but professions that
        // opt into real vanilla registration must also be visible here, before registries freeze.
        collectDataRoot(com.aetherianartificer.townstead.data.KubeJsPackSource.dataDirectory(),
                Set.of("kubejs"), out);
        return List.copyOf(out.values());
    }

    static void collectDataRoot(Path data, Set<String> providerModIds,
                                Map<ResourceLocation, ScannedDef> out) {
        if (!Files.isDirectory(data)) return;
        try (var namespaces = Files.list(data)) {
            for (Path nsDir : (Iterable<Path>) namespaces::iterator) {
                collectNamespace(nsDir, providerModIds, out);
            }
        } catch (Exception error) {
            Townstead.LOGGER.warn("Profession scan could not read Career-pack data root {}", data, error);
        }
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
                            JsonObject pathDocument = path.getAsJsonObject();
                            if (pathDocument.has("mods") && !Boolean.TRUE.equals(
                                    com.aetherianartificer.townstead.data.ModGate.evaluate(
                                            pathDocument.get("mods")))) continue;
                            ProfessionPathDocument.apply(profession, pathId, pathDocument);
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

    /** Whether a JSON file declares one of Townstead's Profession document schemas. */
    static boolean hasProfessionSchema(Path file) {
        try (Reader reader = Files.newBufferedReader(file)) {
            return hasProfessionSchema(reader);
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean hasProfessionSchema(Reader reader) {
        try {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return false;
            JsonElement schema = parsed.getAsJsonObject().get("schema");
            return schema != null && schema.isJsonPrimitive()
                    && SCHEMAS.contains(schema.getAsString());
        } catch (Exception ignored) {
            return false;
        }
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
