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
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Extend TAN's existing consumption path without applying a second drink effect. */
@Pseudo
@Mixin(targets = "toughasnails.thirst.ThirstHandler", remap = false)
public class TanDrinkConsumptionMixin {
    @WrapOperation(method = "onItemUseFinish", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/tags/TagKey;)Z", remap = true))
    private static boolean townstead$configuredDrink(ItemStack stack, TagKey<Item> tag, Operation<Boolean> original) {
        return original.call(stack, tag) || (tag.location().equals(ToughAsNailsThirstBridge.id("drinks"))
                && DataDrivenThirstCompat.tanProjection(stack).hydrates());
    }

    @ModifyArgs(method = "onItemUseFinish", at = @At(value = "INVOKE",
            target = "Ltoughasnails/api/thirst/IThirst;drink(IF)V"))
    private static void townstead$configuredHydration(Args args, @Local ItemStack stack) {
        var effect = DataDrivenThirstCompat.tanProjection(stack);
        if (effect.immediateHydration() > 0) {
            // TAN multiplies this modifier by thirst restored * 2.
            args.set(1, effect.lastingHydration() / (effect.immediateHydration() * 2.0F));
        }
    }
}
