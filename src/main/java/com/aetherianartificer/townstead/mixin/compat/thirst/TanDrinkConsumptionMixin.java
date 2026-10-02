package com.aetherianartificer.townstead.mixin.compat.thirst;

import com.aetherianartificer.townstead.compat.thirst.DataDrivenThirstCompat;
import com.aetherianartificer.townstead.compat.thirst.ToughAsNailsThirstBridge;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Extend TAN's existing consumption path without applying a second drink effect. */
@Pseudo
@Mixin(targets = "toughasnails.thirst.ThirstHandler", remap = false)
public class TanDrinkConsumptionMixin {
    @WrapOperation(method = "onItemUseFinish", at = @At(value = "INVOKE",
            //? if neoforge {
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/tags/TagKey;)Z", remap = true))
            //?} else {
            /*target = "Lnet/minecraft/world/item/ItemStack;m_204117_(Lnet/minecraft/tags/TagKey;)Z"))
            *///?}
    private static boolean townstead$configuredDrink(ItemStack stack, TagKey<Item> tag, Operation<Boolean> original) {
        return original.call(stack, tag) || (tag.location().equals(ToughAsNailsThirstBridge.id("drinks"))
                && DataDrivenThirstCompat.tanProjection(stack).hydrates());
    }

    // ModifyArg, not ModifyArgs: Forge's module classloader cannot load ModifyArgs' synthetic Args classes.
    @ModifyArg(method = "onItemUseFinish", at = @At(value = "INVOKE",
            target = "Ltoughasnails/api/thirst/IThirst;drink(IF)V"), index = 1)
    private static float townstead$configuredHydration(float modifier, @Local ItemStack stack) {
        var effect = DataDrivenThirstCompat.tanProjection(stack);
        // TAN multiplies this modifier by thirst restored * 2.
        return effect.immediateHydration() > 0
                ? effect.lastingHydration() / (effect.immediateHydration() * 2.0F)
                : modifier;
    }
}
