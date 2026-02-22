package net.minecraft.nbt;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal test stub for Minecraft's CompoundTag.
 * Supports getInt/putInt and getLong/putLong used by progress data classes.
 */
public class CompoundTag {
    private final Map<String, Object> data = new HashMap<>();

    public int getInt(String key) {
        Object v = data.get(key);
        return v instanceof Number n ? n.intValue() : 0;
    }

    public void putInt(String key, int value) {
        data.put(key, value);
    }

    public long getLong(String key) {
        Object v = data.get(key);
        return v instanceof Number n ? n.longValue() : 0L;
    }

    public void putLong(String key, long value) {
        data.put(key, value);
    }
}
