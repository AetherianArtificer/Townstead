package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A profession's ordered choice of otherwise-valid storage blocks.
 *
 * <p>This does not decide whether a block is storage; {@link StorageRoles} owns that safety
 * question. It only orders the usable shelves in a worker's current worksite. If none of the
 * preferred selectors are present, every ordinary storage candidate remains available.</p>
 */
public record StoragePreference(List<String> buildings, List<Selector> preferred) {
    public static final StoragePreference NONE = new StoragePreference(List.of(), List.of());
    public static final int FALLBACK_RANK = Integer.MAX_VALUE;

    public StoragePreference {
        buildings = List.copyOf(buildings);
        preferred = List.copyOf(preferred);
    }

    /** Zero is the first preferred building type; fallback storage has no building rank. */
    public int buildingRank(String buildingType) {
        if (buildingType == null) return FALLBACK_RANK;
        for (int i = 0; i < buildings.size(); i++) {
            if (buildings.get(i).equals(buildingType)) return i;
        }
        return FALLBACK_RANK;
    }

    /** Zero is the first preference; {@link #FALLBACK_RANK} is ordinary worksite storage. */
    public int rank(BlockState state) {
        if (state == null) return FALLBACK_RANK;
        for (int i = 0; i < preferred.size(); i++) {
            if (preferred.get(i).matches(state)) return i;
        }
        return FALLBACK_RANK;
    }

    public static StoragePreference forVillager(VillagerEntityMCA villager) {
        if (villager == null) return NONE;
        ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        ProfessionDef def = id == null ? null : ProfessionDefs.byId(id);
        return def == null ? NONE : def.storage();
    }

    /** Parses the concise {@code "storage":{"preferred":[...]}} work-sidecar shape. */
    public static StoragePreference parse(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException("'storage' must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        List<String> buildings = new ArrayList<>();
        if (object.has("buildings")) {
            if (!object.get("buildings").isJsonArray()) {
                throw new IllegalArgumentException("'storage.buildings' must be an array");
            }
            for (JsonElement entry : object.getAsJsonArray("buildings")) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()
                        || entry.getAsString().isBlank()) {
                    throw new IllegalArgumentException(
                            "'storage.buildings' entries must be non-empty building type ids");
                }
                String type = entry.getAsString();
                if (!buildings.contains(type)) buildings.add(type);
            }
        }
        List<Selector> selectors = new ArrayList<>();
        if (object.has("preferred")) {
            if (!object.get("preferred").isJsonArray()) {
                throw new IllegalArgumentException("'storage.preferred' must be an array");
            }
            for (JsonElement entry : object.getAsJsonArray("preferred")) {
                if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
                    throw new IllegalArgumentException(
                            "'storage.preferred' entries must be block ids or #block tags");
                }
                String raw = entry.getAsString();
                boolean tag = raw.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
                if (id == null) {
                    throw new IllegalArgumentException("Invalid storage selector '" + raw + "'");
                }
                Selector selector = new Selector(id, tag);
                if (!selectors.contains(selector)) selectors.add(selector);
            }
        }
        return buildings.isEmpty() && selectors.isEmpty()
                ? NONE : new StoragePreference(buildings, selectors);
    }

    public record Selector(ResourceLocation id, boolean tag) {
        public boolean matches(BlockState state) {
            if (tag) return state.is(TagKey.create(Registries.BLOCK, id));
            Block block = BuiltInRegistries.BLOCK.get(id);
            return block != null && state.is(block);
        }
    }
}
