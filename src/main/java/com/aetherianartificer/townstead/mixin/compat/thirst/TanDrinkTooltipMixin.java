package com.aetherianartificer.townstead.mixin.compat.thirst;

import com.aetherianartificer.townstead.compat.thirst.DataDrivenThirstCompat;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Keep TAN's renderer, textures, half-drops, and enable-thirst setting. */
@Pseudo
@Mixin(targets = "toughasnails.client.handler.TooltipHandler", remap = false)
public class TanDrinkTooltipMixin {
    @WrapOperation(method = "onRenderTooltip", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/tags/TagKey;)Z", remap = true))
    private static boolean townstead$configuredDrink(ItemStack stack, TagKey<Item> tag, Operation<Boolean> original) {
        return original.call(stack, tag) || DataDrivenThirstCompat.tanProjection(stack).hydrates();
    }
}
