package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: render root icons ({@code /townstead root_icons}). Icons are drawn by the
 * client's own renderer, so the command asks the player's client to do it. {@code id} is a namespace
 * (every root of a pack) or a root id; {@code rolls} 0 means a calibration sheet.
 */
//? if neoforge {
public record RootIconsS2CPayload(String id, int rolls) implements CustomPacketPayload {
//?} else {
/*public record RootIconsS2CPayload(String id, int rolls) {
*///?}

    //? if neoforge {
    public static final Type<RootIconsS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "root_icons_s2c"));

    public static final StreamCodec<FriendlyByteBuf, RootIconsS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), RootIconsS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id);
        buf.writeVarInt(rolls);
    }

    public static RootIconsS2CPayload read(FriendlyByteBuf buf) {
        return new RootIconsS2CPayload(buf.readUtf(), buf.readVarInt());
    }
}
