package com.aetherianartificer.townstead.hospitality.service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** In-memory lease coordinator preventing Townstead workers from duplicating the same request. */
public final class ServiceClaimLedger {
    private final Map<ServiceRequestKey, ServiceClaim> claims = new HashMap<>();
    private final Map<ServiceRequestKey, ServiceLaborClaim> laborClaims = new HashMap<>();

    public synchronized ServiceClaim tryClaim(ServiceRequest request, UUID worker, long now,
                                              long leaseTicks) {
        if (leaseTicks < 1) throw new IllegalArgumentException("leaseTicks must be positive");
        if (request.expired(now)) return null;
        ServiceClaim current = claims.get(request.key());
        if (current != null && !current.expired(now) && !current.worker().equals(worker)) return null;
        ServiceClaim claim = new ServiceClaim(request, worker, now + leaseTicks);
        claims.put(request.key(), claim);
        return claim;
    }

    public synchronized boolean owns(ServiceRequestKey request, UUID worker, long now) {
        ServiceClaim claim = claims.get(request);
        if (claim == null) return false;
        if (claim.expired(now)) {
            claims.remove(request);
            return false;
        }
        return claim.worker().equals(worker);
    }

    public synchronized void release(ServiceRequestKey request, UUID worker) {
        ServiceClaim claim = claims.get(request);
        if (claim != null && claim.worker().equals(worker)) claims.remove(request);
    }

    public synchronized void prune(long now) {
        claims.entrySet().removeIf(entry -> entry.getValue().expired(now));
        laborClaims.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }

    /**
     * Claims a semantic course/order before product selection. Expiry deliberately uses an
     * exclusive upper bound so another worker can recover the request exactly at the lease tick.
     */
    public synchronized ServiceLaborClaim tryClaim(ServiceRequestKey request, String role, UUID worker,
                                                    long now, long leaseTicks) {
        if (leaseTicks < 1) throw new IllegalArgumentException("leaseTicks must be positive");
        ServiceLaborClaim current = laborClaims.get(request);
        if (current != null && !current.expired(now) && !current.worker().equals(worker)) return null;
        ServiceLaborClaim claim = new ServiceLaborClaim(request, role, worker, now + leaseTicks);
        laborClaims.put(request, claim);
        return claim;
    }

    public synchronized boolean ownsLabor(ServiceRequestKey request, UUID worker, long now) {
        ServiceLaborClaim claim = laborClaims.get(request);
        if (claim == null) return false;
        if (claim.expired(now)) { laborClaims.remove(request); return false; }
        return claim.worker().equals(worker);
    }

    public synchronized void releaseLabor(ServiceRequestKey request, UUID worker) {
        ServiceLaborClaim claim = laborClaims.get(request);
        if (claim != null && claim.worker().equals(worker)) laborClaims.remove(request);
    }

    public synchronized int size() { return claims.size() + laborClaims.size(); }
}
