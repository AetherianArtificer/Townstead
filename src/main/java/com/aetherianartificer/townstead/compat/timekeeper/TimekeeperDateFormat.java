package com.aetherianartificer.townstead.compat.timekeeper;

import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import com.aetherianartificer.townstead.calendar.CalendarDateFormatter;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Shared date formatting for Timekeeper-block UI interception. Two surfaces:
 *
 * <ul>
 *   <li>{@link #tooltipString()} — client-side, called from
 *     {@code Return*TimeProcedure.execute} which returns a {@code String} that
 *     the block splits on {@code "\n"} and adds as literal tooltip lines.</li>
 *   <li>{@link #chatComponent(MinecraftServer)} — server-side, called from
 *     {@code QueryProc*Procedure.execute} which displays a Component to the
 *     right-clicking player.</li>
 * </ul>
 *
 * Both delegate to {@link CalendarDateFormatter} so date order is
 * locale-controlled via lang positional args.
 */
public final class TimekeeperDateFormat {
    private TimekeeperDateFormat() {}

    /**
     * Two lines for the calendar-block tooltip: full date on top, raw year
     * below. Each line is resolved to its localized form here on the client
     * since the block splits on {@code "\n"}.
     */
    public static String tooltipString() {
        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null) return "";
        Component date = CalendarDateFormatter.formatClient(snap,
                CalendarDateFormatter.Style.MEDIUM,
                snap.year(), snap.monthIndex(), snap.dayOfMonth(), snap.dayOfWeek());
        Component yearLine = Component.translatableWithFallback(
                "townstead.calendar.timekeeper.tooltip.year",
                "Year %s",
                snap.year());
        return date.getString() + "\n" + yearLine.getString();
    }

    /** Date Component for the calendar-block right-click chat. */
    public static Component chatComponent(MinecraftServer server) {
        return CalendarDateFormatter.formatToday(server, CalendarDateFormatter.Style.MEDIUM);
    }
}
