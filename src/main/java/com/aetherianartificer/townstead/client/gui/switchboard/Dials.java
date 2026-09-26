package com.aetherianartificer.townstead.client.gui.switchboard;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.switchboard.SettingIndex;
import com.aetherianartificer.townstead.switchboard.Systems;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The broad dials on the Village tab. Each level is a bundle of target values; a dial shows the level
 * whose bundle matches the world now, or Custom. Nothing about a dial is stored.
 */
final class Dials {
    private Dials() {}

    record Level(Component name, Map<String, Object> values) {}

    record Dial(String id, Component label, List<Level> levels, Predicate<String> details) {
        /** The level matching the world's values now, or -1 for Custom. */
        int level(SwitchboardModel model) {
            for (int i = 0; i < levels.size(); i++) {
                boolean match = true;
                for (Map.Entry<String, Object> e : levels.get(i).values().entrySet()) {
                    if (!Objects.equals(model.value(e.getKey()), e.getValue())) {
                        match = false;
                        break;
                    }
                }
                if (match) return i;
            }
            return -1;
        }

        void set(SwitchboardModel model, int level) {
            levels.get(level).values().forEach(model::set);
        }

        Set<String> keys() {
            Set<String> keys = new LinkedHashSet<>();
            for (Level level : levels) keys.addAll(level.values().keySet());
            return keys;
        }

        boolean locked(SwitchboardModel model) {
            for (String key : keys()) if (model.isLocked(key)) return true;
            return false;
        }
    }

    record Style(String id, int[] levels) {
        Component name() {
            return Component.translatable("townstead.switchboard.style." + id);
        }

        Component hint() {
            return Component.translatable("townstead.switchboard.style." + id + ".hint");
        }
    }

    static final String NEEDS = "needs";
    static final String WORK = "work";
    static final String PEOPLES = "peoples";
    static final String SOCIAL = "social";
    static final String RECORDS = "records";

    private static List<Dial> dials;

    static List<Dial> all() {
        if (dials == null) dials = build();
        return dials;
    }

    static Dial get(String id) {
        for (Dial dial : all()) if (dial.id().equals(id)) return dial;
        throw new IllegalArgumentException(id);
    }

    static List<Style> styles() {
        return List.of(
                new Style("full", new int[]{2, 2, 2, 1, 1}),
                new Style("cozy", new int[]{1, 2, 2, 1, 1}),
                new Style("hard", new int[]{3, 2, 2, 1, 1}),
                new Style("storybook", new int[]{0, 0, 2, 1, 1}),
                new Style("light", new int[]{0, 0, 1, 1, 0}),
                new Style("off", new int[]{0, 0, 0, 0, 0}));
    }

    /** The style whose dial levels match the world now, or -1 for Custom. */
    static int style(SwitchboardModel model) {
        List<Style> styles = styles();
        for (int s = 0; s < styles.size(); s++) {
            boolean match = true;
            for (int d = 0; d < all().size(); d++) {
                if (all().get(d).level(model) != styles.get(s).levels()[d]) {
                    match = false;
                    break;
                }
            }
            if (match) return s;
        }
        return -1;
    }

    static void applyStyle(SwitchboardModel model, Style style) {
        for (int d = 0; d < all().size(); d++) {
            Dial dial = all().get(d);
            if (!dial.locked(model)) dial.set(model, style.levels()[d]);
        }
    }

