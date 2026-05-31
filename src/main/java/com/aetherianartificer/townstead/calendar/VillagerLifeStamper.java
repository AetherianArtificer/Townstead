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

        ensureStamped(villager, server);
        stampVillageBirthIfNeeded(villager, serverLevel, server);
    }

    /**
     * Roll the villager's stage durations and fabricate a birth if either is
     * missing, broadcasting a fresh life sync when anything changed. Safe to call
     * outside the per-tick path (e.g. when a player starts tracking the villager)
     * so the client always has a populated snapshot before opening the editor.
     */
    public static void ensureStamped(VillagerEntityMCA villager, MinecraftServer server) {
        TownsteadVillager state = TownsteadVillagers.get(villager);

        // Backfill origin + roll stage durations FIRST so the birth fabrication can
        // place the villager within the correct stage of its rolled cycle. (Assigns
        // the default origin id only; genes are left as MCA rolled them.)
        boolean rolled = com.aetherianartificer.townstead.origin.OriginSpawnHandler.backfillIfMissing(villager);

        boolean stamped = false;
        if (!state.life().hasBirth()) {
            state.life().setBirth(fabricateDob(villager, server), false);
            stamped = true;
        } else if (rolled) {
            // A re-roll means stageDays changed shape/scale (cycle re-authored, aging
            // anchor changed, or a pre-rework save). The old birth was measured against
            // the previous lifespan and is now meaningless, so re-place the villager
            // mid-stage of the freshly-rolled cycle by its current MCA body. Without
            // this, an old birth + new stageDays yields absurd apparent ages (e.g. a
            // 2352-day lifespan villager reading 290 against a 730-day human cycle).
            state.life().setBirth(fabricateDob(villager, server), false);
            stamped = true;
        } else if (townstead$birthIncoherent(state, server, villager)) {
            // Self-heal a stale birth from the old human-decades fabrication: a
            // villager reading older than its entire cycle gets re-placed mid-stage
            // by its current MCA AgeState, so the editor slider seeds sanely.
            state.life().setBirth(fabricateDob(villager, server), false);
            stamped = true;
        }

        // Resolve + commit the current stage NOW (sets isSenior, applies senior effects)
        // rather than waiting up to a full day for LifeStageTicker, so the broadcast below
        // carries the correct senior flag and hair desaturation starts immediately.
        if (stamped || rolled) {
            com.aetherianartificer.townstead.origin.LifeStageProgression.tickResolveStage(villager);
            broadcastFreshStamp(villager);
        }
    }

    /**
     * True when the stored birth no longer makes sense for this villager: either it
     * reads older than the whole cycle (legacy human-decades fabrication), or the
     * stage it resolves to disagrees with the villager's live MCA body (a stale birth
     * stamped near "today" for an already-adult villager, which would otherwise show
     * as a 1-year-old). Both re-fabricate from the current MCA AgeState.
     */
    private static boolean townstead$birthIncoherent(TownsteadVillager state, MinecraftServer server,
                                                      VillagerEntityMCA villager) {
        if (!state.life().hasStageDays()) return false;
        long bioAge = TownsteadCalendar.lifeDay(server) - state.life().birthWorldDay();
        long total = 0;
        for (int d : state.life().stageDays()) total += Math.max(0, d);
        if (bioAge > total) return true;
        return !com.aetherianartificer.townstead.origin.LifeStageProgression.birthMatchesBody(villager);
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
        // Place the villager mid-way through the stage matching its MCA AgeState,
        // using its rolled stage durations — so a spawned adult lands inside the
        // adult stage rather than decades past death under the new game-year cycle.
        return com.aetherianartificer.townstead.origin.LifeStageProgression.fabricateBirthLifeDay(
                villager, server, safeAgeState(villager));
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

        // Life-day axis (like villager DOB): village age stays stable across
        // calendar re-dating; establishmentOf shifts it back for display.
        long today = TownsteadCalendar.lifeDay(server);
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
