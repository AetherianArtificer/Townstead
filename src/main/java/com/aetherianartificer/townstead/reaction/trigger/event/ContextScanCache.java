package com.aetherianartificer.townstead.reaction.trigger.event;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
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
    private boolean scanned;
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
        scan();
        return nearbyVillagers;
    }

    public List<Player> nearbyPlayers() {
        scan();
        return nearbyPlayers;
    }

    public List<Monster> nearbyHostileMobs() {
        scan();
        return nearbyHostileMobs;
    }

    public List<ZombieVillagerEntityMCA> nearbyZombieVillagers() {
        scan();
        return nearbyZombieVillagers;
    }

    private void scan() {
        if (scanned) return;
        scanned = true;
        nearbyVillagers = new ArrayList<>();
        nearbyPlayers = new ArrayList<>();
        nearbyHostileMobs = new ArrayList<>();
        nearbyZombieVillagers = new ArrayList<>();

        AABB box = new AABB(center).inflate(SOCIAL_RADIUS);
        for (Entity entity : level.getEntities(self, box, entity -> true)) {
            if (entity instanceof VillagerEntityMCA villager) {
                nearbyVillagers.add(villager);
            }
            if (entity instanceof Player player) {
                nearbyPlayers.add(player);
            }
            if (entity instanceof Monster monster) {
                nearbyHostileMobs.add(monster);
            }
            if (entity instanceof ZombieVillagerEntityMCA zombieVillager) {
                nearbyZombieVillagers.add(zombieVillager);
            }
        }
    }

    public BlockPos center() {
        return center;
    }

    public ServerLevel level() {
        return level;
    }
}
