package com.aetherianartificer.townstead.client.gui.quest.adapter;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.quest.QuestEntry;
import com.aetherianartificer.townstead.quest.QuestObjective;
import com.aetherianartificer.townstead.quest.QuestProvider;
import com.aetherianartificer.townstead.quest.QuestReward;
import com.aetherianartificer.townstead.quest.QuestState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads Bountiful bounty contracts already held by the local player.
 *
 * <p>Bountiful 6.x stores one {@code BountyData} blob in item NBT. Bountiful 8.x stores objectives,
 * rewards and completion as separate data components behind a {@code BountyStack} wrapper. Both
 * layouts are read reflectively; whichever class is present chooses the reader.
 */
public final class BountifulProvider implements QuestProvider {
    private static final String LEGACY_DATA_CLASS = "io.ejekta.bountiful.bounty.BountyData";
    private static final String STACK_CLASS = "io.ejekta.bountiful.components.BountyStack";
    private static final String BOUNTY_ITEM = "bountiful:bounty";

    @Override public String id() { return "bountiful"; }
    @Override public String displayName() { return "Bountiful"; }

    @Override
    public boolean isAvailable() {
        return ModCompat.isLoaded("bountiful")
                && (ReflectiveAccess.classOrNull(STACK_CLASS) != null
                || ReflectiveAccess.classOrNull(LEGACY_DATA_CLASS) != null);
    }

    @Override
    public String capabilityNote() {
        return "Held contracts are read-only here. Accepting and cashing in bounties still require a Bountiful board.";
    }

    @Override
    public List<QuestEntry> loadQuests() throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return List.of();
        Reader reader = ReflectiveAccess.classOrNull(STACK_CLASS) != null
                ? new ComponentReader() : new LegacyReader();

