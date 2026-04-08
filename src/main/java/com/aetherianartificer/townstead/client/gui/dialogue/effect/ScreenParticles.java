package com.aetherianartificer.townstead.client.gui.dialogue.effect;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Lightweight screen-space particle system for dialogue text effects.
 * Two modes: trail (follows typewriter cursor) and spread (covers a region).
 * Transitions from trail → spread when the typewriter completes.
 */
public class ScreenParticles {
    private static final int MAX_PARTICLES = 80;
    private static final Random RANDOM = new Random();

    private final List<Particle> particles = new ArrayList<>();
    private DialogueEffects activeEffect;

    // Trail mode: single emit point following the typewriter cursor
    private int emitX, emitY;

    // Spread mode: emit across the tagged region
    private boolean spreadMode;
    private int regionX, regionY, regionWidth, regionHeight;

    private boolean emitting;

    private record Particle(float x, float y, float vx, float vy,
                            float r, float g, float b, float startAlpha,
                            int maxLife, int age, float size) {
        Particle aged() {
            return new Particle(x + vx, y + vy, vx * 0.96f, vy * 0.96f,
                    r, g, b, startAlpha, maxLife, age + 1, size);
        }

        float alpha() {
            float life = 1.0f - (float) age / maxLife;
            return startAlpha * life * life;
        }

        boolean isDead() {
            return age >= maxLife;
        }
    }

    public void setEffect(DialogueEffects effect) {
        this.activeEffect = effect;
        if (effect == null || effect == DialogueEffects.NORMAL) {
            emitting = false;
        }
    }

    /** Trail mode: set the typewriter cursor position as the single emit point. */
    public void setEmitPosition(int x, int y) {
        this.emitX = x;
        this.emitY = y;
        this.emitting = activeEffect != null && activeEffect != DialogueEffects.NORMAL
                && activeEffect.getParticleType() != null;
    }

    /**
     * Switch to spread mode: particles emit across the tagged text region.
     * Call this when the typewriter finishes.
     */
    public void setSpreadRegion(int x, int y, int width, int height) {
        this.regionX = x;
        this.regionY = y;
        this.regionWidth = Math.max(1, width);
        this.regionHeight = Math.max(1, height);
        this.spreadMode = true;
    }

    public boolean isSpreadMode() {
        return spreadMode;
    }

    public void tick() {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            if (it.next().isDead()) it.remove();
        }
        for (int i = 0; i < particles.size(); i++) {
            particles.set(i, particles.get(i).aged());
        }

        if (emitting && com.aetherianartificer.townstead.client.gui.dialogue.DialogueAccessibility.particlesEnabled()
                && particles.size() < MAX_PARTICLES) {
            int count = spreadMode ? 3 : 2;
            for (int i = 0; i < count; i++) {
                particles.add(createParticle());
            }
        }
    }

    public void render(GuiGraphics graphics) {
        for (Particle p : particles) {
            float a = p.alpha();
            if (a < 0.02f) continue;
            int color = ((int) (a * 255) << 24)
                    | ((int) (p.r * 255) << 16)
                    | ((int) (p.g * 255) << 8)
                    | (int) (p.b * 255);
            int s = Math.max(1, (int) p.size);
            int px = (int) p.x;
            int py = (int) p.y;
            graphics.fill(px, py, px + s, py + s, color);
        }
    }

    public void clear() {
        particles.clear();
        emitting = false;
        activeEffect = null;
        spreadMode = false;
    }

    private Particle createParticle() {
        float[] rgb = getParticleColor();
        float vx = (RANDOM.nextFloat() - 0.5f) * 1.2f;
        float vy = getParticleVelocityY();
        int life = 20 + RANDOM.nextInt(30);
        float size = 1.0f + RANDOM.nextFloat() * 1.5f;
        float alpha = 0.6f + RANDOM.nextFloat() * 0.4f;

        float spawnX, spawnY;
        if (spreadMode) {
            // Emit from random position within the tagged region
            spawnX = regionX + RANDOM.nextFloat() * regionWidth;
            spawnY = regionY + RANDOM.nextFloat() * regionHeight;
            // Gentler velocity in spread mode
            vx *= 0.6f;
            life += 10;
            alpha *= 0.8f;
        } else {
            // Trail mode: emit near the cursor
            spawnX = emitX + (RANDOM.nextFloat() - 0.5f) * 8;
            spawnY = emitY + (RANDOM.nextFloat() - 0.5f) * 4;
        }

        return new Particle(spawnX, spawnY, vx, vy,
                rgb[0], rgb[1], rgb[2], alpha, life, 0, size);
    }

    private float getParticleVelocityY() {
        if (activeEffect == null) return -0.3f;
        return switch (activeEffect) {
            case BURNING -> -(0.4f + RANDOM.nextFloat() * 0.6f);
            case FROZEN -> 0.1f + RANDOM.nextFloat() * 0.2f;
            case SAD -> 0.2f + RANDOM.nextFloat() * 0.3f;
            default -> -(0.2f + RANDOM.nextFloat() * 0.3f);
        };
    }

    private float[] getParticleColor() {
        if (activeEffect == null) return new float[]{1, 1, 1};
        return switch (activeEffect) {
            case HAPPY -> new float[]{1.0f, 0.85f + r(0.15f), 0.3f + r(0.3f)};
            case ANGRY -> new float[]{1.0f, 0.1f + r(0.2f), 0.0f};
            case SAD -> new float[]{0.4f + r(0.2f), 0.5f + r(0.2f), 0.9f + r(0.1f)};
            case SCARED -> new float[]{0.8f + r(0.2f), 0.8f + r(0.2f), 0.9f + r(0.1f)};
            case FROZEN -> new float[]{0.7f + r(0.3f), 0.9f + r(0.1f), 1.0f};
            case BURNING -> new float[]{1.0f, 0.3f + r(0.5f), 0.0f};
            case FLIRTY -> new float[]{1.0f, 0.4f + r(0.3f), 0.6f + r(0.2f)};
            case MYSTERIOUS -> new float[]{0.6f + r(0.2f), 0.3f + r(0.2f), 1.0f};
            case EXCITED -> {
                float hue = RANDOM.nextFloat();
                int rgb = Mth.hsvToRgb(hue, 0.7f, 1.0f);
                yield new float[]{
                        ((rgb >> 16) & 0xFF) / 255f,
                        ((rgb >> 8) & 0xFF) / 255f,
                        (rgb & 0xFF) / 255f
                };
            }
            default -> new float[]{1, 1, 1};
        };
    }

    private float r(float range) {
        return RANDOM.nextFloat() * range;
    }
}
