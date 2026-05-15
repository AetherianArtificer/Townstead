package com.aetherianartificer.townstead.reaction.effect;

import com.aetherianartificer.townstead.reaction.ParticleSpec;
import com.aetherianartificer.townstead.reaction.SoundSpec;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.Locale;
import java.util.Optional;

/**
 * Broadcasts sound and particle side-effects when a reaction binding
 * fires. Both vanilla helpers ({@code Level.playSound} and {@code
 * ServerLevel.sendParticles}) already replicate to tracking clients, so
 * no Townstead-specific payload is needed.
 */
public final class ReactionSideEffects {
    private ReactionSideEffects() {}

    public static void emit(ServerLevel level, LivingEntity villager,
            Optional<SoundSpec> sound, Optional<ParticleSpec> particles) {
        if (level == null || villager == null) return;
        sound.ifPresent(spec -> playSound(level, villager, spec));
        particles.ifPresent(spec -> playParticles(level, villager, spec));
    }

    private static void playSound(ServerLevel level, LivingEntity villager, SoundSpec spec) {
        ResourceLocation soundId = resolveSoundId(villager, spec);
        if (soundId == null) return;
        SoundEvent event = BuiltInRegistries.SOUND_EVENT.get(soundId);
        if (event == null) return;
        float pitch = spec.pitchMin();
        if (spec.pitchMax() > spec.pitchMin()) {
            pitch = spec.pitchMin() + level.getRandom().nextFloat() * (spec.pitchMax() - spec.pitchMin());
        }
        // For MCA voice sounds, fold in the villager's per-individual VOICE_TONE
        // pitch so each villager sounds like themselves rather than a generic
        // male/female sample.
        if (spec.mcaVoice().isPresent() && villager instanceof VillagerEntityMCA mca) {
            try {
                pitch *= mca.getVoicePitch();
            } catch (Throwable ignored) {}
        }
        level.playSound(null, villager.getX(), villager.getY(), villager.getZ(),
                event, SoundSource.NEUTRAL, Math.max(0.0F, spec.volume()), pitch);
    }

    /**
     * If the spec uses {@code mca_voice}, build {@code mca:villager.<gender>.<suffix>}
     * from the villager's genetics. Falls back to {@code spec.id} when the entity
     * isn't an MCA villager or genetics aren't available.
     */
    private static ResourceLocation resolveSoundId(LivingEntity entity, SoundSpec spec) {
        if (spec.mcaVoice().isPresent() && entity instanceof VillagerEntityMCA mca) {
            try {
                String gender = mca.getGenetics().getGender().binary().getDataName().toLowerCase(Locale.ROOT);
                String voiceId = "villager." + gender + "." + spec.mcaVoice().get();
                ResourceLocation parsed = ResourceLocation.tryParse("mca:" + voiceId);
                if (parsed != null) return parsed;
            } catch (Throwable ignored) {}
        }
        return spec.id().orElse(null);
    }

    private static void playParticles(ServerLevel level, LivingEntity villager, ParticleSpec spec) {
        ParticleOptions options = resolveParticle(spec.id());
        if (options == null) return;
        if (spec.count() <= 0) return;
        level.sendParticles(options,
                villager.getX(), villager.getY() + spec.yOffset(), villager.getZ(),
                spec.count(),
                spec.spreadX(), spec.spreadY(), spec.spreadZ(),
                0.0);
    }

    private static ParticleOptions resolveParticle(ResourceLocation id) {
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(id);
        if (type instanceof SimpleParticleType simple) return simple;
        return null;
    }
}
