package com.aetherianartificer.townstead.mixin.compat.travelerstitles;

import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Traveler's Titles' {@code displayTitle} only assigns the subtitle when one is
 * passed; it never clears a previous one. Townstead is the only thing that ever
 * sets a subtitle (the village's community spirit), so once shown it persisted
 * and re-rendered under every later biome/dimension/waystone title. Clearing it
 * on any null-subtitle call (every TT-native title) resets it as expected.
 *
 * TT is client-only and optional; {@code require = 0} no-ops when absent.
 */
@Pseudo
@Mixin(targets = "com.yungnickyoung.minecraft.travelerstitles.render.TitleRenderer")
public class TitleRendererSubtitleClearMixin {

    @Shadow(remap = false)
    public Component displayedSubTitle;

    @Inject(method = "displayTitle", at = @At("HEAD"), remap = false, require = 0)
    private void townstead$clearStaleSubtitle(Component titleText, Component subtitleText, CallbackInfo ci) {
        if (subtitleText == null) {
            this.displayedSubTitle = null;
        }
    }
}
