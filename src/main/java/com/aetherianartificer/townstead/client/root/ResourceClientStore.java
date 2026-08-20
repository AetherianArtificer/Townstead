package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.root.ability.ResourceSyncS2CPayload;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client resource snapshot plus value-change times used by contextual HUD fading. */
public final class ResourceClientStore {

    public record Tracked(ResourceSyncS2CPayload.Bar bar, long changedAtMillis) {}
    public record Visible(ResourceSyncS2CPayload.Bar bar, float alpha) {}

    private static volatile List<Tracked> tracked = List.of();

    private ResourceClientStore() {}

    public static void set(List<ResourceSyncS2CPayload.Bar> value) {
        set(value, System.currentTimeMillis());
    }

    static void set(List<ResourceSyncS2CPayload.Bar> value, long nowMillis) {
        List<ResourceSyncS2CPayload.Bar> incoming = value == null ? List.of() : List.copyOf(value);
        Map<String, Tracked> previous = new LinkedHashMap<>();
        for (Tracked item : tracked) previous.put(item.bar().resourceId(), item);

        List<Tracked> next = new ArrayList<>(incoming.size());
        for (ResourceSyncS2CPayload.Bar bar : incoming) {
            Tracked old = previous.get(bar.resourceId());
            long changedAt = old == null || !old.bar().equals(bar) ? nowMillis : old.changedAtMillis();
            next.add(new Tracked(bar, changedAt));
        }
        tracked = List.copyOf(next);
    }

    public static List<ResourceSyncS2CPayload.Bar> get() {
        List<ResourceSyncS2CPayload.Bar> out = new ArrayList<>(tracked.size());
        for (Tracked item : tracked) out.add(item.bar());
        return List.copyOf(out);
    }

    public static List<Visible> visible(long nowMillis, TownsteadConfig.ResourceHudVisibility visibility,
                                        int holdTicks, int fadeTicks, boolean forceVisible) {
        if (visibility == TownsteadConfig.ResourceHudVisibility.NEVER) return List.of();
        List<Visible> out = new ArrayList<>();
        for (Tracked item : tracked) {
            ResourceSyncS2CPayload.Bar bar = item.bar();
            float alpha;
            if (forceVisible || visibility == TownsteadConfig.ResourceHudVisibility.ALWAYS) {
                alpha = 1f;
            } else if (visibility == TownsteadConfig.ResourceHudVisibility.NOT_AT_REST
                    && bar.value() != bar.restingValue()) {
                alpha = 1f;
            } else {
                alpha = ResourceHudMath.contextualAlpha(
                        Math.max(0L, nowMillis - item.changedAtMillis()), holdTicks, fadeTicks);
            }
            if (alpha > 0f) out.add(new Visible(bar, alpha));
        }
        return List.copyOf(out);
    }

    public static void clear() {
        tracked = List.of();
    }
}
