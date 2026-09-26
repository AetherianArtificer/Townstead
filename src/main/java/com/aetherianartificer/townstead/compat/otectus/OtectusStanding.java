package com.aetherianartificer.townstead.compat.otectus;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.OptionalInt;
import java.util.UUID;

/** A player's MCA: Reputation score with one community, read through its public API by reflection. */
public final class OtectusStanding {
    private static final String API = "dev.otectus.mcareputation.api.McaReputationApi";
    private static final String COMMUNITY = "dev.otectus.mcareputation.community.CommunityKey";

    private OtectusStanding() {}

    public static boolean available() {
        return OtectusReflect.type(API) != null;
    }

    /** Empty when MCA: Reputation is absent or has no record for this player here. */
    public static OptionalInt score(MinecraftServer server, UUID player, ResourceLocation dimension, int villageId) {
        Class<?> api = OtectusReflect.type(API);
        if (api == null || server == null || player == null) return OptionalInt.empty();
        Object community = OtectusReflect.unwrap(OtectusReflect.callStatic(OtectusReflect.type(COMMUNITY), "of",
                new Class<?>[] {ResourceLocation.class, int.class}, dimension, villageId));
        if (community == null) return OptionalInt.empty();
        Object score = OtectusReflect.callStatic(api, "getScore", server, player, community);
        return score instanceof OptionalInt value ? value : OptionalInt.empty();
    }
}
