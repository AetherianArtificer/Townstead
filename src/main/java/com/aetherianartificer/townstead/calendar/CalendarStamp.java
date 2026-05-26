package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * One stamp placed on the shared world calendar. {@code year/monthIndex/dayOfMonth}
 * is the cell it was dropped over; {@code offX/offY} are its offset within that
 * cell in virtual (pre-UI-scale) coords. {@code textureId} is a resource-pack
 * texture path resolved on the placing client; {@code sourcePack} names the pack
 * so clients missing the art can say so. Private stamps (default) sync only to
 * the placer; public ones to everyone.
 */
public record CalendarStamp(
        UUID id,
        String textureId,
        String sourcePack,
        String caption,
        int year,
        int monthIndex,
        int dayOfMonth,
        float offX,
        float offY,
        UUID placedBy,
        boolean isPublic
) {
    public static void write(FriendlyByteBuf buf, CalendarStamp s) {
        buf.writeUUID(s.id);
        buf.writeUtf(s.textureId);
        buf.writeUtf(s.sourcePack);
        buf.writeUtf(s.caption);
        buf.writeVarInt(s.year);
        buf.writeVarInt(s.monthIndex);
        buf.writeVarInt(s.dayOfMonth);
        buf.writeFloat(s.offX);
        buf.writeFloat(s.offY);
        buf.writeUUID(s.placedBy);
        buf.writeBoolean(s.isPublic);
    }

    public static CalendarStamp read(FriendlyByteBuf buf) {
        UUID id = buf.readUUID();
        String textureId = buf.readUtf();
        String sourcePack = buf.readUtf();
        String caption = buf.readUtf();
        int year = buf.readVarInt();
        int monthIndex = buf.readVarInt();
        int dayOfMonth = buf.readVarInt();
        float offX = buf.readFloat();
        float offY = buf.readFloat();
        UUID placedBy = buf.readUUID();
        boolean isPublic = buf.readBoolean();
        return new CalendarStamp(id, textureId, sourcePack, caption,
                year, monthIndex, dayOfMonth, offX, offY, placedBy, isPublic);
    }

    /** A copy at a new anchor + offset (used when moving). */
    public CalendarStamp movedTo(int year, int monthIndex, int dayOfMonth, float offX, float offY) {
        return new CalendarStamp(id, textureId, sourcePack, caption,
                year, monthIndex, dayOfMonth, offX, offY, placedBy, isPublic);
    }

    /** A copy with new content + visibility (used when editing). */
    public CalendarStamp withContent(String textureId, String sourcePack, String caption, boolean isPublic) {
        return new CalendarStamp(id, textureId, sourcePack, caption,
                year, monthIndex, dayOfMonth, offX, offY, placedBy, isPublic);
    }
}
