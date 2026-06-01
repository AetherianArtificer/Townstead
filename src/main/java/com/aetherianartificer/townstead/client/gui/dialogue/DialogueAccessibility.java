package com.aetherianartificer.townstead.client.gui.dialogue;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.client.accessibility.Accessibility;
import net.minecraft.client.Minecraft;

/**
 * Dialogue-specific accessibility toggles (backed by the {@code DIALOGUE_*}
 * config). Mod-wide queries live in {@link Accessibility}; the passthroughs
 * below are kept so existing dialogue call sites stay terse.
 */
public final class DialogueAccessibility {
    private DialogueAccessibility() {}

    /** Whether screen-space and world-space dialogue particles are enabled. */
    public static boolean particlesEnabled() {
        if (safeGet(TownsteadConfig.DIALOGUE_DISABLE_PARTICLES)) return false;
        // Also respect hideLightningFlash — particles can strobe
        return !Minecraft.getInstance().options.hideLightningFlash().get();
    }

    /** Whether the camera should rotate to face the villager. */
    public static boolean cameraEnabled() {
        return !safeGet(TownsteadConfig.DIALOGUE_DISABLE_CAMERA);
    }

    // ── Mod-wide passthroughs (delegate to Accessibility) ────────────────────
    public static boolean isReduceMotion()      { return Accessibility.isReduceMotion(); }
    public static float effectIntensity()       { return Accessibility.effectIntensity(); }
    public static boolean narratorEnabled()     { return Accessibility.narratorEnabled(); }
    public static boolean emotionColorsEnabled(){ return Accessibility.emotionColorsEnabled(); }
    public static float lineSpacingExtra()      { return Accessibility.lineSpacingExtra(); }
    public static float backgroundAlpha()       { return Accessibility.backgroundAlpha(); }
    public static boolean highContrast()        { return Accessibility.highContrast(); }

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
