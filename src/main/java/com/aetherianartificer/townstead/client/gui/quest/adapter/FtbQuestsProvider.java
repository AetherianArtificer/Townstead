package com.aetherianartificer.townstead.client.gui.quest.adapter;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.quest.QuestAction;
import com.aetherianartificer.townstead.quest.QuestActionResult;
import com.aetherianartificer.townstead.quest.QuestCapability;
import com.aetherianartificer.townstead.quest.QuestEntry;
import com.aetherianartificer.townstead.quest.QuestObjective;
import com.aetherianartificer.townstead.quest.QuestProvider;
import com.aetherianartificer.townstead.quest.QuestReward;
import com.aetherianartificer.townstead.quest.QuestState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reflection-only adapter verified against FTB Quests 2001.4.22 and 2101.1.35. */
public final class FtbQuestsProvider implements QuestProvider {
    private static final String CLIENT_FILE = "dev.ftb.mods.ftbquests.client.ClientQuestFile";
    private static final String QUEST = "dev.ftb.mods.ftbquests.quest.Quest";
    private static final String PIN_PACKET = "dev.ftb.mods.ftbquests.net.TogglePinnedMessage";
    private final Map<String, Long> ids = new HashMap<>();

    @Override public String id() { return "ftbquests"; }
    @Override public String displayName() { return "FTB Quests"; }

