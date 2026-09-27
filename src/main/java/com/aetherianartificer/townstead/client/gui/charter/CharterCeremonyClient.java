package com.aetherianartificer.townstead.client.gui.charter;

import com.aetherianartificer.townstead.client.accessibility.Accessibility;
import com.aetherianartificer.townstead.politics.charter.CharterCeremonyS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A proclamation, as each player sees it: what was proclaimed on screen, and a ring that runs out
 * from the bell across the settlement's ground. The words never depend on the motion.
 */
public final class CharterCeremonyClient {
    /** Blocks the ring travels per tick: a village of 60 blocks is crossed in about a second and a half. */
    private static final double SPEED = 1.25D;
    /** Bell brass: a band of gold dust at the wave front, with a few wax sparks rising from it. */
    private static final DustParticleOptions FRONT = new DustParticleOptions(new Vector3f(1.0F, 0.78F, 0.3F), 1.6F);
    private static final List<Wave> WAVES = new ArrayList<>();
    /** Blocks over which the ring drops from the bell's height to the ground. */
    private static final double SETTLE = 6.0D;
    /** How far above and below the bell a floor is looked for; roofs above that are passed over. */
    private static final int REACH_UP = 3, REACH_DOWN = 16;

    private record Wave(BlockPos bell, int radius, long start) {}

    private CharterCeremonyClient() {}

    public static void play(CharterCeremonyS2CPayload cue) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        minecraft.gui.setTitle(cue.title().component());
        minecraft.gui.setSubtitle(cue.subtitle().component());
        // Particles are not a screen distortion, so only reduced motion turns the wave off.
        if (Accessibility.isReduceMotion()) return;
        WAVES.add(new Wave(cue.bell(), cue.radius(), minecraft.level.getGameTime()));
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (WAVES.isEmpty()) return;
        if (minecraft.level == null || minecraft.isPaused()) {
            if (minecraft.level == null) WAVES.clear();
            return;
        }
        var level = minecraft.level;
        long now = level.getGameTime();
        WAVES.removeIf(wave -> {
            double radius = (now - wave.start()) * SPEED;
            if (radius > wave.radius()) return true;
            // Points about a block apart along the ring.
            int points = Math.max(12, (int) (radius * Math.PI * 2));
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0D * i / points + level.random.nextDouble() * 0.05D;
                double x = wave.bell().getX() + 0.5D + Math.cos(angle) * radius;
                double z = wave.bell().getZ() + 0.5D + Math.sin(angle) * radius;
                Integer floor = floor(level, (int) Math.floor(x), wave.bell().getY(), (int) Math.floor(z));
                if (floor == null) continue;
                // It leaves the bell at the bell's own height and settles onto the ground over the first blocks.
                double settle = Math.min(1.0D, radius / SETTLE);
                double y = (wave.bell().getY() + 0.5D) * (1.0D - settle) + (floor + 0.2D) * settle;
                y = Math.max(y, floor + 0.2D);
                level.addParticle(FRONT, x, y, z, 0.0D, 0.0D, 0.0D);
                level.addParticle(FRONT, x, y + 0.7D, z, 0.0D, 0.0D, 0.0D);
                if (level.random.nextInt(4) == 0) level.addParticle(ParticleTypes.WAX_ON, x, y + 0.2D, z, 0.0D, 0.06D, 0.0D);
            }
            return false;
        });
    }

    /**
     * The walking surface in this column nearest the bell's level: the first open block above solid
     * ground, searched from a little above the bell downward. A heightmap would put the ring on
     * rooftops; a village's streets are what it should run along.
     */
    private static Integer floor(net.minecraft.client.multiplayer.ClientLevel level, int x, int bellY, int z) {
        var pos = new BlockPos.MutableBlockPos(x, bellY + REACH_UP, z);
        if (!level.getBlockState(pos).isAir()) return null;
        for (int y = bellY + REACH_UP; y >= bellY - REACH_DOWN; y--) {
            pos.setY(y - 1);
            if (!level.getBlockState(pos).isAir()) return y;
        }
        return null;
    }
}
