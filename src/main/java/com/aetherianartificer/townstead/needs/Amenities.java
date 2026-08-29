package com.aetherianartificer.townstead.needs;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockActions;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Data-defined, village-wide sources of need effects: sinks, wells, baths, pumps, and similar
 * world services. Identity comes from the root block list; Pheno owns eligibility, preparation,
 * world mutation, and the effect on the user.
 */
public final class Amenities {
    public static final String SCHEMA = "townstead:amenity/v1";
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/Amenities");
    private static volatile List<Definition> DEFINITIONS = List.of();

    private Amenities() {}

    public record Definition(ResourceLocation id, Set<ResourceLocation> blocks,
                             Set<ResourceLocation> blockTags, @Nullable BlockCondition anchor,
                             @Nullable BlockCondition requires,
                             @Nullable BlockAction prepare, @Nullable BlockAction behavior,
                             Action effect, NeedEffectProjection projection) {
        boolean matches(ResourceLocation blockId, BlockState state) {
            if (blocks.contains(blockId)) return true;
            for (ResourceLocation tag : blockTags) {
                if (state.is(TagKey.create(Registries.BLOCK, tag))) return true;
            }
            return false;
        }

        boolean available(ServerLevel level, BlockPos pos) {
            if (anchor != null && !anchor.test(level, pos)) return false;
            return (requires == null || requires.test(level, pos)) || prepare != null;
        }
    }

    public record Candidate(Definition definition, BlockPos pos) {}

    /** Uses MCA's recognized-building block snapshots as the amenity index; no world-volume scan. */
    public static List<Candidate> candidates(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> village = resolveVillage(villager);
        if (village.isEmpty() || DEFINITIONS.isEmpty()) return List.of();
        Collection<Building> buildings = McaBuildings.all(village.get());
        List<Candidate> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Building building : buildings) {
            if (!building.isComplete()) continue;
            for (Map.Entry<ResourceLocation, List<BlockPos>> block : building.getBlocks().entrySet()) {
                for (Definition definition : DEFINITIONS) {
                    if (!definition.blocks().contains(block.getKey()) && definition.blockTags().isEmpty()) continue;
                    for (BlockPos pos : block.getValue()) addIfUsable(level, definition, block.getKey(), pos, seen, out);
                }
                if (!block.getValue().isEmpty()) {
                    for (Definition definition : DEFINITIONS) {
                        if (definition.blockTags().isEmpty() || definition.blocks().contains(block.getKey())) continue;
                        for (BlockPos pos : block.getValue()) addIfUsable(level, definition, block.getKey(), pos, seen, out);
                    }
                }
            }
        }
        return out;
    }

    private static void addIfUsable(ServerLevel level, Definition definition, ResourceLocation indexedId,
                                    BlockPos pos, Set<String> seen, List<Candidate> out) {
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        ResourceLocation liveId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (!indexedId.equals(liveId) || !definition.matches(liveId, state) || !definition.available(level, pos)) return;
        String key = definition.id() + "@" + pos.asLong();
        if (seen.add(key)) out.add(new Candidate(definition, pos.immutable()));
    }

    public static boolean use(ServerLevel level, VillagerEntityMCA villager, Candidate candidate) {
        if (candidate == null || !level.isLoaded(candidate.pos())) return false;
        Definition definition = candidate.definition();
        BlockState state = level.getBlockState(candidate.pos());
        if (!definition.matches(BuiltInRegistries.BLOCK.getKey(state.getBlock()), state)) return false;
        if (definition.anchor() != null && !definition.anchor().test(level, candidate.pos())) return false;

        if (definition.requires() != null && !definition.requires().test(level, candidate.pos())) {
            if (definition.prepare() == null) return false;
            BlockActionContext prepare = new BlockActionContext(level, candidate.pos(), villager);
            definition.prepare().run(prepare);
            if (!prepare.succeeded() || !definition.requires().test(level, candidate.pos())) return false;
        }
        if (definition.behavior() != null) {
            BlockActionContext behavior = new BlockActionContext(level, candidate.pos(), villager);
            definition.behavior().run(behavior);
            if (!behavior.succeeded()) return false;
        }
        ActionContext effect = new ActionContext(villager);
        definition.effect().run(effect);
        return effect.succeeded();
    }

    private static Optional<Village> resolveVillage(VillagerEntityMCA villager) {
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().isWithinBorder(villager)) return home;
        Optional<Village> nearest = Village.findNearest(villager);
        return nearest.filter(village -> village.isWithinBorder(villager));
    }

    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> entry : manager
                    .listResources("amenity", id -> id.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = entry.getKey();
                String path = file.getPath();
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                        + path.substring("amenity/".length(), path.length() - ".json".length()));
                if (id == null) continue;
                try (Reader reader = entry.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
                } catch (Exception exception) {
                    LOGGER.warn("Failed to read amenity {}: {}", file, exception.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager manager,
                             ProfilerFiller profiler) {
            List<Definition> loaded = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonObject> entry : prepared.entrySet()) {
                JsonObject json = entry.getValue();
                try { TownsteadSchema.validate(json, SCHEMA); }
                catch (RuntimeException exception) {
                    LOGGER.warn("Amenity {} rejected: {}", entry.getKey(), exception.getMessage());
                    continue;
                }
                if (json.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(json.get("mods")))) continue;
                Definition definition = parse(entry.getKey(), json);
                if (definition == null) {
                    LOGGER.warn("Invalid amenity {}", entry.getKey());
                    continue;
                }
                loaded.add(definition);
            }
            DEFINITIONS = List.copyOf(loaded);
            LOGGER.info("Loaded {} amenity definitions", loaded.size());
        }
    }

    private static @Nullable Definition parse(ResourceLocation id, JsonObject json) {
        if (!json.has("blocks") || !json.get("blocks").isJsonArray() || !json.has("effect")) return null;
        Set<ResourceLocation> blocks = new LinkedHashSet<>();
        Set<ResourceLocation> tags = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray("blocks")) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
            String selector = element.getAsString();
            boolean tag = selector.startsWith("#");
            ResourceLocation target = ResourceLocation.tryParse(tag ? selector.substring(1) : selector);
            if (target == null) return null;
            (tag ? tags : blocks).add(target);
        }
        if (blocks.isEmpty() && tags.isEmpty()) return null;
        BlockCondition anchor = json.has("anchor") ? BlockConditions.parse(json.get("anchor")) : null;
        if (json.has("anchor") && anchor == null) return null;
        BlockCondition requires = json.has("requires") ? BlockConditions.parse(json.get("requires")) : null;
        if (json.has("requires") && requires == null) return null;
        BlockAction prepare = json.has("prepare") ? BlockActions.parse(json.get("prepare")) : null;
        if (json.has("prepare") && prepare == null) return null;
        BlockAction behavior = json.has("behavior") ? BlockActions.parse(json.get("behavior")) : null;
        if (json.has("behavior") && behavior == null) return null;
        Action effect = Actions.parse(json.get("effect"));
        if (effect == null) return null;
        return new Definition(id, Set.copyOf(blocks), Set.copyOf(tags), anchor, requires, prepare, behavior,
                effect, NeedEffectProjection.project(json.get("effect")));
    }
}
