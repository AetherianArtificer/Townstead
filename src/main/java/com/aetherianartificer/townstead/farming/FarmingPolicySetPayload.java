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
 * Client -> Server farming policy update/query payload.
 * tier == -1 is treated as a query-only request.
 */
//? if neoforge {
public record FarmingPolicySetPayload(String patternId, int tier) implements CustomPacketPayload {
//?} else {
/*public record FarmingPolicySetPayload(String patternId, int tier) {
*///?}

    //? if neoforge {
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
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "farming_policy_set");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "farming_policy_set");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeUtf(patternId);
        buf.writeVarInt(tier);
    }

    public static FarmingPolicySetPayload read(FriendlyByteBuf buf) {
        return new FarmingPolicySetPayload(buf.readUtf(), buf.readVarInt());
    }
    *///?}
}
