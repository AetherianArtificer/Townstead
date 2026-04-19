package com.aetherianartificer.townstead.compat.travelerstitles;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

//? if neoforge {
public record ClientCapsPayload(boolean hasTravelersTitles) implements CustomPacketPayload {
//?} else {
/*public record ClientCapsPayload(boolean hasTravelersTitles) {
*///?}

    //? if neoforge {
    public static final Type<ClientCapsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "client_caps"));

    public static final StreamCodec<FriendlyByteBuf, ClientCapsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, ClientCapsPayload::hasTravelersTitles,
                    ClientCapsPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "client_caps");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "client_caps");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(hasTravelersTitles);
    }

    public static ClientCapsPayload read(FriendlyByteBuf buf) {
        return new ClientCapsPayload(buf.readBoolean());
    }
    *///?}
}
