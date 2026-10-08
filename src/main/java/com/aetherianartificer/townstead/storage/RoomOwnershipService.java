package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.storage.net.RoomOwnershipSetC2SPayload;
import com.aetherianartificer.townstead.storage.net.RoomOwnershipSnapshotS2CPayload;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteRegister;
import com.aetherianartificer.townstead.work.site.Worksites;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Opens, validates, and applies the room ownership editor. */
public final class RoomOwnershipService {
    private static final double MAX_EDIT_DISTANCE_SQR = 64.0;

    private RoomOwnershipService() {}

    public static boolean open(ServerPlayer player, BlockPos tagPos) {
        Context context = context(player, tagPos, -1L);
        if (context == null) {
            player.displayClientMessage(Component.translatable("townstead.room_ownership.no_room"), true);
            return false;
        }
        RoomOwnershipSnapshotS2CPayload payload = snapshot(player, tagPos, context);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
        return true;
    }

    public static void apply(ServerPlayer player, RoomOwnershipSetC2SPayload payload) {
        if (player == null || payload == null) return;
        Context context = context(player, payload.tagPos(), payload.worksiteId());
        if (context == null) return;

        OwnershipScope scope = payload.scope() == OwnershipScope.BUILDING
                && context.wholeBuildingAvailable() ? OwnershipScope.BUILDING : OwnershipScope.ROOM;
        RoomOwnershipAccess.Policy existing = editingPolicy(context);
        Map<UUID, RoomOwner> candidates = candidates(player, context,
                existing == null ? List.of() : existing.site().owners());
        Set<UUID> unique = new LinkedHashSet<>(payload.selectedOwners());
        List<RoomOwner> selected = new ArrayList<>();
        for (UUID uuid : unique) {
            RoomOwner owner = candidates.get(uuid);
            if (owner == null) continue;
            Entity entity = findEntity(player, uuid);
            if (entity instanceof VillagerEntityMCA villager
                    && RoomOwnershipAccess.mcaHomeIsInside(
                    context.level(), villager, context.building(), scope)) continue;
            selected.add(owner);
        }
        selected.sort(Comparator.comparing(RoomOwner::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RoomOwner::uuid));
        if (scope == OwnershipScope.BUILDING) clearOtherBuildingDeeds(context);
        context.site().setOwnership(payload.tagPos(), scope, payload.privateAccess(), selected);
        WorksiteRegister.get(player.getServer()).setDirty();
        WorksiteStorageIndex.invalidate(context.level());
        String message = !payload.privateAccess()
                ? "townstead.room_ownership.saved_public"
                : selected.isEmpty()
                ? "townstead.room_ownership.saved_residents"
                : "townstead.room_ownership.saved_private";
        player.displayClientMessage(Component.translatable(message, selected.size()), true);
    }

    /** Called when the active physical tag is broken or replaced. */
    public static void tagRemoved(ServerLevel level, BlockPos tagPos) {
        if (level == null || tagPos == null || level.getServer() == null) return;
        Worksite site = Worksites.of(level, tagPos);
        if (site == null || !tagPos.equals(site.ownershipTag())) return;
        site.clearOwnership();
        WorksiteRegister.get(level.getServer()).setDirty();
        WorksiteStorageIndex.invalidate(level);
    }

    private static RoomOwnershipSnapshotS2CPayload snapshot(
            ServerPlayer player, BlockPos tagPos, Context context) {
        RoomOwnershipAccess.Policy policy = editingPolicy(context);
        OwnershipScope scope = policy == null ? OwnershipScope.ROOM : policy.scope();
        boolean privateAccess = policy != null && policy.site().ownershipPrivate();
        if (!context.wholeBuildingAvailable()) scope = OwnershipScope.ROOM;
        List<RoomOwner> currentOwners = policy == null ? List.of() : policy.site().owners();
        Map<UUID, RoomOwner> candidates = candidates(player, context, currentOwners);
        Set<UUID> selected = new LinkedHashSet<>();
        for (RoomOwner owner : currentOwners) selected.add(owner.uuid());

        List<RoomOwnershipSnapshotS2CPayload.Person> people = new ArrayList<>();
        for (RoomOwner owner : candidates.values()) {
            boolean homeInRoom = false;
            boolean homeInBuilding = false;
            Entity entity = findEntity(player, owner.uuid());
            if (entity instanceof VillagerEntityMCA villager) {
                homeInRoom = RoomOwnershipAccess.mcaHomeIsInside(
                        context.level(), villager, context.building(), OwnershipScope.ROOM);
                homeInBuilding = RoomOwnershipAccess.mcaHomeIsInside(
                        context.level(), villager, context.building(), OwnershipScope.BUILDING);
            }
            people.add(new RoomOwnershipSnapshotS2CPayload.Person(
                    owner.uuid(), owner.name(), owner.kind(), selected.contains(owner.uuid()),
                    homeInRoom, homeInBuilding));
        }
        people.sort(Comparator
                .comparing((RoomOwnershipSnapshotS2CPayload.Person person) -> person.kind().ordinal())
                .thenComparing(RoomOwnershipSnapshotS2CPayload.Person::name,
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RoomOwnershipSnapshotS2CPayload.Person::uuid));
        return new RoomOwnershipSnapshotS2CPayload(tagPos, context.site().id(),
                context.site().name(), scope, privateAccess,
                context.wholeBuildingAvailable(), people);
    }

