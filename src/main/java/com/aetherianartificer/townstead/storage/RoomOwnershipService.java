package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.compat.mca.McaHomes;
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
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Places, opens, edits and removes Ownership Plaques. */
public final class RoomOwnershipService {
    private static final double MAX_EDIT_DISTANCE_SQR = 64.0;

    private RoomOwnershipService() {}

    /**
     * A new plaque lists the player who placed it and the villagers MCA already houses there. It
     * stays blank when its room already has a plaque, or when it hangs in a place the player may
     * not edit.
     */
    public static void placed(ServerPlayer player, BlockPos tagPos) {
        Context context = context(player, tagPos, -1L);
        if (context == null || context.site().ownershipTag() != null) return;
        RoomOwnershipAccess.Place governing = RoomOwnershipAccess.placeOf(context.level(), context.building());
        if (governing != null && !mayEdit(player, governing)) return;
        OwnershipScope scope = governing == null && context.wholeBuildingAvailable()
                ? OwnershipScope.BUILDING : OwnershipScope.ROOM;
        List<RoomOwner> owners = new ArrayList<>();
        owners.add(new RoomOwner(player.getUUID(), player.getGameProfile().getName(), RoomOwner.Kind.PLAYER));
        if (!BuildingLodging.isLodging(context.village(), context.building())) {
            for (VillagerEntityMCA villager : residents(context)) {
                if (RoomOwnershipAccess.mcaHomeIsInside(context.level(), villager, context.building(), scope)) {
                    owners.add(new RoomOwner(villager.getUUID(), villager.getDisplayName().getString(), RoomOwner.Kind.VILLAGER));
                }
            }
        }
        if (scope == OwnershipScope.BUILDING) clearOtherBuildingDeeds(context);
        context.site().setOwnership(tagPos, scope, owners);
        saved(context);
    }

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

