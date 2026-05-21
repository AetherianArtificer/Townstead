package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

//? if neoforge {
import net.conczin.mca.block.TombstoneBlock;
//?} else {
/*import forge.net.mca.block.TombstoneBlock;
*///?}

/**
 * When a villager is resurrected from a tombstone, reset their hunger,
 * thirst, and energy to midpoints so they don't immediately suffer.
 */
@Mixin(TombstoneBlock.Data.class)
public class TombstoneRevivalMixin {

    @Inject(method = "createEntity", at = @At("RETURN"), remap = false)
    private void townstead$resetNeedsOnRevival(Level level, boolean forRendering,
                                                CallbackInfoReturnable<Optional<Entity>> cir) {
        if (forRendering) return;
        Optional<Entity> result = cir.getReturnValue();
        if (result.isEmpty()) return;
        Entity entity = result.get();
        if (!(entity instanceof VillagerEntityMCA villager)) return;

        TownsteadVillager state = TownsteadVillagers.get(villager);
        state.needs().resetHunger(HungerData.MAX_HUNGER / 2, HungerData.MAX_SATURATION / 2);

        // Reset thirst if active
        if (ThirstBridgeResolver.isActive()) {
            state.needs().resetThirst(ThirstData.MAX_THIRST / 2, ThirstData.MAX_QUENCHED / 4);
        }

        // Reset fatigue
        if (TownsteadConfig.isVillagerFatigueEnabled()) {
            state.needs().resetFatigue(FatigueData.MAX_FATIGUE / 2);
        }
    }
}
