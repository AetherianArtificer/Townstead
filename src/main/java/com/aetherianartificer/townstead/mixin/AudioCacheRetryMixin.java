package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.tts.QueuedPhraseRetry;
import net.conczin.mca.client.tts.AudioCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * Gives a queued TTS phrase one more chance instead of dropping the line. See
 * {@link QueuedPhraseRetry} for why the server answers empty in the first place.
 */
@Mixin(value = AudioCache.class, remap = false)
public class AudioCacheRetryMixin {

    @Inject(method = "cachedRetrieve", at = @At("RETURN"), cancellable = true)
    private static void townstead$retryQueuedPhrase(String identifier, Consumer<OutputStream> retriever,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        if (QueuedPhraseRetry.retryOnce(identifier, retriever)) {
            cir.setReturnValue(true);
        }
    }
}
