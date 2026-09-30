package com.aetherianartificer.townstead.downed;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.naming.VillagerNames;
import com.aetherianartificer.townstead.performance.CollapseMotion;
import com.aetherianartificer.townstead.performance.CollapsePlayback;
import com.aetherianartificer.townstead.persona.PersonaInstances;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import com.aetherianartificer.townstead.switchboard.Systems;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Downed instead of dead. A covered villager who would die drops to 1 health and lies down; they
 * cannot be hurt or targeted, and they get up after a while, or sooner when a player helps them
 * up. A downed villager somewhere nobody can reach (the void, lava) is carried back to their home
 * village. Personas are covered unless permadeath is on; other villagers through
 * {@link DownedRules}. The time left is kept on the entity, so it survives a restart.
 */
public final class DownedService {
    private DownedService() {}

    private static final String KEY = "townstead_downed_until";
    private static final long HELP_UP_TICKS = 60L;
    private static final long MINUTES_TICKS = 6000L;
    private static final long DAY_TICKS = 24000L;
    private static final Map<VillagerEntityMCA, Boolean> DOWN = Collections.synchronizedMap(new WeakHashMap<>());

    /** Whether this entity is downed instead of dying. */
    public static boolean covers(LivingEntity entity) {
        if (!(entity instanceof VillagerEntityMCA villager) || entity.level().isClientSide) return false;
        MinecraftServer server = entity.getServer();
        PersonaInstances.Instance instance = server == null ? null : PersonaInstances.get(server).of(villager.getUUID());
        if (instance != null) {
            var persona = com.aetherianartificer.townstead.persona.Personas.byId(instance.persona());
            return Systems.on(Systems.PERSONAS) && !Switchboard.get(TownsteadConfig.PERSONA_PERMADEATH)
                    && (persona == null || persona.downed());
        }
        return DownedRules.covers(entity);
    }

    public static boolean isDowned(Entity entity) {
        return entity instanceof VillagerEntityMCA villager && DOWN.containsKey(villager);
    }

