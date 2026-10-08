package com.aetherianartificer.townstead.client.story;

import com.aetherianartificer.townstead.client.gui.dialogue.RpgDialogueScreen;
import com.aetherianartificer.townstead.client.gui.quest.QuestTracker;
import com.aetherianartificer.townstead.story.net.StoryC2SPayload;
import com.aetherianartificer.townstead.story.net.StoryQuestSyncS2CPayload;
import com.aetherianartificer.townstead.story.net.StoryS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client end of stories: routes lines to the open dialogue screen, keeps the player's story
 * quests, and announces changes to them. The first sync after joining a world is the baseline and
 * announces nothing.
 */
public final class StoryClient {
    private StoryClient() {}

    private static final String PROVIDER = "townstead_stories";
    private static final byte ACTIVE = 0;
    private static final byte READY = 1;
    private static final byte COMPLETE = 2;

    private static volatile List<StoryQuestSyncS2CPayload.Quest> quests = List.of();
    private static Object baselineLevel;

    public static List<StoryQuestSyncS2CPayload.Quest> quests() {
        return quests;
    }

    public static void setQuests(StoryQuestSyncS2CPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        List<StoryQuestSyncS2CPayload.Quest> previous = quests;
        quests = payload.quests();
        if (mc.level == null || mc.level != baselineLevel) {
            baselineLevel = mc.level;
            return;
        }
        Map<String, StoryQuestSyncS2CPayload.Quest> before = new HashMap<>();
        for (StoryQuestSyncS2CPayload.Quest quest : previous) before.put(quest.id(), quest);
        for (StoryQuestSyncS2CPayload.Quest quest : payload.quests()) announce(mc, before.get(quest.id()), quest);
    }

    private static void announce(Minecraft mc, StoryQuestSyncS2CPayload.Quest before, StoryQuestSyncS2CPayload.Quest now) {
        if (before == null) {
            // A quest settled inside the conversation that gave it needs no toast of its own.
            if (now.state() != COMPLETE) toast(mc, "townstead.story.toast.new", now, "townstead.story.toast.from");
            return;
        }
        if (before.state() == now.state()) {
            if (now.state() == ACTIVE && QuestTracker.isTracked(PROVIDER + ":" + now.id())) progress(mc, before, now);
            return;
        }
        if (now.state() == READY) {
            toast(mc, "townstead.story.toast.ready", now, "townstead.story.quest.talk_to");
        } else if (now.state() == COMPLETE && before.state() == ACTIVE) {
            toast(mc, "townstead.story.toast.complete", now, "townstead.story.toast.from");
        }
    }

    private static void progress(Minecraft mc, StoryQuestSyncS2CPayload.Quest before, StoryQuestSyncS2CPayload.Quest now) {
        for (int i = 0; i < now.objectives().size() && i < before.objectives().size(); i++) {
            StoryQuestSyncS2CPayload.Objective objective = now.objectives().get(i);
            if (objective.total() <= 1 || objective.current() <= before.objectives().get(i).current()) continue;
            mc.gui.setOverlayMessage(Component.translatable("townstead.story.progress", objective.label(),
                    objective.current(), objective.total()), false);
        }
    }

    private static void toast(Minecraft mc, String titleKey, StoryQuestSyncS2CPayload.Quest quest, String bodyKey) {
        mc.getToasts().addToast(SystemToast.multiline(mc,
                //? if >=1.21 {
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                //?} else {
                /*SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                *///?}
                Component.translatable(titleKey, quest.title()),
                Component.translatable(bodyKey, quest.teller())));
    }

    public static void handle(StoryS2CPayload payload) {
        if (Minecraft.getInstance().screen instanceof RpgDialogueScreen screen
                && screen.villagerEntityId() == payload.villagerId()) {
            screen.onStory(payload);
        }
    }

    public static void send(StoryC2SPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }
}
