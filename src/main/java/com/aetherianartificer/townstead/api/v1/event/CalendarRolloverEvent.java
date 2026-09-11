package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.CalendarSnapshot;
import net.minecraft.server.MinecraftServer;

/**
 * The calendar advanced across a boundary. One event is posted per {@link Kind} that changed,
 * finest first, so a new year on a new season posts DAY, WEEK, MONTH, SEASON, YEAR in that order.
 * {@code daysAdvanced} is how many days passed since the previous tick, normally 1.
 */
public record CalendarRolloverEvent(
        MinecraftServer server,
        Kind kind,
        CalendarSnapshot before,
        CalendarSnapshot after,
        int daysAdvanced
) implements TownsteadEvent {

    public String kindId() {
        return kind.id();
    }

    public enum Kind {
        DAY("day"),
        WEEK("week"),
        MONTH("month"),
        SEASON("season"),
        YEAR("year");

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }
}
