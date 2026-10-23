package com.aetherianartificer.townstead.client.gui.quest.adapter;

import com.aetherianartificer.townstead.calendar.ComponentSync;
import com.aetherianartificer.townstead.client.gui.quest.QuestIcons;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.vampirism.VampirismTaskMenuC2SPayload;
import com.aetherianartificer.townstead.compat.vampirism.VampirismTasksS2CPayload;
import com.aetherianartificer.townstead.quest.QuestAction;
import com.aetherianartificer.townstead.quest.QuestActionResult;
import com.aetherianartificer.townstead.quest.QuestCapability;
import com.aetherianartificer.townstead.quest.QuestEntry;
import com.aetherianartificer.townstead.quest.QuestObjective;
import com.aetherianartificer.townstead.quest.QuestProvider;
import com.aetherianartificer.townstead.quest.QuestReward;
import com.aetherianartificer.townstead.quest.QuestState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Vampirism's faction tasks in the Quest Ledger: the ones task masters offer (Available), the ones
 * accepted with each requirement's count, time left and where to hand them in (Active), and those
 * finished (Complete). Read from what the server sends, since Vampirism's client only has them
 * while its menu is open. Open opens Vampirism's own menu; Track uses Townstead's tracker.
 */
public final class VampirismTaskProvider implements QuestProvider {
    private static volatile List<VampirismTasksS2CPayload.Task> tasks = List.of();

    /** A fresh list from the server. */
    public static void accept(VampirismTasksS2CPayload payload) {
        tasks = payload.tasks();
    }

    public static void clear() {
        tasks = List.of();
    }

    @Override public String id() { return "vampirism_tasks"; }
    @Override public String displayName() { return I18n.get("townstead.quest_ledger.provider.tab.vampirism_tasks"); }
    @Override public boolean usesLocalTracker() { return true; }
    @Override public boolean isAvailable() { return ModCompat.isLoaded("vampirism"); }
    @Override public Object changeToken() { return tasks; }

    @Override
    public Map<UUID, Byte> giverMarks() {
        Map<UUID, Byte> marks = new HashMap<>();
        for (VampirismTasksS2CPayload.Task task : tasks) {
            if (task.state() != VampirismTasksS2CPayload.ACCEPTED || !task.ready() || task.board().isEmpty()) continue;
            try {
                marks.put(UUID.fromString(task.board()), (byte) 3);
            } catch (IllegalArgumentException ignored) {
                // Not a task master.
            }
        }
        return marks;
    }

    @Override
    public QuestActionResult perform(QuestAction action, QuestEntry quest) {
        if (action != QuestAction.OPEN_SOURCE) return QuestProvider.super.perform(action, quest);
        VampirismTaskMenuC2SPayload payload = new VampirismTaskMenuC2SPayload();
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
        return QuestActionResult.ok("");
    }

    @Override
    public List<QuestEntry> loadQuests() {
        List<QuestEntry> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        for (VampirismTasksS2CPayload.Task task : tasks) {
            boolean accepted = task.state() == VampirismTasksS2CPayload.ACCEPTED;
            String master = task.board().isEmpty() ? I18n.get("townstead.quest_ledger.vampirism.unique")
                    : task.hasPos() ? I18n.get("townstead.quest_ledger.vampirism.master_at", task.x(), task.y(), task.z())
                    : I18n.get("townstead.quest_ledger.vampirism.master");

            List<QuestObjective> objectives = new ArrayList<>();
            for (VampirismTasksS2CPayload.Requirement req : task.requirements()) {
                String kind = text(req.kind());
                if (kind.endsWith(":")) kind = kind.substring(0, kind.length() - 1);
                String what = req.itemId().isEmpty() ? text(req.label()) : itemName(req.itemId());
                boolean met = req.total() > 0 && req.current() >= req.total();
                objectives.add(new QuestObjective(kind.isEmpty() ? what : kind + ": " + what, req.current(),
                        req.total() > 1 ? req.total() : 0L,
                        met ? QuestObjective.Status.DONE : accepted ? QuestObjective.Status.PENDING : QuestObjective.Status.UNAVAILABLE,
                        req.itemId()));
            }
            if (accepted && task.deadline() > 0 && !task.ready()) {
                long left = task.deadline() - now;
                objectives.add(left <= 0
                        ? new QuestObjective(I18n.get("text.vampirism.task_failed"), 0, 0, QuestObjective.Status.FAILED, "")
                        : new QuestObjective(I18n.get("townstead.quest_ledger.vampirism.time_left", duration(left)), 0, 0,
                                QuestObjective.Status.PENDING, QuestIcons.PREFIX + "waiting"));
            }
            if (accepted) {
                objectives.add(new QuestObjective(task.hasPos()
                        ? I18n.get("townstead.quest_ledger.vampirism.hand_in_at", task.x(), task.y(), task.z())
                        : I18n.get("townstead.quest_ledger.vampirism.hand_in"), 0, 0,
                        task.ready() ? QuestObjective.Status.PENDING : QuestObjective.Status.UNAVAILABLE,
                        QuestIcons.PREFIX + "talk"));
            }

            List<QuestReward> rewards = new ArrayList<>();
            String icon = QuestIcons.PREFIX + "letter";
            for (VampirismTasksS2CPayload.Reward reward : task.rewards()) {
                if (!reward.itemId().isEmpty()) {
                    rewards.add(new QuestReward(reward.count() + " " + itemName(reward.itemId()), reward.itemId()));
                    icon = reward.itemId();
                } else {
                    rewards.add(new QuestReward(text(reward.text()), ""));
                }
            }

            QuestState state = switch (task.state()) {
                case VampirismTasksS2CPayload.ACCEPTED -> QuestState.ACTIVE;
                case VampirismTasksS2CPayload.DONE -> QuestState.COMPLETE;
                default -> QuestState.AVAILABLE;
            };
            Set<QuestCapability> capabilities = accepted
                    ? Set.of(QuestCapability.TRACK, QuestCapability.OPEN_SOURCE) : Set.of(QuestCapability.OPEN_SOURCE);
            out.add(new QuestEntry(id(), displayName(), task.id(), text(task.title()), text(task.description()), master,
                    icon, state, objectives, rewards, capabilities, false, false, List.of(), master));
        }
        return out;
    }

    private static String text(String[] pair) {
        return pair == null || pair.length < 2 ? "" : ComponentSync.reconstruct(pair[0], pair[1]).getString();
    }

    private static String itemName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        return id == null ? itemId : new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString();
    }

    /** "4 min", or "35 s" under a minute, from ticks. */
    private static String duration(long ticks) {
        long seconds = Math.max(0, ticks / 20);
        return seconds >= 60 ? I18n.get("townstead.quest_ledger.vampirism.minutes", (seconds + 59) / 60)
                : I18n.get("townstead.quest_ledger.vampirism.seconds", seconds);
    }
}
