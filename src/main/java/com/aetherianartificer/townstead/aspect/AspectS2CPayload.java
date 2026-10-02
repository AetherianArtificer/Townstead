package com.aetherianartificer.townstead.aspect;

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
 * Server to client: the aspects {@code entityId} can be, the one they are ({@code current}, empty
 * for none), and whether this player may change it now.
 */
//? if neoforge {
public record AspectS2CPayload(int entityId, List<String> options, String current, boolean allowed) implements CustomPacketPayload {
//?} else {
/*public record AspectS2CPayload(int entityId, List<String> options, String current, boolean allowed) {
*///?}

    //? if neoforge {
    public static final Type<AspectS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "aspect_s2c"));
    public static final StreamCodec<FriendlyByteBuf, AspectS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), AspectS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeVarInt(options.size());
        for (String option : options) buf.writeUtf(option, 256);
        buf.writeUtf(current, 256);
        buf.writeBoolean(allowed);
    }

    public static AspectS2CPayload read(FriendlyByteBuf buf) {
        int id = buf.readInt();
        int size = Math.min(buf.readVarInt(), 64);
        List<String> options = new ArrayList<>(size);
        for (int i = 0; i < size; i++) options.add(buf.readUtf(256));
        return new AspectS2CPayload(id, List.copyOf(options), buf.readUtf(256), buf.readBoolean());
    }
}
