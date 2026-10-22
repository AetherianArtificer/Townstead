package com.aetherianartificer.townstead.temperature;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ThermalRegionScanTest {
    private ThermalRegionScan.Access line(Map<Long,ThermalRegionScan.Kind> cells) {
        return new ThermalRegionScan.Access() {
            public ThermalRegionScan.Kind kind(long p) { return cells.getOrDefault(p,ThermalRegionScan.Kind.BARRIER); }
            public long[] neighbors(long p) { return new long[]{p-1,p+1}; }
        };
    }
    @Test void allQueriesInConnectedRoomFindTheSameVolume() {
        var access=line(Map.of(1L,ThermalRegionScan.Kind.INTERIOR,2L,ThermalRegionScan.Kind.INTERIOR));
        var a=ThermalRegionScan.scan(1,100,access).orElseThrow();
        var b=ThermalRegionScan.scan(2,100,access).orElseThrow();
        assertEquals(a.cells(),b.cells()); assertEquals(2,a.cells().size()); assertEquals(2,a.faces().size());
    }
    @Test void doorsRemainBoundaryConnectionsRatherThanMergingRegions() {
        var access=line(Map.of(1L,ThermalRegionScan.Kind.INTERIOR,3L,ThermalRegionScan.Kind.INTERIOR));
        assertEquals(Set.of(1L),ThermalRegionScan.scan(1,100,access).orElseThrow().cells());
        assertEquals(Set.of(3L),ThermalRegionScan.scan(3,100,access).orElseThrow().cells());
    }
    @Test void outsideIsAReservoirAndIsNeverFloodFilled() {
        var access=line(Map.of(1L,ThermalRegionScan.Kind.INTERIOR,2L,ThermalRegionScan.Kind.EXTERIOR));
        assertEquals(Set.of(1L),ThermalRegionScan.scan(1,100,access).orElseThrow().cells());
        assertTrue(ThermalRegionScan.scan(2,100,access).isEmpty());
    }
    @Test void unloadedBoundaryRejectsAnIncompleteRoom() {
        var access=line(Map.of(1L,ThermalRegionScan.Kind.INTERIOR,2L,ThermalRegionScan.Kind.UNLOADED));
        assertTrue(ThermalRegionScan.scan(1,100,access).isEmpty());
    }
    @Test void oversizedSpaceStopsAtBudget() {
        final int[] visited={0};
        var access=new ThermalRegionScan.Access() {
            public ThermalRegionScan.Kind kind(long p) { visited[0]++; return ThermalRegionScan.Kind.INTERIOR; }
            public long[] neighbors(long p) { return new long[]{p-1,p+1}; }
        };
        assertTrue(ThermalRegionScan.scan(0,32,access).isEmpty());
        assertTrue(visited[0]<100);
    }
    @Test void removedWallMergesTheAirVolumesOnRescan() {
        var cells=new HashMap<Long,ThermalRegionScan.Kind>();
        cells.put(1L,ThermalRegionScan.Kind.INTERIOR);cells.put(3L,ThermalRegionScan.Kind.INTERIOR);
        assertEquals(1,ThermalRegionScan.scan(1,100,line(cells)).orElseThrow().cells().size());
        cells.put(2L,ThermalRegionScan.Kind.INTERIOR);
        assertEquals(3,ThermalRegionScan.scan(1,100,line(cells)).orElseThrow().cells().size());
    }
    @Test void aSpaceTooLargeToEncloseBecomesAPartialRoomOpenAtItsEdge() {
        var access=new ThermalRegionScan.Access() {
            public ThermalRegionScan.Kind kind(long p) { return ThermalRegionScan.Kind.INTERIOR; }
            public long[] neighbors(long p) { return new long[]{p-1,p+1}; }
        };
        var partial=ThermalRegionScan.scanPartial(0,32,access,p -> false).orElseThrow();
        assertEquals(32,partial.region().cells().size());
        assertEquals(2,partial.frontier().size(),"the line runs on at both ends");
        assertTrue(partial.region().cells().contains(0L));
    }
    @Test void aPartialRoomStopsAtRoomsAlreadyThere() {
        var access=new ThermalRegionScan.Access() {
            public ThermalRegionScan.Kind kind(long p) { return ThermalRegionScan.Kind.INTERIOR; }
            public long[] neighbors(long p) { return new long[]{p-1,p+1}; }
        };
        var partial=ThermalRegionScan.scanPartial(0,32,access,p -> p>=5).orElseThrow();
        assertFalse(partial.region().cells().contains(5L));
        assertFalse(partial.frontier().contains(5L),"an owned cell joins its room, not the outside air");
        assertTrue(ThermalRegionScan.scanPartial(6,32,access,p -> p>=5).isEmpty());
    }
}
