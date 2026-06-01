package com.aetherianartificer.townstead.client.accessibility;

import com.aetherianartificer.townstead.TownsteadConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Mod-wide accessibility queries, reading from both Minecraft's built-in
 * options and Townstead's config. Shared across screens (dialogue, calendar,
 * spirit, ...); screen-specific toggles live next to their own feature.
 */
public final class Accessibility {
    private Accessibility() {}

    /** Whether Townstead's reduce motion config is enabled. */
    public static boolean isReduceMotion() {
        return safeGet(TownsteadConfig.REDUCE_MOTION);
    }

    /**
     * Effect intensity multiplier (0.0 = disabled, 1.0 = full).
     * Respects both Minecraft's Screen Effect Scale and Townstead's Reduce Motion.
     */
    public static float effectIntensity() {
        if (isReduceMotion()) return 0f;
        return (float) options().screenEffectScale().get().doubleValue();
    }

    /** Whether the narrator should read text aloud. */
    public static boolean narratorEnabled() {
        return options().narrator().get().shouldNarrateChat();
    }

    /** Whether color tints should be applied (respects Chat Colors setting). */
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
