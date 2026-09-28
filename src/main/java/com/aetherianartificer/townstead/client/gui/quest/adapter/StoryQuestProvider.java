package com.aetherianartificer.townstead.client.gui.quest.adapter;

import com.aetherianartificer.townstead.client.story.StoryClient;
import com.aetherianartificer.townstead.quest.QuestEntry;
import com.aetherianartificer.townstead.quest.QuestObjective;
import com.aetherianartificer.townstead.quest.QuestProvider;
import com.aetherianartificer.townstead.quest.QuestState;
import com.aetherianartificer.townstead.story.net.StoryQuestSyncS2CPayload;
import com.aetherianartificer.townstead.switchboard.Systems;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Quests villagers give in their stories, grouped by the villager who gave them. */
public final class StoryQuestProvider implements QuestProvider {
    @Override public String id() { return "townstead_stories"; }
    @Override public String displayName() { return I18n.get("townstead.story.provider"); }

    @Override
    public boolean usesLocalTracker() {
        return true;
    }

    @Override
    public boolean isAvailable() {
        return Systems.on(Systems.STORIES);
    }

    @Override
    public Object changeToken() {
        return StoryClient.quests();
    }

    @Override
    public List<QuestEntry> loadQuests() {
        List<QuestEntry> out = new ArrayList<>();
        for (StoryQuestSyncS2CPayload.Quest quest : StoryClient.quests()) {
            List<QuestObjective> objectives = new ArrayList<>();
            for (StoryQuestSyncS2CPayload.Objective objective : quest.objectives()) {
                objectives.add(new QuestObjective(objective.label(), objective.current(),
                        objective.total() > 1 ? objective.total() : 0L,
                        objective.done() ? QuestObjective.Status.DONE : QuestObjective.Status.PENDING, ""));
            }
            if (quest.handBack()) {
                objectives.add(new QuestObjective(I18n.get("townstead.story.quest.talk_to", quest.teller()), 0L, 0L,
                        quest.state() == 2 ? QuestObjective.Status.DONE
                                : quest.state() == 1 ? QuestObjective.Status.PENDING : QuestObjective.Status.UNAVAILABLE,
                        com.aetherianartificer.townstead.client.gui.quest.QuestIcons.PREFIX + "talk"));
            }
            QuestState state = quest.state() == 2 ? QuestState.COMPLETE : QuestState.ACTIVE;
            out.add(new QuestEntry(id(), displayName(), quest.id(), quest.title(), quest.about(), quest.teller(),
                    com.aetherianartificer.townstead.client.gui.quest.QuestIcons.PREFIX + "letter", state, objectives, List.of(), Set.of(),
                    false, false, List.of(), I18n.get("townstead.story.quest.teller", quest.teller())));
        }
        return out;
    }
}
