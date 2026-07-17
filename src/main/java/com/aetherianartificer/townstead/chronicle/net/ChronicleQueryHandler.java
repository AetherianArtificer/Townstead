package com.aetherianartificer.townstead.chronicle.net;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleEmitter;
import com.aetherianartificer.townstead.chronicle.knowledge.AccountLedger;
import com.aetherianartificer.townstead.chronicle.knowledge.DistortionOverlay;
import com.aetherianartificer.townstead.chronicle.knowledge.KnownStoriesCache;
import com.aetherianartificer.townstead.chronicle.knowledge.NewsScore;
import com.aetherianartificer.townstead.chronicle.knowledge.SpreadChannel;
import com.aetherianartificer.townstead.chronicle.model.Account;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.VillagerMemory;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventRegistry;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server side of the chronicle viewer. Reads are async and hop back to the
 * server thread to respond; display is fully server-resolved; knowledge views
 * ship the BELIEVED version (overlay applied) with the channel labeled — the
 * truth diff stays admin-only.
 */
public final class ChronicleQueryHandler {

    private static final long MIN_QUERY_SPACING_TICKS = 10L;
    private static final int MAX_PAGE = 32;
    private static final double SHARE_RANGE = 8.0;

    private static final Map<UUID, Long> LAST_QUERY = new ConcurrentHashMap<>();

    private ChronicleQueryHandler() {}

    public static void handleQuery(ServerPlayer player, ChronicleQueryC2SPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null || !rateLimit(player)) return;
        int pageSize = Math.max(1, Math.min(payload.pageSize(), MAX_PAGE));

