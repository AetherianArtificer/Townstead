package net.minecraft.nbt;

import java.util.HashMap;
import java.util.Map;

/**
 * Minimal test stub for Minecraft's CompoundTag.
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

    public boolean getBoolean(String key) {
        Object v = data.get(key);
        return v instanceof Boolean b && b;
    }

    public void putBoolean(String key, boolean value) {
        data.put(key, value);
    }

    public float getFloat(String key) {
        Object v = data.get(key);
        return v instanceof Number n ? n.floatValue() : 0f;
    }

    public void putFloat(String key, float value) {
        data.put(key, value);
    }

    public String getString(String key) {
        Object v = data.get(key);
        return v instanceof String s ? s : "";
    }

    public void putString(String key, String value) {
        data.put(key, value);
    }

    public boolean contains(String key) {
        return data.containsKey(key);
    }

    public void remove(String key) {
        data.remove(key);
    }
}
