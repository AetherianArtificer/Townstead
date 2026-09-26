package com.aetherianartificer.townstead.politics.standing;

import com.aetherianartificer.townstead.api.v1.TownsteadApiV1;
import com.aetherianartificer.townstead.api.v1.event.BuildingEstablishedEvent;
import com.aetherianartificer.townstead.api.v1.event.BuildingUpgradedEvent;
import com.aetherianartificer.townstead.api.v1.event.VillageSpiritChangedEvent;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.compat.otectus.ReputationDeeds;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.minecraft.server.level.ServerPlayer;

/**
 * Credits Townstead's own deeds to the player who reported them: a building raised, a building
 * upgraded, a village spirit tier reached. A change nobody reported is nobody's deed.
 */
public final class StandingDeeds {
    static final int BUILDING_RAISED = 3;
    static final int BUILDING_UPGRADED = 2;
    static final int SPIRIT_TIER_REACHED = 5;

    private static boolean registered;

    private StandingDeeds() {}

    public static synchronized void init() {
        if (registered) return;
        registered = true;
        TownsteadApiV1.get().events().subscribe(BuildingEstablishedEvent.class, e ->
                credit(e.village(), "raised:" + e.buildingId() + ":" + e.type(), BUILDING_RAISED));
        TownsteadApiV1.get().events().subscribe(BuildingUpgradedEvent.class, e -> {
            if (e.tierAfter() > e.tierBefore()) {
                credit(e.village(), "upgraded:" + e.buildingId() + ":" + e.typeAfter(), BUILDING_UPGRADED);
            }
        });
        TownsteadApiV1.get().events().subscribe(VillageSpiritChangedEvent.class, e -> {
            if (e.after().tierIndex() > e.before().tierIndex()) {
                credit(e.village(), "spirit_tier:" + e.after().tierIndex(), SPIRIT_TIER_REACHED);
            }
        });
    }

    private static void credit(VillageId village, String key, int points) {
        ServerPlayer reporter = ReputationDeeds.reporter();
        if (reporter == null || reporter.getServer() == null) return;
        DeedLedger.get(reporter.getServer()).credit(new SettlementRef(village.dimension(), village.villageId()),
                reporter.getUUID(), key, points);
    }
}
