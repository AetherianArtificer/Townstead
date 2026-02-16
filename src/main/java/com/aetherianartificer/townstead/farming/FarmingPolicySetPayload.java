package com.aetherianartificer.townstead.farming;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server farming policy update/query payload.
 * tier == -1 is treated as a query-only request.
 */
public record FarmingPolicySetPayload(String patternId, int tier) implements CustomPacketPayload {
    public static final Type<FarmingPolicySetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "farming_policy_set"));

    public static final StreamCodec<FriendlyByteBuf, FarmingPolicySetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, FarmingPolicySetPayload::patternId,
                    ByteBufCodecs.VAR_INT, FarmingPolicySetPayload::tier,
                    FarmingPolicySetPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
