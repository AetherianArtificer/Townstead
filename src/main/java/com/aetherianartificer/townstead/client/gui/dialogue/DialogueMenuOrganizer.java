package com.aetherianartificer.townstead.client.gui.dialogue;

import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Reorganizes MCA's flat "main" dialogue menu into an RPG-style two-tier hub.
 * Social options show directly; romance/adventurer options are behind hub prompts
 * that expand into sub-menus. All client-side — MCA answer names are preserved
 * for server communication.
 */
public final class DialogueMenuOrganizer {
    private DialogueMenuOrganizer() {}

    /** A choice entry that either sends a dialogue answer or opens a sub-menu. */
    public record HubEntry(String displayKey, String mcaAnswer, String subMenuId) {
        /** Leaf entry — clicking sends the MCA answer. */
        static HubEntry leaf(String displayKey, String mcaAnswer) {
            return new HubEntry(displayKey, mcaAnswer, null);
        }
        /** Hub entry — clicking opens a sub-menu. */
        static HubEntry hub(String displayKey, String subMenuId) {
            return new HubEntry(displayKey, null, subMenuId);
        }

        boolean isHub() { return subMenuId != null; }
        boolean isLeaf() { return mcaAnswer != null; }

        Component displayText() {
            return Component.translatable(displayKey);
        }
    }

    // Top-level choices (social shown directly, categories as hubs)
    private static final List<HubEntry> TOP_LEVEL = List.of(
            HubEntry.leaf("townstead.dialogue.main.chat", "chat"),
            HubEntry.leaf("townstead.dialogue.main.joke", "joke"),
            HubEntry.leaf("townstead.dialogue.main.story", "story"),
            HubEntry.leaf("townstead.dialogue.main.hug", "hug"),
            HubEntry.hub("townstead.dialogue.main.romance", "romance"),
            HubEntry.hub("townstead.dialogue.main.adventurer", "adventurer"),
            HubEntry.leaf("townstead.dialogue.main.apologize", "apologize"),
            HubEntry.leaf("townstead.dialogue.main.rumors", "rumors"),
            HubEntry.leaf("townstead.dialogue.main.rock_paper_scissor", "rock_paper_scissor"),
            HubEntry.leaf("townstead.dialogue.main.adopt", "adopt")
    );

    // Sub-menu definitions
    private static final Map<String, List<HubEntry>> SUB_MENUS = Map.of(
            "romance", List.of(
                    HubEntry.leaf("townstead.dialogue.main.flirt", "flirt"),
                    HubEntry.leaf("townstead.dialogue.main.kiss", "kiss"),
                    HubEntry.leaf("townstead.dialogue.main.procreate", "procreate"),
                    HubEntry.leaf("townstead.dialogue.main.procreate_engaged", "procreate_engaged"),
                    HubEntry.leaf("townstead.dialogue.main.divorceInitiate", "divorceInitiate"),
                    HubEntry.leaf("townstead.dialogue.main.divorcePapers", "divorcePapers")
            ),
            "adventurer", List.of(
                    HubEntry.leaf("townstead.dialogue.main.hire", "hire"),
                    HubEntry.leaf("townstead.dialogue.main.stay", "stay")
            )
    );

    // All MCA answers that belong to sub-menus (so they're hidden from top level)
    private static final Set<String> SUB_MENU_ANSWERS = new HashSet<>();
    static {
        for (List<HubEntry> entries : SUB_MENUS.values()) {
            for (HubEntry entry : entries) {
                if (entry.isLeaf()) SUB_MENU_ANSWERS.add(entry.mcaAnswer());
            }
        }
    }

    /**
     * Given the "main" question's available answers (filtered by MCA's constraint system),
     * build the top-level hub entries. Hub prompts are only shown if at least one of their
     * sub-menu answers is available.
     */
    public static List<HubEntry> buildTopLevel(List<String> availableAnswers) {
        Set<String> available = new HashSet<>(availableAnswers);
        List<HubEntry> result = new ArrayList<>();

        for (HubEntry template : TOP_LEVEL) {
            if (template.isLeaf()) {
                if (available.contains(template.mcaAnswer())) {
                    result.add(template);
                }
            } else {
                // Hub — only show if at least one sub-menu answer is available
                List<HubEntry> subEntries = SUB_MENUS.get(template.subMenuId());
                if (subEntries != null && subEntries.stream()
                        .anyMatch(e -> e.isLeaf() && available.contains(e.mcaAnswer()))) {
                    result.add(template);
                }
            }
        }

        // Append any unknown answers not covered by our mapping (modded content, etc.)
        for (String answer : availableAnswers) {
            if (!isKnownAnswer(answer)) {
                result.add(HubEntry.leaf("dialogue.main." + answer, answer));
            }
        }

        return result;
    }

