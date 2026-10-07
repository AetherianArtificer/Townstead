package com.aetherianartificer.townstead.journey;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Villagers who are away on the road. A traveller leaves the world whole (body, name, family links,
 * memories and states all ride along in their saved entity data) and comes back as the same person,
 * with the same UUID, when they arrive. While away, nothing about them is loaded or ticked.
 *
 * <p>A journey has a purpose (who asked for it, so the right system brings them in) and the world
 * day they are ready to arrive. The destination is chosen by that system when they arrive; a fixed
 * one can be kept in {@code data}.</p>
 */
public final class Journeys extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger("Townstead/Journeys");
    private static final String FILE_ID = "townstead_journeys";

    /**
     * @param purpose  the system that owns the journey, such as {@code townstead:persona_move}
     * @param readyDay the world day from which they may arrive
     * @param data     whatever the owning system needs (a destination, an errand)
     */
    public record Journey(UUID traveller, ResourceLocation purpose, String name, CompoundTag entity,
                          long departedDay, long readyDay, CompoundTag data) {}

    private final Map<UUID, Journey> away = new LinkedHashMap<>();
    private static final String COMPANIONS = "companions";
    private static final double COMPANION_RANGE = 24;
    /** Data flag: the traveller's pets stay at home instead of walking out with them. */
    public static final String LEAVE_PETS = "leave_pets";
    /** Data flag: the traveller keeps their home while away (a companion on a trip, not a move). */
    public static final String KEEP_HOME = "keep_home";
    /** A journey that comes back to the village it left, as an errand does. */
    public static final ResourceLocation ERRAND = ResourceLocation.tryParse("townstead:errand");
    private static final int ERRAND_INTERVAL = 100;

    public static Journeys get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(Journeys::new, Journeys::load), FILE_ID);
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(Journeys::load, Journeys::new, FILE_ID);
        *///?}
    }

    /**
     * Sends {@code villager} on the road: they leave their home village, their entity is saved and
     * removed. Returns the journey, or null when they could not be saved.
     */
    public static @Nullable Journey depart(VillagerEntityMCA villager, ResourceLocation purpose, int days, CompoundTag data) {
        MinecraftServer server = villager.getServer();
        if (server == null || !villager.isAlive()) return null;
        if (!data.getBoolean(KEEP_HOME)) villager.getResidency().leaveHome();
        // Townstead's own villager state rides in the entity data, so it is written out first.
        com.aetherianartificer.townstead.villager.TownsteadVillagers.flush(villager);
        CompoundTag entity = new CompoundTag();
        if (!villager.saveAsPassenger(entity)) return null;
        long today = TownsteadCalendar.worldDay(server);
        CompoundTag carried = data.copy();
        // Their pets walk out with them, unless they are told to stay.
        ListTag companions = new ListTag();
        for (net.minecraft.world.entity.Mob pet : data.getBoolean(LEAVE_PETS) ? java.util.List.<net.minecraft.world.entity.Mob>of()
                : com.aetherianartificer.townstead.pet.VillagerPets.petsOf(villager, COMPANION_RANGE)) {
            CompoundTag saved = new CompoundTag();
            if (!pet.saveAsPassenger(saved)) continue;
            companions.add(saved);
            pet.discard();
        }
        if (!companions.isEmpty()) carried.put(COMPANIONS, companions);
        Journey journey = new Journey(villager.getUUID(), purpose, villager.getName().getString(), entity, today,
                today + Math.max(0, days), carried);
        Journeys journeys = get(server);
        journeys.away.put(villager.getUUID(), journey);
        journeys.setDirty();
        villager.discard();
        LOGGER.info("{} left on a journey ({})", journey.name(), purpose);
        for (java.util.function.Consumer<Journey> listener : DEPARTED) listener.accept(journey);
        return journey;
    }

    /** Brings a traveller back into the world at {@code at}. Returns the villager, or null when they could not be restored. */
    public static @Nullable VillagerEntityMCA arrive(ServerLevel level, UUID traveller, BlockPos at) {
        Journeys journeys = get(level.getServer());
        Journey journey = journeys.away.get(traveller);
        if (journey == null) return null;
        Optional<Entity> loaded = Optional.ofNullable(EntityType.loadEntityRecursive(journey.entity().copy(), level, entity -> {
            entity.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, entity.getYRot(), entity.getXRot());
            return entity;
        }));
        if (loaded.isEmpty() || !(loaded.get() instanceof VillagerEntityMCA villager)) {
            LOGGER.warn("Journey of {} could not be restored", journey.name());
            return null;
        }
        if (level.getEntity(traveller) != null || !level.addFreshEntity(villager)) return null;
        journeys.away.remove(traveller);
        journeys.setDirty();
        if (!journey.data().getBoolean(KEEP_HOME)) villager.getResidency().seekHome();
        ListTag companions = journey.data().getList(COMPANIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < companions.size(); i++) {
            Entity pet = EntityType.loadEntityRecursive(companions.getCompound(i).copy(), level, e -> {
                e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, e.getYRot(), e.getXRot());
                return e;
            });
            if (pet != null && level.getEntity(pet.getUUID()) == null) level.addFreshEntity(pet);
        }
        LOGGER.info("{} arrived from a journey ({})", journey.name(), journey.purpose());
        return villager;
    }

    /** Someone walking out of town before a journey starts. */
    private record Leaving(ServerLevel level, BlockPos edge, @Nullable ResourceLocation purpose, int days, CompoundTag data) {}

    private static final Map<UUID, Leaving> LEAVING = new java.util.HashMap<>();
    private static final double UNSEEN = 24;
    private static final String LEAVING_TAG = "townstead:leaving";

    /**
     * Sends {@code villager} on an errand: they walk out of town and leave once no player is near,
     * then come back to their home village once {@code days} have passed and the village is loaded.
     * With {@code adopt}, they come back with a new animal of that type as their pet. Returns whether
     * the errand was set.
     */
    public static boolean errand(VillagerEntityMCA villager, int days, @Nullable ResourceLocation adopt) {
        return errand(villager, days, adopt, false, false);
    }

    /** As {@link #errand(VillagerEntityMCA, int, ResourceLocation)}; the adopted pet can be essential, and their pets can stay home. */
    public static boolean errand(VillagerEntityMCA villager, int days, @Nullable ResourceLocation adopt,
                                 boolean adoptEssential, boolean leavePets) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        var village = net.conczin.mca.server.world.data.VillageManager.get(level)
                .findNearestVillage(villager.blockPosition(), net.conczin.mca.server.world.data.Village.MERGE_MARGIN).orElse(null);
        if (village == null) return false;
        CompoundTag data = new CompoundTag();
        data.putString("return_dimension", level.dimension().location().toString());
        data.putInt("return_village", village.getId());
        if (adopt != null) data.putString("adopt", adopt.toString());
        if (adoptEssential) data.putBoolean("adopt_essential", true);
        if (leavePets) data.putBoolean(LEAVE_PETS, true);
        queue(villager, level, ERRAND, days, data);
        return true;
    }

    /** Called with each journey as its traveller leaves the world. */
    private static final java.util.List<java.util.function.Consumer<Journey>> DEPARTED = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void onDepart(java.util.function.Consumer<Journey> listener) {
        DEPARTED.add(listener);
    }

    /** Whether {@code villager} is walking out of town to leave. */
    public static boolean isLeaving(UUID villager) {
        return LEAVING.containsKey(villager);
    }

    /**
     * Walks {@code villager} out of town and starts a journey once no player can see them go, the
     * way an errand starts. Returns false when they are not in a world.
     */
    public static boolean departUnseen(VillagerEntityMCA villager, ResourceLocation purpose, int days, CompoundTag data) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        queue(villager, level, purpose, days, data);
        return true;
    }

    /**
     * Walks {@code villager} out of town for good: once no player can see them, they are gone.
     * Returns false when they are not in a village.
     */
    public static boolean leave(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return false;
        queue(villager, level, null, 0, new CompoundTag());
        return true;
    }

    /**
     * Sets {@code villager} walking to the edge of their town to leave. What they are leaving for is
     * also written on them, so a world closed while they wait picks it up again when they load.
     */
    private static void queue(VillagerEntityMCA villager, ServerLevel level, @Nullable ResourceLocation purpose, int days, CompoundTag data) {
        var village = net.conczin.mca.server.world.data.VillageManager.get(level)
                .findNearestVillage(villager.blockPosition(), net.conczin.mca.server.world.data.Village.MERGE_MARGIN).orElse(null);
        BlockPos edge = village == null ? null : edgeOf(level, village);
        LEAVING.put(villager.getUUID(), new Leaving(level, edge == null ? villager.blockPosition() : edge, purpose, days, data));
        CompoundTag saved = new CompoundTag();
        if (purpose != null) saved.putString("purpose", purpose.toString());
        saved.putInt("days", days);
        saved.put("data", data.copy());
        villager.getPersistentData().put(LEAVING_TAG, saved);
    }

    /** Join hook: someone who was walking out of town when the world closed sets off again. */
    public static void onJoin(Entity entity) {
        if (!(entity instanceof VillagerEntityMCA villager) || !(entity.level() instanceof ServerLevel level)
                || LEAVING.containsKey(entity.getUUID())) return;
        CompoundTag saved = villager.getPersistentData().getCompound(LEAVING_TAG);
        if (saved.isEmpty()) return;
        ResourceLocation purpose = saved.contains("purpose") ? ResourceLocation.tryParse(saved.getString("purpose")) : null;
        queue(villager, level, purpose, saved.getInt("days"), saved.getCompound("data"));
    }

    /** Walks leavers out of town and starts their journey once no player can see them go. */
    private static void walkLeavers() {
        var it = LEAVING.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Leaving leaving = entry.getValue();
            if (!(leaving.level().getEntity(entry.getKey()) instanceof VillagerEntityMCA villager) || !villager.isAlive()) {
                it.remove();
                continue;
            }
            // Never in front of anyone: a villager held still in a conversation cannot walk away, and
            // leaving on a timer made them vanish mid-sentence. They go when nobody is near to see it.
            boolean seen = leaving.level().getNearestPlayer(villager, UNSEEN) != null;
            if (!seen) {
                it.remove();
                // Off them before they are saved for the road, or they would set out again on arrival.
                villager.getPersistentData().remove(LEAVING_TAG);
                if (leaving.purpose() == null) {
                    villager.getResidency().leaveHome();
                    villager.discard();
                } else {
                    depart(villager, leaving.purpose(), leaving.days(), leaving.data());
                }
                continue;
            }
            villager.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET,
                    new net.minecraft.world.entity.ai.memory.WalkTarget(leaving.edge(), 0.6f, 1));
        }
    }

    /** Brings back errand-runners whose time is up, once their village is loaded. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 == 0 && !LEAVING.isEmpty()) walkLeavers();
        if (server.getTickCount() % ERRAND_INTERVAL != 0) return;
        Journeys journeys = get(server);
        if (journeys.away.isEmpty()) return;
        long today = TownsteadCalendar.worldDay(server);
        for (Journey journey : List.copyOf(journeys.away.values())) {
            if (!journey.purpose().equals(ERRAND) || journey.readyDay() > today) continue;
            ResourceLocation dimension = ResourceLocation.tryParse(journey.data().getString("return_dimension"));
            ServerLevel level = dimension == null ? null
                    : server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension));
            if (level == null) continue;
            var village = net.conczin.mca.server.world.data.VillageManager.get(level)
                    .getOrEmpty(journey.data().getInt("return_village")).orElse(null);
            if (village == null) continue;
            BlockPos at = edgeOf(level, village);
            if (at == null) continue;
            VillagerEntityMCA villager = arrive(level, journey.traveller(), at);
            if (villager == null) continue;
            ResourceLocation adopt = ResourceLocation.tryParse(journey.data().getString("adopt"));
            if (adopt != null && !journey.data().getString("adopt").isEmpty()) {
                var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(adopt).orElse(null);
                var pet = type == null ? null : com.aetherianartificer.townstead.pet.VillagerPets.adoptNew(level, type, villager,
                        journey.data().getBoolean("adopt_essential"));
                if (pet == null) {
                    LOGGER.warn("{} came back without the {} they went to fetch: it could not be spawned at {}",
                            villager.getName().getString(), adopt, villager.blockPosition());
                } else {
                    LOGGER.info("{} came back with a {} at {}", villager.getName().getString(), adopt, pet.blockPosition());
                }
            }
        }
    }

    /** A loaded point on the ground just inside the village edge, or null when that ground is not loaded. */
    private static @Nullable BlockPos edgeOf(ServerLevel level, net.conczin.mca.server.world.data.Village village) {
        var box = village.getBox();
        var center = village.getCenter();
        double angle = level.getRandom().nextDouble() * Math.PI * 2;
        int radius = Math.max(8, Math.max(box.getXSpan(), box.getZSpan()) / 2 - 2);
        BlockPos column = new BlockPos(center.getX() + (int) (Math.cos(angle) * radius), 0, center.getZ() + (int) (Math.sin(angle) * radius));
        if (!level.isLoaded(column)) return null;
        return level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
    }

    public @Nullable Journey of(UUID traveller) {
        return away.get(traveller);
    }

    public List<Journey> by(ResourceLocation purpose) {
        List<Journey> out = new ArrayList<>();
        for (Journey journey : away.values()) if (journey.purpose().equals(purpose)) out.add(journey);
        return out;
    }

    public boolean isAway(UUID traveller) {
        return away.containsKey(traveller);
    }

    /** Ends a journey without bringing the traveller back (they are gone for good). */
    public void forget(UUID traveller) {
        if (away.remove(traveller) != null) setDirty();
    }

    //? if >=1.21 {
    private static Journeys load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
    //?} else {
    /*private static Journeys load(CompoundTag tag) {
    *///?}
        Journeys data = new Journeys();
        ListTag list = tag.getList("away", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation purpose = ResourceLocation.tryParse(entry.getString("purpose"));
            if (purpose == null || !entry.hasUUID("traveller")) continue;
            Journey journey = new Journey(entry.getUUID("traveller"), purpose, entry.getString("name"),
                    entry.getCompound("entity"), entry.getLong("departed"), entry.getLong("ready"), entry.getCompound("data"));
            data.away.put(journey.traveller(), journey);
        }
        return data;
    }

    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        ListTag list = new ListTag();
        for (Journey journey : away.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("traveller", journey.traveller());
            entry.putString("purpose", journey.purpose().toString());
            entry.putString("name", journey.name());
            entry.put("entity", journey.entity());
            entry.putLong("departed", journey.departedDay());
            entry.putLong("ready", journey.readyDay());
            entry.put("data", journey.data());
            list.add(entry);
        }
        tag.put("away", list);
        return tag;
    }
}
