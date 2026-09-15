package com.aetherianartificer.townstead.api.v1;

import com.aetherianartificer.townstead.api.v1.model.NeedsSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillagerRecord;
import com.aetherianartificer.townstead.api.v1.model.VillagerSnapshot;
import com.aetherianartificer.townstead.api.v1.result.NeedResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Reads and need mutations for one villager or player. */
public interface VillagersApi {

    /** Full state of a loaded Townstead-aware entity. Empty for anything that is not one. */
    Optional<VillagerSnapshot> snapshot(Entity entity);

    /** The same, by id, searching every loaded level. Empty when the villager is not loaded. */
    Optional<VillagerSnapshot> snapshot(MinecraftServer server, UUID id);

    /** Just the needs, cheaper than the full snapshot. */
    Optional<NeedsSnapshot> needs(Entity entity);

    /**
     * The last state Townstead saw for a villager, loaded or not, from the resident register.
     * Empty for a villager Townstead has never ticked. A loaded villager's record is refreshed
     * before it is returned.
     */
    Optional<VillagerRecord> record(MinecraftServer server, UUID id);

    /** Every need id this Townstead knows, for example {@code townstead:hunger}. */
    List<String> needIds();

    /**
     * Sets a need to an absolute value, clamped to its range. {@code townstead:energy} goes up
     * through the recovery path that clears a collapse and down by adding fatigue.
     */
    NeedResult setNeed(Entity entity, String needId, int value, ResourceLocation source);

    /** Moves a need by a delta, clamped to its range. */
    NeedResult adjustNeed(Entity entity, String needId, int delta, ResourceLocation source);
}
