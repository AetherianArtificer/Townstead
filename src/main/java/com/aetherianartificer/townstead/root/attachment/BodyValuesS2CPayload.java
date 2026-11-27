package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Server to client: the live values one entity's body shows (see {@link BodyValues}), all of them each time. */
//? if neoforge {
public record BodyValuesS2CPayload(int entityId, Map<String, Float> values) implements CustomPacketPayload {
//?} else {
/*public record BodyValuesS2CPayload(int entityId, Map<String, Float> values) {
*///?}

    private static final int MAX_VALUES = 64;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(values.size());
        values.forEach((key, value) -> {
            buf.writeUtf(key, 256);
            buf.writeFloat(value);
        });
    }

    public static BodyValuesS2CPayload read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        int count = Math.min(buf.readVarInt(), MAX_VALUES);
        Map<String, Float> values = new HashMap<>();
        for (int i = 0; i < count; i++) values.put(buf.readUtf(256), buf.readFloat());
        return new BodyValuesS2CPayload(id, values);
    }

    //? if neoforge {
    public static final Type<BodyValuesS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "body_values"));
    public static final StreamCodec<FriendlyByteBuf, BodyValuesS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), BodyValuesS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
}