    /** Death hook. Returns true when the death was turned into being downed. */
    public static boolean onDeath(LivingEntity entity, DamageSource source) {
        if (com.aetherianartificer.townstead.pet.PetDowned.onDeath(entity, source)) return true;
        if (!covers(entity) || !(entity instanceof VillagerEntityMCA villager)) return false;
        // /kill stays a way for an operator to remove anyone.
        if (source != null && source.is(DamageTypes.GENERIC_KILL)) return false;
        boolean unreachable = source != null && (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.IS_FIRE));
        villager.setHealth(1f);
        if (unreachable) rescue(villager);
        down(villager);
        return true;
    }

    /** Damage hook: a downed villager takes no damage except from the void, which rescues them. */
    public static boolean blocksDamage(LivingEntity entity, DamageSource source) {
        if (com.aetherianartificer.townstead.pet.PetDowned.blocksDamage(entity)) return true;
        if (!isDowned(entity)) return false;
        if (source != null && source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
            rescue((VillagerEntityMCA) entity);
        }
        return true;
    }

    /** Target hook: nothing targets a downed villager. */
    public static boolean blocksTarget(LivingEntity target) {
        return isDowned(target) || com.aetherianartificer.townstead.pet.PetDowned.isDowned(target);
    }

    /** Use hook: using a downed villager helps them up. Returns true when handled. */
    public static boolean helpUp(Player player, Entity target) {
        if (!(target instanceof VillagerEntityMCA villager) || !isDowned(villager)) return false;
        if (player.level().isClientSide) return true;
        long now = villager.level().getGameTime();
        long until = villager.getPersistentData().getLong(KEY);
        if (until > now + HELP_UP_TICKS) {
            villager.getPersistentData().putLong(KEY, now + HELP_UP_TICKS);
            player.displayClientMessage(Component.translatable("townstead.downed.help", name(villager)), true);
        }
        return true;
    }

    /** Join hook: a villager who was down when their chunk unloaded lies back down. */
    public static void onJoin(Entity entity) {
        com.aetherianartificer.townstead.pet.PetDowned.onJoin(entity);
        if (!(entity instanceof VillagerEntityMCA villager) || entity.level().isClientSide) return;
        if (villager.getPersistentData().getLong(KEY) > 0) {
            DOWN.put(villager, Boolean.TRUE);
            pose(villager);
        }
    }

    public static void tick(MinecraftServer server) {
        com.aetherianartificer.townstead.pet.PetDowned.tick(server);
        if (server.getTickCount() % 10 != 0) return;
        for (VillagerEntityMCA villager : new ArrayList<>(DOWN.keySet())) {
            if (villager.isRemoved() || !villager.isAlive()) {
                DOWN.remove(villager);
                continue;
            }
            long now = villager.level().getGameTime();
            long until = villager.getPersistentData().getLong(KEY);
            if (until <= now) {
                standUp(villager);
                continue;
            }
            if (villager.getY() < villager.level().getMinBuildHeight() || villager.isInLava()
                    || villager.isEyeInFluid(FluidTags.WATER) && !villager.canBreatheUnderwater()) {
                rescue(villager);
            }
            if (!CollapsePlayback.active(villager)) pose(villager);
            villager.getNavigation().stop();
            if (villager.getHealth() < 1f) villager.setHealth(1f);
        }
    }

    private static void down(VillagerEntityMCA villager) {
        long until = villager.level().getGameTime() + recoveryTicks(villager);
        villager.getPersistentData().putLong(KEY, until);
        DOWN.put(villager, Boolean.TRUE);
        villager.getNavigation().stop();
        villager.setTarget(null);
        pose(villager);
        announce(villager, "townstead.downed.fell");
    }

    private static void standUp(VillagerEntityMCA villager) {
        DOWN.remove(villager);
        villager.getPersistentData().remove(KEY);
        CollapsePlayback.stop(villager);
        villager.setHealth(Math.max(villager.getHealth(), villager.getMaxHealth() * 0.4f));
        announce(villager, "townstead.downed.recovered");
    }

    private static void pose(VillagerEntityMCA villager) {
        long left = villager.getPersistentData().getLong(KEY) - villager.level().getGameTime();
        CollapsePlayback.start(villager, CollapseMotion.CHANNEL, (int) Math.max(20, Math.min(left, Integer.MAX_VALUE)), 1000);
    }

    /** Moves a villager to their home village, else the nearest village, on solid ground. */
    private static void rescue(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        Optional<Village> village = villager.getResidency().getHomeVillage();
        if (village.isEmpty()) village = VillageManager.get(level).findNearestVillage(villager.blockPosition(), 4096);
        BlockPos target;
        if (village.isPresent()) {
            Vec3i center = village.get().getCenter();
            target = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(center.getX(), 0, center.getZ()));
        } else {
            target = level.getSharedSpawnPos();
        }
        villager.fallDistance = 0f;
        villager.clearFire();
        villager.teleportTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5);
    }

    private static long recoveryTicks(VillagerEntityMCA villager) {
        DownedRecovery recovery = Switchboard.get(TownsteadConfig.DOWNED_RECOVERY);
        return switch (recovery) {
            case MINUTES -> MINUTES_TICKS;
            case DAY -> DAY_TICKS;
            case DAWN -> {
                long toDawn = DAY_TICKS - Math.floorMod(villager.level().getDayTime(), DAY_TICKS);
                yield toDawn < DAY_TICKS / 2 ? toDawn + DAY_TICKS : toDawn;
            }
        };
    }

    private static void announce(VillagerEntityMCA villager, String key) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        Component line = Component.translatable(key, name(villager));
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(villager) <= 48 * 48) player.displayClientMessage(line, true);
        }
    }

    private static String name(VillagerEntityMCA villager) {
        return VillagerNames.display(villager).getString();
    }
}
