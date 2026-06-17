package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.dialogue.McaVoiceFallback;
import com.aetherianartificer.townstead.dialogue.SpeciesVoice;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Routes every spoken villager line through Townstead's species-derived voice. {@code getTranslatable}
 * is the single chokepoint that builds every line (chat, greetings, barks, dialogue answers); MCA
 * leaves it as a {@code Messenger} interface default, so we override it here on the concrete villager
 * (interface-default {@code @Inject} is unsupported by the Mixin AP). When a species/origin defines
 * lines for the phrase we return them resolved server-side; otherwise we reproduce MCA's exact marker
 * key ({@link McaVoiceFallback}) so its generic/personality pools resolve unchanged.
 */
@Mixin(VillagerEntityMCA.class)
public abstract class MessengerVoiceMixin {

    public MutableComponent getTranslatable(Player target, String phraseId, Object... params) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        MutableComponent line = SpeciesVoice.line(self, target, phraseId, params);
        return line != null ? line : McaVoiceFallback.build(self, target, phraseId, params);
    }
}
