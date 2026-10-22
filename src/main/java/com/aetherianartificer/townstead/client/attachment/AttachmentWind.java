package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import org.joml.Vector3f;

/**
 * The air attachment physics streams in: one wind shared by everyone in the level,
 * derived from game time so every bearer (and every client) sees the same gusts. It
 * turns slowly, strengthens with rain, storms and the colder seasons, and fades with
 * sky light so a bearer indoors or underground stands in still air.
 */
public final class AttachmentWind {

    // Air speeds in blocks per second.
    private static final float CALM = 1.2f;
    private static final float RAIN = 1.6f;
    private static final float THUNDER = 2.4f;
    // How far a gust front travels along the wind before the next one, in blocks.
    private static final float GUST_SPACING = 7f;

    private AttachmentWind() {}

    /** The air velocity at a point, into {@code dest}. */
    public static Vector3f at(Level level, double x, double y, double z, float partialTick, Vector3f dest) {
        float exposure = level.getBrightness(LightLayer.SKY, BlockPos.containing(x, y + 1.0, z)) / 15f;
        if (exposure <= 0f) return dest.zero();
        float t = (level.getGameTime() + partialTick) / 20f;
        float heading = t * 0.004f + 1.7f * Mth.sin(t * 0.0013f) + 0.6f * Mth.sin(t * 0.0071f + 2.1f);
        float dirX = Mth.cos(heading), dirZ = Mth.sin(heading);
        // Gust fronts sweep along the wind, so neighbours feel the same gust in turn.
        float along = (float) (x * dirX + z * dirZ) / GUST_SPACING;
        float gust = 0.6f + 0.25f * Mth.sin(t * 0.9f - along + 1.7f * Mth.sin(t * 0.31f))
                + 0.15f * Mth.sin(t * 2.3f - along * 1.7f);
        float strength = (CALM + RAIN * level.getRainLevel(partialTick) + THUNDER * level.getThunderLevel(partialTick))
                * seasonFactor() * Math.max(0f, gust) * exposure * exposure;
        return dest.set(dirX * strength, 0f, dirZ * strength);
    }

    private static float seasonFactor() {
        CalendarClientStore.Snapshot calendar = CalendarClientStore.get();
        if (calendar == null || !calendar.hasSeason()) return 1f;
        String key = calendar.seasonKey();
        if (key.contains("winter")) return 1.3f;
        if (key.contains("autumn") || key.contains("fall")) return 1.2f;
        if (key.contains("summer")) return 0.8f;
        return 1f;
    }
}
