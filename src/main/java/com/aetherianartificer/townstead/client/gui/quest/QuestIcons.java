package com.aetherianartificer.townstead.client.gui.quest;

import com.aetherianartificer.townstead.quest.QuestObjective;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The Quest Ledger's pixel icons, in {@code textures/gui/quest}. Most are 9x9 so they sit on a
 * line of text; the quest letter is 16x16 like an item. A provider can name one as a quest's or an
 * objective's icon with {@link #PREFIX}, for example {@code townstead:quest_icon/letter}.
 */
public final class QuestIcons {
    private QuestIcons() {}

    public static final String PREFIX = "townstead:quest_icon/";

    public enum Icon {
        OPEN(9), DONE(9), FAILED(9), WAITING(9), TALK(9), REWARD(9), OBJECTIVES(9), STORY(9),
        PINNED(9), TRACKED(9), DETAILS(9), LETTER(16);

        final int size;
        final ResourceLocation texture;

        Icon(int size) {
            this.size = size;
            this.texture = ResourceLocation.tryParse("townstead:textures/gui/quest/" + name().toLowerCase(Locale.ROOT) + ".png");
        }

        public int size() { return size; }
    }

    public static void draw(GuiGraphics g, Icon icon, int x, int y) {
        g.blit(icon.texture, x, y, 0, 0, icon.size, icon.size, icon.size, icon.size);
    }

    /** The icon a {@link #PREFIX} id names, or null for an item id. */
    public static @Nullable Icon named(@Nullable String id) {
        if (id == null || !id.startsWith(PREFIX)) return null;
        try {
            return Icon.valueOf(id.substring(PREFIX.length()).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** The state mark for an objective. A named icon stands in until the objective is done. */
    public static Icon forObjective(QuestObjective objective) {
        if (objective.done()) return Icon.DONE;
        if (objective.status() == QuestObjective.Status.FAILED) return Icon.FAILED;
        Icon named = named(objective.iconItemId());
        if (named != null) return named;
        return objective.status() == QuestObjective.Status.UNAVAILABLE ? Icon.WAITING : Icon.OPEN;
    }
}
