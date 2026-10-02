package com.aetherianartificer.townstead.client.gui.quest;

import com.aetherianartificer.townstead.client.building.BuildingPinHud;
import com.aetherianartificer.townstead.quest.QuestEntry;
import com.aetherianartificer.townstead.quest.QuestLedgerService;
import com.aetherianartificer.townstead.quest.QuestObjective;
import com.aetherianartificer.townstead.quest.QuestProviderInfo;
import com.aetherianartificer.townstead.quest.QuestState;
import com.aetherianartificer.townstead.switchboard.Systems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The quests tracked with Townstead's tracker, top right under the building pin. Reads its
 * providers about once a second, and only while something is tracked.
 */
public final class QuestTrackerHud {
    private QuestTrackerHud() {}

    private static final int REFRESH_TICKS = 20;
    private static final int MAX_OBJECTIVES = 3;
    private static final int TITLE_HEIGHT = 18;
    private static final int ROW_HEIGHT = 11;

    private static QuestLedgerService ledger;
    private static List<QuestEntry> shown = List.of();
    private static long nextRefresh;

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || minecraft.options.hideGui || !Systems.on(Systems.QUESTS)) return;
        List<String> keys = QuestTracker.keys();
        if (keys.isEmpty()) {
            shown = List.of();
            return;
        }
        long now = minecraft.level.getGameTime();
        if (ledger == null) ledger = QuestProviders.createLocallyTracked();
        if (now >= nextRefresh || ledger.hasExternalChanges()) {
            nextRefresh = now + REFRESH_TICKS;
            refresh(keys);
        }
        if (shown.isEmpty()) return;
        draw(graphics, minecraft.font);
    }

    private static void refresh(List<String> keys) {
        ledger.refresh();
        Map<String, QuestEntry> byKey = new HashMap<>();
        for (QuestEntry entry : ledger.snapshot().quests()) byKey.put(entry.key(), entry);
        Map<String, QuestProviderInfo> providers = new HashMap<>();
        for (QuestProviderInfo info : ledger.snapshot().providers()) providers.put(info.id(), info);
        List<QuestEntry> next = new ArrayList<>();
        for (String key : keys) {
            QuestEntry entry = byKey.get(key);
            if (entry != null && entry.state() != QuestState.COMPLETE) {
                next.add(entry);
                continue;
            }
            // Drop a finished or vanished quest, but only once its source has answered cleanly.
            QuestProviderInfo info = providers.get(key.substring(0, Math.max(0, key.indexOf(':'))));
            if (info != null && info.available() && info.error().isEmpty()) QuestTracker.untrack(key);
        }
        shown = List.copyOf(next);
    }

    private static void draw(GuiGraphics graphics, Font font) {
        int screenW = graphics.guiWidth();
        int width = Math.max(136, Math.min(168, screenW - 16));
        int x = screenW - width - 8;
        int y = BuildingPinHud.lastBottom() > 0 ? BuildingPinHud.lastBottom() + 4 : 8;

        List<List<QuestObjective>> rows = new ArrayList<>();
        int height = 0;
        for (QuestEntry quest : shown) {
            // Steps that cannot be done yet, such as handing a quest back early, wait in the ledger.
            List<QuestObjective> open = quest.objectives().stream()
                    .filter(o -> !o.done() && o.status() != QuestObjective.Status.UNAVAILABLE)
                    .limit(MAX_OBJECTIVES).toList();
            rows.add(open);
            height += TITLE_HEIGHT + 2;
            for (QuestObjective objective : open) height += lines(font, objective, width).size() * ROW_HEIGHT;
        }
        height += shown.size() - 1;
        graphics.fill(x, y, x + width, y + height, 0xC8120E0A);

        int top = y;
        for (int i = 0; i < shown.size(); i++) {
            QuestEntry quest = shown.get(i);
            if (i > 0) {
                graphics.fill(x, top, x + width, top + 1, 0x667F6840);
                top += 1;
            }
            QuestIcons.Icon named = QuestIcons.named(quest.iconItemId());
            ItemStack icon = named != null ? ItemStack.EMPTY : icon(quest.iconItemId());
            if (named != null) QuestIcons.draw(graphics, named, x + 4, top + 1);
            else if (!icon.isEmpty()) graphics.renderItem(icon, x + 4, top + 1);
            int titleX = x + (named == null && icon.isEmpty() ? 5 : 23);
            String title = font.plainSubstrByWidth(quest.title(), Math.max(24, x + width - 5 - titleX));
            int titleColor = rows.get(i).isEmpty() ? 0xFF7FCE70 : 0xFFF2E4C1;
            graphics.drawString(font, title, titleX, top + 5, titleColor, false);
            top += TITLE_HEIGHT;
            for (QuestObjective objective : rows.get(i)) {
                String count = count(objective);
                List<net.minecraft.util.FormattedCharSequence> lines = lines(font, objective, width);
                QuestIcons.draw(graphics, QuestIcons.forObjective(objective), x + 7, top);
                for (int line = 0; line < lines.size(); line++) {
                    graphics.drawString(font, lines.get(line), x + 19, top + 1, 0xFFD8D0C2, false);
                    if (line == 0 && !count.isEmpty()) {
                        graphics.drawString(font, count, x + width - 5 - font.width(count), top + 1, 0xFFB9A578, false);
                    }
                    top += ROW_HEIGHT;
                }
            }
            top += 2;
        }
    }

    private static String count(QuestObjective objective) {
        return objective.total() > 0 ? objective.current() + "/" + objective.total() : "";
    }

    /** An objective's label wrapped to at most two lines beside its count. */
    private static List<net.minecraft.util.FormattedCharSequence> lines(Font font, QuestObjective objective, int width) {
        String count = count(objective);
        int textWidth = Math.max(20, width - 24 - (count.isEmpty() ? 0 : font.width(count) + 4));
        List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(net.minecraft.network.chat.Component.literal(objective.label()), textWidth);
        return lines.size() > 2 ? lines.subList(0, 2) : lines;
    }

    private static ItemStack icon(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(id));
    }
}
