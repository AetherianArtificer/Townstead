package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.HangoutsApi;
import com.aetherianartificer.townstead.api.v1.model.HangoutVisitSnapshot;
import com.aetherianartificer.townstead.hangout.HangoutData;
import com.aetherianartificer.townstead.hangout.HangoutEngine;
import com.aetherianartificer.townstead.hangout.HangoutVisit;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class HangoutsImpl implements HangoutsApi {

    @Override
    public Optional<HangoutVisitSnapshot> visit(Entity villager) {
        try {
            if (villager == null) return Optional.empty();
            HangoutVisit visit = HangoutEngine.visit(villager.getUUID());
            return visit == null ? Optional.empty() : Optional.of(snapshot(visit));
        } catch (Throwable t) {
            ApiSupport.swallow("hangouts.visit", t);
            return Optional.empty();
        }
    }

    @Override
    public List<ResourceLocation> venueIds() {
        return new ArrayList<>(HangoutData.venues().keySet());
    }

    @Override
    public List<ResourceLocation> spotIds() {
        return new ArrayList<>(HangoutData.spots().keySet());
    }

    @Override
    public List<ResourceLocation> activityIds() {
        return new ArrayList<>(HangoutData.activities().keySet());
    }

    public static HangoutVisitSnapshot snapshot(HangoutVisit visit) {
        HangoutVisit.Visitor visitor = visit.visitor();
        return new HangoutVisitSnapshot(visit.id(), visitor.entity(), visit.dimension(), visit.venueDefinition(),
                visit.buildingId(), visit.policy(), visit.venueAnchor(), visitor.spot(), visitor.posture(),
                visit.phase().name().toLowerCase(Locale.ROOT), visit.createdAt(), visit.presentAt(), visit.deadline(),
                visit.exitReason());
    }
}
