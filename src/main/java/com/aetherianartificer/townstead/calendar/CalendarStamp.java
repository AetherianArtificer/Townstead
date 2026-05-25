package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/**
 * One stamp placed on the shared world calendar.
 *
 * <p>Day-aware free placement: {@code year}/{@code monthIndex}/{@code dayOfMonth}
 * are the day cell the stamp was dropped over (so it travels with the right
 * month and can be queried per-day), while {@code offX}/{@code offY} are the
 * pixel offset of the stamp's top-left from that cell's top-left, in the
 * calendar's virtual (pre-UI-scale) coordinate space — so a stamp lands in the
 * same spot regardless of window size or GUI scale.</p>
 *
 * <p>{@code textureId} is a fully-qualified texture path (e.g.
 * {@code townstead:textures/stamps/heart.png}) resolved from the placing
 * client's resource packs. {@code sourcePack} is the id of the pack that
 * provided that art at placement time (may be empty) — carried so a client
 * <em>without</em> the pack can tell the player which pack the missing art
 * comes from. The server never needs the art itself, only the strings.</p>
 *
 * <p>{@code isPublic} controls visibility: private stamps (the default) are only
 * sent to the player who placed them; public stamps are sent to everyone. The
 * server filters per-recipient, so a client never even receives others' private
 * stamps.</p>
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
