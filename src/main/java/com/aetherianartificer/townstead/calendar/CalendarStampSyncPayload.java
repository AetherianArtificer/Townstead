package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server → client: the full visible stamp set, sent on login and after any change. */
//? if neoforge {
public record CalendarStampSyncPayload(List<CalendarStamp> stamps) implements CustomPacketPayload {
//?} else {
/*public record CalendarStampSyncPayload(List<CalendarStamp> stamps) {
*///?}

    //? if neoforge {
    public static final Type<CalendarStampSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "calendar_stamp_sync"));

    public static final StreamCodec<FriendlyByteBuf, CalendarStampSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), CalendarStampSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "calendar_stamp_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "calendar_stamp_sync");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(stamps.size());
        for (CalendarStamp s : stamps) {
            CalendarStamp.write(buf, s);
        }
    }

    public static CalendarStampSyncPayload read(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<CalendarStamp> stamps = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            stamps.add(CalendarStamp.read(buf));
        }
        return new CalendarStampSyncPayload(stamps);
    }
}
