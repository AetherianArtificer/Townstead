package com.aetherianartificer.townstead.hospitality.service;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceClaimLedgerTest {
    @Test
    void leaseIsExclusiveRenewableAndRecoverable() {
        ServiceClaimLedger ledger = new ServiceClaimLedger();
        ServiceRequest request = request(200L);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertNotNull(ledger.tryClaim(request, first, 10L, 20L));
        assertNull(ledger.tryClaim(request, second, 11L, 20L));
        assertTrue(ledger.owns(request.key(), first, 20L));
        assertNotNull(ledger.tryClaim(request, first, 25L, 20L), "owner may renew its lease");
        assertNull(ledger.tryClaim(request, second, 31L, 20L), "renewal extended the lease");
        assertNotNull(ledger.tryClaim(request, second, 46L, 20L), "expired lease is recoverable");
        assertFalse(ledger.owns(request.key(), first, 46L));
        assertTrue(ledger.owns(request.key(), second, 46L));
    }

    @Test
    void expiredForeignRequestCannotBeClaimed() {
        ServiceClaimLedger ledger = new ServiceClaimLedger();
        assertNull(ledger.tryClaim(request(50L), UUID.randomUUID(), 51L, 20L));
        assertEquals(0, ledger.size());
    }

    @Test
    void semanticLaborClaimNamesRoleAndRecoversAtExpiry() {
        ServiceClaimLedger ledger = new ServiceClaimLedger();
        ServiceRequestKey key = request(200L).key();
        UUID bartender = UUID.randomUUID();
        UUID relief = UUID.randomUUID();

        ServiceLaborClaim first = ledger.tryClaim(key, "bartender", bartender, 10L, 20L);
        assertNotNull(first);
        assertEquals("bartender", first.role());
        assertNull(ledger.tryClaim(key, "bartender", relief, 29L, 20L));
        assertNotNull(ledger.tryClaim(key, "bartender", relief, 30L, 20L));
        assertFalse(ledger.ownsLabor(key, bartender, 30L));
        assertTrue(ledger.ownsLabor(key, relief, 30L));
    }

    private static ServiceRequest request(long deadline) {
        ResourceLocation provider = id("cozycafe:cafe");
        ServiceRequestKey key = new ServiceRequestKey(
                provider, id("minecraft:overworld"), "0,64,0", "table-1", "course-0");
        return new ServiceRequest(key, ServiceRequest.Authority.FOREIGN,
                ExactServiceProduct.descriptor(id("minecraft:apple"), id("minecraft:apple"), 1),
                id("cozycafe:main"),
                id("townstead:cook"), new BlockPos(0, 0, 0), deadline, 10, Map.of());
    }

    private static ResourceLocation id(String raw) { return ResourceLocation.tryParse(raw); }
}
