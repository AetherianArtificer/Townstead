package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A thrall serves one vampire of their village, bound by a thrall Bond. A thrall without a master
 * finds one among their village's vampires, the one with fewest thralls; a master who stops being
 * a vampire lets them go. Thralls cannot be turned (the thrall state excludes the vampire state),
 * so their master may keep drinking from them.
 */
public final class Thralls {
    public static final ResourceLocation STATE = ResourceLocation.tryParse("townstead_state:thrall");
    public static final ResourceLocation BOND = ResourceLocation.tryParse("townstead:thrall");
    private static final ResourceLocation PROVENANCE = ResourceLocation.tryParse("townstead:thrall_service");
    private static final int INTERVAL = 200;

    private Thralls() {}

    public static void tick(VillagerEntityMCA villager) {
        if ((villager.tickCount + villager.getId()) % INTERVAL != 0 || !(villager.level() instanceof ServerLevel level)) return;
        if (EntityStates.definition(STATE) == null || !EntityStates.resolve(villager, STATE).active()) return;
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        BondInstance bond = bond(data, villager.getUUID());
        if (bond != null) {
            Party master = bond.party("master");
            Entity entity = master == null || master.person() == null ? null : level.getEntity(master.person());
            if (entity instanceof LivingEntity living && !VampireVillagers.isVampire(living)) {
                FactionBonds.end(data, bond, level.getGameTime(), "released");
            }
            return;
        }
        VillagerEntityMCA master = candidate(level, data, villager);
        if (master == null) return;
        FactionBonds.form(data, BOND, List.of(new BondInstance.Side("master", Party.person(master.getUUID())),
                new BondInstance.Side("thrall", Party.person(villager.getUUID()))), PROVENANCE, level.getGameTime());
    }

    /** Whether {@code thrall} serves {@code master}. */
    public static boolean serves(LivingEntity master, LivingEntity thrall) {
        if (!(thrall.level() instanceof ServerLevel level)) return false;
        BondInstance bond = bond(PoliticalSavedData.get(level.getServer()), thrall.getUUID());
        Party party = bond == null ? null : bond.party("master");
        return party != null && master.getUUID().equals(party.person());
    }

    private static @Nullable BondInstance bond(PoliticalSavedData data, UUID thrall) {
        Party self = Party.person(thrall);
        for (BondInstance bond : data.activeBonds(self)) {
            if (bond.kind().equals(BOND) && "thrall".equals(bond.roleOf(self))) return bond;
        }
        return null;
    }

    private static @Nullable VillagerEntityMCA candidate(ServerLevel level, PoliticalSavedData data, VillagerEntityMCA thrall) {
        Optional<Village> home = thrall.getResidency().getHomeVillage();
        if (home.isEmpty()) return null;
        VillagerEntityMCA best = null;
        int fewest = Integer.MAX_VALUE;
        for (UUID id : home.get().getResidentsUUIDs().toList()) {
            if (id == null || id.equals(thrall.getUUID()) || !(level.getEntity(id) instanceof VillagerEntityMCA resident)) continue;
            if (!VampireVillagers.isVampire(resident)) continue;
            Party master = Party.person(resident.getUUID());
            int thralls = (int) data.activeBonds(master).stream()
                    .filter(bond -> bond.kind().equals(BOND) && "master".equals(bond.roleOf(master))).count();
            if (thralls < fewest) {
                fewest = thralls;
                best = resident;
            }
        }
        return best;
    }
}
