package com.aetherianartificer.townstead.hunger;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the two old radius-based work loops from drifting away from shared storage again. */
class BespokeProfessionStorageRouteTest {
    @Test
    void farmerUsesSharedPurposeAwareStorage() {
        String source = source("HarvestWorkTask.java");
        assertTrue(source.contains("WorksiteStorageIndex.snapshot"));
        assertTrue(source.contains("PhysicalStorageDelivery.findDestination"));
        assertTrue(source.contains("StorageUse.INGREDIENT"));
        assertTrue(source.contains("StorageUse.TOOL"));
        assertTrue(source.contains("StorageUse.OUTPUT"));
        assertFalse(source.contains("NearbyItemSources.pullSingleToInventory"));
    }

    @Test
    void fishermanUsesSharedPurposeAwareStorage() {
        String supply = source("FishermanSupplyManager.java");
        assertTrue(supply.contains("WorksiteStorageIndex.snapshot"));
        assertTrue(supply.contains("PhysicalStorageDelivery.findDestination"));
        assertTrue(supply.contains("StorageUse.TOOL"));
        assertTrue(supply.contains("StorageUse.OUTPUT"));
        assertFalse(supply.contains("findBestNearbySlot"));

        String task = source("FishermanWorkTask.java");
        assertTrue(task.contains("findCatchDestination"));
        assertTrue(task.contains("depositCatchesAt"));
        assertFalse(task.contains("NearbyItemSources.insertIntoNearbyStorage"));
    }

    private static String source(String name) {
        Path relative = Path.of("src/main/java/com/aetherianartificer/townstead/hunger", name);
        Path path = relative;
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
             root != null;
             root = root.getParent()) {
            Path candidate = root.resolve(relative);
            if (Files.isRegularFile(candidate)) {
                path = candidate;
                break;
            }
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new AssertionError("Could not read " + path, error);
        }
    }
}
