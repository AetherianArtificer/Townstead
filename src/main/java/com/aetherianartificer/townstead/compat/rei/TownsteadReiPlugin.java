package com.aetherianartificer.townstead.compat.rei;

import com.aetherianartificer.townstead.switchboard.ContentGates;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import me.shedaniel.rei.api.client.entry.filtering.base.BasicFilteringRule;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Keeps items of switched-off systems out of REI, and brings them back when their system returns. */
@REIPluginClient
public class TownsteadReiPlugin implements REIClientPlugin {
    private static BasicFilteringRule.MarkDirty rule;
    private static boolean listening;

    @Override
    public void registerBasicEntryFiltering(BasicFilteringRule<?> filtering) {
        rule = filtering.hide(TownsteadReiPlugin::gated);
        if (!listening) {
            listening = true;
            Switchboard.onChange(() -> Minecraft.getInstance().execute(() -> {
                BasicFilteringRule.MarkDirty current = rule;
                if (current != null) current.markDirty();
            }));
        }
    }

    private static Collection<EntryStack<?>> gated() {
        List<EntryStack<?>> out = new ArrayList<>();
        for (Item item : ContentGates.gatedItems()) out.add(EntryStacks.of(item));
        return out;
    }
}
