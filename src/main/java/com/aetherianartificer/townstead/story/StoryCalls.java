package com.aetherianartificer.townstead.story;

import com.aetherianartificer.townstead.performance.PerformanceProviders;
import com.aetherianartificer.townstead.performance.PerformanceRequest;
import com.aetherianartificer.townstead.story.net.StoryCallS2CPayload;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Who wants a word with whom. Once a second, each player's nearby story villagers are checked for
 * something waiting: a quest ready to hand back, a scene the story is calling them over for (its
 * {@code calling()} function returns a line), or a quest still open. Each player hears only the
 * changes, and sees a mark over those heads. Coming close to one who is calling, they wave and say
 * their line once for that scene.
 */
public final class StoryCalls {
    public static final byte NONE = 0, WAITING = 1, CALLING = 2, READY = 3;
    private static final double SCAN = 32, CALL_OUT = 8;
    /** Asking the Ink costs a story load, so its answer is kept this long unless a conversation ends. */
    private static final long INK_TTL = 200;
    private static final ResourceLocation WAVE = ResourceLocation.tryParse("townstead:reaction_wave");

    private record Ink(String line, String scene, long at) {}

    private static final Map<UUID, Map<Integer, Byte>> SENT = new HashMap<>();
    private static final Map<UUID, Map<UUID, Ink>> INK = new HashMap<>();
    /** The scene each villager last called this player over for, so they call once per scene. */
    private static final Map<UUID, Map<UUID, String>> CALLED = new HashMap<>();
    private static int ticks;

    private StoryCalls() {}

    public static void tick(MinecraftServer server) {
        if (++ticks % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) scan(player);
        SENT.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        INK.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    /** A conversation with this player ended: ask the Ink again next time. */
    static void forget(UUID player) {
        INK.remove(player);
    }

    private static void scan(ServerPlayer player) {
        Map<Integer, Byte> sent = SENT.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Set<Integer> seen = new HashSet<>();
        boolean talking = StoryService.inConversation(player);
        long now = player.level().getGameTime();
        for (VillagerEntityMCA villager : player.serverLevel().getEntitiesOfClass(VillagerEntityMCA.class,
                player.getBoundingBox().inflate(SCAN), v -> v.isAlive() && !v.isBaby())) {
            StoryDefinition story = StoryService.storyFor(player, villager);
            if (story == null) continue;
            PlayerStories.Entry entry = StoryService.existingEntry(player, villager, story);
            byte state = NONE;
            if (entry != null) {
                for (PlayerStories.QuestRecord record : entry.quests.values()) {
                    if (record.state == PlayerStories.QuestState.READY) state = READY;
                    else if (record.state == PlayerStories.QuestState.ACTIVE && state == NONE) state = WAITING;
                }
            }
            Ink ink = null;
            if (state != READY && !talking) {
                ink = ink(player, villager, story, now);
                if (ink != null && !ink.line().isEmpty()) state = CALLING;
            }
            seen.add(villager.getId());
            Byte before = sent.put(villager.getId(), state);
            if (before == null ? state != NONE : before != state) send(player, villager.getId(), state);
            if (state == CALLING && ink != null && villager.distanceToSqr(player) <= CALL_OUT * CALL_OUT) {
                callOut(player, villager, ink);
            }
        }
        sent.entrySet().removeIf(e -> {
            if (seen.contains(e.getKey())) return false;
            if (e.getValue() != NONE) send(player, e.getKey(), NONE);
            return true;
        });
    }

    private static @Nullable Ink ink(ServerPlayer player, VillagerEntityMCA villager, StoryDefinition story, long now) {
        Map<UUID, Ink> cache = INK.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Ink cached = cache.get(villager.getUUID());
        if (cached != null && now - cached.at() < INK_TTL) return cached;
        String[] answer = StoryService.calling(player, villager, story);
        Ink fresh = answer == null ? new Ink("", "", now) : new Ink(answer[0], answer[1], now);
        cache.put(villager.getUUID(), fresh);
        return fresh;
    }

    private static void callOut(ServerPlayer player, VillagerEntityMCA villager, Ink ink) {
        Map<UUID, String> called = CALLED.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        if (ink.scene().equals(called.get(villager.getUUID()))) return;
        called.put(villager.getUUID(), ink.scene());
        villager.getLookControl().setLookAt(player);
        if (WAVE != null) {
            PerformanceProviders.play(player.serverLevel(), new PerformanceRequest(villager, WAVE, "story_call", 40, 45,
                    PerformanceRequest.Fallback.NONE));
        }
        String line = StoryText.resolve(ink.line(), key -> key);
        player.sendSystemMessage(Component.literal("§7" + villager.getDisplayName().getString() + ": §o" + line));
    }

    private static void send(ServerPlayer player, int entityId, byte state) {
        StoryCallS2CPayload payload = new StoryCallS2CPayload(entityId, state);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }
}
