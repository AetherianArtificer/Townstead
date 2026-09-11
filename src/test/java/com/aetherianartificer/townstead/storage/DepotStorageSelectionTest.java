package com.aetherianartificer.townstead.storage;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class DepotStorageSelectionTest {
    @Test
    void explicitCellsExcludeNearbyAndFallbackShelvesBeforeInspectingTheirItems() {
        BlockPos depot = new BlockPos(0, 64, 0);
        BlockPos neighbour = new BlockPos(1, 64, 0);
        BlockPos fallback = new BlockPos(20, 64, 0);
        var snapshot = new WorksiteStorageIndex.Snapshot(
                List.of(entry(depot), entry(neighbour), entry(fallback)), Map.of(), 100);
        AtomicInteger inspected = new AtomicInteger();
        assertNull(snapshot.findBestSlotAt(null, stack -> {
            inspected.incrementAndGet();
            return false;
        }, StorageUse.INGREDIENT, Set.of(depot.asLong())));
        assertEquals(1, inspected.get());
        assertNull(snapshot.findBestSlotAt(null, stack -> {
            fail("An empty depot restriction must not fall back to other storage");
            return false;
        }, StorageUse.INGREDIENT, Set.of()));
        assertEquals(3, snapshot.entries().size(), "Restricted selection must not mutate the shared snapshot");
    }

    private static WorksiteStorageIndex.Entry entry(BlockPos pos) {
        // No item or villager needed: the matcher declines each permitted shelf before scoring.
        return new WorksiteStorageIndex.Entry(pos,
                List.of(new WorksiteStorageIndex.SlotView(pos, null, false, 0, null, null)),
                StoragePreference.LOCAL_RANK, Set.of(StorageRoleDef.Role.STORAGE));
    }
}
