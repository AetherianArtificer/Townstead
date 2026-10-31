package com.aetherianartificer.townstead.chronicle.knowledge;

/**
 * A way information travels. Channels are the platform seam: newspapers, town
 * criers, caravans, and photographs are future channels with their own
 * signatures — the engine only ever sees this record.
 *
 * @param id             namespaced string id, interned in the archive
 * @param fidelityFactor multiplier applied to the source account's fidelity per hop
 * @param fidelityFloor  lowest fidelity this channel can deliver
 * @param driftChance    chance per hop to apply magnitude/valence drift
 * @param substitutionChance chance per hop to misattribute a substitutable role
 */
public record SpreadChannel(String id, float fidelityFactor, float fidelityFloor,
                            float driftChance, float substitutionChance) {

    public static final SpreadChannel WITNESS =
            new SpreadChannel("townstead:witness", 1.0f, 1.0f, 0f, 0f);
    public static final SpreadChannel GOSSIP =
            new SpreadChannel("townstead:gossip", 0.8f, 0.25f, 0.35f, 0.05f);
    public static final SpreadChannel VILLAGE_DIGEST =
            new SpreadChannel("townstead:village_digest", 0.6f, 0.4f, 0.15f, 0f);
    public static final SpreadChannel PLAYER_WORD =
            new SpreadChannel("townstead:player_word", 0.9f, 0.5f, 0f, 0f);
}
