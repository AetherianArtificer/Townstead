package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Generates opaque namespaced world-instance ids without coupling identity to a display name. */
public final class PoliticalIds {
    private PoliticalIds() {}

    public static ResourceLocation organization(String namespace) {
        return create(namespace, "organization");
    }

    public static ResourceLocation polity(String namespace) {
        return create(namespace, "polity");
    }

    public static ResourceLocation affiliation(String namespace) {
        return create(namespace, "affiliation");
    }

    public static ResourceLocation villagePolity(SettlementRef settlement) {
        return villageActor("polity", settlement);
    }

    public static ResourceLocation villageCouncil(SettlementRef settlement) {
        return villageActor("village_council", settlement);
    }

    /** Stable generated-government id for one profile at one settlement. */
    public static ResourceLocation villageGovernment(SettlementRef settlement, ResourceLocation profile) {
        return villageActor("government/" + profile.getNamespace() + "/" + profile.getPath(), settlement);
    }

    private static ResourceLocation villageActor(String family, SettlementRef settlement) {
        ResourceLocation id = DataPackLang.parseId("townstead:" + family + "/"
                + settlement.dimension().getNamespace() + "/" + settlement.dimension().getPath()
                + "/" + settlement.villageId());
        if (id == null) throw new IllegalArgumentException("Invalid settlement identity " + settlement);
        return id;
    }

    private static ResourceLocation create(String namespace, String family) {
        ResourceLocation id = DataPackLang.parseId(namespace + ":" + family + "/"
                + UUID.randomUUID().toString().toLowerCase(java.util.Locale.ROOT));
        if (id == null) throw new IllegalArgumentException("Invalid political id namespace '" + namespace + "'");
        return id;
    }
}
