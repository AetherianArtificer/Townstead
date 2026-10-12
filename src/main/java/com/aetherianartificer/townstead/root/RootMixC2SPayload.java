package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server, from the root browser's Mix mode. With {@code apply} false it only asks what the
 * mix is called: the server answers with a {@link RootMixS2CPayload} carrying {@code requestId}, since
 * heritage profiles live server-side. With {@code apply} true it rolls a founder of the mix onto the
 * target ({@link RootSetC2SPayload#SELF} or a villager's network id), as a root Apply does.
 */
//? if neoforge {
public record RootMixC2SPayload(int entityId, int requestId, boolean apply, RootMix mix) implements CustomPacketPayload {
//?} else {
/*public record RootMixC2SPayload(int entityId, int requestId, boolean apply, RootMix mix) {
*///?}

    //? if neoforge {
    public static final Type<RootMixC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "root_mix_c2s"));
    public static final StreamCodec<FriendlyByteBuf, RootMixC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), RootMixC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeVarInt(requestId);
        buf.writeBoolean(apply);
        mix.write(buf);
    }

    public static RootMixC2SPayload read(FriendlyByteBuf buf) {
        return new RootMixC2SPayload(buf.readInt(), buf.readVarInt(), buf.readBoolean(), RootMix.read(buf));
    }
}
