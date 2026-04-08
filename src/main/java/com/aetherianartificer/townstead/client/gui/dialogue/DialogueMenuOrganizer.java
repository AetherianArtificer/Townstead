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
            Component text = Component.translatable(displayKey);
            if (isHub()) {
                return Component.literal("").append(text).append(Component.literal(" \u2192"));
            }
            return text;
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

    private static boolean isKnownAnswer(String answer) {
        for (HubEntry entry : TOP_LEVEL) {
            if (entry.isLeaf() && answer.equals(entry.mcaAnswer())) return true;
        }
        return SUB_MENU_ANSWERS.contains(answer);
    }
}
