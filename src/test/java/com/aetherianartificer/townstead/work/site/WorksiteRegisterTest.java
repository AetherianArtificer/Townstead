package com.aetherianartificer.townstead.work.site;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The register is where a place stops being re-derived and starts being remembered, so what matters
 * is that an id, once minted, means one place forever — across removals, across a binding being
 * repaired underneath it, and across a reload.
 *
 * <p>The NBT round trip itself is not exercised here: the test source set shadows
 * {@code CompoundTag} with a stub that does not implement the real {@code Tag}, so save and load
 * cannot be called. The one rule in that path that is easy to get wrong — where the id counter
 * resumes — is extracted into {@link WorksiteRegister#counterAfterLoad} and tested directly.</p>
 */
class WorksiteRegisterTest {

    private static final ResourceLocation OVERWORLD = id("minecraft:overworld");
    private static final ResourceLocation NETHER = id("minecraft:the_nether");
    private static final ResourceLocation ROOM = id("townstead:mca_room");
    private static final ResourceLocation ANCHOR = id("townstead:anchor");

    @Test
    void registeringTwiceIsTheSamePlace() {
        WorksiteRegister register = new WorksiteRegister();
        WorksiteKey kitchen = new WorksiteKey(ROOM, OVERWORLD, 47);

        Worksite first = register.register(kitchen, "The Kitchen", 3, 100L);
        Worksite again = register.register(kitchen, "ignored", 3, 250L);

        assertSame(first, again, "a key is a place; asking twice must not make two");
        assertEquals(1, register.size());
        assertEquals("The Kitchen", again.name(), "re-registering must not rename what the player named");
        assertEquals(250L, again.lastSeenGameTime(), "but it does count as having been seen");
    }

    @Test
    void differentBindingsAndDimensionsAreDifferentPlaces() {
        WorksiteRegister register = new WorksiteRegister();

        register.register(new WorksiteKey(ANCHOR, OVERWORLD, 900L), "Yard", 1, 0L);
        register.register(new WorksiteKey(ANCHOR, NETHER, 900L), "Nether Yard", 1, 0L);
        register.register(new WorksiteKey(ROOM, OVERWORLD, 900L), "Room", 1, 0L);

        assertEquals(3, register.size(), "the key is binding plus dimension plus value, all three");
    }

    @Test
    void idsAreNeverReused() {
        WorksiteRegister register = new WorksiteRegister();
        WorksiteKey first = new WorksiteKey(ANCHOR, OVERWORLD, 1);
        long retiredId = register.register(first, "Gone", 1, 0L).id();

        assertTrue(register.remove(first));
        long freshId = register.register(new WorksiteKey(ANCHOR, OVERWORLD, 2), "New", 1, 0L).id();

        assertNotEquals(retiredId, freshId,
                "reusing an id would silently hand one place's orders to another");
        assertTrue(freshId > retiredId);
    }

    @Test
    void rebindingKeepsTheRecord() {
        WorksiteRegister register = new WorksiteRegister();
        WorksiteKey before = new WorksiteKey(ROOM, OVERWORLD, 47);
        WorksiteKey after = new WorksiteKey(ROOM, OVERWORLD, 512);

        Worksite site = register.register(before, "The Smithy", 2, 0L);
        long id = site.id();

        assertTrue(register.rebind(before, after));
        assertNull(register.find(before), "the old key must stop resolving");

        Worksite moved = register.find(after);
        assertNotNull(moved);
        assertEquals(id, moved.id(), "MCA restructuring costs a binding, never an identity");
        assertEquals("The Smithy", moved.name());
        assertEquals(1, register.size());
    }

    @Test
    void rebindingRefusesToClobber() {
        WorksiteRegister register = new WorksiteRegister();
        WorksiteKey a = new WorksiteKey(ROOM, OVERWORLD, 1);
        WorksiteKey b = new WorksiteKey(ROOM, OVERWORLD, 2);
        register.register(a, "A", 1, 0L);
        register.register(b, "B", 1, 0L);

        assertFalse(register.rebind(a, b), "merging two places would lose one of them");
        assertEquals(2, register.size());
    }

    @Test
    void lookupByIdSurvivesRebinding() {
        WorksiteRegister register = new WorksiteRegister();
        WorksiteKey before = new WorksiteKey(ROOM, OVERWORLD, 3);
        long id = register.register(before, "The Bakery", 1, 0L).id();

        register.rebind(before, new WorksiteKey(ANCHOR, OVERWORLD, 44));

        Worksite found = register.byId(id);
        assertNotNull(found, "anything holding an id must still find its place after a re-bind");
        assertEquals("The Bakery", found.name());
    }

    @Test
    void villageIsRefreshedMetadataNotIdentity() {
        WorksiteRegister register = new WorksiteRegister();
        WorksiteKey kitchen = new WorksiteKey(ROOM, OVERWORLD, 47);
        long id = register.register(kitchen, "The Kitchen", 1, 0L).id();

        Worksite same = register.register(kitchen, "The Kitchen", 7, 10L);

        assertEquals(id, same.id(), "changing hands between villages is not becoming a new place");
        assertEquals(7, same.villageId());
    }

    @Test
    void theCounterResumesAboveEverythingAlreadyHandedOut() {
        assertEquals(6L, WorksiteRegister.counterAfterLoad(6L, 5L),
                "a healthy save resumes where it left off");
        assertEquals(9L, WorksiteRegister.counterAfterLoad(3L, 8L),
                "a counter that lagged the ids on disk must catch up, or it re-mints a live id");
        assertEquals(1L, WorksiteRegister.counterAfterLoad(0L, 0L),
                "an empty or pre-schema file still starts at one");
    }

    private static ResourceLocation id(String raw) {
        //? if >=1.21 {
        return ResourceLocation.parse(raw);
        //?} else {
        /*return new ResourceLocation(raw);
        *///?}
    }
}
