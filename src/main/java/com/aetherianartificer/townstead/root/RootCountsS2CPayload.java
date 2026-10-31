package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Server to client: how many loaded players and villagers carry each root id. */
//? if neoforge {
public record RootCountsS2CPayload(Map<String, Integer> counts) implements CustomPacketPayload {
//?} else {
/*public record RootCountsS2CPayload(Map<String, Integer> counts) {
*///?}

    //? if neoforge {
    public static final Type<RootCountsS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "root_counts_s2c"));
    public static final StreamCodec<FriendlyByteBuf, RootCountsS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), RootCountsS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public RootCountsS2CPayload {
        counts = Map.copyOf(counts);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(counts.size());
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeVarInt(entry.getValue());
        }
    }

    public static RootCountsS2CPayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        Map<String, Integer> counts = new HashMap<>(n);
        for (int i = 0; i < n; i++) counts.put(buf.readUtf(), buf.readVarInt());
        return new RootCountsS2CPayload(counts);
    }
}
