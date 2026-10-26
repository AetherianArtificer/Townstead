package com.aetherianartificer.townstead.chronicle.model;

import java.util.Map;
import java.util.Objects;

/**
 * A story thread grouping related events (a founding era, an expedition, a
 * feud). Events reference arcs by id; arcs carry their own lifecycle.
 */
public record Arc(long arcId, String type, int villageId, int status,
                  long startDay, long endDay, Map<String, String> params) {

    public static final int STATUS_OPEN = 0;
    public static final int STATUS_CLOSED = 1;

    public Arc {
        Objects.requireNonNull(type, "type");
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public Arc closed(long day) {
        return new Arc(arcId, type, villageId, STATUS_CLOSED, startDay, day, params);
    }
}
