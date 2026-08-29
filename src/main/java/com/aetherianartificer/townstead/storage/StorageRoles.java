package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What villagers may treat as a shelf, stated in data.
 *
 * <p>This used to be a list of block ids in Java, which is why it was always out of date: it knew
 * four Farmer's Delight blocks and one Farm &amp; Charm one, so every other mod's stations read as
 * storage and villagers pulled staged ingredients back out of them.</p>
 *
 * <h2>Order</h2>
 *
 * <p>Deny beats allow, and both beat anything inferred:</p>
 * <ol>
 *   <li>the player's protected-storage config, because it is their world;</li>
 *   <li>a {@code not_storage} declaration;</li>
 *   <li>a declared workstation, since a block a pack already calls a station is a machine and
 *       saying so twice would be a second place to forget;</li>
 *   <li>a {@code storage} declaration;</li>
 *   <li>failing all of that, the guesses in {@code NearbyItemSources} (a furnace-like block
 *       entity, a name that reads like machinery). They are last, so data always overrules one.</li>
 * </ol>
 */
public final class StorageRoles {

    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/StorageRoles");

    /**
     * Optional version marker, matching every other author-facing Townstead document. Omitting it
     * reads as "current", so existing packs keep loading; declaring the wrong one is refused
     * rather than half-read, which is the point of having it once the format grows a v2.
     */
    public static final String SCHEMA = "townstead:storage_role/v1";

    private static volatile List<StorageRoleDef> DEFS = List.of();

    private StorageRoles() {}

    public static void replaceAll(List<StorageRoleDef> defs) {
        DEFS = List.copyOf(defs);
    }

    public static List<StorageRoleDef> all() {
        return DEFS;
    }

