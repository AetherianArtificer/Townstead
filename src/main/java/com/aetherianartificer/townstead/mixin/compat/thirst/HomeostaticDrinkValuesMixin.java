package com.aetherianartificer.townstead.mixin.compat.thirst;

import com.aetherianartificer.townstead.compat.thirst.DataDrivenThirstCompat;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Homeostatic's drinking, held-item overlay and tooltip all read drinkable items here, on both
 * sides. Answering here rather than writing its map keeps the overlay through its own reload and
 * client sync, which replace the map wholesale.
 */
@Pseudo
@Mixin(targets = "homeostatic.common.item.DrinkableItemManager", remap = false)
public class HomeostaticDrinkValuesMixin {
    @Inject(method = "get(Lnet/minecraft/world/item/ItemStack;)Lhomeostatic/common/item/DrinkableItem;",
            at = @At("RETURN"), cancellable = true, require = 0)
    private static void townstead$configured(ItemStack stack, CallbackInfoReturnable<Object> cir) {
        Object value = DataDrivenThirstCompat.homeostaticDrink(stack, cir.getReturnValue());
        if (value != cir.getReturnValue()) cir.setReturnValue(value);
    }
}
