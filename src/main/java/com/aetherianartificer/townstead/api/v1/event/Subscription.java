package com.aetherianartificer.townstead.api.v1.event;

/** A live listener registration. Closing it is idempotent. */
public interface Subscription extends AutoCloseable {

    boolean isActive();

    @Override
    void close();
}
