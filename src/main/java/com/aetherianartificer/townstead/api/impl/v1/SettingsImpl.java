package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.api.v1.SettingsApi;
import com.aetherianartificer.townstead.api.v1.model.CultureAccessSnapshot;
import com.aetherianartificer.townstead.api.v1.model.RootAccessSnapshot;
import com.aetherianartificer.townstead.api.v1.model.SettingKind;
import com.aetherianartificer.townstead.api.v1.model.SettingSnapshot;
import com.aetherianartificer.townstead.api.v1.model.SettingSource;
import com.aetherianartificer.townstead.culture.CultureRules;
import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.RootRules;
import com.aetherianartificer.townstead.switchboard.ContentGates;
import com.aetherianartificer.townstead.switchboard.SettingIndex;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import com.aetherianartificer.townstead.switchboard.SwitchboardPack;
import com.aetherianartificer.townstead.switchboard.SwitchboardServer;
import com.aetherianartificer.townstead.switchboard.Systems;
import com.aetherianartificer.townstead.switchboard.WorldKeys;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** {@link SettingsApi} over the Switchboard, shared by the server and client entry points. */
public final class SettingsImpl implements SettingsApi {
    public static final SettingsImpl INSTANCE = new SettingsImpl();

    private SettingsImpl() {}

    @Override
    public List<String> systems() {
        return TownsteadConfig.SYSTEM_NAMES;
    }

    @Override
    public boolean systemEnabled(String system) {
        return system == null || Systems.on(system);
    }

    @Override
    public List<String> keys() {
        return SettingIndex.all().stream().map(SettingIndex.Entry::key).toList();
    }

    @Override
    public Optional<SettingSnapshot> setting(String key) {
        if (key == null) return Optional.empty();
        try {
            Object value = value(key);
            if (value == null) return Optional.empty();
            return Optional.of(new SettingSnapshot(key, kind(key, value), text(value), list(value), source(key, value),
                    SwitchboardPack.isLocked(key) && SwitchboardPack.values().containsKey(key)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean flag(String key, boolean fallback) {
        Object value = safeValue(key);
        return value instanceof Boolean b ? b : fallback;
    }

    @Override
    public double number(String key, double fallback) {
        Object value = safeValue(key);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    @Override
    public String text(String key, String fallback) {
        Object value = safeValue(key);
        return value == null ? fallback : text(value);
    }

    @Override
    public Optional<RootAccessSnapshot> rootAccess(ResourceLocation root) {
        if (root == null || RootRegistry.byId(root) == null) return Optional.empty();
        try {
            return Optional.of(new RootAccessSnapshot(root, RootRules.state(root).name().toLowerCase(Locale.ROOT),
                    RootRules.villagersSpawn(root), RootRules.playersChoose(root), RootRules.rate(root),
                    com.aetherianartificer.townstead.root.RootDiscovery.isDiscovered(root)));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<CultureAccessSnapshot> cultureAccess(ResourceLocation culture) {
        if (culture == null || Cultures.get(culture) == null) return Optional.empty();
        String id = culture.toString();
        return Optional.of(new CultureAccessSnapshot(culture, CultureRules.enabled(id), CultureRules.rate(id)));
    }

    @Override
    public boolean contentEnabled(ResourceLocation blockOrItem) {
        return ContentGates.enabled(blockOrItem);
    }

    private static Object safeValue(String key) {
        try {
            return key == null ? null : value(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Object value(String key) {
        SettingIndex.Entry entry = SettingIndex.get(key);
        if (entry != null) {
            try {
                return Switchboard.get(entry.value());
            } catch (IllegalStateException e) {
                return entry.defaultValue();
            }
        }
        return WorldKeys.isKey(key) ? Switchboard.content(key) : null;
    }

    private static SettingKind kind(String key, Object value) {
        if (value instanceof Boolean) return SettingKind.BOOLEAN;
        if (value instanceof Integer || value instanceof Long) return SettingKind.INTEGER;
        if (value instanceof Number) return SettingKind.DECIMAL;
        if (value instanceof Enum<?>) return SettingKind.CHOICE;
        if (value instanceof List<?>) return SettingKind.LIST;
        return SettingKind.TEXT;
    }

    private static String text(Object value) {
        if (value instanceof Enum<?> e) return e.name().toLowerCase(Locale.ROOT);
        if (value instanceof List<?> items) return String.join(",", items.stream().map(String::valueOf).toList());
        return String.valueOf(value);
    }

    private static List<String> list(Object value) {
        return value instanceof List<?> items ? items.stream().map(String::valueOf).toList() : List.of();
    }

    private static SettingSource source(String key, Object value) {
        if (SwitchboardPack.isLocked(key) && SwitchboardPack.values().containsKey(key)) return SettingSource.MODPACK;
        if (SwitchboardServer.worldKeys().contains(key)) return SettingSource.WORLD;
        if (SwitchboardPack.values().containsKey(key)) return SettingSource.MODPACK;
        // A remote client holds the synced values but not where they came from.
        if (Switchboard.overrides().containsKey(key)) return SettingSource.WORLD;
        SettingIndex.Entry entry = SettingIndex.get(key);
        Object def = entry != null ? entry.defaultValue() : WorldKeys.defaultValue(key);
        return Objects.equals(value, def) ? SettingSource.DEFAULT : SettingSource.CONFIG;
    }
}
