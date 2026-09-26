package com.aetherianartificer.townstead.politics.standing;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.otectus.OtectusStanding;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/** Computes a person's {@link Standing} in a settlement. Every source reads data that survives unloading. */
public final class StandingService {
    /** Hearts are counted per resident, so they are scaled down to sit beside deeds and reputation. */
    private static final int HEARTS_PER_POINT = 10;
    private static final int REPUTATION_PER_POINT = 10;

    private static @Nullable Field reputationField;
    private static boolean reputationFieldResolved;

    private StandingService() {}

    public static Standing of(MinecraftServer server, UUID person, SettlementRef settlement) {
        if (server == null || person == null || settlement == null) return Standing.NONE;
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, settlement.dimension()));
        Village village = level == null ? null : VillageManager.get(level).getOrEmpty(settlement.villageId()).orElse(null);
        int hearts = village == null ? 0 : hearts(village, person) / HEARTS_PER_POINT;
        int deeds = DeedLedger.get(server).points(settlement, person);
        int reputation = OtectusStanding.score(server, person, settlement.dimension(), settlement.villageId())
                .orElse(0) / REPUTATION_PER_POINT;
        return new Standing(hearts, deeds, reputation);
    }

    /**
     * The sum of every resident's hearts toward this person, from MCA's village record. MCA only
     * exposes it for an online player, so the record is read directly.
     */
    @SuppressWarnings("unchecked")
    private static int hearts(Village village, UUID person) {
        Field field = reputationField();
        if (field == null) return 0;
        try {
            Map<UUID, Map<UUID, Integer>> reputation = (Map<UUID, Map<UUID, Integer>>) field.get(village);
            Map<UUID, Integer> byResident = reputation == null ? null : reputation.get(person);
            if (byResident == null) return 0;
            int sum = 0;
            for (int value : byResident.values()) sum += value;
            return sum;
        } catch (ReflectiveOperationException | ClassCastException error) {
            return 0;
        }
    }

    /** Clears a person's standing everywhere: MCA's village hearts record and their deeds. */
    @SuppressWarnings("unchecked")
    public static void forget(MinecraftServer server, UUID person) {
        DeedLedger.get(server).forget(person);
        Field field = reputationField();
        if (field == null) return;
        for (ServerLevel level : server.getAllLevels()) {
            for (Village village : VillageManager.get(level)) {
                try {
                    Map<UUID, Map<UUID, Integer>> reputation = (Map<UUID, Map<UUID, Integer>>) field.get(village);
                    if (reputation != null && reputation.remove(person) != null) VillageManager.get(level).setDirty();
                } catch (ReflectiveOperationException | ClassCastException ignored) {
                    return;
                }
            }
        }
    }

    private static synchronized @Nullable Field reputationField() {
        if (reputationFieldResolved) return reputationField;
        reputationFieldResolved = true;
        try {
            Field field = Village.class.getDeclaredField("reputation");
            field.setAccessible(true);
            reputationField = field;
        } catch (ReflectiveOperationException | RuntimeException error) {
            Townstead.LOGGER.warn("MCA village reputation is not readable; standing will not count hearts: {}", error.toString());
        }
        return reputationField;
    }
}
