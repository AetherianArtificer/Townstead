package com.aetherianartificer.townstead.story.goal;

import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Who a goal is read for: the player on the quest and the villager telling it. Either may be
 * offline or unloaded, in which case goals that read them report {@link Goal#UNKNOWN}.
 */
public record GoalContext(MinecraftServer server, UUID playerId, UUID speakerId, String speakerName,
                          @Nullable VillagerEntityMCA speaker) {

    public @Nullable ServerPlayer player() {
        return server.getPlayerList().getPlayer(playerId);
    }

    /** The teller's home village, or the nearest one. */
    public Optional<Village> village() {
        if (speaker == null) return Optional.empty();
        try {
            Optional<Village> home = speaker.getResidency().getHomeVillage();
            return home.isPresent() ? home : Village.findNearest(speaker);
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    /** The village of the teller or the player, as the public API names it. */
    public Optional<VillageId> villageId(boolean ofTeller) {
        Entity who = ofTeller ? speaker : player();
        if (who == null || !(who.level() instanceof ServerLevel level)) return Optional.empty();
        Optional<Village> village = ofTeller ? village() : Village.findNearest(who);
        return village.map(v -> new VillageId(level.dimension().location(), v.getId()));
    }
}
