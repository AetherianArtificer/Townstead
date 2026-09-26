package com.aetherianartificer.townstead.politics.seat;

import com.aetherianartificer.townstead.politics.state.MembershipInstance;
import com.aetherianartificer.townstead.politics.state.OrganizationInstance;
import com.aetherianartificer.townstead.politics.state.PoliticalActorRef;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.PolityInstance;
import com.aetherianartificer.townstead.politics.state.SeatInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * Tells a faction's online members when their Seat is damaged, repaired, or lost. Notices are
 * queued at the write and sent on the next Seat tick, once the tick's political writes settle.
 */
public final class SeatNotices {
    private static final ConcurrentLinkedQueue<Notice> PENDING = new ConcurrentLinkedQueue<>();

    private record Notice(PoliticalActorRef actor, Function<String, Component> message) {}

    private SeatNotices() {}

    public static void changed(@Nullable SeatInstance before, SeatInstance after) {
        if (before == null || !before.sameHost(after)) return;
        if (!before.damaged() && after.damaged()) {
            String key = "townstead.seat.notice.damaged." + after.damage();
            PENDING.add(new Notice(after.actor(), name -> Component.translatable(key, name)));
        } else if (before.damaged() && !after.damaged()) {
            PENDING.add(new Notice(after.actor(), name -> Component.translatable("townstead.seat.notice.repaired", name)));
        }
    }

    public static void lost(SeatInstance before, String reason) {
        // A dissolution is already announced to the whole server.
        if (SeatService.ACTOR_DISSOLVED.equals(reason)) return;
        PENDING.add(new Notice(before.actor(), name -> Component.translatable("townstead.seat.notice.lost." + reason, name)));
    }

    static void send(MinecraftServer server) {
        Notice notice;
        while ((notice = PENDING.poll()) != null) {
            PoliticalSavedData data = PoliticalSavedData.get(server);
            String name = name(data, notice.actor());
            Component message = notice.message().apply(name);
            for (UUID person : members(data, notice.actor())) {
                ServerPlayer player = server.getPlayerList().getPlayer(person);
                if (player != null) player.displayClientMessage(message, false);
            }
        }
    }

    static void clear() {
        PENDING.clear();
    }

    /** Members of the actor itself and, for a polity, of its government. */
    private static Set<UUID> members(PoliticalSavedData data, PoliticalActorRef actor) {
        Set<UUID> out = new LinkedHashSet<>();
        addActive(out, data, actor);
        if (actor.kind() == PoliticalActorRef.Kind.POLITY) {
            PolityInstance polity = data.polity(actor.id());
            if (polity != null && polity.governmentOrganization() != null) {
                OrganizationInstance government = data.organization(polity.governmentOrganization());
                if (government != null) addActive(out, data, government.actor());
            }
        }
        return out;
    }

    private static void addActive(Set<UUID> out, PoliticalSavedData data, PoliticalActorRef actor) {
        for (MembershipInstance membership : data.memberships(actor)) {
            if (membership.affiliation().active()) out.add(membership.affiliation().person());
        }
    }

    private static String name(PoliticalSavedData data, PoliticalActorRef actor) {
        if (actor.kind() == PoliticalActorRef.Kind.POLITY) {
            PolityInstance polity = data.polity(actor.id());
            if (polity != null) return polity.name();
        } else {
            OrganizationInstance organization = data.organization(actor.id());
            if (organization != null) return organization.name();
        }
        return actor.id().toString();
    }
}