        List<QuestEntry> result = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !BOUNTY_ITEM.equals(ReflectiveAccess.itemId(stack))) continue;
            Bounty bounty;
            try {
                bounty = reader.read(stack, player);
            } catch (Throwable failure) {
                continue;
            }
            if (bounty == null) continue;

            boolean ready = !bounty.objectives.isEmpty()
                    && bounty.objectives.stream().allMatch(QuestObjective::done);
            String metadata = ready ? "Ready to cash in at a bounty board" : "Inventory slot " + (slot + 1);
            if (!bounty.timeLeft.isBlank()) metadata += " • " + bounty.timeLeft;
            result.add(new QuestEntry(id(), displayName(), "inventory-" + slot,
                    stack.getHoverName().getString(), "", "Held contracts", BOUNTY_ITEM,
                    QuestState.ACTIVE, bounty.objectives, bounty.rewards, Set.of(), false, false, List.of(),
                    metadata));
        }
        return result;
    }

    private record Bounty(List<QuestObjective> objectives, List<QuestReward> rewards, String timeLeft) {}

    private interface Reader {
        Bounty read(ItemStack stack, LocalPlayer player) throws ReflectiveOperationException;
    }

    private static QuestObjective objective(String label, long current, long total, Object icon) {
        return new QuestObjective(label, current, total,
                total > 0L && current >= total ? QuestObjective.Status.DONE : QuestObjective.Status.PENDING,
                ReflectiveAccess.text(icon));
    }

    /** Bountiful 6.x: {@code BountyData.Companion.get(stack)} decodes the NBT blob. */
    private static final class LegacyReader implements Reader {
        private final Class<?> dataType;
        private final Object companion;
        private final Method stackReader;

        LegacyReader() throws ReflectiveOperationException {
            dataType = Class.forName(LEGACY_DATA_CLASS, false, getClass().getClassLoader());
            companion = ReflectiveAccess.field(dataType, "Companion");
            stackReader = stackReader(companion.getClass(), dataType);
            if (stackReader == null) throw new NoSuchMethodException("BountyData companion ItemStack reader");
        }

        @Override
        public Bounty read(ItemStack stack, LocalPlayer player) throws ReflectiveOperationException {
            Object data = stackReader.invoke(companion, stack);
            if (data == null || !dataType.isInstance(data)) return null;

            List<QuestObjective> objectives = new ArrayList<>();
            for (Object entry : ReflectiveAccess.list(ReflectiveAccess.call(data, "getObjectives"))) {
                Object progress = null;
                Object logic = ReflectiveAccess.callOrNull(entry, "getLogic");
                if (logic != null) progress = ReflectiveAccess.callOrNull(logic, "getProgress", entry, player);
                long current = progress == null
                        ? ReflectiveAccess.number(ReflectiveAccess.callOrNull(entry, "getCurrent"))
                        : ReflectiveAccess.number(ReflectiveAccess.callOrNull(progress, "getCurrent"));
                long total = progress == null
                        ? ReflectiveAccess.number(ReflectiveAccess.callOrNull(entry, "getAmount"))
                        : ReflectiveAccess.number(ReflectiveAccess.callOrNull(progress, "getGoal"));
                String label = ReflectiveAccess.text(ReflectiveAccess.callOrNull(entry, "textSummary", player, true));
                if (label.isBlank()) label = ReflectiveAccess.text(ReflectiveAccess.callOrNull(entry, "getTranslation"));
                objectives.add(objective(label, current, total, ReflectiveAccess.callOrNull(entry, "getIcon")));
            }

            List<QuestReward> rewards = new ArrayList<>();
            for (Object entry : ReflectiveAccess.list(ReflectiveAccess.call(data, "getRewards"))) {
                String label = ReflectiveAccess.text(ReflectiveAccess.callOrNull(entry, "textSummary", player, false));
                if (label.isBlank()) label = ReflectiveAccess.text(ReflectiveAccess.callOrNull(entry, "getTranslation"));
                rewards.add(new QuestReward(label, ""));
            }
            return new Bounty(objectives, rewards, "");
        }

        private static Method stackReader(Class<?> companionType, Class<?> dataType) {
            try {
                for (Method method : companionType.getMethods()) {
                    if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) continue;
                    if (!ItemStack.class.isAssignableFrom(method.getParameterTypes()[0])) continue;
                    if (!dataType.isAssignableFrom(method.getReturnType()) && method.getReturnType() != Object.class) continue;
                    method.setAccessible(true);
                    return method;
                }
            } catch (Throwable ignored) {
            }
            return null;
        }
    }

    /** Bountiful 8.x: {@code new BountyStack(stack)} reads the item's data components. */
    private static final class ComponentReader implements Reader {
        private final Class<?> stackType;

        ComponentReader() throws ReflectiveOperationException {
            stackType = Class.forName(STACK_CLASS, false, getClass().getClassLoader());
        }

        @Override
        public Bounty read(ItemStack stack, LocalPlayer player) throws ReflectiveOperationException {
            Object bounty = ReflectiveAccess.construct(stackType, stack);

            List<QuestObjective> objectives = new ArrayList<>();
            for (Object entry : ReflectiveAccess.list(ReflectiveAccess.call(bounty, "getObjs"))) {
                int tracked = (int) ReflectiveAccess.number(ReflectiveAccess.callOrNull(bounty, "progressOf", entry));
                Object progress = null;
                Object logic = ReflectiveAccess.callOrNull(entry, "getLogic");
                if (logic != null) progress = ReflectiveAccess.callOrNull(logic, "getProgress", entry, player, tracked);
                long current = progress == null
                        ? tracked
                        : ReflectiveAccess.number(ReflectiveAccess.callOrNull(progress, "getCurrent"));
                long total = progress == null
                        ? ReflectiveAccess.number(ReflectiveAccess.callOrNull(entry, "getAmount"))
                        : ReflectiveAccess.number(ReflectiveAccess.callOrNull(progress, "getGoal"));
                objectives.add(objective(summary(entry, player, true, tracked), current, total,
                        ReflectiveAccess.callOrNull(entry, "getIcon")));
            }

            List<QuestReward> rewards = new ArrayList<>();
            for (Object entry : ReflectiveAccess.list(ReflectiveAccess.call(bounty, "getRews"))) {
                rewards.add(new QuestReward(summary(entry, player, false, 0), ""));
            }

            String timeLeft = "";
            Object info = ReflectiveAccess.callOrNull(bounty, "getInfo");
            if (info != null && player.level() != null) {
                timeLeft = ReflectiveAccess.text(ReflectiveAccess.callOrNull(info, "formattedTimeLeft", player.level()));
            }
            return new Bounty(objectives, rewards, timeLeft);
        }

        /** First line of Bountiful's own tooltip for this entry, falling back to its display name. */
        private static String summary(Object entry, LocalPlayer player, boolean objective, int progress) {
            List<?> lines = ReflectiveAccess.list(ReflectiveAccess.callOrNull(entry, "textOnBounty", player, objective, progress));
            String label = lines.isEmpty() ? "" : ReflectiveAccess.text(lines.get(0));
            if (label.isBlank()) label = ReflectiveAccess.text(ReflectiveAccess.callOrNull(entry, "getTranslation"));
            if (label.isBlank()) label = ReflectiveAccess.text(ReflectiveAccess.callOrNull(entry, "getName"));
            return label;
        }
    }
}
