package com.aetherianartificer.townstead.compat.otectus;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.api.v1.TownsteadApiV1;
import com.aetherianartificer.townstead.api.v1.event.BuildingEstablishedEvent;
import com.aetherianartificer.townstead.api.v1.event.BuildingUpgradedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillageSpiritChangedEvent;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * Townstead's deeds, filed into MCA: Reputation through its own {@code record} entry point so
 * standing moves and, through Reputation's gossip block, MCA: Conversations tells the story.
 * Incident definitions live in {@code data/townstead/mcareputation/incidents/}.
 *
 * <p>Attribution is the hard part: a building appears in a register because a player reported
 * it, so {@link #withReporter} marks the player for the duration of that reconcile and the
 * deed goes to them. A change nobody reported is nobody's deed.
 */
public final class ReputationDeeds {
    static final ResourceLocation SOURCE = id("townstead:deeds");
    static final ResourceLocation BUILDING_RAISED = id("townstead:building_raised");
    static final ResourceLocation BUILDING_UPGRADED = id("townstead:building_upgraded");
    static final ResourceLocation SPIRIT_TIER_REACHED = id("townstead:spirit_tier_reached");

    private static final String API = "dev.otectus.mcareputation.api.McaReputationApi";
    private static final String REQUEST = "dev.otectus.mcareputation.api.ReputationRequest";
    private static final String COMMUNITY = "dev.otectus.mcareputation.community.CommunityKey";
    private static final String SUBJECT = "dev.otectus.mcareputation.incident.IncidentSubject";

    private static final ThreadLocal<ServerPlayer> REPORTER = new ThreadLocal<>();
    private static boolean active;

    private ReputationDeeds() {}

    static void init() {
        if (active) return;
        active = OtectusReflect.type(API) != null && OtectusReflect.type(REQUEST) != null;
        if (!active) return;
        TownsteadApiV1.get().events().subscribe(BuildingEstablishedEvent.class, e -> {
            ServerPlayer reporter = REPORTER.get();
            if (reporter == null) return;
            file(reporter.getServer(), reporter, e.village(), BUILDING_RAISED,
                    e.village().asString() + ":" + e.buildingId() + ":" + e.type(),
                    Map.of("building", e.type(), "family", e.family(), "tier", Integer.toString(e.tier())), null);
        });
        TownsteadApiV1.get().events().subscribe(BuildingUpgradedEvent.class, e -> {
            ServerPlayer reporter = REPORTER.get();
            if (reporter == null || e.tierAfter() <= e.tierBefore()) return;
            file(reporter.getServer(), reporter, e.village(), BUILDING_UPGRADED,
                    e.village().asString() + ":" + e.buildingId() + ":" + e.typeAfter(),
                    Map.of("building", e.typeAfter(), "tier", Integer.toString(e.tierAfter())), null);
        });
        TownsteadApiV1.get().events().subscribe(VillageSpiritChangedEvent.class, e -> {
            ServerPlayer reporter = REPORTER.get();
            if (reporter == null || e.after().tierIndex() <= e.before().tierIndex()) return;
            file(reporter.getServer(), reporter, e.village(), SPIRIT_TIER_REACHED,
                    e.village().asString() + ":" + e.after().tierIndex(),
                    Map.of("tier", Integer.toString(e.after().tierIndex()),
                            "spirit", e.after().primarySpiritId().orElse("")), null);
        });
    }

    /** Runs {@code body} with {@code player} as the reporter every deed filed inside it is credited to. */
    public static void withReporter(@Nullable ServerPlayer player, Runnable body) {
        if (player == null) {
            body.run();
            return;
        }
        ServerPlayer previous = REPORTER.get();
        REPORTER.set(player);
        try {
            body.run();
        } finally {
            if (previous == null) REPORTER.remove();
            else REPORTER.set(previous);
        }
    }

    private static void file(MinecraftServer server, ServerPlayer player, VillageId village, ResourceLocation type,
                             String dedupe, Map<String, String> context, @Nullable LivingEntity subject) {
        if (!active || server == null) return;
        try {
            Object community = OtectusReflect.unwrap(OtectusReflect.callStatic(OtectusReflect.type(COMMUNITY), "of",
                    new Class<?>[] {ResourceLocation.class, int.class}, village.dimension(), village.villageId()));
            if (community == null) return;
            Object builder = OtectusReflect.callStatic(OtectusReflect.type(REQUEST), "builder",
                    server, player.getUUID(), community, type, SOURCE);
            if (builder == null) return;
            OtectusReflect.call(builder, "dedupeKey", dedupe);
            for (Map.Entry<String, String> entry : context.entrySet()) {
                OtectusReflect.call(builder, "context", entry.getKey(), entry.getValue());
            }
            if (subject != null) {
                Object incidentSubject = OtectusReflect.callStatic(OtectusReflect.type(SUBJECT), "villager",
                        subject.getUUID(), subject.getName().getString(), "subject");
                if (incidentSubject != null) OtectusReflect.call(builder, "subject", incidentSubject);
                OtectusReflect.call(builder, "witness", (UUID) subject.getUUID());
            }
            Object request = OtectusReflect.call(builder, "build");
            if (request == null) return;
            Object result = OtectusReflect.callStatic(OtectusReflect.type(API), "record", request);
            Townstead.LOGGER.debug("[Otectus] filed {} for {}: {}", type, player.getGameProfile().getName(), result);
        } catch (Throwable t) {
            Townstead.LOGGER.debug("[Otectus] filing {} failed: {}", type, t.toString());
        }
    }

    private static ResourceLocation id(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }
}
