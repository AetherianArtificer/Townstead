package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client → server: a stamp mutation, one packet for all four verbs. The server
 * assigns id + {@code placedBy} on PLACE; MOVE/EDIT/REMOVE validate placer-or-op.
 * EDIT with an empty texture keeps the current art (caption/visibility-only edit).
 */
//? if neoforge {
public record CalendarStampActionC2SPayload(
        int action, UUID id, String textureId, String sourcePack, String caption,
        int year, int monthIndex, int dayOfMonth, float offX, float offY, boolean isPublic
) implements CustomPacketPayload {
//?} else {
/*public record CalendarStampActionC2SPayload(
        int action, UUID id, String textureId, String sourcePack, String caption,
        int year, int monthIndex, int dayOfMonth, float offX, float offY, boolean isPublic
) {
*///?}

    public static final int ACTION_PLACE = 0;
    public static final int ACTION_MOVE = 1;
    public static final int ACTION_EDIT = 2;
    public static final int ACTION_REMOVE = 3;

    /** Placeholder id for PLACE (server assigns the real one). */
    public static final UUID NIL = new UUID(0L, 0L);

    public static CalendarStampActionC2SPayload place(String textureId, String sourcePack, String caption,
                                                      int year, int monthIndex, int dayOfMonth,
                                                      float offX, float offY, boolean isPublic) {
        return new CalendarStampActionC2SPayload(ACTION_PLACE, NIL, textureId, sourcePack, caption,
                year, monthIndex, dayOfMonth, offX, offY, isPublic);
    }

    public static CalendarStampActionC2SPayload move(UUID id, int year, int monthIndex, int dayOfMonth,
                                                     float offX, float offY) {
        return new CalendarStampActionC2SPayload(ACTION_MOVE, id, "", "", "",
                year, monthIndex, dayOfMonth, offX, offY, false);
    }

    public static CalendarStampActionC2SPayload edit(UUID id, String textureId, String sourcePack,
                                                     String caption, boolean isPublic) {
        return new CalendarStampActionC2SPayload(ACTION_EDIT, id, textureId, sourcePack, caption,
                0, 0, 0, 0f, 0f, isPublic);
    }

    public static CalendarStampActionC2SPayload remove(UUID id) {
        return new CalendarStampActionC2SPayload(ACTION_REMOVE, id, "", "", "", 0, 0, 0, 0f, 0f, false);
    }

    //? if neoforge {
    public static final Type<CalendarStampActionC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "calendar_stamp_action"));

    public static final StreamCodec<FriendlyByteBuf, CalendarStampActionC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), CalendarStampActionC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "calendar_stamp_action");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "calendar_stamp_action");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
        buf.writeUUID(id);
        buf.writeUtf(textureId);
        buf.writeUtf(sourcePack);
        buf.writeUtf(caption);
        buf.writeVarInt(year);
        buf.writeVarInt(monthIndex);
        buf.writeVarInt(dayOfMonth);
        buf.writeFloat(offX);
        buf.writeFloat(offY);
        buf.writeBoolean(isPublic);
    }

    public static CalendarStampActionC2SPayload read(FriendlyByteBuf buf) {
        int action = buf.readVarInt();
        UUID id = buf.readUUID();
        String textureId = buf.readUtf();
        String sourcePack = buf.readUtf();
        String caption = buf.readUtf();
        int year = buf.readVarInt();
        int monthIndex = buf.readVarInt();
        int dayOfMonth = buf.readVarInt();
        float offX = buf.readFloat();
        float offY = buf.readFloat();
        boolean isPublic = buf.readBoolean();
        return new CalendarStampActionC2SPayload(action, id, textureId, sourcePack, caption,
                year, monthIndex, dayOfMonth, offX, offY, isPublic);
    }
}
