package com.aetherianartificer.townstead.calendar;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.UUID;

/**
 * Server-side apply logic for calendar stamp actions, shared by both the
 * NeoForge and Forge network handlers. Enforces the placer-or-operator
 * permission rule on move/edit/remove; placement is open to anyone.
 */
public final class CalendarStampServer {

    private CalendarStampServer() {}

    /**
     * Apply a stamp action from {@code sp}. Returns true if the stamp set
     * changed (the caller is responsible for broadcasting the new snapshot).
     */
    public static boolean apply(ServerPlayer sp, CalendarStampActionC2SPayload p) {
        MinecraftServer server = sp.getServer();
        if (server == null) return false;
        CalendarStampSavedData data = CalendarStampSavedData.get(server);

        switch (p.action()) {
            case CalendarStampActionC2SPayload.ACTION_PLACE: {
                String tex = p.textureId();
                if (tex == null || tex.isBlank()) return false;
                CalendarStamp s = new CalendarStamp(
                        UUID.randomUUID(), tex, nonNull(p.sourcePack()), sanitize(p.caption()),
                        p.year(), p.monthIndex(), p.dayOfMonth(), p.offX(), p.offY(), sp.getUUID(), p.isPublic());
                return data.add(s);
            }
            case CalendarStampActionC2SPayload.ACTION_MOVE: {
                CalendarStamp s = data.get(p.id());
                if (s == null || !canEdit(sp, s)) return false;
                data.replace(s.movedTo(p.year(), p.monthIndex(), p.dayOfMonth(), p.offX(), p.offY()));
                return true;
            }
            case CalendarStampActionC2SPayload.ACTION_EDIT: {
                CalendarStamp s = data.get(p.id());
                if (s == null || !canEdit(sp, s)) return false;
                boolean texProvided = p.textureId() != null && !p.textureId().isBlank();
                String tex = texProvided ? p.textureId() : s.textureId();
                // Source pack follows the art: keep the old one for caption-only edits.
                String src = texProvided ? nonNull(p.sourcePack()) : s.sourcePack();
                data.replace(s.withContent(tex, src, sanitize(p.caption()), p.isPublic()));
                return true;
            }
            case CalendarStampActionC2SPayload.ACTION_REMOVE: {
                CalendarStamp s = data.get(p.id());
                if (s == null || !canEdit(sp, s)) return false;
                return data.remove(p.id());
            }
            default:
                return false;
        }
    }

    /**
     * The stamps {@code viewer} is allowed to see: every public stamp plus their
     * own private ones. Private stamps of other players are never sent.
     */
    public static CalendarStampSyncPayload snapshotFor(MinecraftServer server, ServerPlayer viewer) {
        java.util.UUID id = viewer.getUUID();
        ArrayList<CalendarStamp> visible = new ArrayList<>();
        for (CalendarStamp s : CalendarStampSavedData.get(server).all()) {
            if (s.isPublic() || s.placedBy().equals(id)) visible.add(s);
        }
        return new CalendarStampSyncPayload(visible);
    }

    private static boolean canEdit(ServerPlayer sp, CalendarStamp s) {
        return sp.getUUID().equals(s.placedBy()) || sp.hasPermissions(2);
    }

    private static String nonNull(String s) {
        return s == null ? "" : s;
    }

    private static String sanitize(String caption) {
        if (caption == null) return "";
        String c = caption;
        if (c.length() > CalendarStampSavedData.MAX_CAPTION_LEN) {
            c = c.substring(0, CalendarStampSavedData.MAX_CAPTION_LEN);
        }
        return c;
    }
}
