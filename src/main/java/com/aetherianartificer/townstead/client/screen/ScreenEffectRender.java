package com.aetherianartificer.townstead.client.screen;

import com.aetherianartificer.townstead.client.accessibility.Accessibility;
import com.aetherianartificer.townstead.pheno.screen.ScreenEffect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws {@code pheno:screen_effect} cues: flashes, vignettes and tints over the HUD, and shakes and
 * field-of-view kicks on the camera. Every cue is scaled by the player's screen-effect setting,
 * and reduced motion turns shakes and FOV kicks off entirely.
 */
public final class ScreenEffectRender {

    private ScreenEffectRender() {}

    /** HUD layer: the colour cues. */
    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui && mc.screen == null) return;
        float scale = Accessibility.effectIntensity();
        if (scale <= 0f) return;
        long now = ScreenEffect.Client.clock();
        float partial = partial(mc);
        int w = g.guiWidth(), h = g.guiHeight();
        for (ScreenEffect.Client.Active cue : ScreenEffect.Client.active()) {
            float strength = cue.intensity() * cue.envelope(now, partial) * scale;
            if (strength <= 0.004f) continue;
            int rgb = cue.color() & 0xFFFFFF;
            switch (cue.kind()) {
                case FLASH -> g.fill(0, 0, w, h, argb(strength * 0.8f, rgb));
                case TINT -> g.fill(0, 0, w, h, argb(strength * 0.35f, rgb));
                case VIGNETTE -> vignette(g, w, h, rgb, strength);
                default -> { }
            }
        }
    }

    private static void vignette(GuiGraphics g, int w, int h, int rgb, float strength) {
        int edge = argb(Math.min(0.85f, strength * 0.9f), rgb);
        int clear = argb(0f, rgb);
        int band = Math.max(8, Math.round(Math.min(w, h) * 0.22f));
        g.fillGradient(0, 0, w, band, edge, clear);
        g.fillGradient(0, h - band, w, h, clear, edge);
        // Side bands: fillGradient is vertical-only, so draw them as thin vertical strips.
        for (int i = 0; i < band; i += 2) {
            float a = (1f - i / (float) band);
            int col = argb(Math.min(0.85f, strength * 0.9f) * a * a, rgb);
            g.fill(i, 0, i + 2, h, col);
            g.fill(w - i - 2, 0, w - i, h, col);
        }
    }

    /** Camera offsets {yaw, pitch, roll} in degrees from active shakes. */
    public static float[] shake() {
        if (Accessibility.isReduceMotion()) return null;
        Minecraft mc = Minecraft.getInstance();
        long now = ScreenEffect.Client.clock();
        float partial = partial(mc);
        float amplitude = 0f;
        for (ScreenEffect.Client.Active cue : ScreenEffect.Client.active()) {
            if (cue.kind() == ScreenEffect.Kind.SHAKE) amplitude += cue.intensity() * cue.envelope(now, partial);
        }
        amplitude *= Accessibility.effectIntensity();
        if (amplitude <= 0.001f) return null;
        double t = (now + partial) * 1.7;
        float degrees = 3f * amplitude;
        return new float[]{(float) (Math.sin(t * 2.3) * degrees), (float) (Math.sin(t * 3.1 + 1) * degrees),
                (float) (Math.sin(t * 1.9 + 2) * degrees * 0.6f)};
    }

    /** Multiplier for the field of view from active kicks, 1 when none. */
    public static double fovMultiplier() {
        if (Accessibility.isReduceMotion()) return 1.0;
        Minecraft mc = Minecraft.getInstance();
        long now = ScreenEffect.Client.clock();
        float partial = partial(mc);
        double kick = 0;
        for (ScreenEffect.Client.Active cue : ScreenEffect.Client.active()) {
            if (cue.kind() == ScreenEffect.Kind.FOV) kick += cue.intensity() * cue.envelope(now, partial);
        }
        return 1.0 + kick * Accessibility.effectIntensity();
    }

    //? if neoforge {
    public static void onComputeCameraAngles(net.neoforged.neoforge.client.event.ViewportEvent.ComputeCameraAngles event) {
    //?} else {
    /*public static void onComputeCameraAngles(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles event) {
    *///?}
        float[] offset = shake();
        if (offset == null) return;
        event.setYaw(event.getYaw() + offset[0]);
        event.setPitch(event.getPitch() + offset[1]);
        event.setRoll(event.getRoll() + offset[2]);
    }

    //? if neoforge {
    public static void onComputeFov(net.neoforged.neoforge.client.event.ViewportEvent.ComputeFov event) {
    //?} else {
    /*public static void onComputeFov(net.minecraftforge.client.event.ViewportEvent.ComputeFov event) {
    *///?}
        double multiplier = fovMultiplier();
        if (multiplier != 1.0) event.setFOV(event.getFOV() * multiplier);
    }

    private static float partial(Minecraft mc) {
        //? if >=1.21 {
        return mc.getTimer().getGameTimeDeltaPartialTick(false);
        //?} else {
        /*return mc.getFrameTime();
        *///?}
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
