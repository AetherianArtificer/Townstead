package com.aetherianartificer.townstead.tick;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EmergencyBedPoiCleanupContractTest {
    @Test
    void emergencyBedReleaseChecksPoiAndTicketBeforeRelease() throws IOException {
        String source = source("src/main/java/com/aetherianartificer/townstead/fatigue/EmergencyBedReservation.java");
        int helper = source.indexOf("public static void releaseIfClaimed");
        int poiTypeCheck = source.indexOf("poiManager.getType(pos)", helper);
        int freeTicketCheck = source.indexOf("poiManager.getFreeTickets(pos) > 0", helper);
        int release = source.indexOf("poiManager.release(pos)", helper);

        assertTrue(helper >= 0, "Emergency-bed cleanup must use a dedicated guarded release helper");
        assertTrue(poiTypeCheck > helper && poiTypeCheck < release,
                "Cleanup must verify that the POI is still registered before releasing it");
        assertTrue(freeTicketCheck > poiTypeCheck && freeTicketCheck < release,
                "Cleanup must not release a ticket that MCA has already returned");
        assertTrue(source.substring(poiTypeCheck, release).contains("PoiTypes.HOME"),
                "Cleanup must only release a HOME POI");
    }

    @Test
    void borrowedBedNeverBecomesMcaHome() throws IOException {
        String source = source("src/main/java/com/aetherianartificer/townstead/fatigue/BorrowBedWhenFatiguedTask.java");

        assertTrue(source.contains("getPoiManager().take("),
                "Borrowing must reserve the bed against MCA and other villagers");
        assertTrue(source.contains("villager.startSleeping(borrowedBed)"),
                "Borrowing must use the normal entity sleep lifecycle");
        assertTrue(!source.contains("setMemory(MemoryModuleType.HOME"),
                "A temporary borrowed bed must never mutate MCA HOME");
        assertTrue(!source.contains("getResidency()."),
                "A temporary borrowed bed must never mutate MCA village residency");
        assertTrue(source.contains("NAVIGATING.add(villager)"),
                "A live borrowed-bed trip must identify itself to persisted-state cleanup");
        assertTrue(source.contains("NAVIGATING.remove(villager)"),
                "Every borrowed-bed stop must end live navigation ownership");
    }

    @Test
    void activeBorrowRemainsRequestedAfterReservationIsPersisted() throws IOException {
        String source = source("src/main/java/com/aetherianartificer/townstead/tick/FatigueVillagerTicker.java");

        assertTrue(source.contains("needs.usesDirectEmergencyBed()")
                        && source.contains("BorrowBedWhenFatiguedTask.isNavigating(self)"),
                "Persisting a direct reservation must not cancel its navigation on the next tick");
        assertTrue(source.contains("!needs.hasEmergencyBed() || activelyBorrowingBed"),
                "An active borrow must remain requested while it owns the reservation");
    }

    @Test
    void legacyCleanupDoesNotRestoreTownsteadHomeSnapshot() throws IOException {
        String source = source("src/main/java/com/aetherianartificer/townstead/tick/FatigueVillagerTicker.java");

        assertTrue(!source.contains("setMemory(MemoryModuleType.HOME"),
                "Townstead must leave canonical HOME reconstruction to MCA, including old-save cleanup");
        assertTrue(source.contains("self.getBrain().eraseMemory(MemoryModuleType.HOME)"),
                "Old Townstead temporary HOME memory still needs to be removed during migration");
    }

    @Test
    void directReservationModeSurvivesEntityReload() throws IOException {
        String source = source("src/main/java/com/aetherianartificer/townstead/villager/TownsteadVillager.java");

        assertTrue(source.contains("tag.putBoolean(\"directEmergencyBed\", true)"),
                "Direct borrowed-bed ownership must be persisted");
        assertTrue(source.contains("directEmergencyBed = tag.getBoolean(\"directEmergencyBed\")"),
                "Direct borrowed-bed ownership must be restored after reload");
    }

    private static String source(String relativePath) throws IOException {
        Path relative = Path.of(relativePath);
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
             root != null; root = root.getParent()) {
            Path path = root.resolve(relative);
            if (Files.isRegularFile(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Unable to locate " + relativePath);
    }
}
