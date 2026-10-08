package com.aetherianartificer.townstead.mixin.compat.farmersdelight;

import com.aetherianartificer.townstead.profession.career.PlayerWorkHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adapts the Farmer's Delight skillet lifecycle to Townstead's generic cooking-completion
 * contract. The adapter remembers the initiating player but awards work only after the recipe
 * engine consumes an ingredient. Removing the raw item cancels the pending work.
 */
@Pseudo
@Mixin(targets = "vectorwing.farmersdelight.common.block.entity.SkilletBlockEntity")
public abstract class SkilletCookingCompletionMixin {
    @Unique
    private ItemStack townstead$ingredientBeforeTick = ItemStack.EMPTY;

    @Shadow(remap = false)
    public abstract ItemStack getStoredStack();

    @Inject(method = "addItemToCook", at = @At("RETURN"), remap = false)
    private void townstead$rememberSkilletCook(ItemStack addedStack, Player player,
                                               CallbackInfoReturnable<ItemStack> cir) {
        if (player == null || player.level().isClientSide) return;
        ItemStack remainder = cir.getReturnValue();
        int accepted = addedStack.getCount() - (remainder == null ? 0 : remainder.getCount());
        if (accepted > 0) {
            PlayerWorkHooks.rememberCookingPlayer(townstead$blockEntity(), player);
        }
    }

    @Inject(method = "removeItem", at = @At("RETURN"), remap = false)
    private void townstead$forgetRemovedIngredient(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack removed = cir.getReturnValue();
        if (removed != null && !removed.isEmpty()) {
            PlayerWorkHooks.forgetCookingPlayer(townstead$blockEntity());
        }
    }

    @Inject(method = "cookAndOutputItems", at = @At("HEAD"), remap = false)
    private void townstead$recordIngredient(ItemStack ingredient, Level level, CallbackInfo ci) {
        townstead$ingredientBeforeTick = ingredient.copy();
    }

    @Inject(method = "cookAndOutputItems", at = @At("RETURN"), remap = false)
    private void townstead$creditCompletedCooking(ItemStack ingredient, Level level, CallbackInfo ci) {
        ItemStack originalIngredient = townstead$ingredientBeforeTick;
        townstead$ingredientBeforeTick = ItemStack.EMPTY;
        int completed = Math.max(0,
                originalIngredient.getCount() - getStoredStack().getCount());
        if (completed <= 0) return;

        PlayerWorkHooks.onRememberedCookingCompleted(
                townstead$blockEntity(), originalIngredient, completed, "skillet");
    }

    @Unique
    private BlockEntity townstead$blockEntity() {
        return (BlockEntity) (Object) this;
    }
}
