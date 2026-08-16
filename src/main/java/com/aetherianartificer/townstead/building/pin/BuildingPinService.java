package com.aetherianartificer.townstead.building.pin;

import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.recognition.BuildingEnclosurePolicies;
import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
//? if neoforge {
import net.conczin.mca.server.world.data.BuildingFloorRegion;
//?}
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?} else if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.Method;

/**
 * Server-side owner of catalog building pins.
 *
 * <p>Completion is event-driven from MCA's building recognition, while the small progress snapshot
 * is refreshed twice a second and sent only when a value changes. A pin therefore cannot be cleared
 * by an old instance that already existed when it was selected.</p>
 */
public final class BuildingPinService {
    private static final Map<UUID, Pin> PINS = new ConcurrentHashMap<>();
    private static final String TAG_TYPE = "TownsteadBuildingPin";
    private static final String TAG_DIMENSION = "TownsteadBuildingPinDimension";
    private static final String TAG_VILLAGE = "TownsteadBuildingPinVillage";
    private static final Method ANALYZE_BUILDING = findAnalyzeBuildingMethod();
    private static final Method SCAN_BUILDING = findScanBuildingMethod();

    private BuildingPinService() {}

    public static void set(ServerPlayer player, String requestedType) {
        String typeName = requestedType == null ? "" : requestedType.trim();
        if (typeName.isEmpty()) {
            PINS.remove(player.getUUID());
            clearSaved(player);
            send(player, BuildingPinProgressS2CPayload.unpinned());
            return;
        }
        if (typeName.length() > 256 || !BuildingTypes.getInstance().getBuildingTypes().containsKey(typeName)) {
            sync(player);
            return;
        }
        if (CatalogDataLoader.isActiveSupersededBuildingType(typeName)) {
            sync(player);
            return;
        }
        BuildingType type = BuildingTypes.getInstance().getBuildingType(typeName);
        if (type == null || !type.visible()) {
            sync(player);
            return;
        }
        Village nearest = Village.findNearest(player).orElse(null);
        Pin pin = new Pin(typeName, player.serverLevel().dimension().location(),
                nearest == null ? -1 : nearest.getId());
        PINS.put(player.getUUID(), pin);
        save(player, pin);
        sendProgress(player, pin, true);
    }

    public static void sync(ServerPlayer player) {
        Pin pin = PINS.computeIfAbsent(player.getUUID(), ignored -> load(player));
        if (pin == null) {
            PINS.remove(player.getUUID());
            send(player, BuildingPinProgressS2CPayload.unpinned());
        } else {
            sendProgress(player, pin, true);
        }
    }

    public static void logout(UUID playerId) {
        PINS.remove(playerId);
    }

    public static void clear() {
        PINS.clear();
    }

