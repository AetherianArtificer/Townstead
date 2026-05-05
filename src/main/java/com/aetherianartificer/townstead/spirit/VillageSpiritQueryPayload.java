package com.aetherianartificer.townstead.spirit;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client -> Server request for the current village's spirit snapshot.
 */
//? if neoforge {
public record VillageSpiritQueryPayload() implements CustomPacketPayload {
//?} else {
/*public record VillageSpiritQueryPayload() {
*///?}

    //? if neoforge {
    public static final Type<VillageSpiritQueryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "village_spirit_query"));

    public static final StreamCodec<FriendlyByteBuf, VillageSpiritQueryPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public VillageSpiritQueryPayload decode(FriendlyByteBuf buf) {
                    return new VillageSpiritQueryPayload();
                }

                @Override
                public void encode(FriendlyByteBuf buf, VillageSpiritQueryPayload payload) {
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "village_spirit_query");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "village_spirit_query");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
    }

    public static VillageSpiritQueryPayload read(FriendlyByteBuf buf) {
        return new VillageSpiritQueryPayload();
    }
    *///?}
}
