package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.politics.charter.CharterLecternAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

/** Persists and synchronizes the virtual charter presentation on an ordinary lectern. */
@Mixin(LecternBlockEntity.class)
public abstract class LecternBlockEntityCharterMixin implements CharterLecternAccess {
    @Unique private static final String TOWNSTEAD_CHARTER_STATE = "TownsteadCharterState";
    @Unique private int townstead$charterState;
    @Unique private String townstead$emblem = "";
    @Override public String townstead$emblem() { return townstead$emblem; }
    @Override public void townstead$setEmblem(String recipe) { townstead$emblem = recipe.length() <= 1024 ? recipe : ""; }

    @Override
    public int townstead$charterState() {
        return townstead$charterState;
    }

    @Override
    public void townstead$setCharterState(int state) {
        townstead$charterState = Math.max(NONE, Math.min(FOUNDED, state));
    }

    //? if >=1.21 {
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void townstead$saveCharter(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "saveAdditional", at = @At("TAIL"))
    private void townstead$saveCharter(CompoundTag tag, CallbackInfo ci) {
    *///?}
        if (townstead$charterState != NONE) tag.putInt(TOWNSTEAD_CHARTER_STATE, townstead$charterState);
        tag.putString("TownsteadEmblem", townstead$emblem);
    }

    //? if >=1.21 {
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void townstead$loadCharter(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "load", at = @At("TAIL"))
    private void townstead$loadCharter(CompoundTag tag, CallbackInfo ci) {
    *///?}
        townstead$charterState = Math.max(NONE, Math.min(FOUNDED, tag.getInt(TOWNSTEAD_CHARTER_STATE)));
        townstead$setEmblem(tag.getString("TownsteadEmblem"));
    }

    @Inject(method = "getUpdatePacket", at = @At("HEAD"), cancellable = true)
    private void townstead$syncCharter(CallbackInfoReturnable<Packet<ClientGamePacketListener>> cir) {
        cir.setReturnValue(ClientboundBlockEntityDataPacket.create((LecternBlockEntity) (Object) this));
    }

    //? if >=1.21 {
    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void townstead$updateTag(HolderLookup.Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
    //?} else {
    /*@Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void townstead$updateTag(CallbackInfoReturnable<CompoundTag> cir) {
    *///?}
        var lectern = (LecternBlockEntity) (Object) this;
        if (lectern.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            townstead$setEmblem(com.aetherianartificer.townstead.politics.heraldry.HeraldryService.clothRecipe(level, lectern.getBlockPos()));
        cir.getReturnValue().putString("TownsteadEmblem", townstead$emblem);
        if (townstead$charterState != NONE) cir.getReturnValue().putInt(TOWNSTEAD_CHARTER_STATE, townstead$charterState);
    }
}
