package com.aetherianartificer.townstead.origin.sound;

import com.aetherianartificer.townstead.origin.ExpressedGenes;
import com.aetherianartificer.townstead.origin.gene.types.PreventSoundGeneType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-side check for {@code prevent_sound} genes: does any expressed gene on the entity
 * suppress the given sound id? Called from the level-sound event listener.
 */
public final class PreventSounds {

    private PreventSounds() {}

    public static boolean shouldPrevent(LivingEntity entity, @Nullable ResourceLocation soundId) {
        if (entity == null || soundId == null) return false;
        List<PreventSoundGeneType.Instance> genes =
                ExpressedGenes.instancesOf(entity, PreventSoundGeneType.Instance.class);
        for (PreventSoundGeneType.Instance gene : genes) {
            if (gene.sounds().contains(soundId)) return true;
        }
        return false;
    }
}
