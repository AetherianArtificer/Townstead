package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Whether a person may act for a faction: they hold an active bond with it whose faction role gives
 * the capability. Bonds with every other faction are ignored.
 */
public final class PoliticalAuthority {
    private PoliticalAuthority() {}

    public static Decision mayAct(PoliticalSavedData data, UUID person, ResourceLocation factionId,
                                  ResourceLocation capability) {
        if (data == null || person == null || factionId == null || capability == null) {
            return deny("invalid_request");
        }
        Faction faction = data.faction(factionId);
        if (faction == null) return deny("unknown_faction");
        if (!faction.active()) return deny("inactive_faction");
        if (data.externalGovernment(faction.id())) return deny("external_authority");
        Party self = Party.faction(faction.id());
        boolean bonded = false;
        for (BondInstance bond : data.activeBonds(Party.person(person))) {
            if (!bond.involves(self)) continue;
            bonded = true;
            BondKind kind = BondKinds.all().get(bond.kind());
            String role = bond.roleOf(self);
            BondKind.Role definition = kind == null || role == null ? null : kind.role(role);
            if (definition != null && definition.gives().contains(capability)) {
                return new Decision(true, reason("allowed"), bond.kind());
            }
        }
        return deny(bonded ? "missing_capability" : "no_bond");
    }

    public static boolean allowed(PoliticalSavedData data, UUID person, ResourceLocation faction, ResourceLocation capability) {
        return mayAct(data, person, faction, capability).allowed();
    }

    private static Decision deny(String reason) {
        return new Decision(false, reason(reason), null);
    }

    private static ResourceLocation reason(String path) {
        ResourceLocation id = DataPackLang.parseId("townstead:" + path);
        if (id == null) throw new IllegalStateException(path);
        return id;
    }

    /** {@code grantingBond} is the kind of bond, such as an office, that granted the capability. */
    public record Decision(boolean allowed, ResourceLocation reason, @Nullable ResourceLocation grantingBond) {}
}
