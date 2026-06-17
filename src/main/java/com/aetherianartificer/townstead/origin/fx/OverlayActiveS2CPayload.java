package com.aetherianartificer.townstead.origin.fx;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → owning player: the gene ids of that player's {@code overlay} genes whose
 * condition currently holds. The client looks each id up in the gene catalog for its
 * texture and alpha, then blits them full-screen. Player-only (overlays are a HUD
 * effect); resent each throttle tick so a condition flip clears or shows promptly.
 */
//? if neoforge {
public record OverlayActiveS2CPayload(List<String> geneIds) implements CustomPacketPayload {
//?} else {
/*public record OverlayActiveS2CPayload(java.util.List<String> geneIds) {
*///?}

    //? if neoforge {
    public static final Type<OverlayActiveS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "overlay_active_s2c"));

    public static final StreamCodec<FriendlyByteBuf, OverlayActiveS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), OverlayActiveS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "overlay_active_s2c");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "overlay_active_s2c");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(geneIds.size());
        for (String geneId : geneIds) buf.writeUtf(geneId);
    }

    public static OverlayActiveS2CPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) ids.add(buf.readUtf());
        return new OverlayActiveS2CPayload(ids);
    }
}
