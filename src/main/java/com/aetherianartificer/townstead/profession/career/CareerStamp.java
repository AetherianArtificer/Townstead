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
public record CareerStamp(int x, int y, float rotation, String authority, String date,
                          String textureId, String sourcePack, String label) {

    /** Centre bounds of the pinned registry field, in record-panel coordinates. */
    public static final int MIN_X = 7;
    public static final int MAX_X = 183;
    public static final int MIN_Y = 5;
    public static final int MAX_Y = 36;

    /** Compatibility constructor for records written before selectable career stamp heads. */
    public CareerStamp(int x, int y, float rotation, String authority, String date) {
        this(x, y, rotation, authority, date, "", "", "");
    }

    public static CareerStamp sanitized(int x, int y, float rotation, String authority, String date) {
        return sanitized(x, y, rotation, authority, date, "", "", "");
    }

    public static CareerStamp sanitized(int x, int y, float rotation, String authority, String date,
                                        String textureId, String sourcePack, String label) {
        return new CareerStamp(
                Math.max(MIN_X, Math.min(MAX_X, x)),
                Math.max(MIN_Y, Math.min(MAX_Y, y)),
                Math.max(-0.6f, Math.min(0.6f, rotation)),
                authority == null ? "" : authority,
                date == null ? "" : date,
                sanitizeTexture(textureId),
                truncate(sourcePack, 80),
                truncate(label, 48));
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putFloat("rot", rotation);
        if (!authority.isEmpty()) tag.putString("authority", authority);
        if (!date.isEmpty()) tag.putString("date", date);
        if (!textureId.isEmpty()) tag.putString("texture", textureId);
        if (!sourcePack.isEmpty()) tag.putString("source_pack", sourcePack);
        if (!label.isEmpty()) tag.putString("label", label);
        return tag;
    }

    public static CareerStamp fromTag(CompoundTag tag) {
        return sanitized(tag.getInt("x"), tag.getInt("y"), tag.getFloat("rot"),
                tag.getString("authority"), tag.getString("date"),
                tag.getString("texture"), tag.getString("source_pack"), tag.getString("label"));
    }

    /** Career art is deliberately separate from the Calendar's unrestricted decorative stamps. */
    private static String sanitizeTexture(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        //? if >=1.21 {
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.tryParse(raw);
        //?} else {
        /*net.minecraft.resources.ResourceLocation id;
        try { id = new net.minecraft.resources.ResourceLocation(raw); }
        catch (Exception ex) { id = null; }
        *///?}
        if (id == null) return "";
        String path = id.getPath();
        return path.startsWith("textures/stamps/career/") && path.endsWith(".png")
                ? id.toString() : "";
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
