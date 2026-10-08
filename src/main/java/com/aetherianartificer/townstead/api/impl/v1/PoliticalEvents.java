package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.api.v1.event.BondChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.FactionFoundedEvent;
import com.aetherianartificer.townstead.api.v1.event.FactionIdentityChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.FactionKindChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.FactionStatusChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.SeatDamagedEvent;
import com.aetherianartificer.townstead.api.v1.event.SeatDesignatedEvent;
import com.aetherianartificer.townstead.api.v1.event.SeatLostEvent;
import com.aetherianartificer.townstead.api.v1.event.SeatMovedEvent;
import com.aetherianartificer.townstead.api.v1.event.SeatRepairedEvent;
import com.aetherianartificer.townstead.api.v1.event.SettlementFactionChangedEvent;
import com.aetherianartificer.townstead.api.v1.event.SettlementFoundedEvent;
import com.aetherianartificer.townstead.api.v1.event.TownsteadEvent;
import com.aetherianartificer.townstead.api.v1.model.BondSnapshot;
import com.aetherianartificer.townstead.api.v1.model.FactionSnapshot;
import com.aetherianartificer.townstead.api.v1.model.SettlementFoundingSnapshot;
import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
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

    public static void faction(PoliticalSavedData data, @Nullable Faction before, Faction after) {
        safe(() -> {
            FactionSnapshot next = PoliticsImpl.faction(data, after);
            if (before == null) {
                queue(server -> new FactionFoundedEvent(server, next));
                return;
            }
            FactionSnapshot prev = PoliticsImpl.faction(data, before);
            if (before.status() != after.status()) queue(server -> new FactionStatusChangedEvent(server, prev, next));
            if (!before.name().equals(after.name()) || before.color() != after.color()
                    || !Objects.equals(before.emblem(), after.emblem())) {
                queue(server -> new FactionIdentityChangedEvent(server, prev, next));
            }
            if (!before.kind().equals(after.kind())) queue(server -> new FactionKindChangedEvent(server, prev, next));
        });
    }

    /**
     * Call before a faction write lands. A move spans two writes (one faction loses the settlement,
     * another gains it), so ownership is captured once per tick and compared at flush.
     */
    public static void beforeFactionWrite(PoliticalSavedData data) {
        if (settlementBaseline != null) return;
        safe(() -> settlementBaseline = ownership(data));
    }

    public static void bond(@Nullable BondInstance before, BondInstance after) {
        safe(() -> {
            if (before != null && before.active() == after.active()) return;
            Optional<BondSnapshot> prev = Optional.ofNullable(before).map(PoliticsImpl::bond);
            BondSnapshot next = PoliticsImpl.bond(after);
            queue(server -> new BondChangedEvent(server, prev, next));
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
            queue(server -> new SettlementFactionChangedEvent(server, PoliticsImpl.village(settlement), from, to));
        }
    }

    private static Map<SettlementRef, ResourceLocation> ownership(PoliticalSavedData data) {
        Map<SettlementRef, ResourceLocation> out = new LinkedHashMap<>();
        for (Faction faction : data.factions()) {
            for (SettlementRef settlement : faction.settlements()) {
                if (out.containsKey(settlement)) continue;
                Faction holder = data.faction(settlement);
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
