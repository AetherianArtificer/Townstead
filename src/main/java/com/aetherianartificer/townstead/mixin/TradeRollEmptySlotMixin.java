package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.profession.DataDrivenTrades;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Drops data trade slots that offer this villager nothing before vanilla rolls its listings.
 * 1.20.1 picks a fixed set of listings and keeps only the ones that return an offer, so a
 * profession with few data trades often rolled none and could not be traded with.
 */
@Mixin(AbstractVillager.class)
public abstract class TradeRollEmptySlotMixin {
    //? if neoforge {
    @ModifyVariable(method = "addOffersFromItemListings", at = @At("HEAD"), argsOnly = true)
    //?} else {
    /*@ModifyVariable(method = "m_35277_", remap = false, at = @At("HEAD"), argsOnly = true)
    *///?}
    private VillagerTrades.ItemListing[] townstead$withoutEmptySlots(VillagerTrades.ItemListing[] listings) {
        return DataDrivenTrades.withoutEmptySlots(listings, (AbstractVillager) (Object) this);
    }
}
