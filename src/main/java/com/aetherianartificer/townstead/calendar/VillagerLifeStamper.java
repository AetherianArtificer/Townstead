package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-villager tick step that fabricates a date-of-birth on first encounter
 * and an establishment date on the villager's home village if neither exists
 * yet. Stamping is deterministic (seeded by entity UUID and village id) so a
 * villager / village gets the same fabricated date across reloads up until
 * the first save persists it.
 *
 * Called from {@link com.aetherianartificer.townstead.tick.VillagerServerTickDispatcher}.
 */
public final class VillagerLifeStamper {
    private static final Set<ServerVillageKey> KNOWN_STAMPED_VILLAGES =
            ConcurrentHashMap.newKeySet();

    private VillagerLifeStamper() {}

    public static void tick(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel serverLevel)) return;
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return;

        TownsteadVillager state = TownsteadVillagers.get(villager);
        if (!state.life().hasBirth()) {
            state.life().setBirth(fabricateDob(villager, server), false);
            broadcastFreshStamp(villager);
        }

        // Backfill an origin for villagers that predate the Origins system (or any
        // that bypassed the FinalizeSpawn hook). Assigns the default id only; genes
        // are left as MCA rolled them, so existing villagers keep their appearance.
        com.aetherianartificer.townstead.origin.OriginSpawnHandler.backfillIfMissing(villager);

        stampVillageBirthIfNeeded(villager, serverLevel, server);
    }

    private static void broadcastFreshStamp(VillagerEntityMCA villager) {
        com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload payload =
                com.aetherianartificer.townstead.Townstead.townstead$lifeSync(villager);
        if (payload == null) return;
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(villager, payload);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(villager, payload);
        *///?}
    }

    // ---- DOB fabrication ----

    private static long fabricateDob(VillagerEntityMCA villager, MinecraftServer server) {
        long today = TownsteadCalendar.worldDay(server);
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        int dpy = profile != null && profile.daysPerYear() > 0 ? profile.daysPerYear() : 360;
        AgeState state = safeAgeState(villager);

        int yearsAgoMin;
        int yearsAgoMax;
        switch (state) {
            case BABY -> { yearsAgoMin = 0; yearsAgoMax = 1; }
            case TODDLER -> { yearsAgoMin = 2; yearsAgoMax = 4; }
            case CHILD -> { yearsAgoMin = 5; yearsAgoMax = 9; }
            case TEEN -> { yearsAgoMin = 10; yearsAgoMax = 17; }
            default -> { yearsAgoMin = 18; yearsAgoMax = 60; } // ADULT and any future stages
        }

        Random rng = new Random(villager.getUUID().getLeastSignificantBits()
                ^ villager.getUUID().getMostSignificantBits());
        int yearsAgo = yearsAgoMin + rng.nextInt(Math.max(1, yearsAgoMax - yearsAgoMin + 1));
        int dayInYear = rng.nextInt(dpy);
        long birthDay = today - (long) yearsAgo * dpy - dayInYear;
        return birthDay;
    }

    private static AgeState safeAgeState(VillagerEntityMCA villager) {
        try {
            AgeState s = villager.getAgeState();
            return s != null ? s : AgeState.ADULT;
        } catch (Throwable t) {
            return AgeState.ADULT;
        }
    }

    // ---- Village establishment fabrication ----

    private static void stampVillageBirthIfNeeded(VillagerEntityMCA villager, ServerLevel level, MinecraftServer server) {
        Village village = resolveHomeVillage(villager);
        if (village == null) return;

        WorldCalendarSavedData.VillageKey key = new WorldCalendarSavedData.VillageKey(
                level.dimension().location(), village.getId());
        ServerVillageKey cacheKey = new ServerVillageKey(System.identityHashCode(server), key);
        if (KNOWN_STAMPED_VILLAGES.contains(cacheKey)) return;

        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        if (data.getVillageBirth(key) != null) {
            KNOWN_STAMPED_VILLAGES.add(cacheKey);
            return;
        }

        long today = TownsteadCalendar.worldDay(server);
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        int dpy = profile != null && profile.daysPerYear() > 0 ? profile.daysPerYear() : 360;

        boolean playerFounded = looksPlayerFounded(village);
        long birthDay;
        if (playerFounded) {
            birthDay = today;
        } else {
            Random rng = new Random((long) village.getId()
                    ^ level.dimension().location().toString().hashCode() * 0x9E3779B97F4A7C15L);
            int yearsAgo = 50 + rng.nextInt(251); // 50..300 inclusive
            int dayInYear = rng.nextInt(dpy);
            birthDay = today - (long) yearsAgo * dpy - dayInYear;
        }
        data.putVillageBirth(key, new WorldCalendarSavedData.VillageBirth(birthDay, playerFounded));
        KNOWN_STAMPED_VILLAGES.add(cacheKey);
    }

    @Nullable
    private static Village resolveHomeVillage(VillagerEntityMCA villager) {
        try {
            Optional<Village> home = villager.getResidency().getHomeVillage();
            if (home.isPresent()) return home.get();
            return Village.findNearest(villager).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Pregenerated MCA villages typically appear with a full adult population
     * and historical buildings already present. Player-founded villages tend
     * to start with the player and one or two seed villagers. We use building
     * count as the cheapest proxy available without scanning resident lists.
     */
    private static boolean looksPlayerFounded(Village village) {
        try {
            return village.getBuildings().size() <= 2;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---- Helper for the calendar API ----

    @Nullable
    public static CompoundTag peek(VillagerEntityMCA villager) {
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        return life.hasBirth() ? life.toTag() : null;
    }

    private record ServerVillageKey(int serverId, WorldCalendarSavedData.VillageKey villageKey) {}
}
