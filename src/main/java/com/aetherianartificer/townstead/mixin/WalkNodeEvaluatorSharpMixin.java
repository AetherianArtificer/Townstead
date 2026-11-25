package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.mess.MessBridge;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Barefoot villagers keep off broken glass and anything else sharp that Bit of a Mess has lying on the ground: a step onto it
 * costs as much as vanilla's other dangers, less where there's only a little. They still cross it when there's no other way.
 * MCA's evaluator extends vanilla's, so this covers it. {@code m_164725_} is 1.20.1's name outside a development game.
 */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorSharpMixin extends NodeEvaluator {

    // What a step onto the sharpest ground costs, as vanilla's DANGER_OTHER does for a villager.
    @Unique
    private static final float TOWNSTEAD_SHARP_COST = 8f;

    @Inject(method = {"findAcceptedNode", "m_164725_"}, remap = false, at = @At("RETURN"))
    private void townstead$keepOffSharp(CallbackInfoReturnable<Node> cir) {
        Node node = cir.getReturnValue();
        if (node == null || node.costMalus < 0f || !MessBridge.active()) return;
        if (!(this.mob instanceof VillagerEntityMCA) || !(this.mob.level() instanceof ServerLevel level)) return;
        if (!this.mob.getItemBySlot(EquipmentSlot.FEET).isEmpty()) return;
        float sharp = MessBridge.sharpAt(level, new BlockPos(node.x, node.y - 1, node.z));
        if (sharp > 0f) node.costMalus += TOWNSTEAD_SHARP_COST * sharp;
    }
}
