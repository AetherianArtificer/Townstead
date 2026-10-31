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
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

/**
 * Persists and synchronizes the virtual charter presentation on an ordinary lectern. Below the
 * default priority so another mod's update-packet overrides replace ours instead of being skipped;
 * those send the saved data, which carries the charter state and emblem written here.
 */
@Mixin(value = LecternBlockEntity.class, priority = 900)
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
    /*@Inject(method = "m_183515_", remap = false, at = @At("TAIL"))
    private void townstead$saveCharter(CompoundTag tag, CallbackInfo ci) {
    *///?}
        if (townstead$charterState != NONE) {
            tag.putInt(TOWNSTEAD_CHARTER_STATE, townstead$charterState);
            townstead$refreshEmblem();
        }
        tag.putString("TownsteadEmblem", townstead$emblem);
    }

    @Unique
    private void townstead$refreshEmblem() {
        var lectern = (LecternBlockEntity) (Object) this;
        if (lectern.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
            townstead$setEmblem(com.aetherianartificer.townstead.politics.heraldry.HeraldryService.clothRecipe(level, lectern.getBlockPos()));
    }

    //? if >=1.21 {
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void townstead$loadCharter(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "m_142466_", remap = false, at = @At("TAIL"))
    private void townstead$loadCharter(CompoundTag tag, CallbackInfo ci) {
    *///?}
        townstead$charterState = Math.max(NONE, Math.min(FOUNDED, tag.getInt(TOWNSTEAD_CHARTER_STATE)));
        townstead$setEmblem(tag.getString("TownsteadEmblem"));
    }

    // LecternBlockEntity inherits both sync methods, so they are overridden here rather than injected.
    //? if neoforge {
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create((LecternBlockEntity) (Object) this);
    }

    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return townstead$updateTag();
    }
    //?} else {
    /*public Packet<ClientGamePacketListener> m_58483_() {
        return ClientboundBlockEntityDataPacket.create((LecternBlockEntity) (Object) this);
    }

    public CompoundTag m_5995_() {
        return townstead$updateTag();
    }
    *///?}

    @Unique
    private CompoundTag townstead$updateTag() {
        townstead$refreshEmblem();
        CompoundTag tag = new CompoundTag();
        tag.putString("TownsteadEmblem", townstead$emblem);
        if (townstead$charterState != NONE) tag.putInt(TOWNSTEAD_CHARTER_STATE, townstead$charterState);
        return tag;
    }
}
