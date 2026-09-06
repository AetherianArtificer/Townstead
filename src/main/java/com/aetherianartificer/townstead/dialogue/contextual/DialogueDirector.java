package com.aetherianartificer.townstead.dialogue.contextual;

import com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

/** Adds stable world/personality/counterpart hooks, then delegates repetition control to the selector. */
public final class DialogueDirector {
    private static final DialogueSelector SELECTOR = new DialogueSelector();

    private DialogueDirector() {}

    public static boolean speak(VillagerEntityMCA speaker, String intent, Set<String> authoredContext,
                                Set<String> authoredRelationships, LivingEntity counterpart) {
        return speak(speaker, intent, authoredContext, authoredRelationships, counterpart, false);
    }

    /** Debug path: preserve the shuffle-bag history but do not make testers wait out chat cadence. */
    public static boolean speakDebug(VillagerEntityMCA speaker, String intent, LivingEntity counterpart) {
        return speak(speaker, intent, Set.of(), Set.of(), counterpart, true);
    }

    private static boolean speak(VillagerEntityMCA speaker, String intent, Set<String> authoredContext,
                                 Set<String> authoredRelationships, LivingEntity counterpart,
                                 boolean bypassCooldown) {
        if (!bypassCooldown && com.aetherianartificer.townstead.dialogue.conversation.ConversationEngine.active(speaker.getUUID())) return false;
        DialogueRequest request = describe(speaker, intent, authoredContext, authoredRelationships, counterpart);
        java.util.Optional<DialoguePalette.Line> line = SELECTOR.select(speaker.getUUID(), request,
                ContextualDialogue.forIntent(intent), speaker.level().getGameTime(),
                new Random(speaker.getRandom().nextLong()), bypassCooldown);
        if (line.isEmpty()) return false;
        speaker.sendChatToAllAround(line.get().translation());
        return true;
    }

    /** Shared context vocabulary for standalone lines and connected conversations. */
    public static DialogueRequest describe(VillagerEntityMCA speaker, String intent, Set<String> authoredContext,
                                           Set<String> authoredRelationships, LivingEntity counterpart) {
        Set<String> context = new LinkedHashSet<>(authoredContext);
        if (speaker.level().isDay()) context.add("time:day"); else context.add("time:night");
        if (speaker.level().isRaining()) context.add("weather:rain"); else context.add("weather:clear");
        ResourceLocation profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(
                speaker.getVillagerData().getProfession());
        if (profession != null) context.add("profession:" + profession);
        context.add("profession:" + com.aetherianartificer.townstead.profession.ProfessionIdentity.rawId(speaker));
        var needs = com.aetherianartificer.townstead.villager.TownsteadVillagers.get(speaker).needs();
        if (needs.hunger() < 70) context.add("need:hungry");
        if (needs.thirst() < com.aetherianartificer.townstead.thirst.ThirstData.ADEQUATE_THRESHOLD) context.add("need:thirsty");
        if (needs.fatigue() >= com.aetherianartificer.townstead.fatigue.FatigueData.TIRED_THRESHOLD) context.add("need:tired");
        if (counterpart != null) {
            context.add("counterpart:present");
            context.add(counterpart instanceof Player ? "counterpart:player" : "counterpart:entity");
        }
        Set<String> personality = Set.of(McaPersonalityCompat.id(
                speaker.getVillagerBrain().getPersonality()).toLowerCase(java.util.Locale.ROOT));
        Set<String> relationships = new LinkedHashSet<>(authoredRelationships);
        if (counterpart != null) {
            for (com.aetherianartificer.townstead.social.Bond bond
                    : com.aetherianartificer.townstead.social.Bonds.of(speaker).all()) {
                if (bond.active() && counterpart.getUUID().equals(bond.other())) {
                    String kind = bond.kind().toLowerCase(java.util.Locale.ROOT);
                    relationships.add(kind);
                    int separator = kind.indexOf(':');
                    relationships.add(separator >= 0 ? kind.substring(separator + 1) : kind);
                    if (kind.contains("marriage") || kind.contains("partner")) relationships.add("partner");
                    if (kind.contains("friend")) relationships.add("friend");
                    if (kind.contains("family") || kind.contains("parent") || kind.contains("sibling")) relationships.add("family");
                }
            }
        }
        if (relationships.contains("partner") || relationships.contains("friend") || relationships.contains("family")
                || relationships.contains("familiar")) relationships.remove("stranger");
        if (counterpart != null && relationships.isEmpty()) relationships.add("stranger");
        return new DialogueRequest(intent, context, personality, relationships);
    }

    static void clear() { SELECTOR.clear(); }
}