    /**
     * Saves the plaque's list. In a home, a villager added gets a free bed there and a villager
     * taken off lets go of theirs; in lodging the list only says who runs the place.
     */
    public static void apply(ServerPlayer player, RoomOwnershipSetC2SPayload payload) {
        if (player == null || payload == null) return;
        Context context = context(player, payload.tagPos(), payload.worksiteId());
        if (context == null) return;
        RoomOwnershipAccess.Place governing = editingPlace(context);
        if (governing != null && !mayEdit(player, governing)) {
            player.displayClientMessage(Component.translatable("townstead.room_ownership.read_only"), true);
            return;
        }

        OwnershipScope scope = payload.scope() == OwnershipScope.BUILDING
                && context.wholeBuildingAvailable() ? OwnershipScope.BUILDING : OwnershipScope.ROOM;
        List<RoomOwner> currentOwners = governing == null ? List.of() : governing.site().owners();
        Map<UUID, RoomOwner> candidates = candidates(player, context, currentOwners);
        Set<UUID> wanted = new LinkedHashSet<>(payload.selectedOwners());
        boolean lodging = BuildingLodging.isLodging(context.village(), context.building());
        List<Building> rooms = McaBuildingCompat.rooms(context.village(), context.building(),
                scope == OwnershipScope.BUILDING);

        int withoutBed = 0;
        if (!lodging) {
            for (VillagerEntityMCA villager : residents(context)) {
                boolean inside = RoomOwnershipAccess.mcaHomeIsInside(context.level(), villager, context.building(), scope);
                boolean want = wanted.contains(villager.getUUID());
                if (inside && !want) {
                    McaHomes.release(villager);
                } else if (!inside && want && !giveBed(context.level(), villager, rooms)) {
                    wanted.remove(villager.getUUID());
                    withoutBed++;
                }
            }
        }

        List<RoomOwner> selected = new ArrayList<>();
        for (UUID uuid : wanted) {
            RoomOwner owner = candidates.get(uuid);
            if (owner != null) selected.add(owner);
        }
        selected.sort(Comparator.comparing(RoomOwner::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(RoomOwner::uuid));
        if (scope == OwnershipScope.BUILDING) clearOtherBuildingDeeds(context);
        context.site().setOwnership(payload.tagPos(), scope, selected);
        saved(context);

        Component message = withoutBed > 0
                ? Component.translatable("townstead.room_ownership.no_free_bed", withoutBed)
                : selected.isEmpty()
                ? Component.translatable("townstead.room_ownership.saved_open")
                : Component.translatable("townstead.room_ownership.saved", selected.size());
        player.displayClientMessage(message, true);
    }

    /** Called when the active physical tag is broken or replaced. */
    public static void tagRemoved(ServerLevel level, BlockPos tagPos) {
        if (level == null || tagPos == null || level.getServer() == null) return;
        Worksite site = Worksites.of(level, tagPos);
        if (site == null || !tagPos.equals(site.ownershipTag())) return;
        site.clearOwnership();
        WorksiteRegister.get(level.getServer()).setDirty();
        WorksiteStorageIndex.invalidate(level);
        Building building = McaBuildingCompat.buildingAt(level, tagPos);
        refreshResidents(level, building == null ? null : RoomOwnershipAccess.villageOf(level, building));
    }

    /**
     * After MCA's "set home": a villager housed in a home with a plaque joins it when the player
     * may edit the plaque, and is sent back out when not.
     */
    public static void homeSetByPlayer(ServerPlayer player, VillagerEntityMCA villager) {
        if (player == null || villager == null || !(villager.level() instanceof ServerLevel level)) return;
        Optional<GlobalPos> home = villager.getBrain().getMemory(MemoryModuleType.HOME);
        if (home.isEmpty() || !home.get().dimension().equals(level.dimension())) return;
        RoomOwnershipAccess.Place place = RoomOwnershipAccess.placeAt(level, home.get().pos());
        if (place == null || !place.restricts() || place.lodging()) return;
        if (place.site().owners().stream().anyMatch(owner -> owner.uuid().equals(villager.getUUID()))) return;
        if (!mayEdit(player, place)) {
            McaHomes.release(villager);
            player.displayClientMessage(Component.translatable("townstead.room_ownership.sethome_denied"), true);
            return;
        }
        List<RoomOwner> owners = new ArrayList<>(place.site().owners());
        owners.add(new RoomOwner(villager.getUUID(), villager.getDisplayName().getString(), RoomOwner.Kind.VILLAGER));
        place.site().setOwnership(place.site().ownershipTag(), place.scope(), owners);
        WorksiteRegister.get(level.getServer()).setDirty();
        WorksiteStorageIndex.invalidate(level);
    }

    /**
     * Who may change a plaque: operators, the people on it, and anyone holding an office in the
     * village's faction. A plaque that lists nobody is open to anyone.
     */
    public static boolean mayEdit(ServerPlayer player, RoomOwnershipAccess.Place place) {
        if (player == null) return false;
        if (place == null || !place.restricts() || player.hasPermissions(2)) return true;
        if (place.lists(player.getUUID(), null)) return true;
        return place.village() != null && holdsOffice(player, place.level(), place.village());
    }

    private static boolean holdsOffice(ServerPlayer player, ServerLevel level, Village village) {
        var data = com.aetherianartificer.townstead.politics.state.PoliticalSavedData.get(level.getServer());
        var faction = data.faction(new com.aetherianartificer.townstead.politics.state.SettlementRef(
                level.dimension().location(), village.getId()));
        if (faction == null) return false;
        var kind = com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions.snapshot().kind(faction.kind());
        if (kind == null) return false;
        Set<net.minecraft.resources.ResourceLocation> held =
                com.aetherianartificer.townstead.politics.state.FactionBonds.kinds(data, player.getUUID(), faction.id());
        return kind.offices().stream().anyMatch(office -> held.contains(office.bond()));
    }

    private static boolean giveBed(ServerLevel level, VillagerEntityMCA villager, List<Building> rooms) {
        for (BlockPos bed : beds(level, rooms)) {
            if (McaHomes.assign(level, villager, bed)) return true;
        }
        return false;
    }

    /** The head of every bed in these rooms. */
    static List<BlockPos> beds(ServerLevel level, List<Building> rooms) {
        Set<Long> seen = new TreeSet<>();
        List<BlockPos> out = new ArrayList<>();
        for (Building room : rooms) {
            for (BlockPos pos : (Iterable<BlockPos>) room.getBlockPosStream()::iterator) {
                if (!level.isLoaded(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof BedBlock) || !state.hasProperty(BedBlock.PART)) continue;
                BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD
                        ? pos : pos.relative(BedBlock.getConnectedDirection(state));
                if (seen.add(head.asLong())) out.add(head.immutable());
            }
        }
        return out;
    }

    private static List<VillagerEntityMCA> residents(Context context) {
        List<VillagerEntityMCA> out = new ArrayList<>();
        if (context.village() == null) return out;
        context.village().getResidentsUUIDs().forEach(uuid -> {
            if (context.level().getEntity(uuid) instanceof VillagerEntityMCA villager && villager.isAlive()) out.add(villager);
        });
        return out;
    }

    private static void saved(Context context) {
        WorksiteRegister.get(context.level().getServer()).setDirty();
        WorksiteStorageIndex.invalidate(context.level());
        refreshResidents(context.level(), context.village());
    }

    /** Citizenship by residence follows the plaque at once instead of at the next day's upkeep. */
    private static void refreshResidents(ServerLevel level, @Nullable Village village) {
        if (village == null || !com.aetherianartificer.townstead.switchboard.Systems.on(
                com.aetherianartificer.townstead.switchboard.Systems.POLITICS)) return;
        com.aetherianartificer.townstead.politics.state.PoliticalVillageBootstrap.ensure(level, village);
    }

    private static RoomOwnershipSnapshotS2CPayload snapshot(
            ServerPlayer player, BlockPos tagPos, Context context) {
        RoomOwnershipAccess.Place place = editingPlace(context);
        OwnershipScope scope = place == null ? OwnershipScope.ROOM : place.scope();
        if (!context.wholeBuildingAvailable()) scope = OwnershipScope.ROOM;
        List<RoomOwner> currentOwners = place == null ? List.of() : place.site().owners();
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
        int roomBeds = beds(context.level(), McaBuildingCompat.rooms(context.village(), context.building(), false)).size();
        int buildingBeds = context.wholeBuildingAvailable()
                ? beds(context.level(), McaBuildingCompat.rooms(context.village(), context.building(), true)).size()
                : roomBeds;
        return new RoomOwnershipSnapshotS2CPayload(tagPos, context.site().id(),
                context.site().name(), scope, context.wholeBuildingAvailable(),
                BuildingLodging.isLodging(context.village(), context.building()),
                place == null || mayEdit(player, place), roomBeds, buildingBeds, people);
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

    /** This room's own plaque when it has one, else the plaque governing it. */
    private static @Nullable RoomOwnershipAccess.Place editingPlace(Context context) {
        if (context.site().ownershipTag() != null) {
            return new RoomOwnershipAccess.Place(context.level(), context.village(), context.building(),
                    context.site(), context.site().ownershipScope(),
                    BuildingLodging.isLodging(context.village(), context.building()));
        }
        return RoomOwnershipAccess.placeOf(context.level(), context.building());
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
