package com.aetherianartificer.townstead.mixin.compat.thirst;

import com.aetherianartificer.townstead.compat.thirst.DataDrivenThirstCompat;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ThirstWasTaken2 resolves data-pack drinks here, after its config and before its tag and keyword
 * fallbacks, on both sides. Townstead's consumables answer in the same place, so player drinking,
 * the mod's tooltip and villagers agree, and a server owner's config still wins.
 */
@Pseudo
@Mixin(targets = "com.thirstwastaken2.data.DataPackDrinks", remap = false)
public class Twt2DrinkValuesMixin {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true, require = 0)
    private static void townstead$configured(Item item, CallbackInfoReturnable<int[]> cir) {
        int[] value = DataDrivenThirstCompat.twt2Value(item, cir.getReturnValue());
        if (value != cir.getReturnValue()) cir.setReturnValue(value);
    }
}
