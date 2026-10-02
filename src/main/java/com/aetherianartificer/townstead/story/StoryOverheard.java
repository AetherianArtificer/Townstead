package com.aetherianartificer.townstead.story;

import com.aetherianartificer.townstead.switchboard.Systems;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Scenes a player overhears. A story knot tagged {@code # overheard: once} plays in chat, with no
 * dialogue screen, when the player comes near the villager who tells the story. Lines tagged
 * {@code # who:} come from the rest of the cast, as in a conversation. It runs on the player's own
 * copy of the story, so it plays once per player. A knot that says nothing (because its checks
 * fail, such as {@code here()}) is dropped unsaved and tried again later.
 */
final class StoryOverheard {
    private static final int SCAN_TICKS = 200;
    /** How close the player has to be to the teller for a scene to start. */
    private static final double NEAR = 10;
    /** Past this, the player has walked off and the scene stops. */
    private static final double AWAY_SQR = 24 * 24;
    /** Players within this of a speaker see the line. */
    private static final double HEARD = 20;
    /** At most one scene per player in this many ticks. */
    private static final long COOLDOWN = 20 * 60 * 3;

    private static final class Run {
        final StorySession session;
        int wait;

        Run(StorySession session) {
            this.session = session;
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private StoryOverheard() {}

    static void tick(MinecraftServer server, int ticks) {
        Iterator<Map.Entry<UUID, Run>> it = RUNS.entrySet().iterator();
        while (it.hasNext()) {
            Run run = it.next().getValue();
            StorySession session = run.session;
            ServerPlayer player = server.getPlayerList().getPlayer(session.player.getUUID());
            if (player == null || !session.villager.isAlive() || session.villager.level() != player.level()
                    || session.villager.distanceToSqr(player) > AWAY_SQR) {
                end(run);
                it.remove();
                continue;
            }
            session.holdInPlace();
            if (--run.wait > 0) continue;
            StorySession.Spoken line = session.nextSpoken();
            if (line == null) {
                end(run);
                it.remove();
                continue;
            }
            say(line);
            run.wait = pause(line.text());
        }
        if (ticks % SCAN_TICKS != 0 || !Systems.on(Systems.STORIES)) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (RUNS.containsKey(player.getUUID()) || StoryService.inConversation(player) || player.isSpectator()) continue;
            long now = player.level().getGameTime();
            Long last = LAST.get(player.getUUID());
            if (last != null && now - last < COOLDOWN) continue;
            Run run = find(player);
            if (run == null) continue;
            RUNS.put(player.getUUID(), run);
            LAST.put(player.getUUID(), now);
        }
    }

    /** The first scene the player can overhear from a villager near them, already on its first line. */
    private static @Nullable Run find(ServerPlayer player) {
        var nearby = player.level().getEntitiesOfClass(VillagerEntityMCA.class, player.getBoundingBox().inflate(NEAR),
                v -> v.isAlive() && v.getInteractions().getInteractingPlayer().isEmpty());
        nearby.sort(Comparator.comparingDouble(v -> v.distanceToSqr(player)));
        for (VillagerEntityMCA villager : nearby) {
            StoryDefinition story = StoryService.storyFor(player, villager);
            if (story == null || story.overheard().isEmpty()) continue;
            PlayerStories.Entry entry = StoryService.existingEntry(player, villager, story);
            // A conversation left mid-way keeps its place; a scene would move the story on under it.
            if (entry == null || entry.interrupted) continue;
            for (String knot : story.overheard()) {
                StorySession session;
                try {
                    session = StorySession.open(player, villager, story, entry);
                } catch (Exception e) {
                    com.aetherianartificer.townstead.Townstead.LOGGER.warn("Story {} could not open: {}", story.id(), e.getMessage());
                    break;
                }
                if (session.visited(knot) || !session.begin(knot)) {
                    session.discard();
                    continue;
                }
                StorySession.Spoken first = session.nextSpoken();
                if (first == null) {
                    session.discard();
                    continue;
                }
                Run run = new Run(session);
                say(first);
                run.wait = pause(first.text());
                return run;
            }
        }
        return null;
    }

    /** Stops the player's scene, keeping what they heard. Called before they open a conversation. */
    static void stop(ServerPlayer player) {
        Run run = RUNS.remove(player.getUUID());
        if (run != null) end(run);
    }

    /** Drops every scene, as when stories reload. */
    static void clear() {
        for (Run run : RUNS.values()) run.session.discard();
        RUNS.clear();
    }

    private static void end(Run run) {
        run.session.finish();
        StoryService.changed(run.session.player);
    }

    private static void say(StorySession.Spoken line) {
        VillagerEntityMCA speaker = line.by();
        for (ServerPlayer player : speaker.level().getEntitiesOfClass(ServerPlayer.class, speaker.getBoundingBox().inflate(HEARD))) {
            speaker.sendChatMessage(StoryText.component(line.text())
                    .withStyle(player.distanceTo(speaker) < 10 ? ChatFormatting.WHITE : ChatFormatting.GRAY), player);
        }
    }

    /** Ticks to wait after a line, so it can be read before the next one comes. */
    private static int pause(String text) {
        return Math.max(50, Math.min(160, 30 + text.length() * 6 / 5));
    }
}
