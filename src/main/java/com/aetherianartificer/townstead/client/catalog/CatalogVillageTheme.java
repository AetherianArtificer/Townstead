package com.aetherianartificer.townstead.client.catalog;

import com.aetherianartificer.townstead.spirit.SpiritRegistry;
import com.aetherianartificer.townstead.spirit.VillageSpiritSyncPayload;

/** Inherit the current village's leaning; grouping/filtering never changes the screen's identity. */
public final class CatalogVillageTheme {
    private CatalogVillageTheme() {}
    public static CatalogDataLoader.Theme resolve(CatalogDataLoader.Theme base, VillageSpiritSyncPayload village) {
        if (village == null) return base;
        String id = village.toReadout().primarySpiritId();
        if (id == null) {
            int most = 0;
            for (var spirit : SpiritRegistry.ordered()) {
                int points = village.perSpirit().getOrDefault(spirit.id(), 0);
                if (points > most) { most = points; id = spirit.id(); }
            }
        }
        var spirit = SpiritRegistry.get(id == null ? "" : id);
        if (spirit.isEmpty()) return base;
        int color = spirit.get().color();
        return new CatalogDataLoader.Theme(base.backgroundTexture(), base.frameColor(), blend(base.panelColor(), color, .22f),
                blend(base.titleBarColor(), color, .35f), blend(base.graphBackgroundColor(), color, .14f),
                blend(base.detailsBackgroundColor(), color, .22f), blend(base.borderColor(), color, .35f), base.gridColor(),
                base.showGrid(), base.nodeFillColor(), base.nodeHoverFillColor(), base.nodeSelectedFillColor(),
                base.nodeBorderColor(), base.nodeHoverBorderColor(), base.nodeSelectedBorderColor(), base.builtNodeFillColor(),
                base.builtNodeHoverFillColor(), base.builtNodeSelectedFillColor(), base.builtNodeBorderColor(),
                base.builtNodeHoverBorderColor(), base.builtNodeSelectedBorderColor());
    }
    private static int blend(int base, int accent, float weight) {
        int out = base & 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) out |= Math.round(((base >> shift) & 255) * (1 - weight) + ((accent >> shift) & 255) * weight) << shift;
        return out;
    }
}
