package com.aetherianartificer.townstead.work;

import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteKey;
import com.aetherianartificer.townstead.work.site.WorksiteRegister;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A view is where one villager is working right now; the {@link Worksite} it carries is the place
 * itself. Keeping both on the same object is what lets anything downstream — orders, shifts, a name
 * for a message — reach the record without resolving the whole thing again.
 */
class WorkSiteViewTest {

    @Test
    void aViewCarriesTheRecordItIsAViewOf() {
        WorksiteRegister register = new WorksiteRegister();
        Worksite kitchen = register.register(
                new WorksiteKey(id("townstead:mca_room"), id("minecraft:overworld"), 47),
                "The Kitchen", 1, 0L);

        WorkSiteView view = WorkSiteView.building(null, Set.of(1L, 2L), kitchen);

        assertNotNull(view.site());
        assertEquals("The Kitchen", view.site().name());
        assertEquals(WorkSiteView.Kind.BUILDING, view.kind());
    }

    @Test
    void aViewWithoutARecordStillWorks() {
        WorkSiteView view = WorkSiteView.zone(null, 8, 3, null);
        assertNull(view.site(),
                "an unregistered place must still be workable, or a broken register stops the village");
        assertEquals(WorkSiteView.Kind.ZONE, view.kind());
        assertEquals(8, view.horizontalRadius());
    }

    @Test
    void containmentIsUnchangedByCarryingARecord() {
        WorkSiteView building = WorkSiteView.building(null, Set.of(5L), null);
        assertTrue(building.ownedBounds().contains(5L));
        assertFalse(building.ownedBounds().contains(6L));
    }

    private static ResourceLocation id(String raw) {
        //? if >=1.21 {
        return ResourceLocation.parse(raw);
        //?} else {
        /*return new ResourceLocation(raw);
        *///?}
    }
}
