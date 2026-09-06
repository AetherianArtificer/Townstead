package com.aetherianartificer.townstead.dialogue.contextual;

import java.util.LinkedHashSet;
import java.util.Set;

/** Semantic hooks supplied by a social system; unknown/modded tags are intentionally preserved. */
public record DialogueRequest(String intent, Set<String> context, Set<String> personality,
                              Set<String> relationship) {
    public DialogueRequest {
        intent = DialoguePalette.normalized(intent);
        context = normalize(context); personality = normalize(personality); relationship = normalize(relationship);
    }

    private static Set<String> normalize(Set<String> source) {
        if (source == null || source.isEmpty()) return Set.of();
        Set<String> out = new LinkedHashSet<>();
        for (String value : source) {
            String normalized = DialoguePalette.normalized(value);
            if (!normalized.isEmpty()) out.add(normalized);
        }
        return Set.copyOf(out);
    }
}
