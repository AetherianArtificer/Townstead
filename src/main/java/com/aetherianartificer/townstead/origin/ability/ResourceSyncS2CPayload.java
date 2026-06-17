package com.aetherianartificer.townstead.origin.ability;

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
 * Server → owning player: a snapshot of that player's resource meters for the HUD.
 * Each entry is a gene id, current value, max, and bar colour. Villagers have no
 * HUD, so only players receive this.
 */
//? if neoforge {
public record ResourceSyncS2CPayload(List<Bar> bars) implements CustomPacketPayload {
//?} else {
/*public record ResourceSyncS2CPayload(java.util.List<ResourceSyncS2CPayload.Bar> bars) {
*///?}

    public record Bar(String geneId, int value, int max, int color) {}

    //? if neoforge {
    public static final Type<ResourceSyncS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "resource_sync_s2c"));

    public static final StreamCodec<FriendlyByteBuf, ResourceSyncS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), ResourceSyncS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "resource_sync_s2c");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "resource_sync_s2c");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(bars.size());
        for (Bar bar : bars) {
            buf.writeUtf(bar.geneId());
            buf.writeVarInt(bar.value());
            buf.writeVarInt(bar.max());
            buf.writeInt(bar.color());
        }
    }

    public static ResourceSyncS2CPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Bar> bars = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            bars.add(new Bar(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readInt()));
        }
        return new ResourceSyncS2CPayload(bars);
    }
}
