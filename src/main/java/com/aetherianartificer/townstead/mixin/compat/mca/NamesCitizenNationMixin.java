package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.naming.NamingRegisters;
import net.conczin.mca.resources.Names;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a villager's naming register stop moving when the loaded name buckets change.
 *
 * <p>MCA derives the register as {@code REGION_NAMES.get(floorMod(regionId, REGION_NAMES.size()))}
 * and persists only the raw region id, so adding any {@code mca_names} folder re-sorts and resizes
 * that list and silently re-rolls every region in the world. This returns the register Townstead
 * recorded, and otherwise freezes whatever MCA derived so the next call is stable.</p>
 *
 * <p>HEAD rather than a return modifier on purpose: MCA's own derivation calls
 * {@code Nationality.getRegionId}, which <em>writes</em> a new region entry and marks its saved
 * data dirty, and indexes into {@code REGION_NAMES} without checking it is non-empty. Answering at
 * HEAD skips both. The RETURN handler only runs when HEAD declined, since setting a return value
 * there cancels before the original return is reached.</p>
 */
@Mixin(Names.class)
public abstract class NamesCitizenNationMixin {

    @Inject(method = "getCitizenNation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void townstead$recordedRegister(Entity entity, CallbackInfoReturnable<String> cir) {
        String register = NamingRegisters.resolve(entity);
        if (!register.isEmpty()) {
            cir.setReturnValue(register);
        }
    }

    @Inject(method = "getCitizenNation", at = @At("RETURN"), remap = false)
    private static void townstead$freezeDerivedRegister(Entity entity, CallbackInfoReturnable<String> cir) {
        NamingRegisters.freeze(entity, cir.getReturnValue());
    }
}
