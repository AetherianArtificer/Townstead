package com.aetherianartificer.townstead.emote;

import net.conczin.mca.entity.VillagerEntityMCA;

public interface VillagerEmotePlaybackBackend {
    boolean canPlayVillagerEmote();

    boolean tryPlayVillagerEmote(VillagerEntityMCA villager, String emoteKey);
}
