package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;

/**
 * Per-villager overrides for the butchery integration. Currently just the
 * slaughter toggle, which lets a player tell a specific butcher whether to
 * kill livestock autonomously without flipping the global config.
 *
 * <p>Stored as a byte in the villager's HUNGER_DATA compound under
 * {@link #SLAUGHTER_OVERRIDE_KEY}. Absent / 0 = follow the global config;
 * 1 = force on; 2 = force off.
 */
public final class ButcherSettings {
    public static final String SLAUGHTER_OVERRIDE_KEY = "townstead_butcherySlaughterOverride";
    public static final String EDITOR_KEY_SLAUGHTER_OVERRIDE = "townstead_editorButcherySlaughter";

    public enum SlaughterOverride {
        FOLLOW_CONFIG((byte) 0),
        ENABLED((byte) 1),
        DISABLED((byte) 2);

        public final byte code;
        SlaughterOverride(byte code) { this.code = code; }

        public static SlaughterOverride fromCode(byte code) {
            return switch (code) {
                case 1 -> ENABLED;
                case 2 -> DISABLED;
                default -> FOLLOW_CONFIG;
            };
        }

        public SlaughterOverride next() {
            return switch (this) {
                case FOLLOW_CONFIG -> ENABLED;
                case ENABLED -> DISABLED;
                case DISABLED -> FOLLOW_CONFIG;
            };
        }
    }

    private ButcherSettings() {}

    public static SlaughterOverride getSlaughterOverride(CompoundTag hungerData) {
        if (hungerData == null || !hungerData.contains(SLAUGHTER_OVERRIDE_KEY)) {
            return SlaughterOverride.FOLLOW_CONFIG;
        }
        return SlaughterOverride.fromCode(hungerData.getByte(SLAUGHTER_OVERRIDE_KEY));
    }

    public static void setSlaughterOverride(CompoundTag hungerData, SlaughterOverride value) {
        if (hungerData == null) return;
        if (value == SlaughterOverride.FOLLOW_CONFIG) {
            hungerData.remove(SLAUGHTER_OVERRIDE_KEY);
        } else {
            hungerData.putByte(SLAUGHTER_OVERRIDE_KEY, value.code);
        }
    }

    public static SlaughterOverride getSlaughterOverride(VillagerEntityMCA villager) {
        //? if neoforge {
        return getSlaughterOverride(villager.getData(Townstead.HUNGER_DATA));
        //?} else {
        /*return getSlaughterOverride(villager.getPersistentData().getCompound("townstead_hunger"));
        *///?}
    }
}
