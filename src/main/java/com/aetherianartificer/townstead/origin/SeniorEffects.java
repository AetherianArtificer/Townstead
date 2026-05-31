package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

//? if forge {
/*import java.util.UUID;
*///?}

/**
 * Attribute modifiers that ride alongside a villager's senior flag: a small
 * movement-speed penalty. Modifiers are transient (don't persist across
 * save/load), so {@link com.aetherianartificer.townstead.tick.LifeStageTicker}
 * re-applies/clears every tick based on {@code Life.isSenior}.
 */
public final class SeniorEffects {

    /** Multiplicative speed penalty applied while a villager is in their senior stage. */
    public static final double SENIOR_SPEED_PENALTY = -0.15;

    //? if >=1.21 {
    private static final ResourceLocation TOWNSTEAD_SENIOR_SPEED =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "senior_speed");
    //?} else {
    /*private static final ResourceLocation TOWNSTEAD_SENIOR_SPEED =
            new ResourceLocation(Townstead.MOD_ID, "senior_speed");
    *///?}
    //? if forge {
    /*private static final UUID TOWNSTEAD_SENIOR_SPEED_UUID =
            UUID.nameUUIDFromBytes("townstead:senior_speed".getBytes());
    *///?}

    private SeniorEffects() {}

    /** Add the senior speed modifier if it isn't already present. */
    public static void applySenior(VillagerEntityMCA villager) {
        AttributeInstance speed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        //? if >=1.21 {
        if (speed.getModifier(TOWNSTEAD_SENIOR_SPEED) != null) return;
        speed.addTransientModifier(new AttributeModifier(
                TOWNSTEAD_SENIOR_SPEED,
                SENIOR_SPEED_PENALTY,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        //?} else {
        /*if (speed.getModifier(TOWNSTEAD_SENIOR_SPEED_UUID) != null) return;
        speed.addTransientModifier(new AttributeModifier(
                TOWNSTEAD_SENIOR_SPEED_UUID,
                "townstead:senior_speed",
                SENIOR_SPEED_PENALTY,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
        *///?}
    }

    /** Remove the senior speed modifier if present. */
    public static void clearSenior(VillagerEntityMCA villager) {
        AttributeInstance speed = villager.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        //? if >=1.21 {
        if (speed.getModifier(TOWNSTEAD_SENIOR_SPEED) != null) {
            speed.removeModifier(TOWNSTEAD_SENIOR_SPEED);
        }
        //?} else {
        /*if (speed.getModifier(TOWNSTEAD_SENIOR_SPEED_UUID) != null) {
            speed.removeModifier(TOWNSTEAD_SENIOR_SPEED_UUID);
        }
        *///?}
    }
}
