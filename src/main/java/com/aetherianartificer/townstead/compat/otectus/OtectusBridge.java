package com.aetherianartificer.townstead.compat.otectus;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleEmitter;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.chronicle.template.ChronicleTriggerIndex;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Chronicles' ear on MCA: Crime and MCA: Reputation. Their loader-bus events become chronicle taps
 * with trigger types {@code crime} and {@code reputation}, keyed by the event's own ids so packs
 * speak one vocabulary; templates in {@code data/townstead/chronicle_event/compat/} turn them
 * into stories, and counters increment whether or not a template is loaded.
 *
 * <p>When both mods are present, deeds are taken from Reputation's incident feed alone: Crime
 * files its own deeds into Reputation under the same ids, so tapping both would record one punch
 * twice. Crime then contributes only what Reputation does not carry: jail, custody, fines,
 * warrants and reports.
 */
public final class OtectusBridge {
    public static final String CRIME = "mcacrime";
    public static final String REPUTATION = "mcareputation";
    static final String TRIGGER_CRIME = "crime";
    static final String TRIGGER_REPUTATION = "reputation";

    private static final String CRIME_EVENTS = "dev.otectus.mcacrime.api.event.";
    private static final String REPUTATION_EVENTS = "dev.otectus.mcareputation.api.event.";

    private static boolean initialised;

    private OtectusBridge() {}

    public static void init() {
        if (initialised) return;
        initialised = true;
        boolean reputation = ModCompat.isLoaded(REPUTATION);
        boolean crime = ModCompat.isLoaded(CRIME);
        if (reputation) {
            subscribe(REPUTATION_EVENTS + "ReputationTierChangedEvent", OtectusBridge::onTierChanged);
            subscribe(REPUTATION_EVENTS + "ReputationIncidentCreatedEvent", OtectusBridge::onIncidentCreated);
            subscribe(REPUTATION_EVENTS + "ReputationIncidentResolvedEvent", OtectusBridge::onIncidentResolved);
            subscribe(REPUTATION_EVENTS + "ReputationTitleGrantedEvent", OtectusBridge::onTitleGranted);
            ReputationDeeds.init();
        }
        if (crime) {
            if (!reputation) subscribe(CRIME_EVENTS + "CrimeCommittedEvent", OtectusBridge::onCrimeCommitted);
            subscribe(CRIME_EVENTS + "NpcCrimeCommittedEvent", OtectusBridge::onNpcCrimeCommitted);
            subscribe(CRIME_EVENTS + "CrimeReportEvent$Post", OtectusBridge::onCrimeReported);
            subscribe(CRIME_EVENTS + "PlayerJailedEvent", OtectusBridge::onJailed);
            subscribe(CRIME_EVENTS + "PlayerReleasedFromJailEvent", OtectusBridge::onReleasedFromJail);
            subscribe(CRIME_EVENTS + "EntityKidnappedEvent", OtectusBridge::onKidnapped);
            subscribe(CRIME_EVENTS + "EntityReleasedFromCaptivityEvent", OtectusBridge::onFreed);
            subscribe(CRIME_EVENTS + "FinePaidEvent", OtectusBridge::onFinePaid);
            subscribe(CRIME_EVENTS + "WarrantChangedEvent", OtectusBridge::onWarrantChanged);
        }
        if (reputation || crime) {
            Townstead.LOGGER.info("[Otectus] chronicle bridge active (reputation={}, crime={})", reputation, crime);
        }
    }

    // ---- Reputation ----

    private static void onTierChanged(Object event) {
        ServerPlayer player = player(event);
        if (player == null) return;
        Map<String, String> params = new HashMap<>();
        params.put("old_tier", OtectusReflect.string(OtectusReflect.call(event, "oldTierId")));
        params.put("new_tier", OtectusReflect.string(OtectusReflect.call(event, "newTierId")));
        params.put("direction", Boolean.TRUE.equals(OtectusReflect.call(event, "upward")) ? "up" : "down");
        params.put("first_time", OtectusReflect.string(OtectusReflect.call(event, "firstTime")));
        params.put("village", community(OtectusReflect.call(event, "community")));
        tap(TRIGGER_REPUTATION, REPUTATION + ":tier_changed", player, null, 1.0f, params);
    }

    private static void onIncidentCreated(Object event) {
        ServerPlayer player = player(event);
        Object incident = OtectusReflect.call(event, "incident");
        if (player == null || incident == null) return;
        String type = OtectusReflect.string(OtectusReflect.call(incident, "type"));
        if (type.isEmpty()) return;
        Map<String, String> params = new HashMap<>();
        params.put("incident", type);
        params.put("severity", OtectusReflect.string(OtectusReflect.call(incident, "severity")));
        params.put("village", community(OtectusReflect.call(incident, "community")));
        LivingEntity other = firstSubject(player.getServer(), incident);
        tap(TRIGGER_REPUTATION, type, player, other, 1.0f, params);
    }

