package com.aetherianartificer.townstead.mixin.compat.vampirism;

import com.aetherianartificer.townstead.compat.vampirism.ConvertedVillagers;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every villager a Vampirism totem spawns passes through this factory. Those spawns are events,
 * which MCA leaves vanilla, so they are marked here for {@link ConvertedVillagers} to replace.
 */
@Pseudo
@Mixin(targets = "de.teamlapen.vampirism.util.VampirismEventFactory", remap = false)
public abstract class TotemVillagerSpawnMixin {
    @Inject(method = "fireSpawnNewVillagerEvent", at = @At("RETURN"), require = 0)
    private static void townstead$markTotemVillager(CallbackInfoReturnable<Villager> cir) {
        ConvertedVillagers.markTotemSpawn(cir.getReturnValue());
    }
}
