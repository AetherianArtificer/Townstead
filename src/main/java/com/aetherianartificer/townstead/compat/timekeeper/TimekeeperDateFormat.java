package com.aetherianartificer.townstead.compat.timekeeper;

import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
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
 */
public final class TimekeeperDateFormat {
    private TimekeeperDateFormat() {}

    /**
     * Two locale-aware lines: date and year. Format args are positional
     * (%1$s = day, %2$s = month) so each lang file controls ordering
     * (en_us inverts to "Month Day"). Returns empty string if no snapshot is
     * available yet. The block splits on {@code "\n"}, so we resolve each
     * line to its localized String here on the client.
     */
    public static String tooltipString() {
        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null) return "";
        Component month = snap.monthComponent();
        if (month.getString().isEmpty()) {
            month = Component.literal("Month " + snap.monthIndex());
        }
        Component dateLine = Component.translatableWithFallback(
                "townstead.calendar.timekeeper.tooltip.date",
                "%1$s %2$s",
                snap.dayOfMonth(), month);
        Component yearLine = Component.translatableWithFallback(
                "townstead.calendar.timekeeper.tooltip.year",
                "Year %s",
                snap.year());
        return dateLine.getString() + "\n" + yearLine.getString();
    }

    /**
     * "{day} {month}, Year {year}" sent server-side as a translatable
     * component. Positional args (%1$s = day, %2$s = month, %3$s = year) so
     * en_us can reorder to "Month Day, Year".
     */
    public static Component chatComponent(MinecraftServer server) {
        CalendarDate today = TownsteadCalendar.today(server);
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        Component month;
        if (profile != null
                && today.monthIndex() >= 1
                && today.monthIndex() <= profile.months().size()) {
            month = profile.months().get(today.monthIndex() - 1).commonName();
        } else {
            month = Component.literal("Month " + today.monthIndex());
        }
        return Component.translatableWithFallback(
                "townstead.calendar.timekeeper.chat",
                "%1$s %2$s, Year %3$s",
                today.dayOfMonth(), month, today.year());
    }
}