    private static List<Dial> build() {
        List<Dial> out = new ArrayList<>();
        List<Object> needSwitches = List.of(TownsteadConfig.ENABLE_VILLAGER_HUNGER, orMissing(TownsteadConfig.ENABLE_VILLAGER_THIRST),
                TownsteadConfig.ENABLE_VILLAGER_TEMPERATURE, TownsteadConfig.ENABLE_VILLAGER_FATIGUE);
        out.add(new Dial(NEEDS, label("needs"), List.of(
                level("needs.off", with(needSwitches, false)),
                level("needs.gentle", pace(with(needSwitches, true), 0.5)),
                level("needs.normal", pace(with(needSwitches, true), 1.0)),
                level("needs.harsh", pace(with(needSwitches, true), 1.5))),
                key -> key.startsWith("needs.") || key.startsWith("caregiving.")));

        List<String> trades = List.of(Systems.WORK, Systems.FARMING, Systems.FISHING, Systems.SHEPHERDING,
                Systems.HOSPITALITY, Systems.CLOTHING, Systems.SHIFTS);
        Map<String, Object> mca = systems(trades, false);
        mca.put(Systems.key(Systems.CAREERS), false);
        Map<String, Object> jobs = systems(trades, true);
        jobs.put(Systems.key(Systems.CAREERS), false);
        Map<String, Object> careers = systems(trades, true);
        careers.put(Systems.key(Systems.CAREERS), true);
        Set<String> workSystems = new LinkedHashSet<>(careers.keySet());
        out.add(new Dial(WORK, label("work"), List.of(level("work.mca", mca), level("work.jobs", jobs),
                level("work.careers", careers)),
                key -> workSystems.contains(key) || key.startsWith("farming.") || key.startsWith("fishing.")
                        || key.startsWith("professionWork.") || key.startsWith("storage.")
                        || key.startsWith("mca_buildings.") || key.startsWith("chefsdelight_compat.")));

        out.add(new Dial(PEOPLES, label("peoples"), List.of(
                level("peoples.overworlders", systems(Map.of(Systems.ROOTS, false, Systems.CULTURES, false, Systems.NAMING, false))),
                level("peoples.roots", systems(Map.of(Systems.ROOTS, true, Systems.CULTURES, false, Systems.NAMING, true))),
                level("peoples.cultures", systems(Map.of(Systems.ROOTS, true, Systems.CULTURES, true, Systems.NAMING, true)))),
                key -> key.startsWith("naming.") || key.startsWith("rebirth.") || key.equals(Systems.key(Systems.NAMING))));

        List<Object> socialSwitch = List.of(TownsteadConfig.ENABLE_CONVERSATIONS);
        Map<String, Object> socialOff = with(socialSwitch, false);
        socialOff.putAll(systems(List.of(Systems.HANGOUTS, Systems.REACTIONS), false));
        Map<String, Object> socialOn = with(socialSwitch, true);
        socialOn.putAll(systems(List.of(Systems.HANGOUTS, Systems.REACTIONS), true));
        Set<String> socialKeys = new LinkedHashSet<>(socialOn.keySet());
        out.add(new Dial(SOCIAL, label("social"), List.of(onOff(false, socialOff), onOff(true, socialOn)),
                key -> socialKeys.contains(key) || key.startsWith("conversations.") || key.startsWith("feedback.")));

        List<String> records = List.of(Systems.CHRONICLES, Systems.POLITICS, Systems.SPIRIT);
        Set<String> recordKeys = systems(records, true).keySet();
        out.add(new Dial(RECORDS, label("records"), List.of(onOff(false, systems(records, false)),
                onOff(true, systems(records, true))), recordKeys::contains));
        return out;
    }

    private static Component label(String id) {
        return Component.translatable("townstead.switchboard.dial." + id);
    }

    private static Level level(String key, Map<String, Object> values) {
        return new Level(Component.translatable("townstead.switchboard.dial." + key), values);
    }

    private static Level onOff(boolean on, Map<String, Object> values) {
        return new Level(on ? SettingControls.on() : SettingControls.off(), values);
    }

    private static Object orMissing(Object configValue) {
        return configValue == null ? "" : configValue;
    }

    private static Map<String, Object> with(List<Object> configValues, boolean value) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Object configValue : configValues) {
            String key = SettingIndex.keyOf(configValue);
            if (key != null) out.put(key, value);
        }
        return out;
    }

    /** Deadly needs are never part of a dial: a player turns "Needs can kill" on deliberately. */
    private static Map<String, Object> pace(Map<String, Object> values, double pace) {
        String paceKey = SettingIndex.keyOf(TownsteadConfig.NEEDS_PACE);
        if (paceKey != null) values.put(paceKey, pace);
        return values;
    }

    private static Map<String, Object> systems(List<String> names, boolean value) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String name : names) out.put(Systems.key(name), value);
        return out;
    }

    private static Map<String, Object> systems(Map<String, Boolean> values) {
        Map<String, Object> out = new LinkedHashMap<>();
        values.forEach((name, value) -> out.put(Systems.key(name), value));
        return out;
    }
}
