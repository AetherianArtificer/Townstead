package com.aetherianartificer.townstead.farming;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client farming policy sync payload.
 */
public record FarmingPolicySyncPayload(String patternId, int tier, int areaCount) implements CustomPacketPayload {
    public static final Type<FarmingPolicySyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "farming_policy_sync"));

    public static final StreamCodec<FriendlyByteBuf, FarmingPolicySyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, FarmingPolicySyncPayload::patternId,
                    ByteBufCodecs.VAR_INT, FarmingPolicySyncPayload::tier,
                    ByteBufCodecs.VAR_INT, FarmingPolicySyncPayload::areaCount,
                    FarmingPolicySyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
