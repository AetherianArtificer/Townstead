package com.aetherianartificer.townstead.api.v1;

import com.aetherianartificer.townstead.api.v1.model.HangoutVisitSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;

/** Social hangouts: where a villager is spending its free time, and what venues exist. */
public interface HangoutsApi {

    /** The villager's current visit, from setting out until it leaves. Empty otherwise. */
    Optional<HangoutVisitSnapshot> visit(Entity villager);

    List<ResourceLocation> venueIds();

    List<ResourceLocation> spotIds();

    List<ResourceLocation> activityIds();
}
