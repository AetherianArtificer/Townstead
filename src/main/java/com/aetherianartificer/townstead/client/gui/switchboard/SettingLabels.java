package com.aetherianartificer.townstead.client.gui.switchboard;

import com.aetherianartificer.townstead.switchboard.SettingIndex;
import net.minecraft.ChatFormatting;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Names and tooltips for config settings, from the existing {@code townstead.configuration.*} lang. */
final class SettingLabels {
    private SettingLabels() {}

    private static final String LANG = "townstead.configuration.";

    static Component label(SettingIndex.Entry entry) {
        String key = LANG + entry.key();
        if (I18n.exists(key)) return Component.translatable(key);
        String translation = SettingIndex.translationKey(entry);
        if (translation != null && I18n.exists(translation)) return Component.translatable(translation);
        return Component.literal(pretty(entry.key().substring(entry.key().lastIndexOf('.') + 1)));
    }

    static Component groupLabel(String group) {
        String key = LANG + group;
        Component text = I18n.exists(key) ? Component.translatable(key)
                : Component.literal(pretty(group.substring(group.lastIndexOf('.') + 1)));
        return text.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW);
    }

    static @Nullable Component tooltip(SettingIndex.Entry entry, boolean locked) {
        String key = LANG + entry.key() + ".tooltip";
        Component text = null;
        if (I18n.exists(key)) {
            text = Component.translatable(key);
        } else {
            String comment = SettingIndex.comment(entry);
            if (comment != null && !comment.isBlank()) text = Component.literal(comment.lines().findFirst().orElse(""));
        }
        if (!locked) return text;
        Component lockedText = Component.translatable("townstead.switchboard.locked").withStyle(ChatFormatting.GOLD);
        return text == null ? lockedText : text.copy().append("\n").append(lockedText);
    }

    /** "PACK_DECIDED" and "farmerFarmRadius" both read as words. */
    static String pretty(String raw) {
        String spaced = raw.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT).trim();
        return spaced.isEmpty() ? raw : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
