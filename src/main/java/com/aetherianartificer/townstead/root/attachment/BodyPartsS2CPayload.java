package com.aetherianartificer.townstead.root.attachment;

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
 * Server to client: an entity's hittable parts and their ids. A client aiming at a part names it by id,
 * so both sides must use the server's. An empty list means it has none.
 */
//? if neoforge {
public record BodyPartsS2CPayload(int entityId, List<Entry> parts) implements CustomPacketPayload {
//?} else {
/*public record BodyPartsS2CPayload(int entityId, List<BodyPartsS2CPayload.Entry> parts) {
*///?}

    public record Entry(String attachment, int box, int partId) {}

    private static final int MAX_PARTS = 64;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(parts.size());
        for (Entry entry : parts) {
            buf.writeUtf(entry.attachment(), 256);
            buf.writeVarInt(entry.box());
            buf.writeVarInt(entry.partId());
        }
    }

    public static BodyPartsS2CPayload read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int count = Math.min(buf.readVarInt(), MAX_PARTS);
        List<Entry> parts = new ArrayList<>(count);
        for (int i = 0; i < count; i++) parts.add(new Entry(buf.readUtf(256), buf.readVarInt(), buf.readVarInt()));
        return new BodyPartsS2CPayload(id, parts);
    }

    //? if neoforge {
    public static final Type<BodyPartsS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "body_parts"));
    public static final StreamCodec<FriendlyByteBuf, BodyPartsS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), BodyPartsS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
}