    /** Cheap live progress refresh; unchanged snapshots produce no packet. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0 || PINS.isEmpty()) return;
        for (Map.Entry<UUID, Pin> entry : PINS.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null) sendProgress(player, entry.getValue(), false);
        }
    }

    /** Called by the same recognition event which announces a completed or upgraded building. */
    public static void onRecognized(ServerLevel level, Village village, Building building) {
        if (level == null || village == null || building == null || PINS.isEmpty()) return;
        List<UUID> completed = new ArrayList<>();
        for (Map.Entry<UUID, Pin> entry : PINS.entrySet()) {
            Pin pin = entry.getValue();
            if (!pin.dimension.equals(level.dimension().location())) continue;
            if (pin.villageId >= 0 && pin.villageId != village.getId()) continue;
            BuildingType pinnedType = BuildingTypes.getInstance().getBuildingTypes().get(pin.buildingType);
            if (pinnedType == null || !matchesRequirements(pinnedType, building)) continue;
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player != null) {
                clearSaved(player);
                send(player, BuildingPinProgressS2CPayload.completed(pin.buildingType));
            }
            completed.add(entry.getKey());
        }
        completed.forEach(PINS::remove);
    }

    private static void sendProgress(ServerPlayer player, Pin pin, boolean force) {
        BuildingType type = BuildingTypes.getInstance().getBuildingTypes().get(pin.buildingType);
        if (type == null) {
            PINS.remove(player.getUUID());
            send(player, BuildingPinProgressS2CPayload.unpinned());
            return;
        }

        Village village = villageFor(player, pin);
        if (pin.villageId < 0 && village != null) {
            pin.villageId = village.getId();
            save(player, pin);
        }
        Building current = village == null ? null : currentBuilding(village, player.blockPosition());
        Map<ResourceLocation, Integer> inventory = inventoryGroups(player, type);
        Map<ResourceLocation, Integer> placed = placedGroups(player, type, current);

        List<BuildingPinProgressS2CPayload.Row> rows = type.getGroups().entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().toString()))
                .map(e -> new BuildingPinProgressS2CPayload.Row(
                        e.getKey(), e.getValue(), inventory.getOrDefault(e.getKey(), 0),
                        placed.getOrDefault(e.getKey(), 0)))
                .toList();
        BuildingPinProgressS2CPayload progress = new BuildingPinProgressS2CPayload(
                true, pin.buildingType, current != null, false, rows);
        if (force || !progress.equals(pin.lastProgress)) {
            pin.lastProgress = progress;
            send(player, progress);
        }
    }

    private static Village villageFor(ServerPlayer player, Pin pin) {
        Village nearest = Village.findNearest(player).orElse(null);
        if (pin.villageId < 0) return nearest;
        for (Village village : VillageManager.get(player.serverLevel())) {
            if (village.getId() == pin.villageId) return village;
        }
        return nearest;
    }

    private static Building currentBuilding(Village village, net.minecraft.core.BlockPos pos) {
        for (Building building : McaBuildings.all(village)) {
            if (building.containsPos(pos)) return building;
        }
        return null;
    }

    private static boolean matchesRequirements(BuildingType type, Building building) {
        Map<ResourceLocation, List<net.minecraft.core.BlockPos>> groups = type.getGroups(building.getBlocks());
        for (Map.Entry<ResourceLocation, Integer> requirement : type.getGroups().entrySet()) {
            if (groups.getOrDefault(requirement.getKey(), List.of()).size() < requirement.getValue()) return false;
        }
        return true;
    }

    private static Map<ResourceLocation, Integer> inventoryGroups(ServerPlayer player, BuildingType type) {
        Map<ResourceLocation, Integer> result = new HashMap<>();
        Map<ResourceLocation, ResourceLocation> blockToGroup = type.getBlockToGroup();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) continue;
            ResourceLocation block = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
            ResourceLocation group = blockToGroup.get(block);
            if (group != null) result.merge(group, stack.getCount(), Integer::sum);
        }
        return result;
    }

    private static Map<ResourceLocation, Integer> placedGroups(ServerPlayer player, BuildingType type,
                                                               Building current) {
        if (current != null) {
            return liveBuildingGroups(player.serverLevel(), type, current);
        }

        // Before a room is committed, ask MCA to flood-fill the same candidate footprint its
        // Add Room action will use. This keeps a neighbouring building's stations out of the
        // checklist while still updating live as blocks are placed inside the new room.
        Building preview = previewRoom(player.serverLevel(), player.blockPosition());
        if (preview != null) {
            return liveBuildingGroups(player.serverLevel(), type, preview);
        }

        // Enclosed buildings must never fall back to a proximity estimate. The old 10-block
        // radius made a stove in an adjacent kitchen count toward a not-yet-recognized Bakery.
        if (!BuildingEnclosurePolicies.allowsOpenAir(type.name())) return Map.of();

        // Open-air grouped sites do not have an MCA room boundary while they are being assembled.
        // A small, non-chunk-loading fallback keeps their checklist useful until recognition gives
        // us an exact footprint. Once a room exists, the exact branch above replaces this estimate.
        ServerLevel level = player.serverLevel();
        net.minecraft.core.BlockPos origin = player.blockPosition();
        net.minecraft.core.BlockPos.MutableBlockPos cursor = new net.minecraft.core.BlockPos.MutableBlockPos();
        Map<ResourceLocation, Integer> result = new HashMap<>();
        for (int y = origin.getY() - 5; y <= origin.getY() + 5; y++) {
            for (int x = origin.getX() - 10; x <= origin.getX() + 10; x++) {
                for (int z = origin.getZ() - 10; z <= origin.getZ() + 10; z++) {
                    cursor.set(x, y, z);
                    if (!level.hasChunkAt(cursor)) continue;
                    countLiveBlock(level, cursor, type, result);
                }
            }
        }
        return result;
    }

    private static Building previewRoom(ServerLevel level, net.minecraft.core.BlockPos origin) {
        if (ANALYZE_BUILDING == null || SCAN_BUILDING == null) return null;
        try {
            Object scan = ANALYZE_BUILDING.invoke(VillageManager.get(level), origin, true);
            Object building = SCAN_BUILDING.invoke(scan);
            return building instanceof Building candidate ? candidate : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static Method findAnalyzeBuildingMethod() {
        try {
            return VillageManager.class.getMethod(
                    "analyzeBuilding", net.minecraft.core.BlockPos.class, boolean.class);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method findScanBuildingMethod() {
        if (ANALYZE_BUILDING == null) return null;
        try {
            return ANALYZE_BUILDING.getReturnType().getMethod("building");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * Counts the world's current block states inside MCA's recognized room footprint.
     * {@link Building#getBlocks()} is the snapshot from the last recognition scan and therefore
     * cannot drive a live construction checklist.
     */
    private static Map<ResourceLocation, Integer> liveBuildingGroups(ServerLevel level, BuildingType type,
                                                                     Building building) {
        Map<ResourceLocation, Integer> result = new HashMap<>();
        //? if neoforge {
        net.minecraft.core.BlockPos a = building.getRawPos0();
        net.minecraft.core.BlockPos b = building.getRawPos1();
        //?} else if forge {
        /*net.minecraft.core.BlockPos a = building.getPos0();
        net.minecraft.core.BlockPos b = building.getPos1();
        *///?}
        int minX = Math.min(a.getX(), b.getX());
        int maxX = Math.max(a.getX(), b.getX());
        int minY = Math.min(a.getY(), b.getY()) - 1;
        int maxY = Math.max(a.getY(), b.getY());
        int minZ = Math.min(a.getZ(), b.getZ());
        int maxZ = Math.max(a.getZ(), b.getZ());
        //? if neoforge {
        List<BuildingFloorRegion> footprint = building.getFloorRegions();
        //?}
        net.minecraft.core.BlockPos.MutableBlockPos cursor = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                //? if neoforge {
                boolean insideFootprint = footprint.isEmpty();
                for (BuildingFloorRegion region : footprint) {
                    if (region.containsHorizontally(x, z)) {
                        insideFootprint = true;
                        break;
                    }
                }
                if (!insideFootprint) continue;
                //?}
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!level.hasChunkAt(cursor)) continue;
                    countLiveBlock(level, cursor, type, result);
                }
            }
        }
        return result;
    }

    private static void countLiveBlock(ServerLevel level, net.minecraft.core.BlockPos pos, BuildingType type,
                                       Map<ResourceLocation, Integer> result) {
        var state = level.getBlockState(pos);
        if (!type.matchesBlock(state)) return;
        ResourceLocation block = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        ResourceLocation group = type.getBlockToGroup().get(block);
        if (group != null) result.merge(group, 1, Integer::sum);
    }

    private static void save(ServerPlayer player, Pin pin) {
        CompoundTag data = player.getPersistentData();
        data.putString(TAG_TYPE, pin.buildingType);
        data.putString(TAG_DIMENSION, pin.dimension.toString());
        data.putInt(TAG_VILLAGE, pin.villageId);
    }

    private static Pin load(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        String type = data.getString(TAG_TYPE);
        if (type.isBlank() || !BuildingTypes.getInstance().getBuildingTypes().containsKey(type)
                || CatalogDataLoader.isActiveSupersededBuildingType(type)) {
            clearSaved(player);
            return null;
        }
        ResourceLocation dimension = ResourceLocation.tryParse(data.getString(TAG_DIMENSION));
        if (dimension == null) dimension = player.serverLevel().dimension().location();
        return new Pin(type, dimension, data.contains(TAG_VILLAGE) ? data.getInt(TAG_VILLAGE) : -1);
    }

    private static void clearSaved(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.remove(TAG_TYPE);
        data.remove(TAG_DIMENSION);
        data.remove(TAG_VILLAGE);
    }

    private static void send(ServerPlayer player, BuildingPinProgressS2CPayload payload) {
        //? if neoforge {
        PacketDistributor.sendToPlayer(player, payload);
        //?} else if forge {
        /*TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    private static final class Pin {
        private final String buildingType;
        private final ResourceLocation dimension;
        private int villageId;
        private BuildingPinProgressS2CPayload lastProgress;

        private Pin(String buildingType, ResourceLocation dimension, int villageId) {
            this.buildingType = buildingType;
            this.dimension = dimension;
            this.villageId = villageId;
        }
    }
}