    /**
     * Whether villagers may use this block as a shelf.
     *
     * <p>Config first, then a machine check, then a stated role, then "does it actually hold
     * things". The Farmer's Delight cabinet/chest tag list that used to sit in the middle of this
     * was redundant — every one of those blocks is a {@link net.minecraft.world.Container} with
     * slots, so the container branch already answered yes. Dropping it is what makes this
     * mod-neutral.</p>
     */
    public static boolean isStorageCandidate(net.minecraft.server.level.ServerLevel level,
                                             net.minecraft.core.BlockPos pos,
                                             net.minecraft.world.level.block.entity.BlockEntity be) {
        BlockState state = level.getBlockState(pos);
        if (com.aetherianartificer.townstead.TownsteadConfig.isProtectedStorage(state)) return false;
        if (denied(state)) return false;
        if (com.aetherianartificer.townstead.hunger.NearbyItemSources
                .isProcessingContainer(level, pos, be)) return false;
        if (allowed(state)) return true;
        if (be instanceof net.minecraft.world.Container container) {
            return container.getContainerSize() > 0;
        }
        if (com.aetherianartificer.townstead.work.station.BlockInventories
                .itemHandler(be, level, pos, null) != null) return true;
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            if (com.aetherianartificer.townstead.work.station.BlockInventories
                    .itemHandler(be, level, pos, dir) != null) return true;
        }
        return false;
    }

    /** Storage eligibility plus the room-level permission for this particular villager. */
    public static boolean isStorageCandidate(net.minecraft.server.level.ServerLevel level,
                                             net.minecraft.core.BlockPos pos,
                                             net.minecraft.world.level.block.entity.BlockEntity be,
                                             net.conczin.mca.entity.VillagerEntityMCA villager) {
        return isStorageCandidate(level, pos, be)
                && RoomOwnershipAccess.mayAccess(level, villager, pos);
    }

    /** Storage eligibility plus its semantic suitability for the operation being planned. */
    public static boolean isStorageCandidate(net.minecraft.server.level.ServerLevel level,
                                             net.minecraft.core.BlockPos pos,
                                             net.minecraft.world.level.block.entity.BlockEntity be,
                                             net.conczin.mca.entity.VillagerEntityMCA villager,
                                             StorageUse use) {
        return isStorageCandidate(level, pos, be, villager)
                && (use != StorageUse.PERSONAL || RoomOwnershipAccess.isPrivate(level, pos))
                && useRank(level.getBlockState(pos), use) != Integer.MAX_VALUE;
    }

    /**
     * Lower is preferred. A semantic label overrides generic {@code storage}; this lets a chest
     * remain in the broad mod-compatible storage tag while a narrower datapack tag gives it a
     * predictable job. Personal storage is intentionally unavailable to profession automation.
     */
    public static int useRank(BlockState state, StorageUse use) {
        if (state == null || use == null || denied(state)) return Integer.MAX_VALUE;
        Set<StorageRoleDef.Role> semantic = semanticRoles(state);
        if (semantic.isEmpty()) return use == StorageUse.PERSONAL ? Integer.MAX_VALUE : 1;
        return useRank(semantic, use);
    }

    public static int useRank(Set<StorageRoleDef.Role> roles, StorageUse use) {
        if (roles == null || roles.isEmpty() || use == null) return Integer.MAX_VALUE;
        int best = Integer.MAX_VALUE;
        for (StorageRoleDef.Role role : roles) {
            int rank = switch (role) {
                case INPUTS -> use == StorageUse.INGREDIENT ? 0 : Integer.MAX_VALUE;
                case OUTPUTS -> use == StorageUse.OUTPUT ? 0 : Integer.MAX_VALUE;
                case TOOLS -> (use == StorageUse.TOOL || use == StorageUse.TOOL_RETURN)
                        ? 0 : Integer.MAX_VALUE;
                case RESERVES -> (use == StorageUse.INGREDIENT || use == StorageUse.TOOL)
                        ? 2 : Integer.MAX_VALUE;
                case PERSONAL -> use == StorageUse.PERSONAL ? 0 : Integer.MAX_VALUE;
                case STORAGE -> use == StorageUse.PERSONAL ? Integer.MAX_VALUE : 1;
                case NOT_STORAGE -> Integer.MAX_VALUE;
            };
            best = Math.min(best, rank);
        }
        return best;
    }

    public static Set<StorageRoleDef.Role> semanticRoles(BlockState state) {
        if (state == null) return Set.of();
        EnumSet<StorageRoleDef.Role> roles = EnumSet.noneOf(StorageRoleDef.Role.class);
        for (StorageRoleDef def : DEFS) {
            if (def.matches(state) && def.role() != StorageRoleDef.Role.STORAGE
                    && def.role() != StorageRoleDef.Role.NOT_STORAGE) {
                roles.add(def.role());
            }
        }
        return roles.isEmpty() ? Set.of() : Set.copyOf(roles);
    }

    /** Whether data explicitly refuses this block as storage. */
    public static boolean denied(BlockState state) {
        return matches(state, StorageRoleDef.Role.NOT_STORAGE);
    }

    /** Whether data explicitly offers this block as storage. */
    public static boolean allowed(BlockState state) {
        if (state == null) return false;
        for (StorageRoleDef def : DEFS) {
            if (def.role() != StorageRoleDef.Role.NOT_STORAGE && def.matches(state)) return true;
        }
        return false;
    }

    private static boolean matches(BlockState state, StorageRoleDef.Role role) {
        List<StorageRoleDef> defs = DEFS;
        // The common case is no declarations at all; keep that one volatile read.
        if (defs.isEmpty() || state == null) return false;
        for (StorageRoleDef def : defs) {
            if (def.role() == role && def.matches(state)) return true;
        }
        return false;
    }

    /** Loads {@code data/<ns>/storage_role/*.json}; defs behind unmet {@code mods} gates don't exist. */
    public static final class Loader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {

        @Override
        protected Map<ResourceLocation, JsonObject> prepare(ResourceManager resourceManager,
                                                            ProfilerFiller profiler) {
            Map<ResourceLocation, JsonObject> out = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, Resource> e : resourceManager
                    .listResources("storage_role", loc -> loc.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = e.getKey();
                String path = file.getPath();
                ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                        + path.substring("storage_role/".length(), path.length() - ".json".length()));
                if (id == null) continue;
                try (Reader reader = e.getValue().openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (parsed.isJsonObject()) out.put(id, parsed.getAsJsonObject());
                } catch (Exception ex) {
                    LOGGER.warn("Failed to read storage role {}: {}", file, ex.getMessage());
                }
            }
            return out;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonObject> prepared, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            List<StorageRoleDef> defs = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonObject> e : prepared.entrySet()) {
                JsonObject obj = e.getValue();
                try {
                    TownsteadSchema.validate(obj, SCHEMA);
                } catch (RuntimeException ex) {
                    LOGGER.warn("Storage role {} rejected: {}", e.getKey(), ex.getMessage());
                    continue;
                }
                if (obj.has("mods") && !Boolean.TRUE.equals(ModGate.evaluate(obj.get("mods")))) {
                    LOGGER.debug("Storage role {} skipped: mods gate unmet or malformed", e.getKey());
                    continue;
                }
                StorageRoleDef def = StorageRoleDef.parse(e.getKey(), obj);
                if (def == null) {
                    LOGGER.warn("Invalid storage role {} (unknown \"role\" or missing"
                            + " non-empty \"blocks\" and/or \"namespaces\")", e.getKey());
                    continue;
                }
                defs.add(def);
            }
            replaceAll(defs);
            if (!defs.isEmpty()) LOGGER.info("Loaded {} storage role defs", defs.size());
        }
    }
}
