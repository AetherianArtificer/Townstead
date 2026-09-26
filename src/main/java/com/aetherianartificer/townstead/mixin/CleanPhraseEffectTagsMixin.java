package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.gui.dialogue.effect.EffectTagParser;
import net.conczin.mca.client.tts.OnlineSpeechManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Takes Townstead's effect tags out of a phrase before it is spoken.
 *
 * <p>Townstead ships its dialogue in MCA's own {@code mca_dialogue} namespaces, tags and all, so a
 * line MCA resolves from a translation key arrives as {@code <yell>Help!</yell>} and the voice reads
 * the markup aloud. {@code cleanPhrase} is where MCA already strips what should not be spoken, such
 * as {@code *waves*} and leftover format placeholders, so the tags come off in the same place.</p>
 *
 * <p>It also settles the phrase's cache key: a tagged line and the plain line now hash alike, so
 * one is reused for the other instead of each being generated separately.</p>
 */
@Mixin(value = OnlineSpeechManager.class, remap = false)
public class CleanPhraseEffectTagsMixin {

    @Inject(method = "cleanPhrase", at = @At("RETURN"), cancellable = true)
    private static void townstead$stripEffectTags(String phrase, CallbackInfoReturnable<String> cir) {
        String cleaned = cir.getReturnValue();
        if (cleaned == null || cleaned.indexOf('<') < 0) return;
        cir.setReturnValue(EffectTagParser.stripTags(cleaned).trim());
    }
}
