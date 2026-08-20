package com.aetherianartificer.townstead.mixin.compat.farmersdelight;

import com.aetherianartificer.townstead.profession.career.PlayerWorkHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Credits the direct-serving path: right-clicking a Farmer's Delight cooking pot with a bowl
 * serves a portion through {@code useHeldItemOnMeal} without ever opening the GUI, so the
 * result-slot hook never sees it. The method is Farmer's Delight's own (never obfuscated,
 * identical on 1.20 and 1.21) but carries no player, so the serving is attributed to the
 * nearest player in reach; the only caller is the block's use handler with the clicking
 * player standing at the pot.
 */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity")
public abstract class CookingPotServeMixin {

    @Inject(method = "useHeldItemOnMeal", at = @At("RETURN"), remap = false)
    private void townstead$creditServing(ItemStack container, CallbackInfoReturnable<ItemStack> cir) {
        ItemStack served = cir.getReturnValue();
        if (served == null || served.isEmpty()) return;
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide) return;
        Player player = level.getNearestPlayer(
                self.getBlockPos().getX() + 0.5, self.getBlockPos().getY() + 0.5,
                self.getBlockPos().getZ() + 0.5, 5.0, false);
        if (player != null) {
            PlayerWorkHooks.onCookingCompleted(
                    player, served, served.getCount(), "cooking_pot");
        }
    }
}