    private static void onIncidentResolved(Object event) {
        ServerPlayer player = player(event);
        Object incident = OtectusReflect.call(event, "incident");
        if (player == null || incident == null) return;
        Map<String, String> params = new HashMap<>();
        params.put("incident", OtectusReflect.string(OtectusReflect.call(incident, "type")));
        params.put("status", OtectusReflect.string(OtectusReflect.call(event, "newStatus")));
        params.put("village", community(OtectusReflect.call(incident, "community")));
        tap(TRIGGER_REPUTATION, REPUTATION + ":resolved", player, firstSubject(player.getServer(), incident), 1.0f, params);
    }

    private static void onTitleGranted(Object event) {
        ServerPlayer player = player(event);
        if (player == null) return;
        Map<String, String> params = new HashMap<>();
        params.put("title", OtectusReflect.string(OtectusReflect.call(event, "title")));
        params.put("scope", OtectusReflect.string(OtectusReflect.call(event, "scope")));
        tap(TRIGGER_REPUTATION, REPUTATION + ":title_granted", player, null, 1.0f, params);
    }

    // ---- Crime ----

    private static void onCrimeCommitted(Object event) {
        ServerPlayer player = player(event);
        if (player == null) return;
        String type = OtectusReflect.string(OtectusReflect.call(event, "getCrimeType"));
        if (type.isEmpty()) return;
        Map<String, String> params = new HashMap<>();
        params.put("crime", type);
        params.put("witnessed", OtectusReflect.string(OtectusReflect.call(event, "isWitnessed")));
        LivingEntity victim = living(player.getServer(), OtectusReflect.call(event, "getVictim"));
        tap(TRIGGER_CRIME, type, player, victim, 1.0f, params);
    }

    private static void onNpcCrimeCommitted(Object event) {
        MinecraftServer server = anyServer();
        LivingEntity offender = living(server, OtectusReflect.call(event, "getOffender"));
        if (offender == null) return;
        String type = OtectusReflect.string(OtectusReflect.call(event, "getCrimeId"));
        if (type.isEmpty()) return;
        Map<String, String> params = new HashMap<>();
        params.put("crime", type);
        tap(TRIGGER_CRIME, type, offender, living(server, OtectusReflect.call(event, "getVictim")), 1.0f, params);
    }

    private static void onCrimeReported(Object event) {
        MinecraftServer server = anyServer();
        LivingEntity reporter = living(server, OtectusReflect.call(event, "getReporterId"));
        if (reporter == null) return;
        Map<String, String> params = new HashMap<>();
        params.put("crime", OtectusReflect.string(OtectusReflect.call(event, "getActionId")));
        params.put("authoritative", OtectusReflect.string(OtectusReflect.call(event, "isAuthoritative")));
        tap(TRIGGER_CRIME, CRIME + ":reported", reporter, living(server, OtectusReflect.call(event, "getSuspectId")), 1.0f, params);
    }

    private static void onJailed(Object event) {
        ServerPlayer player = player(event);
        if (player == null) return;
        Map<String, String> params = new HashMap<>();
        params.put("ticks", OtectusReflect.string(OtectusReflect.call(event, "getTicks")));
        tap(TRIGGER_CRIME, CRIME + ":jailed", player, null, 1.0f, params);
    }

    private static void onReleasedFromJail(Object event) {
        ServerPlayer player = player(event);
        if (player == null) return;
        Map<String, String> params = new HashMap<>();
        params.put("reason", OtectusReflect.string(OtectusReflect.call(event, "getReason")));
        tap(TRIGGER_CRIME, CRIME + ":released", player, null, 1.0f, params);
    }

    private static void onKidnapped(Object event) {
        MinecraftServer server = anyServer();
        LivingEntity captive = living(server, OtectusReflect.call(event, "getCaptive"));
        if (captive == null) return;
        Map<String, String> params = new HashMap<>();
        params.put("restraint", OtectusReflect.string(OtectusReflect.call(event, "getRestraint")));
        tap(TRIGGER_CRIME, CRIME + ":kidnapped", captive, living(server, OtectusReflect.call(event, "getCaptor")), 1.0f, params);
    }

    private static void onFreed(Object event) {
        MinecraftServer server = anyServer();
        LivingEntity captive = living(server, OtectusReflect.call(event, "getCaptive"));
        if (captive == null) return;
        Map<String, String> params = new HashMap<>();
        params.put("reason", OtectusReflect.string(OtectusReflect.call(event, "getReason")));
        tap(TRIGGER_CRIME, CRIME + ":freed", captive, living(server, OtectusReflect.call(event, "getFormerCaptor")), 1.0f, params);
    }

