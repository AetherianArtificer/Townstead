package com.aetherianartificer.townstead.mixin.compat.mca;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.tasks.SmarterOpenDoorsTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SmarterOpenDoorsTask.class, remap = false)
public class VillagerOpenedDoorMixin {
    @Inject(method = "setOpen", at = @At("RETURN"))
    private static void townstead$rememberOpening(Entity entity, Level level, BlockState state, BlockPos pos,
                                                  boolean open, CallbackInfoReturnable<Boolean> cir) {
        if (open && cir.getReturnValueZ() && entity instanceof VillagerEntityMCA && level instanceof ServerLevel server)
            com.aetherianartificer.townstead.temperature.VillagerDoorCleanup.opened(server, pos);
    }
}
