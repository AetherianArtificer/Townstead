package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client butcher policy sync payload.
 */
public record ButcherPolicySyncPayload(String profileId, int tier, int areaCount) implements CustomPacketPayload {
    public static final Type<ButcherPolicySyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "butcher_policy_sync"));

    public static final StreamCodec<FriendlyByteBuf, ButcherPolicySyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, ButcherPolicySyncPayload::profileId,
                    ByteBufCodecs.VAR_INT, ButcherPolicySyncPayload::tier,
                    ByteBufCodecs.VAR_INT, ButcherPolicySyncPayload::areaCount,
                    ButcherPolicySyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
