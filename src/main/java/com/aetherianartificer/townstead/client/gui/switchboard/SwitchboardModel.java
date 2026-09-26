package com.aetherianartificer.townstead.client.gui.switchboard;

import com.aetherianartificer.townstead.switchboard.SettingIndex;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import com.aetherianartificer.townstead.switchboard.SwitchboardOpenS2CPayload;
import com.aetherianartificer.townstead.switchboard.SwitchboardPreset;
import com.aetherianartificer.townstead.switchboard.SwitchboardSaveC2SPayload;
import com.aetherianartificer.townstead.switchboard.WorldKeys;
import com.aetherianartificer.townstead.switchboard.WorldKeys.RootState;
import com.google.gson.JsonElement;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Everything the World Setup screens edit before Done: this world's values, this computer's client
 * values, and what each falls back to. Every Switchboard screen shares one model.
 */
final class SwitchboardModel {
    final boolean firstJoin;
    final Map<String, JsonElement> pack;
    final Set<String> locked;
    final Map<String, JsonElement> world;
    final ContentCatalog catalog;
    private final Map<SettingIndex.Entry, Object> device = new LinkedHashMap<>();

    SwitchboardModel(SwitchboardOpenS2CPayload payload) {
        this.firstJoin = payload.firstJoin();
        this.pack = payload.packValues();
        this.locked = payload.lockedKeys();
        this.world = new LinkedHashMap<>(payload.worldValues());
        this.catalog = new ContentCatalog(payload.catalog());
    }

    boolean isLocked(String key) {
        return locked.contains(key);
    }

    // ── World values ──────────────────────────────────────────────────────

    /** The default a key has when neither the world, the modpack nor the config file sets it. */
    static @Nullable Object defaultOf(String key) {
        SettingIndex.Entry entry = SettingIndex.get(key);
        return entry != null ? entry.defaultValue() : WorldKeys.defaultValue(key);
    }

    /** What a world value falls back to: the modpack default, else the config file, else the default. */
    @Nullable Object baselineOf(String key) {
        Object fromPack = pack.containsKey(key) ? Switchboard.parse(key, pack.get(key)) : null;
        if (fromPack != null) return fromPack;
        SettingIndex.Entry entry = SettingIndex.get(key);
        return entry != null ? configured(entry) : WorldKeys.defaultValue(key);
    }

    @Nullable Object value(String key) {
        return valueOf(key, world);
    }

    @Nullable Object valueOf(String key, Map<String, JsonElement> values) {
        Object own = values.containsKey(key) ? Switchboard.parse(key, values.get(key)) : null;
        return own != null ? own : baselineOf(key);
    }

    void set(String key, Object value) {
        if (isLocked(key)) return;
        if (Objects.equals(value, baselineOf(key))) world.remove(key);
        else world.put(key, SettingIndex.toJson(value));
    }

    void resetWorld(Predicate<String> keys) {
        world.keySet().removeIf(key -> !isLocked(key) && keys.test(key));
    }

    static Object configured(SettingIndex.Entry entry) {
        try {
            Object v = entry.value().get();
            return v != null ? v : entry.defaultValue();
        } catch (IllegalStateException e) {
            return entry.defaultValue();
        }
    }

    // ── Client values ─────────────────────────────────────────────────────

    Object deviceValue(SettingIndex.Entry entry) {
        Object v = device.get(entry);
        return v != null ? v : configured(entry);
    }

    void setDevice(SettingIndex.Entry entry, Object value) {
        device.put(entry, value);
    }

    void resetDevice() {
        for (SettingIndex.Entry entry : SettingIndex.client()) device.put(entry, entry.defaultValue());
    }

    void save() {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(SwitchboardSaveC2SPayload.of(world, firstJoin));
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(SwitchboardSaveC2SPayload.of(world, firstJoin));
        *///?}
        if (!device.isEmpty()) {
            device.forEach(SettingIndex::writeClient);
            SettingIndex.saveClient();
        }
    }

