package com.aetherianartificer.townstead.client.gui.quest.adapter;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.quest.QuestAction;
import com.aetherianartificer.townstead.quest.QuestActionResult;
import com.aetherianartificer.townstead.quest.QuestCapability;
import com.aetherianartificer.townstead.quest.QuestEntry;
import com.aetherianartificer.townstead.quest.QuestObjective;
import com.aetherianartificer.townstead.quest.QuestProvider;
import com.aetherianartificer.townstead.quest.QuestState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reflection-only adapter for MCA: Quests' synchronized log and journal caches. */
public final class McaQuestsProvider implements QuestProvider {
    private static final String CLIENT_DATA = "dev.otectus.mcaquests.client.ClientQuestData";
    private static final String JOURNAL_DATA = "dev.otectus.mcaquests.client.ClientJournalData";
    private static final String LOG_SCREEN = "dev.otectus.mcaquests.client.QuestLogScreen";
    private static final String TRACK_PACKET = "dev.otectus.mcaquests.network.QuestTrackC2SPacket";
    private final Map<String, ActiveKey> activeKeys = new HashMap<>();

    private record ActiveKey(Object villagerUuid, Object questId, boolean tracked) {}

    @Override public String id() { return "mcaquests"; }
    @Override public String displayName() { return "MCA: Quests"; }

    @Override
    public boolean isAvailable() {
        return ModCompat.isLoaded("mcaquests") && ReflectiveAccess.classOrNull(CLIENT_DATA) != null;
    }

