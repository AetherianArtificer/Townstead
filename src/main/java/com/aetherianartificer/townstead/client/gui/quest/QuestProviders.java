package com.aetherianartificer.townstead.client.gui.quest;

import com.aetherianartificer.townstead.client.gui.quest.adapter.BountifulProvider;
import com.aetherianartificer.townstead.client.gui.quest.adapter.FtbQuestsProvider;
import com.aetherianartificer.townstead.client.gui.quest.adapter.McaQuestsProvider;
import com.aetherianartificer.townstead.client.gui.quest.adapter.VanillaAdvancementProvider;
import com.aetherianartificer.townstead.quest.QuestLedgerService;

import java.util.List;

/** Constructs the client ledger without loading any optional provider classes. */
public final class QuestProviders {
    private QuestProviders() {}

    public static final com.aetherianartificer.townstead.quest.LocalTracker LOCAL_TRACKER =
            new com.aetherianartificer.townstead.quest.LocalTracker() {
                @Override public boolean isTracked(String key) { return QuestTracker.isTracked(key); }
                @Override public boolean toggle(String key) { return QuestTracker.toggle(key); }
            };

    public static QuestLedgerService createDefault() {
        return new QuestLedgerService(List.of(
                new McaQuestsProvider(),
                new com.aetherianartificer.townstead.client.gui.quest.adapter.StoryQuestProvider(),
                new BountifulProvider(),
                new FtbQuestsProvider(),
                new VanillaAdvancementProvider()), LOCAL_TRACKER);
    }

    /** Only the sources that use Townstead's tracker, for the tracker HUD. */
    public static QuestLedgerService createLocallyTracked() {
        return new QuestLedgerService(List.of(
                new com.aetherianartificer.townstead.client.gui.quest.adapter.StoryQuestProvider(),
                new BountifulProvider(),
                new VanillaAdvancementProvider()), LOCAL_TRACKER);
    }
}
