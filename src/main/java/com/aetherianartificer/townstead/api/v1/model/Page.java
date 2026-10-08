package com.aetherianartificer.townstead.api.v1.model;

import java.util.List;

/** One page of a paged read. Pass {@link #next} back as the cursor to continue. */
public record Page<T>(List<T> items, boolean hasMore, Cursor next) {
    public Page {
        items = items == null ? List.of() : List.copyOf(items);
        next = next == null ? Cursor.END : next;
    }

    public static <T> Page<T> empty() {
        return new Page<>(List.of(), false, Cursor.END);
    }
}