    @Override
    public Object changeToken() {
        Class<?> data = ReflectiveAccess.classOrNull(CLIENT_DATA);
        if (data == null) return null;
        try {
            return ReflectiveAccess.callStatic(data, "active");
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Override
    public List<QuestEntry> loadQuests() throws Exception {
        activeKeys.clear();
        List<QuestEntry> result = new ArrayList<>();
        Class<?> data = Class.forName(CLIENT_DATA, false, getClass().getClassLoader());
        for (Object entry : ReflectiveAccess.list(ReflectiveAccess.callStatic(data, "active"))) {
            Object questId = ReflectiveAccess.call(entry, "questId");
            Object villager = ReflectiveAccess.call(entry, "villagerUuid");
            boolean tracked = ReflectiveAccess.bool(ReflectiveAccess.callOrNull(entry, "tracked"));
            String localId = questId + "@" + villager;
            activeKeys.put(localId, new ActiveKey(villager, questId, tracked));

            List<QuestObjective> objectives = new ArrayList<>();
            for (Object objective : ReflectiveAccess.list(ReflectiveAccess.call(entry, "objectives"))) {
                String statusName = String.valueOf(ReflectiveAccess.callOrNull(objective, "state"));
                QuestObjective.Status status = switch (statusName) {
                    case "DONE" -> QuestObjective.Status.DONE;
                    case "UNAVAILABLE" -> QuestObjective.Status.UNAVAILABLE;
                    case "LOST" -> QuestObjective.Status.FAILED;
                    default -> QuestObjective.Status.PENDING;
                };
                objectives.add(new QuestObjective(
                        ReflectiveAccess.text(ReflectiveAccess.call(objective, "text")),
                        ReflectiveAccess.number(ReflectiveAccess.callOrNull(objective, "current")),
                        ReflectiveAccess.number(ReflectiveAccess.callOrNull(objective, "required")),
                        status,
                        ReflectiveAccess.itemId(ReflectiveAccess.callOrNull(objective, "icon"))));
            }

            String chain = ReflectiveAccess.text(ReflectiveAccess.callOrNull(entry, "chainLabel"));
            String giver = ReflectiveAccess.text(ReflectiveAccess.callOrNull(entry, "giverName"));
            boolean ready = ReflectiveAccess.bool(ReflectiveAccess.callOrNull(entry, "ready"));
            boolean suspended = ReflectiveAccess.bool(ReflectiveAccess.callOrNull(entry, "suspended"));
            String metadata = giver.isBlank() ? "" : "Quest giver: " + giver;
            if (ready) metadata += (metadata.isBlank() ? "" : " • ") + "Ready to turn in";
            if (suspended) metadata += (metadata.isBlank() ? "" : " • ") + "Waiting for an optional integration";
            result.add(new QuestEntry(id(), displayName(), localId,
                    ReflectiveAccess.text(ReflectiveAccess.call(entry, "title")),
                    ReflectiveAccess.text(ReflectiveAccess.callOrNull(entry, "description")),
                    chain.isBlank() ? "Village quests" : chain, "minecraft:book", QuestState.ACTIVE,
                    objectives, List.of(), Set.of(QuestCapability.TRACK, QuestCapability.OPEN_SOURCE),
                    tracked, false, List.of(), metadata));
        }
        loadArchive(result);
        return result;
    }

    private void loadArchive(List<QuestEntry> result) {
        Class<?> journal = ReflectiveAccess.classOrNull(JOURNAL_DATA);
        if (journal == null) return;
        Object archive;
        try {
            archive = ReflectiveAccess.callStatic(journal, "archive");
        } catch (ReflectiveOperationException ignored) {
            return;
        }
        int index = 0;
        for (Object item : ReflectiveAccess.list(archive)) {
            String title = ReflectiveAccess.text(ReflectiveAccess.callOrNull(item, "questTitle"));
            long count = ReflectiveAccess.number(ReflectiveAccess.callOrNull(item, "count"));
            result.add(new QuestEntry(id(), displayName(), "archive-" + index++ + "-" + title.hashCode(), title, "",
                    "Completed quests", "minecraft:writable_book", QuestState.COMPLETE, List.of(), List.of(),
                    Set.of(QuestCapability.OPEN_SOURCE), false, false, List.of(),
                    count > 1 ? "Completed " + count + " times" : "Completed"));
        }
    }

    @Override
    public QuestActionResult perform(QuestAction action, QuestEntry quest) {
        if (action == QuestAction.OPEN_SOURCE) return openSource();
        if (action == QuestAction.TRACK) return toggleTrack(quest);
        return QuestProvider.super.perform(action, quest);
    }

    private QuestActionResult openSource() {
        try {
            Class<?> screenClass = Class.forName(LOG_SCREEN, true, getClass().getClassLoader());
            Object screen = ReflectiveAccess.construct(screenClass);
            if (!(screen instanceof Screen source)) return QuestActionResult.unavailable("MCA: Quests log is not a screen.");
            Minecraft.getInstance().setScreen(source);
            return QuestActionResult.ok("Opened MCA: Quests.");
        } catch (Throwable failure) {
            return QuestActionResult.unavailable("MCA: Quests log is unavailable in this version.");
        }
    }

    private QuestActionResult toggleTrack(QuestEntry quest) {
        ActiveKey key = activeKeys.get(quest.id());
        if (key == null) return QuestActionResult.unavailable("Only active MCA quests can be tracked.");
        try {
            Class<?> packetType = Class.forName(TRACK_PACKET, true, getClass().getClassLoader());
            Object packet = key.tracked
                    ? ReflectiveAccess.callStatic(packetType, "none")
                    : ReflectiveAccess.callStatic(packetType, "of", key.villagerUuid, key.questId);
            if (!sendPacket(packet)) return QuestActionResult.unavailable("MCA: Quests owns tracking in this version.");
            return QuestActionResult.ok(key.tracked ? "Stopped tracking quest." : "Tracking quest.");
        } catch (Throwable failure) {
            return QuestActionResult.unavailable("MCA: Quests owns tracking in this version.");
        }
    }

    private boolean sendPacket(Object packet) {
        try {
            Class<?> network = Class.forName("dev.otectus.mcaquests.network.QuestNetwork", true, getClass().getClassLoader());
            Object channel = ReflectiveAccess.field(network, "CHANNEL");
            ReflectiveAccess.call(channel, "sendToServer", packet);
            return true;
        } catch (Throwable ignored) {
        }
        for (String sender : List.of("net.neoforged.neoforge.network.PacketDistributor",
                "dev.architectury.networking.NetworkManager")) {
            Class<?> type = ReflectiveAccess.classOrNull(sender);
            if (type == null) continue;
            try {
                ReflectiveAccess.callStatic(type, "sendToServer", packet);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}
