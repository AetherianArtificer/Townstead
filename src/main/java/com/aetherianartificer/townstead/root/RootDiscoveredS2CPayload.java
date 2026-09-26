package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Server to client: someone discovered a Root, for the toast. */
//? if neoforge {
public record RootDiscoveredS2CPayload(String rootId, String discoverer) implements CustomPacketPayload {
//?} else {
/*public record RootDiscoveredS2CPayload(String rootId, String discoverer) {
*///?}

    //? if neoforge {
    public static final Type<RootDiscoveredS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "root_discovered_s2c"));

    public static final StreamCodec<FriendlyByteBuf, RootDiscoveredS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), RootDiscoveredS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(rootId, 256);
        buf.writeUtf(discoverer, 64);
    }

    public static RootDiscoveredS2CPayload read(FriendlyByteBuf buf) {
        return new RootDiscoveredS2CPayload(buf.readUtf(256), buf.readUtf(64));
    }
}
