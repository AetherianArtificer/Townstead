package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.TownsteadConfig;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmBlock.class)
public class FarmBlockMixin {
    @Inject(method = "fallOn", at = @At("HEAD"), cancellable = true)
    private void townstead$preventHarvestChoreTrample(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance, CallbackInfo ci) {
        if (!TownsteadConfig.ENABLE_FARM_ASSIST.get()) return;
        if (!(entity instanceof VillagerEntityMCA villager)) return;
        if (villager.getVillagerData().getProfession() == VillagerProfession.FARMER) {
            ci.cancel();
        }
    }
}
