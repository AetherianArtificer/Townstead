package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.tts.QueuedPhraseRetry;
import net.conczin.mca.client.tts.AudioCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives a queued TTS phrase one more chance instead of dropping the line. See
 * {@link QueuedPhraseRetry} for why the server answers empty in the first place.
 */
@Mixin(value = AudioCache.class, remap = false)
public class AudioCacheRetryMixin {

    @Inject(method = "cachedRetrieve", at = @At("RETURN"), cancellable = true)
    // The retriever is coerced to Object: MCA changed its type (Consumer, then a package-private
    // AudioRetriever), and a handler that names either fails on the other build.
    private static void townstead$retryQueuedPhrase(String identifier, @Coerce Object retriever,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (QueuedPhraseRetry.retryOnce(identifier, retriever)) {
            cir.setReturnValue(true);
        }
    }
}
