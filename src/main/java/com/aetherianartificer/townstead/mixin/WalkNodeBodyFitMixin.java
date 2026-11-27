package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.attachment.BodyFit;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Drops path steps a body's volume doesn't fit through. It targets vanilla's walking evaluator, which
 * MCA's extends, so villagers are covered. Parity: SRG name on Forge.
 */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeBodyFitMixin extends NodeEvaluator {

    //? if neoforge {
    @Inject(method = "getNeighbors", at = @At("RETURN"), cancellable = true)
    //?} else {
    /*@Inject(method = "m_6065_", remap = false, at = @At("RETURN"), cancellable = true)
    *///?}
    private void townstead$dropStepsThatDontFit(Node[] nodes, Node origin, CallbackInfoReturnable<Integer> cir) {
        int count = cir.getReturnValue();
        if (count <= 0) return;
        int kept = 0;
        for (int i = 0; i < count; i++) {
            Node candidate = nodes[i];
            if (candidate != null && BodyFit.canStep(this.mob, origin, candidate)) nodes[kept++] = candidate;
        }
        if (kept == count) return;
        for (int i = kept; i < count; i++) nodes[i] = null;
        cir.setReturnValue(kept);
    }
}
