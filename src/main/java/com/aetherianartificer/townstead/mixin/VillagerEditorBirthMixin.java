package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.LifeData;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload;
import com.aetherianartificer.townstead.origin.LifeStageProgression;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the age-slider edit from the villager editor. The editor writes the
 * desired biological age (in calendar days) into {@code villagerData} under
 * {@link LifeData#EDITOR_KEY_BIO_AGE_DAYS}; MCA carries that tag to the server
 * on "Done", where this re-stamps the villager's birth ({@code today - bioAge})
 * so the calendar-driven life-stage resolution lands on the chosen age.
 *
 * <p>The key is transient — it is never written on normal saves, so a regular
 * world load never contains it and this is a no-op there.</p>
 */
@Mixin(VillagerEntityMCA.class)
public abstract class VillagerEditorBirthMixin {

    //? if neoforge {
    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_7378_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$applyEditorAge(CompoundTag nbt, CallbackInfo ci) {
        boolean hasFrozen = nbt.contains(LifeData.EDITOR_KEY_FROZEN_STAGE_INDEX);
        boolean hasAge = nbt.contains(LifeData.EDITOR_KEY_BIO_AGE_DAYS);
        boolean hasMonthDay = nbt.contains(LifeData.EDITOR_KEY_BIRTH_MONTH)
                || nbt.contains(LifeData.EDITOR_KEY_BIRTH_DAY);
        if (!hasFrozen && !hasAge && !hasMonthDay) return;

        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        if (self.level().isClientSide) return;
        MinecraftServer server = self.level().getServer();
        if (server == null) return;

        TownsteadVillager.Life life = TownsteadVillagers.get(self).life();
        boolean changed = false;

        // Immortal appearance: re-freeze at the chosen stage; date of birth (hence
        // calendar age) is left untouched.
        if (hasFrozen) {
            LifeStageProgression.freezeAtStage(self, nbt.getInt(LifeData.EDITOR_KEY_FROZEN_STAGE_INDEX));
            changed = true;
        }

        // Celebrated birthday (month/day) is decoupled from age: it changes only the
        // stored celebrated date, never birthWorldDay. The age slider, below, is the
        // sole control over how old the villager is.
        if (hasMonthDay) {
            int m = nbt.contains(LifeData.EDITOR_KEY_BIRTH_MONTH)
                    ? nbt.getInt(LifeData.EDITOR_KEY_BIRTH_MONTH) : life.birthMonth();
            int d = nbt.contains(LifeData.EDITOR_KEY_BIRTH_DAY)
                    ? nbt.getInt(LifeData.EDITOR_KEY_BIRTH_DAY) : life.birthDay();
            if (m != life.birthMonth() || d != life.birthDay()) {
                life.setCelebratedBirthday(m, d);
                changed = true;
            }
        }

        // Age slider: stamps birthWorldDay = today - bioAge, the only thing that sets age.
        if (hasAge) {
            long newBirth = TownsteadCalendar.lifeDay(server) - Math.max(0, nbt.getInt(LifeData.EDITOR_KEY_BIO_AGE_DAYS));
            if (!life.hasBirth() || newBirth != life.birthWorldDay()) {
                life.setBirth(newBirth, true);
                // Re-resolve the stage: setAgeState runs our life-stage @ModifyVariable,
                // which recomputes the canonical stage from the freshly stamped birth.
                self.setAgeState(self.getAgeState());
                changed = true;
            }
        }

        if (!changed) return;
        VillagerLifeSyncPayload payload = Townstead.townstead$lifeSync(self);
        if (payload == null) return;
        //? if neoforge {
        PacketDistributor.sendToPlayersTrackingEntity(self, payload);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(self, payload);
        *///?}
    }
}