    private static Map<UUID, RoomOwner> candidates(
            ServerPlayer player, Context context, List<RoomOwner> currentOwners) {
        Map<UUID, RoomOwner> result = new LinkedHashMap<>();
        for (RoomOwner owner : currentOwners) result.put(owner.uuid(), owner);

        Village village = context.village();
        if (village != null) {
            village.getResidentsUUIDs().forEach(uuid -> {
                Entity entity = findEntity(player, uuid);
                if (entity instanceof VillagerEntityMCA villager && villager.isAlive()) {
                    result.put(uuid, new RoomOwner(uuid, villager.getDisplayName().getString(),
                            RoomOwner.Kind.VILLAGER));
                }
            });
        }
        for (ServerPlayer online : player.getServer().getPlayerList().getPlayers()) {
            result.put(online.getUUID(), new RoomOwner(online.getUUID(),
                    online.getGameProfile().getName(), RoomOwner.Kind.PLAYER));
        }
        return result;
    }

    private static Entity findEntity(ServerPlayer player, UUID uuid) {
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    private static Context context(ServerPlayer player, BlockPos tagPos, long expectedWorksiteId) {
        if (player == null || tagPos == null || !(player.level() instanceof ServerLevel level)) return null;
        if (player.distanceToSqr(tagPos.getX() + 0.5, tagPos.getY() + 0.5,
                tagPos.getZ() + 0.5) > MAX_EDIT_DISTANCE_SQR) return null;
        if (!level.getBlockState(tagPos).is(Townstead.ROOM_OWNERSHIP_TAG.get())) return null;

        Building building = McaBuildingCompat.buildingAt(level, tagPos);
        if (building == null) return null;
        Worksite site = Worksites.of(level, building);
        if (site == null || expectedWorksiteId >= 0 && site.id() != expectedWorksiteId) return null;

        Village ownerVillage = null;
        for (Village village : VillageManager.get(level)) {
            if (McaBuildings.byId(village, building.getId()) != null) {
                ownerVillage = village;
                break;
            }
        }
        return new Context(level, building, ownerVillage, site,
                McaBuildingCompat.hasWholeBuildingScope(ownerVillage, building));
    }

    private static RoomOwnershipAccess.Policy editingPolicy(Context context) {
        if (context.site().ownershipTag() != null) {
            return new RoomOwnershipAccess.Policy(
                    context.site(), context.site().ownershipScope());
        }
        return RoomOwnershipAccess.policyFor(context.level(), context.building());
    }

    private static void clearOtherBuildingDeeds(Context context) {
        WorksiteRegister register = WorksiteRegister.get(context.level().getServer());
        for (Worksite candidate : register.all()) {
            if (candidate == context.site() || candidate.ownershipTag() == null
                    || candidate.ownershipScope() != OwnershipScope.BUILDING
                    || !candidate.key().dimension().equals(context.level().dimension().location())) continue;
            Building anchor = com.aetherianartificer.townstead.compat.mca.McaRoomBinding
                    .byId(context.level(), candidate.key());
            if (anchor != null && McaBuildingCompat.sameWholeBuilding(
                    context.village(), context.building(), anchor)) {
                candidate.clearOwnership();
            }
        }
    }

    private record Context(ServerLevel level, Building building, Village village, Worksite site,
                           boolean wholeBuildingAvailable) {}
}
