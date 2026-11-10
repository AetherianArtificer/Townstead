package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.compat.mcacapitals.CapitalsDialogueBridge;
import com.aetherianartificer.townstead.dialogue.McaVoiceFallback;
import com.aetherianartificer.townstead.dialogue.SpeciesVoice;
import com.aetherianartificer.townstead.root.LifeStageProgression;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Routes every spoken villager line through Townstead's species-derived voice. {@code getTranslatable}
 * is the single chokepoint that builds every line (chat, greetings, barks, dialogue answers); MCA
 * leaves it as a {@code Messenger} interface default, so we override it here on the concrete villager
 * (interface-default {@code @Inject} is unsupported by the Mixin AP). When a species/origin defines
 * lines for the phrase we return them resolved server-side; otherwise we reproduce MCA's exact marker
 * key ({@link McaVoiceFallback}) so its generic/personality pools resolve unchanged. MCA Capitals adds
 * this method too and only one can stay, so the priority makes it this one, whatever the load order,
 * and a phrase no voice has lines for goes to Capitals first.
 */
@Mixin(value = VillagerEntityMCA.class, priority = 1100)
public abstract class MessengerVoiceMixin {

    public MutableComponent getTranslatable(Player target, String phraseId, Object... params) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        MutableComponent line = SpeciesVoice.line(self, target, phraseId, params);
        if (line == null) line = CapitalsDialogueBridge.line(self, target, phraseId);
        return line != null ? line : McaVoiceFallback.build(self, target, phraseId, params);
    }

    /**
     * A villager at a {@code talkable:false} life stage (e.g. an egg) can't form real words, so MCA
     * babbles its lines like a baby ({@code transformMessage}) and skips TTS. You can still open its
     * GUI, edit it, and trade. Falls back to MCA's own baby gate, which this overrides (the interface
     * default lives on {@code VillagerLike}, so we redeclare it on the concrete villager).
     */
    public boolean isToYoungToSpeak() {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        return LifeStageProgression.isMuteStage(self) || self.getAgeState() == AgeState.BABY;
    }
}
