package com.aetherianartificer.townstead.reaction.trigger.event;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * One-shot scratchpad built at the start of a {@link ContextResolver} pass
 * to amortize entity scans across the tag families that need them. Each
 * AABB query into the world happens at most once per villager per stride
 * even if a dozen tags want "is X within radius?".
 *
 * <p>Built with the largest radius any consumer needs; downstream tags
 * filter the cached list by their own (smaller) radius.</p>
 */
public final class ContextScanCache {
    public static final double SOCIAL_RADIUS = 12.0;

    private final ServerLevel level;
    private final BlockPos center;
    private final LivingEntity self;
    private List<VillagerEntityMCA> nearbyVillagers;
    private List<Player> nearbyPlayers;
    private List<Monster> nearbyHostileMobs;
    private List<ZombieVillagerEntityMCA> nearbyZombieVillagers;

    public ContextScanCache(ServerLevel level, LivingEntity self) {
        this.level = level;
        this.self = self;
        this.center = self.blockPosition();
    }

    public List<VillagerEntityMCA> nearbyVillagers() {
        if (nearbyVillagers == null) {
            AABB box = new AABB(center).inflate(SOCIAL_RADIUS);
            nearbyVillagers = level.getEntitiesOfClass(VillagerEntityMCA.class, box, v -> v != self);
        }
        return nearbyVillagers;
    }

    public List<Player> nearbyPlayers() {
        if (nearbyPlayers == null) {
            AABB box = new AABB(center).inflate(SOCIAL_RADIUS);
            nearbyPlayers = level.getEntitiesOfClass(Player.class, box, p -> p != self);
        }
        return nearbyPlayers;
    }

    public List<Monster> nearbyHostileMobs() {
        if (nearbyHostileMobs == null) {
            AABB box = new AABB(center).inflate(SOCIAL_RADIUS);
            nearbyHostileMobs = level.getEntitiesOfClass(Monster.class, box);
        }
        return nearbyHostileMobs;
    }

    public List<ZombieVillagerEntityMCA> nearbyZombieVillagers() {
        if (nearbyZombieVillagers == null) {
            AABB box = new AABB(center).inflate(SOCIAL_RADIUS);
            nearbyZombieVillagers = level.getEntitiesOfClass(ZombieVillagerEntityMCA.class, box);
        }
        return nearbyZombieVillagers;
    }

    public BlockPos center() {
        return center;
    }

    public ServerLevel level() {
        return level;
    }
}
