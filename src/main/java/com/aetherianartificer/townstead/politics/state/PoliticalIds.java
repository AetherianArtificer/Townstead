package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Opaque namespaced world-instance ids, never derived from a display name. */
public final class PoliticalIds {
    private PoliticalIds() {}

    public static ResourceLocation faction(String namespace) {
        ResourceLocation id = DataPackLang.parseId(namespace + ":faction/"
                + UUID.randomUUID().toString().toLowerCase(java.util.Locale.ROOT));
        if (id == null) throw new IllegalArgumentException("Invalid political id namespace '" + namespace + "'");
        return id;
    }

    /** The stable id of the faction a village gets when it is first recognized. */
    public static ResourceLocation villageFaction(SettlementRef settlement) {
        ResourceLocation id = DataPackLang.parseId("townstead:faction/"
                + settlement.dimension().getNamespace() + "/" + settlement.dimension().getPath()
                + "/" + settlement.villageId());
        if (id == null) throw new IllegalArgumentException("Invalid settlement identity " + settlement);
        return id;
    }
}
