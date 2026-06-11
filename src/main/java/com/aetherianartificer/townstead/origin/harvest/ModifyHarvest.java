package com.aetherianartificer.townstead.origin.harvest;

import com.aetherianartificer.townstead.origin.ExpressedGenes;
import com.aetherianartificer.townstead.origin.gene.types.ModifyHarvestGeneType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Server-side check for {@code modify_harvest} genes: may the player harvest this block
 * by gene grant? Called from the harvest-check event.
 */
public final class ModifyHarvest {

    private ModifyHarvest() {}

    public static boolean allows(Player player, BlockState state) {
        if (player == null) return false;
        List<ModifyHarvestGeneType.Instance> genes =
                ExpressedGenes.instancesOf(player, ModifyHarvestGeneType.Instance.class);
        for (ModifyHarvestGeneType.Instance gene : genes) {
            if (gene.allow() && gene.matches(state)) return true;
        }
        return false;
    }
}
