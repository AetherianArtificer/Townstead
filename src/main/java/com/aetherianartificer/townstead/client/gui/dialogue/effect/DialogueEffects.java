package com.aetherianartificer.townstead.client.gui.dialogue.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of dialogue text effects for expressing villager emotions.
 * Animations are slow and smooth for a cinematic RPG feel.
 */
public enum DialogueEffects implements DialogueEffect {
    NORMAL("Normal", null) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
        }
    },

    HAPPY("Happy", ParticleTypes.HEART) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            s.y += Mth.sin(t * 0.25f + i * 0.4f) * 0.8f;
            s.r = Mth.lerp(0.3f, s.r, 1.0f);
            s.g = Mth.lerp(0.3f, s.g, 0.84f);
            s.b = Mth.lerp(0.3f, s.b, 0.0f);
        }
    },

    ANGRY("Angry", ParticleTypes.SMOKE) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            s.x += Mth.sin(t * 1.7f + i * 3.7f) * 1.2f;
            s.y += Mth.sin(t * 1.3f + i * 5.3f) * 1.2f;
            s.r = Mth.lerp(0.5f, s.r, 1.0f);
            s.g = Mth.lerp(0.5f, s.g, 0.27f);
            s.b = Mth.lerp(0.5f, s.b, 0.27f);
            s.scale *= 1.05f;
        }
    },

    SAD("Sad", ParticleTypes.FALLING_WATER) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            float droop = (float) i / Math.max(1, total) * 1.2f;
            s.y += droop + Mth.sin(t * 0.08f + i * 0.2f) * 0.3f;
            s.r = Mth.lerp(0.4f, s.r, 0.53f);
            s.g = Mth.lerp(0.4f, s.g, 0.53f);
            s.b = Mth.lerp(0.4f, s.b, 0.8f);
        }
    },

    SCARED("Scared", null, 2.0f) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            s.x += Mth.sin(t * 2.5f + i * 7.1f) * 1.5f;
            s.y += Mth.sin(t * 2.1f + i * 5.9f) * 1.5f;
            s.scale *= 0.85f;
            s.r = Mth.lerp(0.2f, s.r, 0.9f);
            s.g = Mth.lerp(0.2f, s.g, 0.9f);
            s.b = Mth.lerp(0.2f, s.b, 0.95f);
        }
    },

    WHISPER("Whisper", null, 0.5f) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            s.scale *= 0.65f;
            s.a *= 0.50f;
            s.r = Mth.lerp(0.6f, s.r, 0.67f);
            s.g = Mth.lerp(0.6f, s.g, 0.67f);
            s.b = Mth.lerp(0.6f, s.b, 0.67f);
            s.y += Mth.sin(t * 0.1f + i * 0.5f) * 0.2f;
        }
    },

    YELL("Yell", null, 3.0f, 1.35f) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            s.scale *= 1.35f;
            s.x += Mth.sin(t * 0.8f + i * 2.3f) * 0.4f;
            float pulse = 0.85f + 0.15f * Mth.sin(t * 0.6f + i * 0.3f);
            s.r = Math.min(1.0f, s.r * pulse * 1.2f);
            s.g = Math.min(1.0f, s.g * pulse);
            s.b = Math.min(1.0f, s.b * pulse * 0.7f);
        }
    },

    FROZEN("Frozen", ParticleTypes.SNOWFLAKE) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            s.x += Mth.sin(t * 0.07f + i * 0.3f) * 0.3f;
            s.y += Mth.sin(t * 0.05f + i * 0.4f) * 0.3f;
            float blend = 0.5f + 0.5f * Mth.sin(t * 0.15f + i * 0.6f);
            s.r = Mth.lerp(0.5f, s.r, Mth.lerp(blend, 0.53f, 0.9f));
            s.g = Mth.lerp(0.5f, s.g, Mth.lerp(blend, 0.87f, 0.95f));
            s.b = Mth.lerp(0.5f, s.b, 1.0f);
        }
    },

    BURNING("Burning", ParticleTypes.FLAME) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            s.y -= Math.abs(Mth.sin(t * 0.3f + i * 0.5f)) * 1.5f;
            float blend = 0.5f + 0.5f * Mth.sin(t * 0.4f + i * 0.8f);
            s.r = Mth.lerp(0.6f, s.r, 1.0f);
            s.g = Mth.lerp(0.6f, s.g, Mth.lerp(blend, 0.1f, 0.4f));
            s.b = Mth.lerp(0.6f, s.b, 0.0f);
        }
    },

    NERVOUS("Nervous", null, 1.5f) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            s.x += Mth.sin(t * 1.2f + i * 4.1f) * 0.4f;
            s.y += Mth.sin(t * 1.0f + i * 3.3f) * 0.4f;
            s.scale *= 0.95f;
        }
    },

    FLIRTY("Flirty", ParticleTypes.HEART) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            s.y -= Math.abs(Mth.sin(t * 0.2f + i * 0.5f)) * 1.2f;
            s.r = Mth.lerp(0.35f, s.r, 1.0f);
            s.g = Mth.lerp(0.35f, s.g, 0.53f);
            s.b = Mth.lerp(0.35f, s.b, 0.67f);
        }
    },

    MYSTERIOUS("Mysterious", ParticleTypes.ENCHANT) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            float fade = 0.6f + 0.4f * Mth.sin(t * 0.15f + i * 0.3f);
            s.a *= fade;
            s.r = Mth.lerp(0.4f, s.r, 0.67f);
            s.g = Mth.lerp(0.4f, s.g, 0.4f);
            s.b = Mth.lerp(0.4f, s.b, 1.0f);
        }
    },

    DRUNK("Drunk", null) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            s.x += Mth.sin(t * 0.15f + i * 0.6f) * 1.5f;
            s.y += Mth.sin(t * 0.2f + i * 0.4f) * 1.0f;
            s.r = Mth.lerp(0.15f, s.r, 0.7f);
            s.g = Mth.lerp(0.15f, s.g, 0.85f);
            s.b = Mth.lerp(0.15f, s.b, 0.5f);
        }
    },

    SLEEPY("Sleepy", null, 0.4f) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            s.y += 0.6f + Mth.sin(t * 0.06f) * 0.4f;
            s.a *= 0.6f + 0.3f * Mth.sin(t * 0.08f + i * 0.1f);
            s.r = Mth.lerp(0.2f, s.r, 0.8f);
            s.g = Mth.lerp(0.2f, s.g, 0.8f);
            s.b = Mth.lerp(0.2f, s.b, 0.9f);
        }
    },

    EXCITED("Excited", ParticleTypes.NOTE, 2.5f, 1.08f) {
        @Override
        public void apply(CharRenderState s, int i, int total, float t) {
            s.y -= Math.abs(Mth.sin(t * 0.5f + i * 0.7f)) * 2.0f;
            s.scale *= 1.03f + 0.03f * Mth.sin(t * 0.4f);
            float hue = (t * 0.03f + i * 0.05f) % 1.0f;
            int rgb = Mth.hsvToRgb(hue, 0.4f, 1.0f);
            s.r = Mth.lerp(0.3f, s.r, ((rgb >> 16) & 0xFF) / 255.0f);
            s.g = Mth.lerp(0.3f, s.g, ((rgb >> 8) & 0xFF) / 255.0f);
            s.b = Mth.lerp(0.3f, s.b, (rgb & 0xFF) / 255.0f);
        }
    };

    private final String displayName;
    private final @Nullable SimpleParticleType particleType;
    private final float typewriterSpeed;
    private final float maxScale;

    DialogueEffects(String displayName, @Nullable SimpleParticleType particleType) {
        this(displayName, particleType, 1.0f, 1.0f);
    }

    DialogueEffects(String displayName, @Nullable SimpleParticleType particleType, float typewriterSpeed) {
        this(displayName, particleType, typewriterSpeed, 1.0f);
    }

    DialogueEffects(String displayName, @Nullable SimpleParticleType particleType, float typewriterSpeed, float maxScale) {
        this.displayName = displayName;
        this.particleType = particleType;
        this.typewriterSpeed = typewriterSpeed;
        this.maxScale = maxScale;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Particle type to spawn near the villager during this effect, or null for none. */
    @Nullable
    public SimpleParticleType getParticleType() {
        return particleType;
    }

    /** Typewriter speed multiplier. >1 = faster reveal, <1 = slower. */
    public float getTypewriterSpeed() {
        return typewriterSpeed;
    }

    /** Maximum scale factor this effect applies (for wrap width calculation). */
    public float getMaxScale() {
        return maxScale;
    }

    public static DialogueEffects[] all() {
        return values();
    }
}
