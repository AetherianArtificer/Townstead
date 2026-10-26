package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.hook.PhenoHooks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercept for the {@code fishing_lure} and {@code fishing_luck} modifier targets on a player's
 * cast; the rod is the subject item and is left untouched. On 1.21.1 {@code lureSpeed} is in ticks
 * (100 per Lure level); on 1.20.1 it is in levels. Villager fishers resolve the same targets in
 * their own engine. 1.20.1 SRG: {@code f_37096_} luck, {@code f_37097_} lureSpeed.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookPhenoMixin {

    //? if neoforge {
    @Shadow @Final @Mutable private int luck;
    @Shadow @Final @Mutable private int lureSpeed;
    //?} else {
    /*@Shadow(remap = false) @Final @Mutable private int f_37096_;
    @Shadow(remap = false) @Final @Mutable private int f_37097_;
    *///?}

    @Inject(method = "<init>(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;II)V",
            at = @At("RETURN"), remap = false)
    private void townstead$fishingModifiers(Player player, Level level, int luck, int lure, CallbackInfo ci) {
        if (level.isClientSide) return;
        ItemStack rod = player.getMainHandItem().getItem() instanceof FishingRodItem
                ? player.getMainHandItem() : player.getOffhandItem();
        //? if neoforge {
        this.luck = PhenoHooks.fishingLuck(player, rod, this.luck);
        int levels = this.lureSpeed / 100;
        this.lureSpeed += (PhenoHooks.fishingLure(player, rod, levels) - levels) * 100;
        //?} else {
        /*this.f_37096_ = PhenoHooks.fishingLuck(player, rod, this.f_37096_);
        this.f_37097_ = PhenoHooks.fishingLure(player, rod, this.f_37097_);
        *///?}
    }
}
