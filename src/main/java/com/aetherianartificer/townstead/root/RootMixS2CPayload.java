package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: what a mix is called. {@code name} is the matching heritage's name or the
 * game's generated one ("Fire Drake-Ice Drake"); {@code backstory} is the heritage's, empty when
 * none matches. An empty {@code name} means the mix was refused.
 */
//? if neoforge {
public record RootMixS2CPayload(int requestId, String name, String backstory) implements CustomPacketPayload {
//?} else {
/*public record RootMixS2CPayload(int requestId, String name, String backstory) {
*///?}

    //? if neoforge {
    public static final Type<RootMixS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "root_mix_s2c"));
    public static final StreamCodec<FriendlyByteBuf, RootMixS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), RootMixS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeUtf(name);
        buf.writeUtf(backstory);
    }

    public static RootMixS2CPayload read(FriendlyByteBuf buf) {
        return new RootMixS2CPayload(buf.readVarInt(), buf.readUtf(), buf.readUtf());
    }
}
