package com.aetherianartificer.townstead.politics.seat;

import com.aetherianartificer.townstead.politics.legitimacy.LegitimacyService;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SeatInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

/**
 * Tells a faction's online members when their Seat is damaged, repaired, or lost. Notices are
 * queued at the write and sent on the next Seat tick, once the tick's political writes settle.
 */
public final class SeatNotices {
    private static final ConcurrentLinkedQueue<Notice> PENDING = new ConcurrentLinkedQueue<>();

    private record Notice(ResourceLocation faction, Function<String, Component> message) {}

    private SeatNotices() {}

    public static void changed(@Nullable SeatInstance before, SeatInstance after) {
        if (before == null || !before.sameHost(after)) return;
        if (!before.damaged() && after.damaged()) {
            String key = "townstead.seat.notice.damaged." + after.damage();
            PENDING.add(new Notice(after.faction(), name -> Component.translatable(key, name)));
        } else if (before.damaged() && !after.damaged()) {
            PENDING.add(new Notice(after.faction(), name -> Component.translatable("townstead.seat.notice.repaired", name)));
        }
    }

    public static void lost(SeatInstance before, String reason) {
        // A dissolution is already announced to the whole server.
        if (SeatService.FACTION_DISSOLVED.equals(reason)) return;
        PENDING.add(new Notice(before.faction(), name -> Component.translatable("townstead.seat.notice.lost." + reason, name)));
    }

    static void send(MinecraftServer server) {
        Notice notice;
        while ((notice = PENDING.poll()) != null) {
            PoliticalSavedData data = PoliticalSavedData.get(server);
            Faction faction = data.faction(notice.faction());
            if (faction == null) continue;
            LegitimacyService.notifyMembers(server, data, faction, notice.message().apply(faction.name()));
        }
    }

    static void clear() {
        PENDING.clear();
    }
}
