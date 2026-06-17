package com.aetherianartificer.townstead.origin.sound;

import com.aetherianartificer.townstead.pheno.sound.SoundSpec;
import com.aetherianartificer.townstead.origin.ExpressedGenes;
import com.aetherianartificer.townstead.origin.gene.types.CustomSoundGeneType;
import com.aetherianartificer.townstead.origin.gene.types.CustomSoundGeneType.Slot;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Resolves an entity's {@code custom_sound} gene for a given {@link Slot}, picking a
 * sound from the gene's {@link SoundSpec}. Called by the sound mixins (which run on
 * both sides, so the result is consistent for everyone hearing it).
 */
public final class CustomSounds {

    private CustomSounds() {}

    /**
     * If the entity has a custom sound for this slot, maybe play it (gated by the sound's {@code
     * chance}, so a species can rattle only now and then) and return true so the caller suppresses
     * MCA's own sound. The play uses the sound's authored volume and pitch, the latter multiplying
     * MCA's per-villager {@code getVoicePitch} so each villager keeps a distinct voice. Doing the
     * {@code playSound} here, in plain code, keeps every vanilla call out of the mixin (which the
     * no-refmap 1.20.1 Forge build can't remap).
     */
    public static boolean handleCustom(LivingEntity entity, Slot slot) {
        SoundSpec.Entry entry = pick(entity, slot);
        if (entry == null) return false;
        if (entity.getRandom().nextFloat() < entry.chance()) {
            entity.playSound(entry.sound(), entry.volume(), entry.pitch() * entity.getVoicePitch());
        }
        return true;
    }

    @Nullable
    public static SoundSpec.Entry pick(LivingEntity entity, Slot slot) {
        if (entity == null) return null;
        List<CustomSoundGeneType.Instance> genes =
                ExpressedGenes.instancesOf(entity, CustomSoundGeneType.Instance.class);
        for (CustomSoundGeneType.Instance gene : genes) {
            SoundSpec spec = gene.sounds().get(slot);
            if (spec != null) return spec.pick(entity.getRandom());
        }
        return null;
    }
}
