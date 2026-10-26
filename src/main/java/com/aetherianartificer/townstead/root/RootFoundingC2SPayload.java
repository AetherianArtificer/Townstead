package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server, from the editor's Root tab and the root browser. {@link #STATE} asks what the
 * target ({@link RootSetC2SPayload#SELF} or a villager's network id) is now. {@link #ROLL} asks the
 * server to roll a founding of a choice, one root or a {@link RootMix} (empty for one root), for the
 * editor to show. {@link #COMMIT} gives the target the founding {@code foundingId} exactly as it was
 * shown. Every one is answered by a {@link RootFoundingS2CPayload} carrying {@code requestId}.
 */
//? if neoforge {
public record RootFoundingC2SPayload(int entityId, int requestId, int op, String rootId, RootMix mix, int foundingId)
        implements CustomPacketPayload {
//?} else {
/*public record RootFoundingC2SPayload(int entityId, int requestId, int op, String rootId, RootMix mix, int foundingId) {
*///?}

    public static final int STATE = 0;
    public static final int ROLL = 1;
    public static final int COMMIT = 2;

    //? if neoforge {
    public static final Type<RootFoundingC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "root_founding_c2s"));
    public static final StreamCodec<FriendlyByteBuf, RootFoundingC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), RootFoundingC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeVarInt(requestId);
        buf.writeVarInt(op);
        buf.writeUtf(rootId);
        mix.write(buf);
        buf.writeVarInt(foundingId);
    }

    public static RootFoundingC2SPayload read(FriendlyByteBuf buf) {
        return new RootFoundingC2SPayload(buf.readInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(),
                RootMix.read(buf), buf.readVarInt());
    }
}
