package com.aetherianartificer.townstead.hangout;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HostedServiceCoordinatorTest {
    @Test
    void coursesAreOrderedAndExposeTerminalReasons() {
        HostedServiceCoordinator coordinator = new HostedServiceCoordinator();
        UUID session = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID server = UUID.randomUUID();
        AtomicInteger commits = new AtomicInteger();
        HangoutActivity.ServiceCourse drinks = course("drinks", HangoutActivity.Kind.DRINK, "bartender", 20);
        HangoutActivity.ServiceCourse supper = course("supper", HangoutActivity.Kind.EAT, "server", 80);

        assertEquals(HostedServiceCoordinator.Status.NOT_DUE,
                coordinator.attempt(id("minecraft:overworld"), session, "tavern", guest, server,
                        drinks, 0, 19, 100, true, true, () -> true).status());
        assertEquals(HostedServiceCoordinator.Status.OUT_OF_ORDER,
                coordinator.attempt(id("minecraft:overworld"), session, "tavern", guest, server,
                        supper, 1, 100, 101, true, true, () -> true).status());
        assertEquals(HostedServiceCoordinator.Status.ACCEPTED,
                coordinator.attempt(id("minecraft:overworld"), session, "tavern", guest, server,
                        drinks, 0, 20, 102, true, true, () -> { commits.incrementAndGet(); return true; }).status());
        HostedServiceCoordinator.Result missing = coordinator.attempt(id("minecraft:overworld"), session,
                "tavern", guest, server, supper, 1, 80, 103, true, false, () -> true);
        assertEquals(HostedServiceCoordinator.Status.MISSING_AMENITY, missing.status());
        assertEquals("missing_amenity:eat", missing.reason());
        assertEquals(1, commits.get());
    }

    @Test
    void missingNamedServerIsDistinctFromGuestRefusal() {
        HostedServiceCoordinator coordinator = new HostedServiceCoordinator();
        HangoutActivity.ServiceCourse course = course("round", HangoutActivity.Kind.DRINK, "bartender", 0);
        HostedServiceCoordinator.Result missing = coordinator.attempt(id("minecraft:overworld"), UUID.randomUUID(),
                "tavern", UUID.randomUUID(), null, course, 0, 0, 1, true, true, () -> true);
        assertEquals(HostedServiceCoordinator.Status.MISSING_SERVER, missing.status());
        assertEquals("missing_role:bartender", missing.reason());
    }

    private static HangoutActivity.ServiceCourse course(String id, HangoutActivity.Kind kind,
                                                         String role, int at) {
        return new HangoutActivity.ServiceCourse(id, kind, role, at, 20);
    }

    private static ResourceLocation id(String raw) { return ResourceLocation.tryParse(raw); }
}
