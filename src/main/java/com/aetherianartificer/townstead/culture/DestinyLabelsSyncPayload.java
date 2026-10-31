package com.aetherianartificer.townstead.culture;

//? if neoforge {
import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server to client: the names of culture village structures, resolved from the culture data packs. */
//? if neoforge {
public record DestinyLabelsSyncPayload(Map<String, String> labels) implements CustomPacketPayload {
//?} else {
/*public record DestinyLabelsSyncPayload(Map<String, String> labels) {
*///?}

    public static DestinyLabelsSyncPayload snapshot() {
        Map<String, String> labels = new LinkedHashMap<>();
        CultureVillages.labels().forEach((structure, label) -> labels.put(structure.toString(), label));
        return new DestinyLabelsSyncPayload(labels);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(labels.size());
        labels.forEach((structure, label) -> {
            buf.writeUtf(structure, 256);
            buf.writeUtf(label, 256);
        });
    }

    public static DestinyLabelsSyncPayload read(FriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), 4096);
        Map<String, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) labels.put(buf.readUtf(256), buf.readUtf(256));
        return new DestinyLabelsSyncPayload(labels);
    }

    //? if neoforge {
    public static final Type<DestinyLabelsSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "destiny_labels_sync"));
    public static final StreamCodec<FriendlyByteBuf, DestinyLabelsSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), DestinyLabelsSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
}
