package com.aetherianartificer.townstead.quest;

/** Townstead's own tracker, used for providers whose quests have no tracker of their own. */
public interface LocalTracker {
    LocalTracker NONE = new LocalTracker() {
        @Override public boolean isTracked(String key) { return false; }
        @Override public boolean toggle(String key) { return false; }
    };

    boolean isTracked(String key);

    /** Returns whether the quest is tracked afterward. */
    boolean toggle(String key);
}
