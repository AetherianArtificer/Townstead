package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.ability.Ability;
import net.minecraft.world.entity.LivingEntity;

import java.util.Set;

/**
 * Client-side mirror of {@code Abilities.isActive}, resolved from the synced
 * per-entity expressed-gene set ({@link RootClientStore}) and the gene catalog
 * ({@link RootCatalogClient}, which carries each ability gene's key + mode). Used
 * for movement abilities that the controlling client must predict locally (e.g.
 * walk-on-fluid for the local player), where a server-only decision would be
 * overridden by client physics and rubber-band.
 *
 * <p>Passive abilities count whenever expressed. Toggle-mode abilities read the
 * owner's live on/off state, synced per-entity by {@code AbilityTogglesS2CPayload}
 * (still enforced server-side for villagers / other entities).</p>
 */
public final class ClientAbilities {

    private ClientAbilities() {}

    public static boolean isActive(LivingEntity entity, Ability ability) {
        Set<String> geneIds = RootClientStore.behaviorGenes(entity.getId());
        if (geneIds.isEmpty()) return false;
        String key = ability.key();
        for (String geneId : geneIds) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            if (gene == null || !gene.isAbility() || !gene.abilityKey().equals(key)) continue;
            if (gene.abilityToggle()) {
                if (RootClientStore.isToggled(entity.getId(), geneId)) return true;
            } else {
                return true;
            }
        }
        return false;
    }

    /** Whether an active {@code hover} gene floats in the drift style (authored, or the default for hover). */
    public static boolean drifts(LivingEntity entity) {
        for (String geneId : RootClientStore.behaviorGenes(entity.getId())) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            if (gene == null || !gene.isAbility() || !gene.abilityKey().equals(Ability.HOVER.key())) continue;
            if (gene.abilityToggle() && !RootClientStore.isToggled(entity.getId(), geneId)) continue;
            String style = gene.abilityStyle();
            return style.isEmpty() || style.equals("drift");
        }
        return false;
    }
}
