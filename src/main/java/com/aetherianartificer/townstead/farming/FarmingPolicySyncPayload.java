package com.aetherianartificer.townstead.farming;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client farming policy sync payload.
 */
//? if neoforge {
public record FarmingPolicySyncPayload(String patternId, int tier, int areaCount) implements CustomPacketPayload {
//?} else {
/*public record FarmingPolicySyncPayload(String patternId, int tier, int areaCount) {
*///?}

    //? if neoforge {
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
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "farming_policy_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "farming_policy_sync");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeUtf(patternId);
        buf.writeVarInt(tier);
        buf.writeVarInt(areaCount);
    }

    public static FarmingPolicySyncPayload read(FriendlyByteBuf buf) {
        return new FarmingPolicySyncPayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt());
    }
    *///?}
}
