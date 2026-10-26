package com.aetherianartificer.townstead.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells each player where the givers of their open quests are, every couple of seconds: where
 * they stand when loaded, else where they were last seen. Sent only when something moved.
 */
public final class QuestGivers {
    private static final int INTERVAL = 40;
    private static final Map<UUID, QuestGiversS2CPayload.Giver> LAST_SEEN = new ConcurrentHashMap<>();
    private static final Map<ServerPlayer, List<QuestGiversS2CPayload.Giver>> SENT = new WeakHashMap<>();

    private QuestGivers() {}

    public static void tick(ServerPlayer player) {
        if ((player.tickCount + player.getId()) % INTERVAL != 0) return;
        List<QuestGiversS2CPayload.Giver> givers = new ArrayList<>();
        for (UUID id : com.aetherianartificer.townstead.story.StoryService.activeGivers(player)) {
            QuestGiversS2CPayload.Giver where = locate(player, id);
            if (where != null) givers.add(where);
        }
        synchronized (SENT) {
            if (givers.equals(SENT.get(player))) return;
            SENT.put(player, givers);
        }
        QuestGiversS2CPayload payload = new QuestGiversS2CPayload(List.copyOf(givers));
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    private static QuestGiversS2CPayload.Giver locate(ServerPlayer player, UUID id) {
        Entity found = player.serverLevel().getEntity(id);
        if (found == null) {
            for (ServerLevel level : player.server.getAllLevels()) {
                if (level == player.serverLevel()) continue;
                found = level.getEntity(id);
                if (found != null) break;
            }
        }
        if (found != null && found.isAlive()) {
            QuestGiversS2CPayload.Giver giver = new QuestGiversS2CPayload.Giver(id,
                    found.level().dimension().location().toString(), BlockPos.containing(found.position()));
            LAST_SEEN.put(id, giver);
            return giver;
        }
        return LAST_SEEN.get(id);
    }
}
