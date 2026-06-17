package com.aetherianartificer.townstead.dialogue;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Locale;

/**
 * MCA's own dialogue-key build, mirrored so Townstead can override {@code Messenger.getTranslatable}
 * (the single line chokepoint) yet still fall back to MCA's exact behaviour for any phrase a species
 * doesn't voice. Keep this in sync with {@code Messenger#getTranslatable}: it assembles the
 * {@code #G<gender>.#E<personality>.#P<profession>.#T<type>.<phrase>} marker key MCA's client resolver
 * expects, with {@code params[0]} = the target's name.
 */
public final class McaVoiceFallback {

    private McaVoiceFallback() {}

    public static MutableComponent build(VillagerEntityMCA villager, Player target, String phraseId, Object[] params) {
        try {
            // Player name for %1$s. MCA prefers a family-tree nickname via Messenger.getName, but that
            // static isn't on every MCA compile jar (forge libs lag), so use the display name on both.
            String targetName = target.getName().getString();
            String genderString = "";
            if (target instanceof ServerPlayer serverPlayer) {
                genderString = "#G" + PlayerSaveData.get(serverPlayer).getGender().name().toLowerCase(Locale.ROOT) + ".";
            }

            Object[] newParams = new Object[params.length + 1];
            System.arraycopy(params, 0, newParams, 1, params.length);
            newParams[0] = targetName;

            String professionString = "";
            if (!villager.isBaby()) {
                professionString = "#P" + BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getProfession()).getPath() + ".";
            }
            String personalityString = "#E" + villager.getVillagerBrain().getPersonality().name() + ".";

            return Component.translatable(genderString + personalityString + professionString
                    + "#T" + villager.getDialogueType(target).name() + "." + phraseId, newParams);
        } catch (Exception e) {
            // Defensive: never let a dialogue line crash; degrade to the bare phrase.
            return Component.translatable(phraseId);
        }
    }
}
