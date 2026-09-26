package com.aetherianartificer.townstead.api.v1;

import com.aetherianartificer.townstead.api.v1.model.CultureAccessSnapshot;
import com.aetherianartificer.townstead.api.v1.model.RootAccessSnapshot;
import com.aetherianartificer.townstead.api.v1.model.SettingSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * The world's Townstead settings as they are in effect now: each world's own choices from World Setup,
 * over the modpack's defaults, over the config file. Read-only; the world's operator owns them.
 * {@code WorldSettingsChangedEvent} fires when they change.
 *
 * <p>Available on the server and, from the synced values, on the client. On a client connected to a
 * remote server, {@link SettingSnapshot#source()} reads {@code WORLD} for any value the world or the
 * modpack set, {@link SettingSnapshot#locked()} is always false, and Root and culture access is empty
 * because the client does not hold that data.</p>
 *
 * <p>Added in API revision 2.</p>
 */
public interface SettingsApi {
    SettingsApi EMPTY = new SettingsApi() {};

    /** Every system a world can switch off, e.g. {@code careers}, {@code roots}, {@code chronicles}. */
    default List<String> systems() {
        return List.of();
    }

    /** Whether a system runs in this world. Unknown ids read as on. */
    default boolean systemEnabled(String system) {
        return true;
    }

    /** Every setting key, as a dotted config path such as {@code needs.hunger.enableVillagerHunger}. */
    default List<String> keys() {
        return List.of();
    }

    /**
     * One setting in effect. Besides the config keys this answers the per-content keys World Setup
     * writes: {@code roots.state.<root>}, {@code roots.rate.<root>}, {@code roots.group.<species|ancestry|lineage|pack>.<id>},
     * {@code roots.groupRate.<dimension>.<id>}, {@code cultures.enabled.<culture>} and {@code cultures.rate.<culture>}.
     */
    default Optional<SettingSnapshot> setting(String key) {
        return Optional.empty();
    }

    /** A true/false setting, or {@code fallback} when the key is unknown or is not true/false. */
    default boolean flag(String key, boolean fallback) {
        return fallback;
    }

    /** A number setting, or {@code fallback} when the key is unknown or is not a number. */
    default double number(String key, double fallback) {
        return fallback;
    }

    /** Any setting as text (choices by id, lists comma-separated), or {@code fallback} when unknown. */
    default String text(String key, String fallback) {
        return fallback;
    }

    /** Who may have a Root in this world and how often it spawns. Empty for an unknown Root. */
    default Optional<RootAccessSnapshot> rootAccess(ResourceLocation root) {
        return Optional.empty();
    }

    /** Whether founders may be raised in a culture, and how often. Empty for an unknown culture. */
    default Optional<CultureAccessSnapshot> cultureAccess(ResourceLocation culture) {
        return Optional.empty();
    }

    /**
     * Whether one of Townstead's blocks or items is usable here. Content of a switched-off system stays
     * registered but cannot be crafted, placed or used. Anything not Townstead's reads as enabled.
     */
    default boolean contentEnabled(ResourceLocation blockOrItem) {
        return true;
    }
}
