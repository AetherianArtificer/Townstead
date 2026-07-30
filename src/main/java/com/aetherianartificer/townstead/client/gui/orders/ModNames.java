package com.aetherianartificer.townstead.client.gui.orders;

import java.util.Locale;

/**
 * A namespace as a player would name it: "farmersdelight" becomes "Farmer's Delight".
 *
 * <p>Read from the mod's own metadata, which is the same source JEI and REI use, so a pack's items
 * group under the heading the player already sees everywhere else. Falls back to a title-cased
 * namespace when a mod is not installed under that id — a data pack can add items for a namespace
 * that has no mod behind it at all.</p>
 */
public final class ModNames {

    private ModNames() {}

    public static String of(String namespace) {
        if (namespace == null || namespace.isEmpty()) return "Unknown";
        if ("minecraft".equals(namespace)) return "Vanilla";
        String display = lookup(namespace);
        return display != null ? display : titleCase(namespace);
    }

    private static String lookup(String namespace) {
        try {
            //? if neoforge {
            return net.neoforged.fml.ModList.get()
                    .getModContainerById(namespace)
                    .map(c -> c.getModInfo().getDisplayName())
                    .orElse(null);
            //?} else {
            /*return net.minecraftforge.fml.ModList.get()
                    .getModContainerById(namespace)
                    .map(c -> c.getModInfo().getDisplayName())
                    .orElse(null);
            *///?}
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String titleCase(String raw) {
        String spaced = raw.replace('_', ' ').replace('-', ' ');
        StringBuilder out = new StringBuilder(spaced.length());
        boolean startOfWord = true;
        for (char c : spaced.toCharArray()) {
            if (c == ' ') {
                startOfWord = true;
                out.append(c);
                continue;
            }
            out.append(startOfWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
            startOfWord = false;
        }
        return out.toString().trim();
    }
}
