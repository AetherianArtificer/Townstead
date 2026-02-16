package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server butcher policy update/query payload.
 * tier == -1 is treated as a query-only request.
 */
public record ButcherPolicySetPayload(String profileId, int tier) implements CustomPacketPayload {
    public static final Type<ButcherPolicySetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "butcher_policy_set"));

    public static final StreamCodec<FriendlyByteBuf, ButcherPolicySetPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ButcherPolicySetPayload::profileId,
                    ByteBufCodecs.VAR_INT, ButcherPolicySetPayload::tier,
                    ButcherPolicySetPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
