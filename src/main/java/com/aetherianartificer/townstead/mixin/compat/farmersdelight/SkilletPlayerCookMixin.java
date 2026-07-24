package com.aetherianartificer.townstead.mixin.compat.farmersdelight;

import com.aetherianartificer.townstead.compat.farmersdelight.PlayerCookingHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Credits a player with cooking when the skillet accepts their food. {@code addItemToCook} is
 * Farmer's Delight's own method (never obfuscated) and returns the remainder stack, so the
 * accepted count is the difference. Attribution happens on acceptance: the item is consumed
 * and will cook; the block entity keeps no owner to credit at completion time.
 */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity")
public abstract class SkilletPlayerCookMixin {

    @Inject(method = "addItemToCook", at = @At("RETURN"), remap = false)
    private void townstead$creditSkilletCooking(ItemStack addedStack, Player player,
                                                CallbackInfoReturnable<ItemStack> cir) {
        if (player == null || player.level().isClientSide) return;
        ItemStack remainder = cir.getReturnValue();
        int accepted = addedStack.getCount() - (remainder == null ? 0 : remainder.getCount());
        if (accepted > 0) {
            PlayerCookingHooks.onSkilletAdd(player, addedStack, accepted);
        }
    }
}