    /**
     * Build the sub-menu entries for a given sub-menu ID, filtered by available answers.
     */
    public static List<HubEntry> buildSubMenu(String subMenuId, List<String> availableAnswers) {
        Set<String> available = new HashSet<>(availableAnswers);
        List<HubEntry> subEntries = SUB_MENUS.get(subMenuId);
        if (subEntries == null) return List.of();

        List<HubEntry> result = new ArrayList<>();
        for (HubEntry entry : subEntries) {
            if (entry.isLeaf() && available.contains(entry.mcaAnswer())) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Whether this is the "main" question that should use hub mode. */
    public static boolean isMainQuestion(String questionId) {
        return "main".equals(questionId);
    }

    /**
     * RPG phrasing overrides for sub-dialogue answers.
     * Key: "questionId.answerId" → Townstead translation key.
     */
    private static final Map<String, String> RPG_PHRASING = new HashMap<>();
    static {
        // Greetings
        rpg("greet", "short", "townstead.dialogue.greet.short");
        rpg("greet", "kind", "townstead.dialogue.greet.kind");
        rpg("greet", "shake_hand", "townstead.dialogue.greet.shake_hand");

        // Jokes
        rpg("joke", "creative", "townstead.dialogue.joke.creative");
        rpg("joke", "animal", "townstead.dialogue.joke.animal");
        rpg("joke", "monster", "townstead.dialogue.joke.monster");

        // Stories
        rpg("story", "generic", "townstead.dialogue.story.generic");
        rpg("story", "exploring", "townstead.dialogue.story.exploring");
        rpg("story", "nether", "townstead.dialogue.story.nether");
        rpg("story", "enderdragon", "townstead.dialogue.story.enderdragon");
        rpg("story", "wither", "townstead.dialogue.story.wither");

        // Hire
        rpg("hire", "short", "townstead.dialogue.hire.short");
        rpg("hire", "long", "townstead.dialogue.hire.long");
        rpg("hire", "cancel", "townstead.dialogue.hire.cancel");

        // Confirmations
        rpg("procreate", "confirm", "townstead.dialogue.confirm.yes");
        rpg("procreate", "cancel", "townstead.dialogue.confirm.no");
        rpg("procreate_engaged", "confirm", "townstead.dialogue.confirm.yes");
        rpg("procreate_engaged", "cancel", "townstead.dialogue.confirm.no");
        rpg("divorce", "confirm", "townstead.dialogue.confirm.yes");
        rpg("divorce", "cancel", "townstead.dialogue.confirm.no");
        rpg("adopt", "confirm", "townstead.dialogue.confirm.yes");
        rpg("adopt", "cancel", "townstead.dialogue.confirm.no");

        // Rock Paper Scissors
        rpg("rock_paper_scissor", "rock", "townstead.dialogue.rps.rock");
        rpg("rock_paper_scissor", "paper", "townstead.dialogue.rps.paper");
        rpg("rock_paper_scissor", "scissor", "townstead.dialogue.rps.scissor");

        // First meeting
        rpg("first.question", "exploring", "townstead.dialogue.first.exploring");
        rpg("first.question", "settling", "townstead.dialogue.first.settling");
        rpg("first.question", "spent_night", "townstead.dialogue.first.spent_night");
    }

    private static void rpg(String question, String answer, String langKey) {
        RPG_PHRASING.put(question + "." + answer, langKey);
    }

    /**
     * Get the RPG-phrased translation key for a dialogue answer.
     * Returns null if no override exists (caller should fall back to MCA's default).
     */
    public static String getRpgPhrasing(String questionId, String answerId) {
        return RPG_PHRASING.get(questionId + "." + answerId);
    }

    private static boolean isKnownAnswer(String answer) {
        for (HubEntry entry : TOP_LEVEL) {
            if (entry.isLeaf() && answer.equals(entry.mcaAnswer())) return true;
        }
        return SUB_MENU_ANSWERS.contains(answer);
    }
}
