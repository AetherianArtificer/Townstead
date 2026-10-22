package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.ability.Ability;
import com.aetherianartificer.townstead.root.locomotion.FallStyle;
import net.minecraft.client.Minecraft;
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

    /** Whether an active water gene ({@code water_breathing} or {@code swimming}) swims in the fish style. */
    public static boolean swimsLikeFish(LivingEntity entity) {
        for (String geneId : RootClientStore.behaviorGenes(entity.getId())) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            if (gene == null || !gene.isAbility() || !gene.abilityStyle().equals("fish")) continue;
            String key = gene.abilityKey();
            if (!key.equals(Ability.WATER_BREATHING.key()) && !key.equals(Ability.SWIMMING.key())) continue;
            if (gene.abilityToggle() && !RootClientStore.isToggled(entity.getId(), geneId)) continue;
            return true;
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

    /** The fall style of an active {@code slow_fall} gene, or null when none names one. */
    @org.jetbrains.annotations.Nullable
    public static FallStyle fallStyle(LivingEntity entity) {
        for (String geneId : RootClientStore.behaviorGenes(entity.getId())) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            if (gene == null || !gene.isAbility() || !gene.abilityKey().equals(Ability.SLOW_FALL.key())) continue;
            if (gene.abilityToggle() && !RootClientStore.isToggled(entity.getId(), geneId)) continue;
            FallStyle style = FallStyle.byKey(gene.abilityStyle());
            if (style != null) return style;
        }
        return null;
    }

    public static boolean isLocalPlayer(LivingEntity entity) {
        return entity == Minecraft.getInstance().player;
    }
}
