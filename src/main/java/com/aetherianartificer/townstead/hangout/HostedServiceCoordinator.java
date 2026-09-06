package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.hospitality.service.ServiceClaimLedger;
import com.aetherianartificer.townstead.hospitality.service.ServiceLaborClaim;
import com.aetherianartificer.townstead.hospitality.service.ServiceRequestKey;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Ordered, lease-backed course state for Townstead-owned social beats. This coordinator is called by
 * the hangout engine, not a second AI scheduler: cook/brew tasks and provider-owned fulfillment
 * remain in the existing hospitality pipeline.
 */
public final class HostedServiceCoordinator {
    public enum Status { CLAIMED, ACCEPTED, REFUSED, MISSING_AMENITY, MISSING_SERVER, NOT_DUE, OUT_OF_ORDER, COMPLETE }
    public record Result(Status status, String reason) {
        public boolean terminal() {
            return status == Status.ACCEPTED || status == Status.REFUSED
                    || status == Status.MISSING_AMENITY || status == Status.MISSING_SERVER;
        }
    }

    private static final ResourceLocation PROVIDER = ResourceLocation.tryParse("townstead_hangouts:hosted_service");
    private final ServiceClaimLedger claims = new ServiceClaimLedger();
    private final Map<Guest, Integer> completed = new HashMap<>();

    public Result attempt(ResourceLocation dimension, UUID beatId, String site, UUID guest, UUID worker,
                   HangoutActivity.ServiceCourse course, int index, long activeTicks, long now,
                   boolean activityServiceAllowed, boolean courseEligible,
                   boolean amenityPresent, BooleanSupplier fulfillment) {
        Guest key = new Guest(beatId, guest);
        int next = completed.getOrDefault(key, 0);
        if (index < next) return new Result(Status.COMPLETE, "course_already_completed");
        if (index > next) return new Result(Status.OUT_OF_ORDER, "previous_course_pending");
        if (activeTicks < course.atTicks()) return new Result(Status.NOT_DUE, "course_not_due");
        // Eligibility is guest truth, independent of whether today's venue happens to have staff
        // or stock. Resolve it before labor acquisition so an excluded course cannot reserve work
        // or be misdiagnosed as a missing bartender.
        if (!activityServiceAllowed) {
            return finish(key, index, Status.REFUSED, "activity_service_condition_refused");
        }
        if (!courseEligible) {
            return finish(key, index, Status.REFUSED, "course_eligibility_refused");
        }
        if (worker == null) return finish(key, index, Status.MISSING_SERVER, "missing_role:" + course.role());

        ServiceRequestKey request = new ServiceRequestKey(PROVIDER, dimension, site,
                beatId.toString(), guest + "/" + course.id());
        ServiceLaborClaim claim = claims.tryClaim(request, course.role(), worker, now, course.leaseTicks());
        if (claim == null) return new Result(Status.CLAIMED, "claimed_by_another_worker");
        try {
            if (!amenityPresent) return finish(key, index, Status.MISSING_AMENITY,
                    "missing_amenity:" + course.kind().name().toLowerCase(java.util.Locale.ROOT));
            if (fulfillment == null || !fulfillment.getAsBoolean()) {
                return finish(key, index, Status.REFUSED, "amenity_refused");
            }
            return finish(key, index, Status.ACCEPTED, "accepted");
        } finally {
            claims.releaseLabor(request, worker);
        }
    }

    public void forget(UUID beatId) {
        completed.keySet().removeIf(key -> key.beatId().equals(beatId));
    }

    public void prune(long now) { claims.prune(now); }

    private Result finish(Guest guest, int index, Status status, String reason) {
        completed.put(guest, index + 1);
        return new Result(status, reason);
    }

    private record Guest(UUID beatId, UUID guest) {}
}
