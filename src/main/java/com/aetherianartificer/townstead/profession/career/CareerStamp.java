package com.aetherianartificer.townstead.profession.career;

import net.minecraft.nbt.CompoundTag;

/**
 * A mark the subject pressed onto their own record when they registered a skill.
 *
 * <p>Position is stored in PAGE space, relative to the record panel's top left, so a mark survives
 * a window resize or a change of GUI scale. Screen coordinates would drift the first time somebody
 * touched their settings.</p>
 *
 * <p>{@code authority} is the village's NAME as it read on the day, not a village id, and the same
 * goes for the date. A village that is later renamed, absorbed or abandoned must not silently
 * rewrite the marks pressed under its old name: a record that edits its own history is worth less
 * than one that is merely out of date. This is also why the field is a plain string rather than a
 * reference, and why it will keep working unchanged when the issuing authority becomes a faction
 * rather than a village.</p>
 */
public record CareerStamp(int x, int y, float rotation, String authority, String date) {

    /** How far a mark may sit from the panel's origin before it is refused as out of bounds. */
    public static final int MAX_X = 512;
    public static final int MAX_Y = 512;

    public static CareerStamp sanitized(int x, int y, float rotation, String authority, String date) {
        return new CareerStamp(
                Math.max(0, Math.min(MAX_X, x)),
                Math.max(0, Math.min(MAX_Y, y)),
                Math.max(-0.6f, Math.min(0.6f, rotation)),
                authority == null ? "" : authority,
                date == null ? "" : date);
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putFloat("rot", rotation);
        if (!authority.isEmpty()) tag.putString("authority", authority);
        if (!date.isEmpty()) tag.putString("date", date);
        return tag;
    }

    public static CareerStamp fromTag(CompoundTag tag) {
        return sanitized(tag.getInt("x"), tag.getInt("y"), tag.getFloat("rot"),
                tag.getString("authority"), tag.getString("date"));
    }
}