    private static void onFinePaid(Object event) {
        ServerPlayer player = player(event);
        if (player == null) return;
        Map<String, String> params = new HashMap<>();
        params.put("amount", OtectusReflect.string(OtectusReflect.call(event, "getAmount")));
        tap(TRIGGER_CRIME, CRIME + ":fine_paid", player, null, 1.0f, params);
    }

    private static void onWarrantChanged(Object event) {
        MinecraftServer server = anyServer();
        LivingEntity offender = living(server, OtectusReflect.call(event, "getOffender"));
        if (offender == null) return;
        String change = OtectusReflect.string(OtectusReflect.call(event, "getChange"));
        if (change.isEmpty()) return;
        tap(TRIGGER_CRIME, CRIME + ":warrant_" + change, offender, null, 1.0f, Map.of("change", change));
    }

    // ---- plumbing ----

    /** Counts under the key unconditionally, then lets templates decide whether it is a story. */
    static void tap(String type, String key, LivingEntity actor, @Nullable LivingEntity other, float magnitude,
                    Map<String, String> params) {
        try {
            if (!(actor.level() instanceof ServerLevel level)) return;
            Chronicles.addCounter(level.getServer(), actor.getUUID(), key, 1);
            if (ChronicleTriggerIndex.isEmpty()) return;
            ChronicleEmitter.emit(level, new ChronicleEventTemplate.TriggerKey(type, key), actor, other, magnitude, params);
        } catch (Throwable t) {
            Townstead.LOGGER.debug("[Otectus] tap {} failed: {}", key, t.toString());
        }
    }

    private static @Nullable ServerPlayer player(Object event) {
        Object direct = OtectusReflect.unwrap(OtectusReflect.call(event, "getPlayer"));
        if (direct instanceof ServerPlayer player) return player;
        Object viaRecord = OtectusReflect.unwrap(OtectusReflect.call(event, "player"));
        if (viaRecord instanceof ServerPlayer player) return player;
        Object id = OtectusReflect.unwrap(OtectusReflect.call(event, "playerId"));
        if (id instanceof UUID uuid) {
            MinecraftServer server = anyServer();
            return server == null ? null : server.getPlayerList().getPlayer(uuid);
        }
        return null;
    }

    private static @Nullable LivingEntity living(@Nullable MinecraftServer server, @Nullable Object id) {
        Object unwrapped = OtectusReflect.unwrap(id);
        if (server == null || !(unwrapped instanceof UUID uuid)) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof LivingEntity living) return living;
        }
        return null;
    }

    private static @Nullable LivingEntity firstSubject(@Nullable MinecraftServer server, Object incident) {
        Object subjects = OtectusReflect.call(incident, "subjects");
        if (!(subjects instanceof java.util.List<?> list)) return null;
        for (Object subject : list) {
            LivingEntity entity = living(server, OtectusReflect.call(subject, "uuid"));
            if (entity != null) return entity;
        }
        return null;
    }

    private static String community(@Nullable Object key) {
        Object unwrapped = OtectusReflect.unwrap(key);
        if (unwrapped == null) return "";
        Object dimension = OtectusReflect.call(unwrapped, "dimension");
        Object villageId = OtectusReflect.call(unwrapped, "villageId");
        return dimension == null || villageId == null ? "" : dimension + "/" + villageId;
    }

    private static @Nullable MinecraftServer anyServer() {
        //? if neoforge {
        return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        //?} else if forge {
        /*return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        *///?}
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void subscribe(String className, Consumer<Object> handler) {
        Class<?> type = OtectusReflect.type(className);
        if (type == null) {
            Townstead.LOGGER.debug("[Otectus] event class {} not found; skipping", className);
            return;
        }
        Consumer safe = event -> {
            try {
                handler.accept(event);
            } catch (Throwable t) {
                Townstead.LOGGER.debug("[Otectus] handler for {} failed: {}", className, t.toString());
            }
        };
        try {
            //? if neoforge {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                    net.neoforged.bus.api.EventPriority.NORMAL, false, (Class) type, safe);
            //?} else if forge {
            /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                    net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, (Class) type, safe);
            *///?}
        } catch (Throwable t) {
            Townstead.LOGGER.warn("[Otectus] could not subscribe to {}: {}", className, t.toString());
        }
    }

    static @Nullable ResourceLocation id(String value) {
        return value == null || value.isEmpty() ? null : ResourceLocation.tryParse(value);
    }
}