    @Override
    public boolean isAvailable() {
        if (!ModCompat.isLoaded("ftbquests")) return false;
        Class<?> client = ReflectiveAccess.classOrNull(CLIENT_FILE);
        if (client == null) return false;
        try {
            return ReflectiveAccess.bool(ReflectiveAccess.callStatic(client, "exists"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public String capabilityNote() {
        return "Progress is synchronized by FTB Quests. Claiming, editing, and dependency details remain in its quest book.";
    }

    @Override
    public List<QuestEntry> loadQuests() throws Exception {
        ids.clear();
        ClassLoader loader = getClass().getClassLoader();
        Class<?> clientType = Class.forName(CLIENT_FILE, false, loader);
        Class<?> questType = Class.forName(QUEST, false, loader);
        Object file = ReflectiveAccess.callStatic(clientType, "getInstance");
        Object teamData = ReflectiveAccess.field(file, "selfTeamData");
        List<QuestEntry> result = new ArrayList<>();
        for (Object quest : ReflectiveAccess.list(ReflectiveAccess.call(file, "collect", questType))) {
            boolean complete = ReflectiveAccess.bool(ReflectiveAccess.call(teamData, "isCompleted", quest));
            boolean started = ReflectiveAccess.bool(ReflectiveAccess.call(teamData, "isStarted", quest));
            boolean visible = complete || ReflectiveAccess.bool(ReflectiveAccess.callOrNull(quest, "isVisible", teamData));
            if (!visible) continue;

            long numericId = ReflectiveAccess.number(ReflectiveAccess.call(quest, "getId"));
            String localId = String.format("%016X", numericId);
            ids.put(localId, numericId);
            boolean tracked = ReflectiveAccess.bool(ReflectiveAccess.callStatic(clientType, "isQuestPinned", numericId));
            QuestState state = complete ? QuestState.COMPLETE : started ? QuestState.ACTIVE : QuestState.AVAILABLE;

            List<QuestObjective> objectives = new ArrayList<>();
            for (Object task : ReflectiveAccess.list(ReflectiveAccess.call(quest, "getTasks"))) {
                long current = ReflectiveAccess.number(ReflectiveAccess.callOrNull(teamData, "getProgress", task));
                long total = ReflectiveAccess.number(ReflectiveAccess.callOrNull(task, "getMaxProgress"));
                boolean done = ReflectiveAccess.bool(ReflectiveAccess.callOrNull(teamData, "isCompleted", task))
                        || total > 0L && current >= total;
                objectives.add(new QuestObjective(
                        ReflectiveAccess.text(ReflectiveAccess.callOrNull(task, "getTitle")), current, total,
                        done ? QuestObjective.Status.DONE : QuestObjective.Status.PENDING, ""));
            }
            List<QuestReward> rewards = new ArrayList<>();
            for (Object reward : ReflectiveAccess.list(ReflectiveAccess.call(quest, "getRewards"))) {
                rewards.add(new QuestReward(ReflectiveAccess.text(ReflectiveAccess.callOrNull(reward, "getTitle")), ""));
            }
            String description = joinComponents(ReflectiveAccess.callOrNull(quest, "getDescription"));
            if (description.isBlank()) description = ReflectiveAccess.text(ReflectiveAccess.callOrNull(quest, "getSubtitle"));
            Object chapter = ReflectiveAccess.callOrNull(quest, "getChapter");
            String group = ReflectiveAccess.text(ReflectiveAccess.callOrNull(chapter, "getTitle"));

            result.add(new QuestEntry(id(), displayName(), localId,
                    ReflectiveAccess.text(ReflectiveAccess.call(quest, "getTitle")), description, group,
                    "minecraft:writable_book", state, objectives, rewards,
                    Set.of(QuestCapability.TRACK, QuestCapability.OPEN_SOURCE), tracked, false, List.of(),
                    tracked ? "Tracked in FTB Quests" : "Progress belongs to the player's FTB team"));
        }
        return result;
    }

    private static String joinComponents(Object value) {
        StringBuilder result = new StringBuilder();
        for (Object line : ReflectiveAccess.list(value)) {
            String text = ReflectiveAccess.text(line);
            if (text.isBlank()) continue;
            if (!result.isEmpty()) result.append('\n');
            result.append(text);
        }
        return result.toString();
    }

    @Override
    public QuestActionResult perform(QuestAction action, QuestEntry quest) {
        Long numericId = ids.get(quest.id());
        if (numericId == null) return QuestActionResult.unavailable("FTB quest data changed; refresh the ledger.");
        if (action == QuestAction.OPEN_SOURCE) return openSource(numericId);
        if (action == QuestAction.TRACK) return toggleNativeTrack(numericId, quest.tracked());
        return QuestProvider.super.perform(action, quest);
    }

    private QuestActionResult openSource(long numericId) {
        try {
            Class<?> client = Class.forName(CLIENT_FILE, true, getClass().getClassLoader());
            ReflectiveAccess.callStatic(client, "openBookToQuestObject", numericId);
            return QuestActionResult.ok("Opened FTB Quests.");
        } catch (Throwable failure) {
            return QuestActionResult.unavailable("FTB Quests could not open this quest.");
        }
    }

    /**
     * FTB calls this pinning, but a pinned quest is exactly what its on-screen tracker draws, so the
     * ledger presents it as tracking. Townstead's own pin stays a local star for list ordering.
     */
    private QuestActionResult toggleNativeTrack(long numericId, boolean tracked) {
        try {
            Class<?> packetType = Class.forName(PIN_PACKET, true, getClass().getClassLoader());
            Object packet = ReflectiveAccess.construct(packetType, numericId);
            try {
                ReflectiveAccess.call(packet, "sendToServer");
                return QuestActionResult.ok(message(tracked));
            } catch (Throwable ignored) {
            }
            for (String sender : List.of("dev.architectury.networking.NetworkManager",
                    "net.neoforged.neoforge.network.PacketDistributor")) {
                Class<?> type = ReflectiveAccess.classOrNull(sender);
                if (type == null) continue;
                try {
                    ReflectiveAccess.callStatic(type, "sendToServer", packet);
                    return QuestActionResult.ok(message(tracked));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return QuestActionResult.unavailable("Tracking is owned by this FTB Quests version.");
    }

    private static String message(boolean tracked) {
        return tracked ? "Stopped tracking in FTB Quests." : "Tracking in FTB Quests.";
    }
}
