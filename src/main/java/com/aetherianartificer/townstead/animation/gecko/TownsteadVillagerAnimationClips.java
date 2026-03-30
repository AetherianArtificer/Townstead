package com.aetherianartificer.townstead.animation.gecko;

import com.aetherianartificer.townstead.animation.VillagerResponseAnimation;
//? if neoforge {
import software.bernie.geckolib.animation.RawAnimation;
//?} else if forge {
/*import software.bernie.geckolib.core.animation.RawAnimation;*/
//?}

public final class TownsteadVillagerAnimationClips {
    public static final String RESPONSE_CONTROLLER = "response";

    public static final RawAnimation NEUTRAL_IDLE = RawAnimation.begin().thenLoop("animation.townstead.villager.neutral_idle");
    public static final RawAnimation WAVE_BACK = RawAnimation.begin().thenPlay("animation.townstead.villager.wave_back");
    public static final RawAnimation CAUTIOUS_ACK = RawAnimation.begin().thenPlay("animation.townstead.villager.cautious_ack");
    public static final RawAnimation APPLAUD = RawAnimation.begin().thenPlay("animation.townstead.villager.applaud");
    public static final RawAnimation BASHFUL = RawAnimation.begin().thenPlay("animation.townstead.villager.bashful");
    public static final RawAnimation COMFORT = RawAnimation.begin().thenPlay("animation.townstead.villager.comfort");
    public static final RawAnimation AWKWARD = RawAnimation.begin().thenPlay("animation.townstead.villager.awkward");
    public static final RawAnimation ACKNOWLEDGE = RawAnimation.begin().thenPlay("animation.townstead.villager.acknowledge");
    public static final RawAnimation PUZZLED = RawAnimation.begin().thenPlay("animation.townstead.villager.puzzled");
    public static final RawAnimation AMUSED = RawAnimation.begin().thenPlay("animation.townstead.villager.amused");
    public static final RawAnimation STARTLED = RawAnimation.begin().thenPlay("animation.townstead.villager.startled");

    private TownsteadVillagerAnimationClips() {}

    public static String triggerName(VillagerResponseAnimation animation) {
        return animation == null ? null : animation.id();
    }

    public static RawAnimation clipFor(VillagerResponseAnimation animation) {
        if (animation == null) return NEUTRAL_IDLE;

        return switch (animation) {
            case NEUTRAL_IDLE -> NEUTRAL_IDLE;
            case WAVE_BACK -> WAVE_BACK;
            case CAUTIOUS_ACK -> CAUTIOUS_ACK;
            case APPLAUD -> APPLAUD;
            case BASHFUL -> BASHFUL;
            case COMFORT -> COMFORT;
            case AWKWARD -> AWKWARD;
            case ACKNOWLEDGE -> ACKNOWLEDGE;
            case PUZZLED -> PUZZLED;
            case AMUSED -> AMUSED;
            case STARTLED -> STARTLED;
        };
    }
}
