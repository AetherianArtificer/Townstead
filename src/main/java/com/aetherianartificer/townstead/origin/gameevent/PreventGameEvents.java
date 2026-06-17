package com.aetherianartificer.townstead.origin.gameevent;

import com.aetherianartificer.townstead.origin.ExpressedGenes;
import com.aetherianartificer.townstead.origin.gene.types.PreventGameEventGeneType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Server-side check for {@code prevent_game_event} genes: does the entity suppress the
 * given game-event id? Called from the game-event emission mixin.
 */
public final class PreventGameEvents {

    private PreventGameEvents() {}

    public static boolean shouldPrevent(LivingEntity entity, @Nullable ResourceLocation eventId) {
        if (entity == null || eventId == null) return false;
        List<PreventGameEventGeneType.Instance> genes =
                ExpressedGenes.instancesOf(entity, PreventGameEventGeneType.Instance.class);
        for (PreventGameEventGeneType.Instance gene : genes) {
            if (gene.events().contains(eventId)) return true;
        }
        return false;
    }
}