    // ── Presets ───────────────────────────────────────────────────────────

    /** Every world key a preset can speak to: config settings, loaded content, and anything already set. */
    private Set<String> worldKeys(Map<String, JsonElement> extra) {
        Set<String> keys = new LinkedHashSet<>();
        for (SettingIndex.Entry entry : SettingIndex.all()) keys.add(entry.key());
        keys.addAll(catalog.keys());
        for (String key : world.keySet()) if (WorldKeys.isKey(key)) keys.add(key);
        for (String key : pack.keySet()) if (WorldKeys.isKey(key)) keys.add(key);
        for (String key : extra.keySet()) if (WorldKeys.isKey(key)) keys.add(key);
        return keys;
    }

    /** The world's settings as a preset: every value that differs from Townstead's default. */
    SwitchboardPreset snapshot(String name) {
        Map<String, JsonElement> values = new LinkedHashMap<>();
        for (String key : worldKeys(Map.of())) {
            Object v = value(key);
            if (v != null && !Objects.equals(v, defaultOf(key))) values.put(key, SettingIndex.toJson(v));
        }
        return new SwitchboardPreset(name, "", values);
    }

    record Change(Component setting, Component from, Component to) {}

    record Preview(List<Change> changes, int unknown, int locked) {}

    Preview preview(SwitchboardPreset preset) {
        Map<String, JsonElement> next = resolve(preset);
        List<Change> changes = new ArrayList<>();
        for (String key : worldKeys(preset.values())) {
            Object before = value(key);
            Object after = valueOf(key, next);
            if (!Objects.equals(before, after)) {
                changes.add(new Change(keyLabel(key), display(key, before), display(key, after)));
            }
        }
        int unknown = 0;
        int lockedCount = 0;
        for (Map.Entry<String, JsonElement> e : preset.values().entrySet()) {
            if (Switchboard.parse(e.getKey(), e.getValue()) == null) unknown++;
            else if (isLocked(e.getKey())) lockedCount++;
        }
        return new Preview(changes, unknown, lockedCount);
    }

    void apply(SwitchboardPreset preset) {
        Map<String, JsonElement> next = resolve(preset);
        world.clear();
        world.putAll(next);
    }

    /** The world values that make every unlocked setting match the preset, or Townstead's default. */
    private Map<String, JsonElement> resolve(SwitchboardPreset preset) {
        Map<String, JsonElement> next = new LinkedHashMap<>();
        for (String key : worldKeys(preset.values())) {
            if (isLocked(key)) {
                if (world.containsKey(key)) next.put(key, world.get(key));
                continue;
            }
            Object target = preset.values().containsKey(key) ? Switchboard.parse(key, preset.values().get(key)) : null;
            if (target == null) target = defaultOf(key);
            if (target != null && !Objects.equals(target, baselineOf(key))) next.put(key, SettingIndex.toJson(target));
        }
        return next;
    }

    Component keyLabel(String key) {
        SettingIndex.Entry entry = SettingIndex.get(key);
        if (entry != null) return SettingLabels.label(entry);
        Component subject = catalog.label(key);
        String part = key.startsWith(WorldKeys.ROOT_STATE) ? "state"
                : key.startsWith(WorldKeys.GROUP_ON) || key.startsWith(WorldKeys.CULTURE_ON) ? "enabled" : "rate";
        return Component.translatable("townstead.switchboard.change." + part, subject);
    }

    static Component display(String key, @Nullable Object value) {
        if (value instanceof Double d && WorldKeys.isKey(key)) return RateSlider.format(d);
        if (value instanceof Boolean b) return b ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF;
        if (value instanceof RootState s) {
            return Component.translatable("townstead.switchboard.root.state." + s.name().toLowerCase(Locale.ROOT));
        }
        if (value instanceof Enum<?> e) return Component.literal(SettingLabels.pretty(e.name()));
        return Component.literal(SettingControls.text(value));
    }
}
