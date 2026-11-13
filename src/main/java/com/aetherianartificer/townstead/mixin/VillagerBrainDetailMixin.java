package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.tick.VillagerDetail;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Far villagers tick their brain every few ticks. The call is skipped, never Brain.tick itself. */
@Mixin(Villager.class)
public abstract class VillagerBrainDetailMixin {

    //? if neoforge {
    @WrapWithCondition(method = "customServerAiStep",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"),
            expect = 1)
    //?} else {
    /*@WrapWithCondition(method = "m_8024_", remap = false,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;m_21865_(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"),
            expect = 1)
    *///?}
    private boolean townstead$farBrain(Brain<?> brain, ServerLevel level, LivingEntity entity) {
        return !(entity instanceof VillagerEntityMCA villager) || VillagerDetail.brainDue(villager);
    }
}
