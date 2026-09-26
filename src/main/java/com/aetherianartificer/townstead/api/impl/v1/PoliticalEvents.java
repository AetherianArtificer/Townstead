package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.api.v1.event.AffiliationChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.MembershipChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.OrganizationFoundedEvent;
import com.aetherianartificer.townstead.api.v1.event.OrganizationIdentityChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.OrganizationStatusChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.PolityFoundedEvent;
import com.aetherianartificer.townstead.api.v1.event.PolityGovernmentChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.PolityIdentityChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.PolityStatusChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.SeatDamagedEvent;
import com.aetherianartificer.townstead.api.v1.event.SeatDesignatedEvent;
import com.aetherianartificer.townstead.api.v1.event.SeatLostEvent;
import com.aetherianartificer.townstead.api.v1.event.SeatMovedEvent;
import com.aetherianartificer.townstead.api.v1.event.SeatRepairedEvent;
import com.aetherianartificer.townstead.api.v1.event.SettlementFoundedEvent;
import com.aetherianartificer.townstead.api.v1.event.SettlementPolityChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.TownsteadEvent;
import com.aetherianartificer.townstead.api.v1.model.AffiliationSnapshot;
import com.aetherianartificer.townstead.api.v1.model.MembershipSnapshot;
import com.aetherianartificer.townstead.api.v1.model.OrganizationSnapshot;
import com.aetherianartificer.townstead.api.v1.model.PolitySnapshot;
import com.aetherianartificer.townstead.api.v1.model.SettlementFoundingSnapshot;
import com.aetherianartificer.townstead.politics.state.AffiliationInstance;
import com.aetherianartificer.townstead.politics.state.MembershipInstance;
import com.aetherianartificer.townstead.politics.state.OrganizationInstance;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.PolityInstance;
import com.aetherianartificer.townstead.politics.state.SeatInstance;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * Diffs every political write and queues the resulting events. Snapshots are taken at write
 * time; the queue is posted in write order at the end of the server tick so listeners never
 * see a founding half applied.
 */
public final class PoliticalEvents {
    private static final ConcurrentLinkedQueue<Function<MinecraftServer, TownsteadEvent>> PENDING =
            new ConcurrentLinkedQueue<>();
    private static volatile @Nullable Map<SettlementRef, ResourceLocation> settlementBaseline;

    private PoliticalEvents() {}

    public static void organization(PoliticalSavedData data, @Nullable OrganizationInstance before,
                                    OrganizationInstance after) {
        safe(() -> {
            OrganizationSnapshot next = PoliticsImpl.organization(data, after);
            if (before == null) {
                queue(server -> new OrganizationFoundedEvent(server, next));
                return;
            }
            OrganizationSnapshot prev = PoliticsImpl.organization(data, before);
            if (before.status() != after.status()) {
                queue(server -> new OrganizationStatusChangedEvent(server, prev, next));
            }
            if (!before.name().equals(after.name()) || !before.shortName().equals(after.shortName())
                    || before.color() != after.color() || !Objects.equals(before.emblem(), after.emblem())) {
                queue(server -> new OrganizationIdentityChangedEvent(server, prev, next));
            }
        });
    }

    public static void polity(@Nullable PolityInstance before, PolityInstance after) {
        safe(() -> {
            PolitySnapshot next = PoliticsImpl.polity(after);
            if (before == null) {
                queue(server -> new PolityFoundedEvent(server, next));
            } else {
                PolitySnapshot prev = PoliticsImpl.polity(before);
                if (before.status() != after.status()) {
                    queue(server -> new PolityStatusChangedEvent(server, prev, next));
                }
                if (!before.name().equals(after.name()) || before.color() != after.color()
                        || !Objects.equals(before.emblem(), after.emblem())) {
                    queue(server -> new PolityIdentityChangedEvent(server, prev, next));
                }
                if (!Objects.equals(before.governmentOrganization(), after.governmentOrganization())) {
                    Optional<ResourceLocation> from = Optional.ofNullable(before.governmentOrganization());
                    Optional<ResourceLocation> to = Optional.ofNullable(after.governmentOrganization());
                    queue(server -> new PolityGovernmentChangedEvent(server, next, from, to));
                }
            }
        });
    }

    /**
     * Call before a polity write lands. A move spans two writes (one polity loses the settlement,
     * another gains it), so ownership is captured once per tick and compared at flush.
     */
    public static void beforePolityWrite(PoliticalSavedData data) {
        if (settlementBaseline != null) return;
        safe(() -> settlementBaseline = ownership(data));
    }

