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

    public static QuestLedgerService createDefault() {
        return new QuestLedgerService(List.of(
                new McaQuestsProvider(),
                new BountifulProvider(),
                new FtbQuestsProvider(),
                new VanillaAdvancementProvider()));
    }
}
