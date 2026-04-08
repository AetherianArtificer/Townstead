package com.aetherianartificer.townstead.client.gui.dialogue;

import com.aetherianartificer.townstead.TownsteadConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Centralized accessibility queries for the RPG dialogue system.
 * Reads from both Minecraft's built-in options and Townstead's config.
 */
public final class DialogueAccessibility {
    private DialogueAccessibility() {}

    /**
     * Effect intensity multiplier (0.0 = disabled, 1.0 = full).
     * Respects both Minecraft's Screen Effect Scale and Townstead's Reduce Motion.
     */
    public static float effectIntensity() {
        if (isReduceMotion()) return 0f;
        return (float) options().screenEffectScale().get().doubleValue();
    }

    /** Whether screen-space and world-space dialogue particles are enabled. */
    public static boolean particlesEnabled() {
        if (safeGet(TownsteadConfig.DIALOGUE_DISABLE_PARTICLES)) return false;
        // Also respect hideLightningFlash — particles can strobe
        return !options().hideLightningFlash().get();
    }

    /** Whether the camera should rotate to face the villager. */
    public static boolean cameraEnabled() {
        return !safeGet(TownsteadConfig.DIALOGUE_DISABLE_CAMERA);
    }

    /** Whether the narrator should read dialogue text aloud. */
    public static boolean narratorEnabled() {
        return options().narrator().get().shouldNarrateChat();
    }

    /** Whether emotion color tints should be applied (respects Chat Colors setting). */
    public static boolean emotionColorsEnabled() {
        return options().chatColors().get();
    }

    /** Extra line spacing from Minecraft's Chat Line Spacing option. */
    public static float lineSpacingExtra() {
        return (float) options().chatLineSpacing().get().doubleValue();
    }

    /** Background alpha multiplier from Minecraft's Text Background Opacity. */
    public static float backgroundAlpha() {
        return (float) options().textBackgroundOpacity().get().doubleValue();
    }

    /** Whether high contrast mode is enabled. */
    public static boolean highContrast() {
        return options().highContrast().get();
    }

    /** Whether Townstead's reduce motion config is enabled. */
    public static boolean isReduceMotion() {
        return safeGet(TownsteadConfig.DIALOGUE_REDUCE_MOTION);
    }

    private static Options options() {
        return Minecraft.getInstance().options;
    }

    //? if neoforge {
    private static boolean safeGet(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue value) {
        try { return value != null && value.get(); } catch (Exception e) { return false; }
    }
    //?} else {
    /*private static boolean safeGet(net.minecraftforge.common.ForgeConfigSpec.BooleanValue value) {
        try { return value != null && value.get(); } catch (Exception e) { return false; }
    }
    *///?}
}
