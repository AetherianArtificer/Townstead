package com.aetherianartificer.townstead.replace;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The nameplate a costumed mob shows one player: the person's name, and how the mob regards that
 * player ({@link com.aetherianartificer.townstead.root.disposition.Disposition} ordinal).
 */
//? if neoforge {
public record WildPlateS2CPayload(int entityId, String name, byte disposition) implements CustomPacketPayload {
//?} else {
/*public record WildPlateS2CPayload(int entityId, String name, byte disposition) {
*///?}
    private static final Map<Integer, WildPlateS2CPayload> PLATES = new ConcurrentHashMap<>();

    //? if neoforge {
    public static final Type<WildPlateS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "wild_plate"));

    public static final StreamCodec<FriendlyByteBuf, WildPlateS2CPayload> STREAM_CODEC =
            StreamCodec.of(WildPlateS2CPayload::write, WildPlateS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public static void write(FriendlyByteBuf buf, WildPlateS2CPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeUtf(payload.name);
        buf.writeByte(payload.disposition);
    }

    public static WildPlateS2CPayload read(FriendlyByteBuf buf) {
        return new WildPlateS2CPayload(buf.readVarInt(), buf.readUtf(), buf.readByte());
    }

    /** Client side: records the plate until the mob leaves. */
    public static void accept(WildPlateS2CPayload payload) {
        PLATES.put(payload.entityId, payload);
    }

    /** Client side: the plate this entity shows, or null. */
    public static WildPlateS2CPayload of(int entityId) {
        return PLATES.get(entityId);
    }

    public static void forget(int entityId) {
        PLATES.remove(entityId);
    }

    public static void clear() {
        PLATES.clear();
    }
}
