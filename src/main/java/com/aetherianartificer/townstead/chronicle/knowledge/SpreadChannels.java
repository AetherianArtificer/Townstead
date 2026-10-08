package com.aetherianartificer.townstead.chronicle.knowledge;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * String-keyed channel registry (same shape as the pheno type registries).
 * Mods and future arcs register additional channels at setup.
 */
public final class SpreadChannels {

    private static final Map<String, SpreadChannel> CHANNELS = new LinkedHashMap<>();

    static {
        register(SpreadChannel.WITNESS);
        register(SpreadChannel.GOSSIP);
        register(SpreadChannel.VILLAGE_DIGEST);
        register(SpreadChannel.PLAYER_WORD);
    }

    private SpreadChannels() {}

    public static synchronized void register(SpreadChannel channel) {
        CHANNELS.put(channel.id(), channel);
    }

    public static SpreadChannel byId(String id) {
        SpreadChannel channel = CHANNELS.get(id);
        return channel != null ? channel : SpreadChannel.GOSSIP;
    }
}
