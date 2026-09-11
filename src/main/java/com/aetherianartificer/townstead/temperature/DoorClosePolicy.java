package com.aetherianartificer.townstead.temperature;

/** Completion of a villager's opening action, independent of its current navigation task. */
public final class DoorClosePolicy {
    private DoorClosePolicy() {}
    public enum Decision { WAIT, CLOSE, FORGET }
    public static Decision decide(long age, boolean loaded, boolean open, boolean powered, boolean passageInUse) {
        if (age > 1200 || loaded && (!open || powered)) return Decision.FORGET;
        if (!loaded || age < 20 || passageInUse) return Decision.WAIT;
        return Decision.CLOSE;
    }
}
