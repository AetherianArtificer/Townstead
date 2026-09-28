package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/**
 * Gives a villager who comes to live in a village its founding profile's {@code resident_states},
 * each by its share, once. Anyone born there is left to the aspects' own inheritance.
 */
public final class ResidentStates {
    private static final String ROLLED = "townstead:resident_states_rolled";
    private static final int INTERVAL = 100;

    private ResidentStates() {}

    public static void tick(VillagerEntityMCA villager) {
        if ((villager.tickCount + villager.getId()) % INTERVAL != 0 || !(villager.level() instanceof ServerLevel level)) return;
        CompoundTag tag = villager.getPersistentData();
        if (tag.getBoolean(ROLLED)) return;
        if (villager.isBaby()) {
            tag.putBoolean(ROLLED, true);
            return;
        }
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isEmpty()) return;
        SettlementRef settlement = new SettlementRef(level.dimension().location(), home.get().getId());
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        // Wait until the village has its identity; its profile may still be on the way.
        if (data.faction(settlement) == null) return;
        tag.putBoolean(ROLLED, true);
        SettlementFoundingRecord record = data.founding(settlement);
        FoundingProfileDefinition profile = record == null ? null : FoundingProfiles.get(record.profile());
        if (profile == null) return;
        for (FoundingProfileDefinition.ResidentState state : profile.residentStates()) {
            if (villager.getRandom().nextFloat() >= state.share()) continue;
            double amount = state.min() + villager.getRandom().nextDouble() * (state.max() - state.min());
            EntityStates.set(villager, state.state(), Math.round(amount), 0, null);
        }
    }
}
