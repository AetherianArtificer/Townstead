package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.types.StuckImmunityGeneType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Side-aware check for whether an entity's {@code stuck_immunity} genes let it move freely through a
 * given block (cobweb, sweet berry bush). On the server it reads the authoritative gene state
 * ({@link ExpressedGenes}); on the controlling client it reads the synced per-entity expressed-gene
 * set, so the local player predicts the same result and does not stick where the server won't slow it.
 *
 * <p>The client branch is only reached when {@code level().isClientSide}, so the client-only class it
 * calls is never linked on a dedicated server.</p>
 */
public final class StuckImmunity {

    private StuckImmunity() {}

    public static boolean covers(LivingEntity entity, BlockState state) {
        if (entity.level().isClientSide) {
            return com.aetherianartificer.townstead.client.origin.ClientStuckImmunity.covers(entity, state);
        }
        for (StuckImmunityGeneType.Instance immunity :
                ExpressedGenes.instancesOf(entity, StuckImmunityGeneType.Instance.class)) {
            for (ResourceLocation blockId : immunity.blocks()) {
                if (state.is(BuiltInRegistries.BLOCK.get(blockId))) return true;
            }
        }
        return false;
    }
}
