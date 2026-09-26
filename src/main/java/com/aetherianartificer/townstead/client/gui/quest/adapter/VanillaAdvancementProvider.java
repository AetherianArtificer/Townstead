package com.aetherianartificer.townstead.client.gui.quest.adapter;

import com.aetherianartificer.townstead.quest.QuestAction;
import com.aetherianartificer.townstead.quest.QuestActionResult;
import com.aetherianartificer.townstead.quest.QuestCapability;
import com.aetherianartificer.townstead.quest.QuestEntry;
import com.aetherianartificer.townstead.quest.QuestObjective;
import com.aetherianartificer.townstead.quest.QuestProvider;
import com.aetherianartificer.townstead.quest.QuestState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Provider for the vanilla advancement tree synchronized to the client by the server. */
public final class VanillaAdvancementProvider implements QuestProvider {
    private Object clientAdvancements;

    @Override public String id() { return "minecraft"; }
    @Override public String displayName() { return "Advancements"; }

    @Override
    public boolean isAvailable() {
        return Minecraft.getInstance().getConnection() != null;
    }

    @Override
    public String capabilityNote() {
        return "";
    }

    @Override
    public List<QuestEntry> loadQuests() throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) return List.of();
        clientAdvancements = minecraft.getConnection().getAdvancements();
        Object tree = ReflectiveAccess.call(clientAdvancements, "getTree");
        Object nodes = ReflectiveAccess.call(tree, "nodes");
        Map<?, ?> progressMap = progressMap(clientAdvancements);
        List<QuestEntry> result = new ArrayList<>();
        for (Object node : ReflectiveAccess.list(nodes)) {
            Object holder = first(ReflectiveAccess.callOrNull(node, "holder"), node);
            Object advancement = first(ReflectiveAccess.callOrNull(holder, "value"),
                    ReflectiveAccess.callOrNull(node, "advancement"), node);
            Object display = ReflectiveAccess.optional(first(
                    ReflectiveAccess.callOrNull(advancement, "display"),
                    ReflectiveAccess.callOrNull(advancement, "getDisplay")));
            if (display == null) continue;
            Object progress = findProgress(progressMap, holder, advancement);
            boolean done = ReflectiveAccess.bool(ReflectiveAccess.callOrNull(progress, "isDone"));
            boolean started = ReflectiveAccess.bool(ReflectiveAccess.callOrNull(progress, "hasProgress"));
            boolean hidden = ReflectiveAccess.bool(ReflectiveAccess.callOrNull(display, "isHidden"));
            if (hidden && !started && !done) continue;

            String localId = idOf(holder, advancement);
            String title = ReflectiveAccess.text(first(
                    ReflectiveAccess.callOrNull(display, "getTitle"),
                    ReflectiveAccess.callOrNull(display, "title")));
            String description = ReflectiveAccess.text(first(
                    ReflectiveAccess.callOrNull(display, "getDescription"),
                    ReflectiveAccess.callOrNull(display, "description")));
            String icon = ReflectiveAccess.itemId(first(
                    ReflectiveAccess.callOrNull(display, "getIcon"),
                    ReflectiveAccess.callOrNull(display, "icon")));
            String group = rootTitle(node, display);
            QuestState state = done ? QuestState.COMPLETE : started ? QuestState.ACTIVE : QuestState.AVAILABLE;
            List<QuestObjective> objectives = progressObjective(progress, done);
            String type = ReflectiveAccess.text(first(
                    ReflectiveAccess.callOrNull(display, "getType"),
                    ReflectiveAccess.callOrNull(display, "type")));
            result.add(new QuestEntry(id(), displayName(), localId, title, description, group, icon,
                    state, objectives, List.of(), Set.of(QuestCapability.OPEN_SOURCE), false, false,
                    type.isBlank() ? List.of() : List.of(type.toLowerCase(Locale.ROOT)), ""));
        }
        return result;
    }

    private static List<QuestObjective> progressObjective(Object progress, boolean done) {
        if (progress == null) return List.of();
        long completed = count(ReflectiveAccess.callOrNull(progress, "getCompletedCriteria"));
        long remaining = count(ReflectiveAccess.callOrNull(progress, "getRemainingCriteria"));
        long total = completed + remaining;
        if (total == 0L) return List.of();
        return List.of(new QuestObjective("Advancement progress", completed, total,
                done ? QuestObjective.Status.DONE : QuestObjective.Status.PENDING, ""));
    }

    private static long count(Object value) {
        long count = 0L;
        if (value instanceof Iterable<?> iterable) for (Object ignored : iterable) count++;
        return count;
    }

    private static String rootTitle(Object node, Object fallbackDisplay) {
        Object root = node;
        for (int guard = 0; guard < 64; guard++) {
            Object parent = first(ReflectiveAccess.callOrNull(root, "parent"),
                    ReflectiveAccess.callOrNull(root, "getParent"));
            if (parent == null) break;
            root = parent;
        }
        Object holder = first(ReflectiveAccess.callOrNull(root, "holder"), root);
        Object advancement = first(ReflectiveAccess.callOrNull(holder, "value"),
                ReflectiveAccess.callOrNull(root, "advancement"), root);
        Object display = ReflectiveAccess.optional(first(
                ReflectiveAccess.callOrNull(advancement, "display"),
                ReflectiveAccess.callOrNull(advancement, "getDisplay"), fallbackDisplay));
        String title = ReflectiveAccess.text(first(
                ReflectiveAccess.callOrNull(display, "getTitle"),
                ReflectiveAccess.callOrNull(display, "title")));
        return title.isBlank() ? "Advancements" : title;
    }

    private static String idOf(Object holder, Object advancement) {
        Object id = first(ReflectiveAccess.callOrNull(holder, "id"),
                ReflectiveAccess.callOrNull(advancement, "getId"),
                ReflectiveAccess.callOrNull(advancement, "id"));
        return id == null ? "unknown-" + System.identityHashCode(holder) : id.toString();
    }

    private static Object findProgress(Map<?, ?> map, Object holder, Object advancement) {
        Object progress = map.get(holder);
        if (progress == null) progress = map.get(advancement);
        if (progress != null) return progress;
        String wanted = idOf(holder, advancement);
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (wanted.equals(idOf(entry.getKey(), entry.getKey()))) return entry.getValue();
        }
        return null;
    }

    private static Map<?, ?> progressMap(Object advancements) throws ReflectiveOperationException {
        try {
            Object named = ReflectiveAccess.field(advancements, "progress");
            if (named instanceof Map<?, ?> map) return map;
        } catch (ReflectiveOperationException ignored) {
        }
        for (Class<?> type = advancements.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                Object value = field.get(advancements);
                if (value instanceof Map<?, ?> map && (map.isEmpty()
                        || map.values().stream().filter(v -> v != null).findFirst()
                        .map(v -> v.getClass().getName().endsWith("AdvancementProgress")).orElse(false))) {
                    return map;
                }
            }
        }
        return Map.of();
    }

    @SafeVarargs
    private static <T> T first(T... values) {
        for (T value : values) if (value != null) return value;
        return null;
    }

    @Override
    public QuestActionResult perform(QuestAction action, QuestEntry quest) {
        if (action != QuestAction.OPEN_SOURCE) return QuestProvider.super.perform(action, quest);
        try {
            Class<?> screenType = Class.forName("net.minecraft.client.gui.screens.advancements.AdvancementsScreen");
            Object value = ReflectiveAccess.construct(screenType, clientAdvancements);
            if (!(value instanceof Screen screen)) return QuestActionResult.unavailable("Advancements screen is unavailable.");
            Minecraft.getInstance().setScreen(screen);
            return QuestActionResult.ok("Opened Advancements.");
        } catch (Throwable failure) {
            return QuestActionResult.unavailable("Advancements screen is unavailable in this version.");
        }
    }
}
