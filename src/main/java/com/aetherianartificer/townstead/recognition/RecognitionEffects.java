package com.aetherianartificer.townstead.recognition;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Shared audio-visual feedback for milestones across Townstead. Three intensity
 * tiers (MINOR / MAJOR / GRAND) define the feel; two shapes choose where the
 * effect lands:
 *
 *   {@link #play}       — point effect (villager, item, specific block).
 *                         Best for personal acknowledgments.
 *   {@link #playArea}   — structure-scale effect. Particles spread across the
 *                         bounds with corner columns, so the whole shape reads
 *                         as "lit up" rather than a puff at the centroid.
 *
 * Plus {@link #announce} to broadcast a one-line action-bar message to players
 * inside a radius — use alongside a tier-up effect so the event is legible
 * without being in perfect view of the structure.
 *
 * All effects are pure vanilla particles + sounds + chat; no custom payloads.
 */
public final class RecognitionEffects {
    public enum Tier { MINOR, MAJOR, GRAND }

    private RecognitionEffects() {}

    // ── Point-based ────────────────────────────────────────────────────────

    public static void play(ServerLevel level, Vec3 center, Tier tier) {
        if (level == null || center == null || tier == null) return;
        switch (tier) {
            case MINOR -> playMinorPoint(level, center);
            case MAJOR -> playMajorPoint(level, center);
            case GRAND -> playGrandPoint(level, center);
        }
    }

    private static void playMinorPoint(ServerLevel level, Vec3 c) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                c.x, c.y + 0.6, c.z, 10, 0.3, 0.3, 0.3, 0.1);
        level.playSound(null, c.x, c.y, c.z,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.5f, 1.2f);
    }

    private static void playMajorPoint(ServerLevel level, Vec3 c) {
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                c.x, c.y + 1.0, c.z, 30, 0.8, 1.0, 0.8, 0.15);
        for (int i = 0; i < 5; i++) {
            level.sendParticles(ParticleTypes.END_ROD,
                    c.x, c.y + 0.5 + i, c.z, 4, 0.15, 0.15, 0.15, 0.02);
        }
        level.playSound(null, c.x, c.y, c.z,
                SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.8f, 1.0f);
    }

    private static void playGrandPoint(ServerLevel level, Vec3 c) {
        level.sendParticles(ParticleTypes.FIREWORK,
                c.x, c.y + 2.0, c.z, 60, 1.5, 1.5, 1.5, 0.2);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                c.x, c.y + 1.0, c.z, 50, 1.0, 1.0, 1.0, 0.2);
        for (int i = 0; i < 8; i++) {
            level.sendParticles(ParticleTypes.END_ROD,
                    c.x, c.y + 0.5 + i * 0.8, c.z, 6, 0.2, 0.2, 0.2, 0.03);
        }
        level.playSound(null, c.x, c.y, c.z,
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 1.0f, 1.0f);
        level.playSound(null, c.x, c.y, c.z,
                SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.9f, 0.8f);
    }

    // ── Area-based ─────────────────────────────────────────────────────────

    /**
     * Structure-scale celebration. Scatters a cloud of happy-villager particles
     * across the full horizontal span of {@code bounds}, plus a column of end-
     * rod particles rising from each corner, so the whole footprint lights up
     * instead of puffing at a single centroid. GRAND adds firework bursts atop
     * each corner column.
     *
     * The particle-count offsets argument to {@link ServerLevel#sendParticles}
     * is a random spread range, so we issue one horizontal-scatter call sized
     * to the bounds rather than looping — cheaper on packets, easier to read.
     */
    public static void playArea(ServerLevel level, BoundingBox bounds, Tier tier) {
        if (level == null || bounds == null || tier == null) return;

        double cx = (bounds.minX() + bounds.maxX() + 1) / 2.0;
        double cz = (bounds.minZ() + bounds.maxZ() + 1) / 2.0;
        // Deck sits one block above bounds.minY() (computeBounds shifts minY-1
        // for margin). Hover particles just above deck level so they sit over
        // the structure instead of inside it.
        double hoverY = bounds.minY() + 2.0;
        double xHalfSpan = (bounds.maxX() - bounds.minX() + 1) / 2.0;
        double zHalfSpan = (bounds.maxZ() - bounds.minZ() + 1) / 2.0;

        int burstCount = switch (tier) {
            case MINOR -> 20;
            case MAJOR -> 60;
            case GRAND -> 120;
        };
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                cx, hoverY, cz, burstCount,
                xHalfSpan, 0.6, zHalfSpan, 0.08);

        int columnHeight = switch (tier) {
            case MINOR -> 3;
            case MAJOR -> 5;
            case GRAND -> 8;
        };
        int columnDensity = tier == Tier.GRAND ? 4 : 3;
        int[][] corners = {
                {bounds.minX(), bounds.minZ()},
                {bounds.maxX(), bounds.minZ()},
                {bounds.minX(), bounds.maxZ()},
                {bounds.maxX(), bounds.maxZ()}
        };
        double columnBaseY = bounds.minY() + 1.0;
        double columnCenterY = columnBaseY + columnHeight / 2.0;
        for (int[] c : corners) {
            level.sendParticles(ParticleTypes.END_ROD,
                    c[0] + 0.5, columnCenterY, c[1] + 0.5,
                    columnHeight * columnDensity,
                    0.15, columnHeight / 2.0, 0.15, 0.03);
        }

        if (tier == Tier.GRAND) {
            double burstY = columnBaseY + columnHeight + 0.5;
            for (int[] c : corners) {
                level.sendParticles(ParticleTypes.FIREWORK,
                        c[0] + 0.5, burstY, c[1] + 0.5,
                        25, 0.5, 0.5, 0.5, 0.2);
            }
        }

        switch (tier) {
            case MINOR -> level.playSound(null, cx, hoverY, cz,
                    SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, 0.5f, 1.2f);
            case MAJOR -> level.playSound(null, cx, hoverY, cz,
                    SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.9f, 1.0f);
            case GRAND -> {
                level.playSound(null, cx, hoverY, cz,
                        SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.NEUTRAL, 1.0f, 1.0f);
                level.playSound(null, cx, hoverY, cz,
                        SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 1.0f, 0.85f);
            }
        }
    }

    // ── Action-bar message ────────────────────────────────────────────────

    /**
     * Broadcast an action-bar message to players within {@code radius} of
     * {@code center}. Pairs with {@code playArea} so tier-ups are legible even
     * to a player who isn't looking directly at the structure.
     */
    public static void announce(ServerLevel level, Vec3 center, Component message, double radius) {
        if (level == null || center == null || message == null) return;
        double r2 = radius * radius;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - center.x;
            double dy = player.getY() - center.y;
            double dz = player.getZ() - center.z;
            if (dx * dx + dy * dy + dz * dz > r2) continue;
            // true = overlay/action bar, not chat, so the message fades and
            // doesn't clutter the log.
            player.displayClientMessage(message, true);
        }
    }
}
