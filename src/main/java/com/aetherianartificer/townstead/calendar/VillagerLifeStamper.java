package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Random;

/**
 * Per-villager tick step that fabricates a date-of-birth on first encounter
 * and an establishment date on the villager's home village if neither exists
 * yet. Stamping is deterministic (seeded by entity UUID and village id) so a
 * villager / village gets the same fabricated date across reloads up until
 * the first save persists it.
 *
 * Cost on the steady-state path is one {@code CompoundTag.getBoolean} per
 * villager per tick. Called from {@link com.aetherianartificer.townstead.tick.VillagerServerTickDispatcher}.
 */
public final class VillagerLifeStamper {

    private VillagerLifeStamper() {}

    public static void tick(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel serverLevel)) return;
        MinecraftServer server = serverLevel.getServer();
        if (server == null) return;

        CompoundTag life = readLife(villager);
        boolean wasStamped = LifeData.hasBirth(life);
        if (!wasStamped) {
            stampDob(villager, server, life);
            writeLife(villager, life);
            broadcastFreshStamp(villager);
        }

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

    private static void stampDob(VillagerEntityMCA villager, MinecraftServer server, CompoundTag life) {
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
        LifeData.setBirth(life, birthDay, false);
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

        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        WorldCalendarSavedData.VillageKey key = new WorldCalendarSavedData.VillageKey(
                level.dimension().location(), village.getId());
        if (data.getVillageBirth(key) != null) return;

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

    // ---- Forge/NeoForge access split ----

    private static CompoundTag readLife(VillagerEntityMCA villager) {
        //? if neoforge {
        return villager.getData(Townstead.LIFE_DATA);
        //?} else {
        /*return villager.getPersistentData().getCompound("townstead_life");
        *///?}
    }

    private static void writeLife(VillagerEntityMCA villager, CompoundTag life) {
        //? if neoforge {
        villager.setData(Townstead.LIFE_DATA, life);
        //?} else {
        /*villager.getPersistentData().put("townstead_life", life);
        *///?}
    }

    // ---- Helper for the calendar API ----

    @Nullable
    public static CompoundTag peek(VillagerEntityMCA villager) {
        CompoundTag t = readLife(villager);
        return LifeData.hasBirth(t) ? t : null;
    }
}
