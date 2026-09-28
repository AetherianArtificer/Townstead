package com.aetherianartificer.townstead.mixin.compat.vampirism;

import com.aetherianartificer.townstead.compat.vampirism.VampireVillagers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A vampire villager answers "vampire" wherever Vampirism asks an entity's faction. */
@Pseudo
@Mixin(targets = "de.teamlapen.vampirism.entity.factions.FactionRegistry", remap = false)
public abstract class FactionRegistryMixin {
    @Inject(method = "getFaction(Lnet/minecraft/world/entity/Entity;)Lde/teamlapen/vampirism/api/entity/factions/IFaction;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void townstead$vampireVillager(Entity entity, CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() != null || !(entity instanceof LivingEntity living)) return;
        if (!VampireVillagers.isVampire(living)) return;
        Object faction = VampireVillagers.vampireFaction();
        if (faction != null) cir.setReturnValue(faction);
    }
}
