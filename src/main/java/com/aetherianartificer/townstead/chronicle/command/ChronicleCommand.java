package com.aetherianartificer.townstead.chronicle.command;

import com.aetherianartificer.townstead.calendar.CalendarDateFormatter;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData.VillageKey;
import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.chronicle.arc.ArcManager;
import com.aetherianartificer.townstead.chronicle.concept.ConceptLedger;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleEmitter;
import com.aetherianartificer.townstead.chronicle.knowledge.DistortionOverlay;
import com.aetherianartificer.townstead.chronicle.knowledge.NewsScore;
import com.aetherianartificer.townstead.chronicle.model.Account;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.model.SentimentEntry;
import com.aetherianartificer.townstead.chronicle.model.VillagerMemory;
import com.aetherianartificer.townstead.chronicle.pregen.PregenScheduler;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.chronicle.store.ChronicleStore;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventRegistry;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Debug/admin surface for the chronicle system. Async archive reads hop back
 * to the server thread before rendering. {@code knows} exposes the
 * believed-vs-truth divergence that the player-facing UI deliberately hides.
 */
public final class ChronicleCommand {

    private static final int PAGE_SIZE = 10;

    private ChronicleCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("chronicle")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("stats").executes(ctx -> stats(ctx.getSource())))
                .then(Commands.literal("dump").executes(ctx -> dump(ctx.getSource())))
                .then(Commands.literal("subject")
                        .executes(ctx -> withFocus(ctx.getSource(),
                                target -> subject(ctx.getSource(), target, 0L)))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> subject(ctx.getSource(),
                                        EntityArgument.getEntity(ctx, "target"), 0L))
                                .then(Commands.argument("before", LongArgumentType.longArg(0))
                                        .executes(ctx -> subject(ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "target"),
                                                LongArgumentType.getLong(ctx, "before"))))))
                .then(Commands.literal("village")
                        .then(Commands.literal("nearest")
                                .executes(ctx -> villageNearest(ctx.getSource(), 0L))
                                .then(Commands.argument("before", LongArgumentType.longArg(0))
                                        .executes(ctx -> villageNearest(ctx.getSource(),
                                                LongArgumentType.getLong(ctx, "before")))))
                        .then(Commands.argument("id", IntegerArgumentType.integer(0))
                                .executes(ctx -> village(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "id"), 0L))))
                .then(Commands.literal("day")
                        .then(Commands.argument("worldDay", LongArgumentType.longArg())
                                .executes(ctx -> day(ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "worldDay")))))
                .then(Commands.literal("arc")
                        .then(Commands.argument("arcId", LongArgumentType.longArg(1))
                                .executes(ctx -> arc(ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "arcId")))))
                .then(Commands.literal("knows")
                        .executes(ctx -> withFocus(ctx.getSource(),
                                target -> knows(ctx.getSource(), target)))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> knows(ctx.getSource(),
                                        EntityArgument.getEntity(ctx, "target")))))
                .then(Commands.literal("score")
                        .then(Commands.argument("eventId", LongArgumentType.longArg(1))
                                .executes(ctx -> score(ctx.getSource(),
                                        LongArgumentType.getLong(ctx, "eventId")))))
                .then(Commands.literal("points")
                        .executes(ctx -> points(ctx.getSource(), null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> points(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("count")
                        .then(Commands.argument("key", StringArgumentType.string())
                                .executes(ctx -> withFocus(ctx.getSource(),
                                        target -> count(ctx.getSource(), target,
                                                StringArgumentType.getString(ctx, "key")))))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("key", StringArgumentType.string())
                                        .executes(ctx -> count(ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "target"),
                                                StringArgumentType.getString(ctx, "key"))))))
                .then(Commands.literal("memories")
                        .executes(ctx -> withFocus(ctx.getSource(),
                                target -> memories(ctx.getSource(), target)))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> memories(ctx.getSource(),
                                        EntityArgument.getEntity(ctx, "target")))))
                .then(Commands.literal("sentiment")
                        .then(Commands.argument("toward", EntityArgument.entity())
                                .executes(ctx -> {
                                    Entity toward = EntityArgument.getEntity(ctx, "toward");
                                    return withFocus(ctx.getSource(),
                                            from -> sentiment(ctx.getSource(), from, toward));
                                }))
                        .then(Commands.argument("from", EntityArgument.entity())
                                .then(Commands.argument("toward", EntityArgument.entity())
                                        .executes(ctx -> sentiment(ctx.getSource(),
                                                EntityArgument.getEntity(ctx, "from"),
                                                EntityArgument.getEntity(ctx, "toward"))))))
                .then(Commands.literal("concepts")
                        .executes(ctx -> concepts(ctx.getSource())))
                .then(Commands.literal("pregen")
                        .then(Commands.literal("reroll")
                                .executes(ctx -> pregenReroll(ctx.getSource()))))
                .then(Commands.literal("emit")
                        .then(Commands.argument("template", StringArgumentType.string())
                                .suggests((ctx, builder) -> {
                                    for (ResourceLocation id : ChronicleEventRegistry.all().keySet()) {
                                        builder.suggest("\"" + id + "\"");
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> withFocus(ctx.getSource(),
                                        target -> emit(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "template"), target)))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(ctx -> emit(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "template"),
                                                EntityArgument.getEntity(ctx, "target"))))))));
    }

    // ---- focus targeting ----

    /**
     * Resolves the villager the calling player is looking at (within 16 blocks),
     * falling back to the nearest villager within 8 blocks, so debug subcommands
     * can omit the entity argument entirely.
     */
    private static @Nullable LivingEntity focusVillager(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return null;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        AABB box = player.getBoundingBox().inflate(16.0);
        VillagerEntityMCA aimed = null;
        double bestDot = 0.97;
        VillagerEntityMCA nearest = null;
        double nearestDistSq = 8.0 * 8.0;
        for (VillagerEntityMCA villager : player.serverLevel().getEntitiesOfClass(VillagerEntityMCA.class, box)) {
            Vec3 to = villager.getBoundingBox().getCenter().subtract(eye);
            double dot = to.normalize().dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                aimed = villager;
            }
            double distSq = to.lengthSqr();
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = villager;
            }
        }
        return aimed != null ? aimed : nearest;
    }

    private interface FocusAction {
        int run(LivingEntity target);
    }

    private static int withFocus(CommandSourceStack source, FocusAction action) {
        LivingEntity target = focusVillager(source);
        if (target == null) {
            source.sendFailure(Component.literal(
                    "no villager in focus - look at one (or stand near one), or pass a target"));
            return 0;
        }
        return action.run(target);
    }

    // ---- event listings ----

    private static int subject(CommandSourceStack source, Entity target, long before) {
        sendEvents(source, "Events for " + target.getName().getString(),
                Chronicles.bySubject(target.getUUID(), before, PAGE_SIZE));
        return 1;
    }

    private static int village(CommandSourceStack source, int villageId, long before) {
        sendEvents(source, "Events for village " + villageId,
                Chronicles.byVillage(source.getLevel().dimension().location(), villageId, before, PAGE_SIZE));
        return 1;
    }

    private static int villageNearest(CommandSourceStack source, long before) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("village nearest needs a player context"));
            return 0;
        }
        Optional<Village> village = Village.findNearest(player);
        if (village.isEmpty()) {
            source.sendFailure(Component.literal("no village nearby"));
            return 0;
        }
        int id = village.get().getId();
        sendEvents(source, "Events for village " + id + " (" + village.get().getName() + ")",
                Chronicles.byVillage(player.level().dimension().location(), id, before, PAGE_SIZE));
        return 1;
    }

    private static int day(CommandSourceStack source, long worldDay) {
        sendEvents(source, "Events on day " + worldDay, Chronicles.byDay(worldDay, PAGE_SIZE));
        return 1;
    }

    private static int arc(CommandSourceStack source, long arcId) {
        sendEvents(source, "Arc #" + arcId, Chronicles.byArc(arcId, 32));
        return 1;
    }

    private static void sendEvents(CommandSourceStack source, String title,
                                   CompletableFuture<List<ChronicleEvent>> future) {
        MinecraftServer server = source.getServer();
        future.thenAccept(events -> server.execute(() -> {
            source.sendSuccess(() -> Component.literal("=== " + title + " ==="), false);
            if (events.isEmpty()) {
                source.sendSuccess(() -> Component.literal("(no events)"), false);
                return;
            }
            for (ChronicleEvent event : events) {
                Component line = Component.literal("#" + event.eventId() + " [")
                        .append(CalendarDateFormatter.format(server, event.worldDay(),
                                CalendarDateFormatter.Style.LONG))
                        .append(Component.literal("] "))
                        .append(renderHeadline(event, event.params()));
                source.sendSuccess(() -> line, false);
            }
            ChronicleEvent last = events.get(events.size() - 1);
            source.sendSuccess(() -> Component.literal(
                    "(older: append " + last.eventId() + " as the cursor)"), false);
        }));
    }

    static Component renderHeadline(ChronicleEvent event, Map<String, String> params) {
        ChronicleEventTemplate template = ChronicleEventRegistry.byId(event.templateId());
        if (template != null) {
            Object[] args = template.display().paramNames().stream()
                    .map(name -> params.getOrDefault(name, "?"))
                    .toArray();
            return Component.translatableWithFallback(
                    template.display().headlineLangKey(), template.display().headlineLiteral(), args);
        }
        return Component.literal(event.templateId() + " " + params);
    }

    // ---- knowledge / belief ----

    private static int knows(CommandSourceStack source, Entity target) {
        MinecraftServer server = source.getServer();
        Chronicles.accountsByKnower(target.getUUID(), 20).thenAccept(accounts -> server.execute(() -> {
            source.sendSuccess(() -> Component.literal(
                    "=== " + target.getName().getString() + " knows (" + accounts.size() + ") ==="), false);
            for (Account account : accounts) {
                StringBuilder line = new StringBuilder();
                line.append("#").append(account.storyEventId())
                        .append(" via ").append(account.channel())
                        .append(String.format(" (fidelity %.2f", account.fidelity()));
                if (account.sourceAccountId() > 0) {
                    line.append(", heard-from account #").append(account.sourceAccountId());
                }
                line.append(")");
                DistortionOverlay overlay = DistortionOverlay.fromJson(account.overlayJson());
                if (!overlay.isNone()) {
                    line.append(" DISTORTED");
                    if (overlay.hasSubstitution()) {
                        line.append(" [believes ").append(overlay.substitutedRole())
                                .append(" was ").append(overlay.substituteName()).append("]");
                    }
                }
                String text = line.toString();
                source.sendSuccess(() -> Component.literal(text), false);
            }
        }));
        return 1;
    }

    private static int score(CommandSourceStack source, long eventId) {
        MinecraftServer server = source.getServer();
        Chronicles.byId(eventId).thenAccept(found -> server.execute(() -> {
            if (found.isEmpty()) {
                source.sendFailure(Component.literal("no event #" + eventId));
                return;
            }
            ChronicleEvent event = found.get();
            ChronicleEventTemplate template = ChronicleEventRegistry.byId(event.templateId());
            if (template == null) {
                source.sendFailure(Component.literal("template gone: " + event.templateId()));
                return;
            }
            long today = TownsteadCalendar.worldDay(server);
            float local = NewsScore.score(template, event.magnitude(), event.worldDay(),
                    event.villageId(), today, event.villageId());
            float foreign = NewsScore.score(template, event.magnitude(), event.worldDay(),
                    event.villageId(), today, event.villageId() + 1);
            source.sendSuccess(() -> Component.literal(String.format(
                    "#%d %s: base %.1f × rarity %.1f × magnitude %.1f, age %dd → score %.2f local / %.2f foreign",
                    eventId, event.templateId(), template.newsValue(),
                    template.rarity().newsMultiplier, event.magnitude(),
                    today - event.worldDay(), local, foreign)), false);
        }));
        return 1;
    }

    private static int points(CommandSourceStack source, ServerPlayer player) {
        ServerPlayer subject = player != null ? player : source.getPlayer();
        if (subject == null) {
            source.sendFailure(Component.literal("points needs a player"));
            return 0;
        }
        int points = ChronicleSavedData.get(source.getServer()).newsPoints(subject.getUUID());
        source.sendSuccess(() -> Component.literal(
                subject.getGameProfile().getName() + " has " + points + " news points"), false);
        return 1;
    }

    private static int count(CommandSourceStack source, Entity target, String key) {
        int value = Chronicles.count(source.getServer(), target.getUUID(), key);
        source.sendSuccess(() -> Component.literal(
                target.getName().getString() + " " + key + " = " + value), false);
        return 1;
    }

    private static int memories(CommandSourceStack source, Entity target) {
        List<VillagerMemory> memories = Chronicles.memories(source.getServer(), target.getUUID());
        source.sendSuccess(() -> Component.literal(
                "=== Memories of " + target.getName().getString() + " (" + memories.size() + ") ==="), false);
        for (VillagerMemory memory : memories) {
            source.sendSuccess(() -> Component.literal(String.format(
                    "%s ×%d strength %.2f valence %+.2f (day %d..%d)",
                    memory.memoryKey(), memory.count(), memory.strength(), memory.valence(),
                    memory.firstDay(), memory.lastDay())), false);
        }
        return 1;
    }

    private static int sentiment(CommandSourceStack source, Entity from, Entity toward) {
        SentimentEntry entry = ChronicleSavedData.get(source.getServer())
                .sentimentEntry(from.getUUID(), toward.getUUID());
        if (entry == null) {
            source.sendSuccess(() -> Component.literal(
                    from.getName().getString() + " has no sentiment toward " + toward.getName().getString()), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal(String.format(
                "%s feels %+.2f toward %s (last moved day %d by account #%d)",
                from.getName().getString(), entry.value(), toward.getName().getString(),
                entry.lastDay(), entry.sourceAccountId())), false);
        return 1;
    }

    private static int concepts(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("concepts needs a player context"));
            return 0;
        }
        Optional<Village> village = Village.findNearest(player);
        if (village.isEmpty()) {
            source.sendFailure(Component.literal("no village nearby"));
            return 0;
        }
        VillageKey key = new VillageKey(player.level().dimension().location(), village.get().getId());
        List<ConceptLedger.ConceptEntry> entries = ConceptLedger.get(source.getServer()).byVillage(key);
        source.sendSuccess(() -> Component.literal(
                "=== Concepts of village " + key.villageId() + " (" + entries.size() + ") ==="), false);
        for (ConceptLedger.ConceptEntry entry : entries) {
            source.sendSuccess(() -> Component.literal(
                    entry.id() + " — " + entry.displayNameLiteral()
                            + " (since day " + entry.foundingDay() + ")"), false);
        }
        return 1;
    }

    private static int pregenReroll(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("pregen reroll needs a player context"));
            return 0;
        }
        Optional<Village> village = Village.findNearest(player);
        if (village.isEmpty()) {
            source.sendFailure(Component.literal("no village nearby"));
            return 0;
        }
        MinecraftServer server = source.getServer();
        VillageKey key = new VillageKey(player.level().dimension().location(), village.get().getId());
        WorldCalendarSavedData.VillageBirth birth = WorldCalendarSavedData.get(server).getVillageBirth(key);
        if (birth == null) {
            source.sendFailure(Component.literal("village has no establishment record yet"));
            return 0;
        }
        ChronicleSavedData.get(server).clearHistory(key);
        PregenScheduler.forget(key);
        PregenScheduler.schedule(key, birth.worldDay(), birth.playerFounded(), player.blockPosition());
        source.sendSuccess(() -> Component.literal(
                "Rerolling pre-history for village " + key.villageId()), false);
        return 1;
    }

    // ---- debug ----

    private static int stats(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ChronicleStore.Stats stats = Chronicles.storeStats();
        ChronicleSavedData data = ChronicleSavedData.get(server);
        source.sendSuccess(() -> Component.literal(String.format(
                "Chronicle archive: %s | queued %d | written %d | dropped %d | %.1f MiB",
                stats.available() ? "available" : "UNAVAILABLE",
                stats.queued(), stats.written(), stats.dropped(),
                stats.dbFileBytes() / (1024.0 * 1024.0))), false);
        source.sendSuccess(() -> Component.literal(String.format(
                "Hot tier: %d counter subjects | %d memory holders | %d village digests | %d sentiment holders | buffer %d | open arcs %d | pregen queued %d",
                data.counterSubjects(), data.memoryHolders(), data.historyVillages(),
                data.sentimentHolders(), Chronicles.buffer().size(), ArcManager.openCount(),
                PregenScheduler.queued())), false);
        return 1;
    }

    private static int dump(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("chronicle dump needs a player context"));
            return 0;
        }
        ChronicleEvent draft = new ChronicleEvent(
                0L,
                rl("townstead", "debug_dump"),
                TownsteadCalendar.worldDay(server),
                player.level().getGameTime(),
                player.level().dimension().location(),
                player.blockPosition().asLong(),
                ChronicleEvent.VILLAGE_NONE,
                "debug",
                1.0f,
                ChronicleEvent.REACH_NONE,
                ChronicleEvent.NONE,
                ChronicleEvent.NONE,
                false,
                List.of(new Participation("subject",
                        ChronicleRef.player(player.getUUID(), player.getGameProfile().getName()))),
                Map.of("note", "manual dump"));
        long id = Chronicles.record(server, draft);
        source.sendSuccess(() -> Component.literal("Recorded debug event #" + id), false);
        return 1;
    }

    private static int emit(CommandSourceStack source, String templateId, Entity target) {
        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("target must be a living entity"));
            return 0;
        }
        ResourceLocation id;
        try {
            id = rlParse(templateId);
        } catch (Exception e) {
            source.sendFailure(Component.literal("bad template id: " + templateId));
            return 0;
        }
        ChronicleEventTemplate template = ChronicleEventRegistry.byId(id);
        if (template == null) {
            source.sendFailure(Component.literal("unknown chronicle_event: " + id));
            return 0;
        }
        var eventId = ChronicleEmitter.emitTemplate(
                (net.minecraft.server.level.ServerLevel) living.level(), template, living, 1.0f, Map.of());
        if (eventId.isEmpty()) {
            source.sendFailure(Component.literal("emission produced no event"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Emitted " + id + " as event #" + eventId.getAsLong()), false);
        return 1;
    }

    private static ResourceLocation rl(String namespace, String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }

    private static ResourceLocation rlParse(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }
}
