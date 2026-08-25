package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.profession.ProfessionClothing;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.npc.VillagerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies data-authored clothing fallback after MCA's built-in Profession wardrobe change. */
@Mixin(VillagerEntityMCA.class)
public abstract class VillagerProfessionClothingMixin {

    @Unique private String townstead$previousClothes;
    @Unique private boolean townstead$professionChanged;

    @Inject(method = "setVillagerData", at = @At("HEAD"))
    private void townstead$captureClothing(VillagerData next, CallbackInfo ci) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        townstead$professionChanged = self.getVillagerData().getProfession() != next.getProfession();
        townstead$previousClothes = townstead$professionChanged ? self.getClothes() : null;
    }

    @Inject(method = "setVillagerData", at = @At("TAIL"))
    private void townstead$resolveProfessionClothing(VillagerData next, CallbackInfo ci) {
        if (!townstead$professionChanged) return;
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        ProfessionClothing.afterProfessionChange(self, townstead$previousClothes);
        townstead$professionChanged = false;
        townstead$previousClothes = null;
    }
}
