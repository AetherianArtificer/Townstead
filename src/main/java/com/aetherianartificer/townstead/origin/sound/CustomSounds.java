package com.aetherianartificer.townstead.origin.sound;

import com.aetherianartificer.townstead.habitus.sound.SoundSpec;
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

    @Nullable
    public static SoundSpec.Entry pick(LivingEntity entity, Slot slot) {
        if (entity == null) return null;
        List<CustomSoundGeneType.Instance> genes =
                ExpressedGenes.instancesOf(entity, CustomSoundGeneType.Instance.class);
        for (CustomSoundGeneType.Instance gene : genes) {
            if (gene.slot() == slot) return gene.sound().pick(entity.getRandom());
        }
        return null;
    }
}
