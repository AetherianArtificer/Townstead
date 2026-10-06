package com.aetherianartificer.townstead.root.rig;

import com.aetherianartificer.townstead.root.LifeStage;
import com.aetherianartificer.townstead.root.LifeStageProgression;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.PlayerRoot;
import com.aetherianartificer.townstead.root.Species;
import com.aetherianartificer.townstead.root.SpeciesRegistry;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Resolves the {@link RigDefinition} an entity currently uses, server-side, from the server registries
 * (the client reads the synced catalog instead). A villager takes its life-stage rig override when the
 * current stage declares one, else its species rig; a player takes its origin's species rig (players have
 * no life stages). Returns null for entities with no Townstead rig. Used by server-side rig consumers
 * (hitboxes, the equipment gate) so they all resolve a rig the same way.
 */
public final class ServerRig {

    private ServerRig() {}

    /** The rig definition this entity renders as, server-side, or null if it has none. */
    public static RigDefinition defFor(LivingEntity entity) {
        String id = rigIdFor(entity);
        return id == null || id.isEmpty() ? null : RigRegistry.byId(id);
    }

    /** The rig id for this entity (life-stage override / species rig), or null. */
    public static String rigIdFor(LivingEntity entity) {
        if (entity instanceof VillagerEntityMCA villager) {
            String form = com.aetherianartificer.townstead.pheno.state.StateForms.rig(villager);
            if (form != null) return form;
            LifeStage stage = LifeStageProgression.currentStage(villager);
            if (stage != null && stage.rig() != null && !stage.rig().isEmpty()) return stage.rig();
            return speciesRig(TownsteadVillagers.get(villager).life().rootId());
        }
        if (entity instanceof Player player) {
            return speciesRig(PlayerRoot.getRootId(player));
        }
        return null;
    }

    /** Whether this entity wears MCA's own villager body rather than a custom rig. */
    public static boolean hasVillagerBody(LivingEntity entity) {
        return isVillagerBody(rigIdFor(entity));
    }

    /** Whether people of this Root wear MCA's own villager body rather than a custom rig. */
    public static boolean hasVillagerBody(ResourceLocation rootId) {
        return isVillagerBody(rootId == null ? null : speciesRig(rootId.toString()));
    }

    // Species that keep MCA's body name it as mca:villager rather than leaving it empty.
    private static boolean isVillagerBody(String rig) {
        return rig == null || rig.isEmpty() || rig.equals(com.aetherianartificer.townstead.root.Rig.VILLAGER.base());
    }

    private static String speciesRig(String rootIdRaw) {
        ResourceLocation rootId = ResourceLocation.tryParse(rootIdRaw);
        ResourceLocation speciesId = RootRegistry.effectiveSpecies(rootId);
        Species species = speciesId == null ? null : SpeciesRegistry.byId(speciesId);
        return species == null ? null : species.rig().base();
    }
}
