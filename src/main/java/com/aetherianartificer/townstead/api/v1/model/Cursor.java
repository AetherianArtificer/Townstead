package com.aetherianartificer.townstead.api.v1.model;

/**
 * Position in a paged read. Opaque to consumers: start with {@link #START}, then pass a page's
 * {@code next} back. {@link #END} means nothing follows.
 */
public record Cursor(long value) {
    public static final Cursor START = new Cursor(0L);
    public static final Cursor END = new Cursor(-1L);

    public boolean isStart() {
        return value == 0L;
    }

    public boolean isEnd() {
        return value < 0L;
    }
}
