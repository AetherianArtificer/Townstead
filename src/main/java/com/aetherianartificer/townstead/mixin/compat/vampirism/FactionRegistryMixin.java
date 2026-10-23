package com.aetherianartificer.townstead.mixin.compat.vampirism;

import com.aetherianartificer.townstead.compat.vampirism.VampireVillagers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A vampire villager answers "vampire", and a werewolf villager "werewolf", wherever Vampirism asks
 * an entity's faction. The Werewolves mod asks this too, for silver, wolfsbane and its plants.
 */
@Pseudo
@Mixin(targets = "de.teamlapen.vampirism.entity.factions.FactionRegistry", remap = false)
public abstract class FactionRegistryMixin {
    @Inject(method = "getFaction(Lnet/minecraft/world/entity/Entity;)Lde/teamlapen/vampirism/api/entity/factions/IFaction;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private void townstead$vampireVillager(Entity entity, CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() != null || !(entity instanceof LivingEntity living)) return;
        Object faction = null;
        if (VampireVillagers.isVampire(living)) faction = VampireVillagers.vampireFaction();
        else if (com.aetherianartificer.townstead.compat.ModCompat.isLoaded("werewolves")
                && com.aetherianartificer.townstead.compat.werewolves.WerewolfVillagers.isWerewolf(living)) {
            faction = com.aetherianartificer.townstead.compat.werewolves.WerewolfVillagers.werewolfFaction();
        }
        if (faction != null) cir.setReturnValue(faction);
    }
}