    public static void affiliation(@Nullable AffiliationInstance before, AffiliationInstance after) {
        safe(() -> {
            if (before != null && before.status() == after.status()) return;
            Optional<AffiliationSnapshot> prev = Optional.ofNullable(before)
                    .map(value -> PoliticsImpl.affiliation(value, false));
            AffiliationSnapshot next = PoliticsImpl.affiliation(after, false);
            queue(server -> new AffiliationChangedEvent(server, prev, next));
        });
    }

    public static void membership(@Nullable MembershipInstance before, MembershipInstance after) {
        safe(() -> {
            if (before != null && before.affiliation().status() == after.affiliation().status()
                    && before.roles().equals(after.roles())) return;
            Optional<MembershipSnapshot> prev = Optional.ofNullable(before).map(PoliticsImpl::membership);
            MembershipSnapshot next = PoliticsImpl.membership(after);
            queue(server -> new MembershipChangedEvent(server, prev, next));
        });
    }

    public static void founding(@Nullable SettlementFoundingRecord before, SettlementFoundingRecord after) {
        if (before != null) return;
        safe(() -> {
            SettlementFoundingSnapshot next = PoliticsImpl.founding(after);
            queue(server -> new SettlementFoundedEvent(server, next));
        });
    }

    /** Snapshots are built at flush, so the host building type is read after the tick's writes. */
    public static void seat(@Nullable SeatInstance before, SeatInstance after) {
        if (before == null) {
            queue(server -> new SeatDesignatedEvent(server, PoliticsImpl.seat(server, after)));
        } else if (!before.sameHost(after)) {
            queue(server -> new SeatMovedEvent(server, PoliticsImpl.seat(server, before), PoliticsImpl.seat(server, after)));
        } else if (!before.damaged() && after.damaged()) {
            queue(server -> new SeatDamagedEvent(server, PoliticsImpl.seat(server, after), after.damage()));
        } else if (before.damaged() && !after.damaged()) {
            queue(server -> new SeatRepairedEvent(server, PoliticsImpl.seat(server, after)));
        }
    }

    public static void seatLost(SeatInstance before, String reason) {
        queue(server -> new SeatLostEvent(server, PoliticsImpl.seat(server, before), reason));
    }

    public static void flush(MinecraftServer server) {
        if (settlementBaseline != null) {
            Map<SettlementRef, ResourceLocation> before = settlementBaseline;
            settlementBaseline = null;
            safe(() -> queueSettlementMoves(before, ownership(PoliticalSavedData.get(server))));
        }
        Function<MinecraftServer, TownsteadEvent> next;
        while ((next = PENDING.poll()) != null) {
            Function<MinecraftServer, TownsteadEvent> event = next;
            safe(() -> ApiEvents.post(event.apply(server)));
        }
    }

    public static void clear() {
        PENDING.clear();
        settlementBaseline = null;
    }

    private static void queueSettlementMoves(Map<SettlementRef, ResourceLocation> before,
                                             Map<SettlementRef, ResourceLocation> after) {
        Set<SettlementRef> settlements = new LinkedHashSet<>(before.keySet());
        settlements.addAll(after.keySet());
        for (SettlementRef settlement : settlements) {
            Optional<ResourceLocation> from = Optional.ofNullable(before.get(settlement));
            Optional<ResourceLocation> to = Optional.ofNullable(after.get(settlement));
            if (from.equals(to)) continue;
            queue(server -> new SettlementPolityChangedEvent(server, PoliticsImpl.village(settlement), from, to));
        }
    }

    private static Map<SettlementRef, ResourceLocation> ownership(PoliticalSavedData data) {
        Map<SettlementRef, ResourceLocation> out = new LinkedHashMap<>();
        for (PolityInstance polity : data.polities()) {
            for (SettlementRef settlement : polity.settlements()) {
                if (out.containsKey(settlement)) continue;
                PolityInstance holder = data.polity(settlement);
                if (holder != null) out.put(settlement, holder.id());
            }
        }
        return out;
    }

    private static void queue(Function<MinecraftServer, TownsteadEvent> event) {
        PENDING.add(event);
    }

    private static void safe(Runnable body) {
        try {
            body.run();
        } catch (Throwable t) {
            Townstead.LOGGER.debug("[TownsteadApi] political event failed: {}", t.toString());
        }
    }
}