        switch (payload.mode()) {
            case ChronicleQueryC2SPayload.MODE_VILLAGE -> {
                Optional<Village> village = Village.findNearest(player);
                if (village.isEmpty()) {
                    sendPage(player, new ChroniclePageS2CPayload(payload.requestId(), payload.mode(),
                            false, List.of()));
                    return;
                }
                Chronicles.byVillage(player.level().dimension().location(), village.get().getId(),
                                payload.beforeEventId(), pageSize)
                        .thenAccept(events -> server.execute(() -> {
                            List<ChroniclePageS2CPayload.EntryView> views = new ArrayList<>(events.size());
                            for (ChronicleEvent event : events) {
                                views.add(truthView(event));
                            }
                            sendPage(player, new ChroniclePageS2CPayload(payload.requestId(),
                                    payload.mode(), events.size() >= pageSize, withDates(server, views)));
                        }));
            }
            case ChronicleQueryC2SPayload.MODE_KNOWS ->
                    Chronicles.accountsByKnower(payload.subject(), pageSize)
                            .thenCompose(accounts -> believedViews(accounts))
                            .thenAccept(views -> server.execute(() ->
                                    sendPage(player, new ChroniclePageS2CPayload(payload.requestId(),
                                            payload.mode(), views.size() >= pageSize,
                                            withDates(server, views)))));
            case ChronicleQueryC2SPayload.MODE_MEMORIES -> {
                List<VillagerMemory> memories = Chronicles.memories(server, payload.subject());
                List<ChroniclePageS2CPayload.EntryView> views = new ArrayList<>(memories.size());
                for (VillagerMemory memory : memories) {
                    views.add(memoryView(memory));
                }
                sendPage(player, new ChroniclePageS2CPayload(payload.requestId(), payload.mode(),
                        false, withDates(server, views)));
            }
            default -> { }
        }
    }

    /** Resolve each account's event, then render its BELIEVED headline. */
    private static CompletableFuture<List<ChroniclePageS2CPayload.EntryView>> believedViews(
            List<Account> accounts) {
        List<CompletableFuture<Optional<ChronicleEvent>>> fetches = new ArrayList<>(accounts.size());
        for (Account account : accounts) {
            fetches.add(account.storyEventId() > 0
                    ? Chronicles.byId(account.storyEventId())
                    : CompletableFuture.completedFuture(Optional.empty()));
        }
        return CompletableFuture.allOf(fetches.toArray(new CompletableFuture[0])).thenApply(ignored -> {
            List<ChroniclePageS2CPayload.EntryView> views = new ArrayList<>(accounts.size());
            for (int i = 0; i < accounts.size(); i++) {
                Account account = accounts.get(i);
                ChronicleEvent event = fetches.get(i).join().orElse(null);
                if (event == null) continue;
                ChronicleEventTemplate template = ChronicleEventRegistry.byId(event.templateId());
                if (template == null) continue;
                DistortionOverlay overlay = DistortionOverlay.fromJson(account.overlayJson());
                Map<String, String> believed = overlay.applyToParams(event.params());
                views.add(new ChroniclePageS2CPayload.EntryView(
                        event.eventId(), event.worldDay(), "",
                        template.display().headlineLiteral(), template.display().headlineLangKey(),
                        argsFor(template, believed), event.category(),
                        account.channel(), account.fidelity()));
            }
            return views;
        });
    }

    private static ChroniclePageS2CPayload.EntryView truthView(ChronicleEvent event) {
        ChronicleEventTemplate template = ChronicleEventRegistry.byId(event.templateId());
        if (template == null) {
            return new ChroniclePageS2CPayload.EntryView(event.eventId(), event.worldDay(), "",
                    event.templateId().getPath(), "", List.of(), event.category(), "", 1f);
        }
        return new ChroniclePageS2CPayload.EntryView(event.eventId(), event.worldDay(), "",
                template.display().headlineLiteral(), template.display().headlineLangKey(),
                argsFor(template, event.params()), event.category(), "", 1f);
    }

    private static ChroniclePageS2CPayload.EntryView memoryView(VillagerMemory memory) {
        ResourceLocation templateId = null;
        try {
            templateId = parseRl(memory.memoryKey());
        } catch (Exception ignored) {
        }
        ChronicleEventTemplate template = templateId == null ? null
                : ChronicleEventRegistry.byId(templateId);
        if (template == null) {
            return new ChroniclePageS2CPayload.EntryView(0L, memory.lastDay(), "", memory.memoryKey(),
                    "", List.of(), "memory", "", memory.strength());
        }
        return new ChroniclePageS2CPayload.EntryView(0L, memory.lastDay(), "",
                template.display().headlineLiteral(), template.display().headlineLangKey(),
                argsFor(template, memory.params()), template.category(), "", memory.strength());
    }

    /** Server-thread only: stamps calendar date labels onto rendered views. */
    private static List<ChroniclePageS2CPayload.EntryView> withDates(
            MinecraftServer server, List<ChroniclePageS2CPayload.EntryView> views) {
        List<ChroniclePageS2CPayload.EntryView> dated = new ArrayList<>(views.size());
        for (ChroniclePageS2CPayload.EntryView view : views) {
            String label = com.aetherianartificer.townstead.calendar.CalendarDateFormatter
                    .format(server, view.worldDay(), com.aetherianartificer.townstead.calendar.CalendarDateFormatter.Style.LONG)
                    .getString();
            dated.add(new ChroniclePageS2CPayload.EntryView(view.eventId(), view.worldDay(), label,
                    view.headlineLiteral(), view.headlineLangKey(), view.args(),
                    view.category(), view.channel(), view.fidelity()));
        }
        return dated;
    }

    private static List<String> argsFor(ChronicleEventTemplate template, Map<String, String> params) {
        List<String> args = new ArrayList<>(template.display().paramNames().size());
        for (String name : template.display().paramNames()) {
            args.add(params.getOrDefault(name, "?"));
        }
        return args;
    }

    // ---- Share News ----

    public static void handleShareNews(ServerPlayer player, ChronicleShareNewsC2SPayload payload) {
        MinecraftServer server = player.getServer();
        if (server == null || !rateLimit(player)) return;
        Entity entity = player.serverLevel().getEntity(payload.villager());
        if (!(entity instanceof VillagerEntityMCA villager)
                || player.distanceTo(villager) > SHARE_RANGE) {
            return;
        }
        boolean playerReady = KnownStoriesCache.ready(player.getUUID());
        boolean villagerReady = KnownStoriesCache.ready(villager.getUUID());
        if (!playerReady || !villagerReady) {
            player.displayClientMessage(Component.translatable("townstead.chronicle.share.thinking"), true);
            return;
        }

        long today = TownsteadCalendar.worldDay(server);
        int villagerVillage = ChronicleEmitter.resolveVillageId(villager);
        KnownStoriesCache.Entry best = null;
        ChronicleEventTemplate bestTemplate = null;
        float bestScore = 0f;
        for (KnownStoriesCache.Entry entry : KnownStoriesCache.entries(player.getUUID())) {
            if (entry.reach < ChronicleEvent.REACH_VILLAGE) continue;
            if (KnownStoriesCache.knows(villager.getUUID(), entry.storyEventId)) continue;
            ChronicleEventTemplate template = ChronicleEventRegistry.byId(entry.templateId);
            if (template == null) continue;
            float score = NewsScore.score(template, entry.magnitude, entry.eventDay,
                    entry.villageId, today, villagerVillage);
            if (score > bestScore) {
                bestScore = score;
                best = entry;
                bestTemplate = template;
            }
        }
        if (best == null) {
            player.displayClientMessage(Component.translatable("townstead.chronicle.share.nothing"), true);
            return;
        }
        ChronicleEvent event = Chronicles.buffer().byId(best.storyEventId);
        if (event == null) {
            player.displayClientMessage(Component.translatable("townstead.chronicle.share.stale"), true);
            return;
        }

        SpreadChannel channel = SpreadChannel.PLAYER_WORD;
        float fidelity = Math.max(channel.fidelityFloor(), best.fidelity * channel.fidelityFactor());
        AccountLedger.learn(server, bestTemplate, event, villager.getUUID(), true, null,
                channel, best.accountId, fidelity, best.overlay, today);

        int points = Math.max(1, Math.round(bestScore * 10f));
        ChronicleSavedData.get(server).addNewsPoints(player.getUUID(), points);
        player.displayClientMessage(Component.translatable("townstead.chronicle.share.delivered",
                villager.getName().getString(), points), true);
    }

    private static boolean rateLimit(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_QUERY.get(player.getUUID());
        if (last != null && now - last < MIN_QUERY_SPACING_TICKS) return false;
        LAST_QUERY.put(player.getUUID(), now);
        return true;
    }

    private static void sendPage(ServerPlayer player, ChroniclePageS2CPayload page) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, page);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, page);
        *///?}
    }

    public static void clearAll() {
        LAST_QUERY.clear();
    }

    private static ResourceLocation parseRl(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }
}
