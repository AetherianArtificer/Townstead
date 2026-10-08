package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;

/** Human wording for semantic recipe selectors that do not have their own translation key. */
public final class RequirementLabels {
    private RequirementLabels() {}

    public static String tagName(ResourceLocation tag) {
        if (tag == null) return "Item";
        String path = tag.getPath();
        String leaf = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
        String[] words = leaf.split(" ");
        if (words.length == 0) return "Item";
        int last = words.length - 1;
        if (words[last].endsWith("knives")) {
            words[last] = words[last].substring(0, words[last].length() - "knives".length()) + "knife";
        } else if (words[last].endsWith("ies") && words[last].length() > 3) {
            words[last] = words[last].substring(0, words[last].length() - 3) + "y";
        } else if (words[last].endsWith("s") && words[last].length() > 1) {
            words[last] = words[last].substring(0, words[last].length() - 1);
        }
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? "Item" : out.toString();
    }

    /** Stable choice when compatibility aliases resolve to the same concrete item set. */
    static ResourceLocation preferredTagAlias(ResourceLocation left, ResourceLocation right) {
        Comparator<ResourceLocation> preference = Comparator
                .comparingInt((ResourceLocation id) -> commonNamespaceRank(id.getNamespace()))
                .thenComparingInt(id -> pluralPenalty(id.getPath()))
                .thenComparingInt(id -> id.getPath().length())
                .thenComparing(ResourceLocation::toString);
        return preference.compare(left, right) <= 0 ? left : right;
    }

    private static int commonNamespaceRank(String namespace) {
        return switch (namespace) {
            case "c" -> 0;
            case "forge", "neoforge" -> 1;
            case "minecraft" -> 2;
            default -> 3;
        };
    }

    private static int pluralPenalty(String path) {
        String leaf = path.substring(path.lastIndexOf('/') + 1);
        return leaf.endsWith("s") ? 1 : 0;
    }
}
