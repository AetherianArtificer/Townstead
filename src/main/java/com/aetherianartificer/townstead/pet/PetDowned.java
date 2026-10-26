package com.aetherianartificer.townstead.pet;

import com.aetherianartificer.townstead.chronicle.Chronicles;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Essential pets are downed instead of killed: at 1 health they lie still, take no damage and are
 * not targeted, and get up after a while, or sooner when their keeper comes to them. The keeper
 * gets a Chronicle count ({@link #DOWNED}) that stories read. The time left is kept on the animal.
 */
public final class PetDowned {
    /** Chronicle counter on the keeper each time their pet goes down. */
    public static final String DOWNED = "townstead:pet_downed";
    private static final String KEY = "townstead:pet_downed_until";
    private static final long RECOVERY = 6000L;
    private static final long KEEPER_NEAR_RECOVERY = 200L;
    private static final Map<Mob, Boolean> DOWN = Collections.synchronizedMap(new WeakHashMap<>());

    private PetDowned() {}

    public static boolean isDowned(Entity entity) {
        return entity instanceof Mob mob && DOWN.containsKey(mob);
    }

    /** Death hook. Returns true when the death was turned into being downed. */
    public static boolean onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Mob pet) || entity.level().isClientSide || !VillagerPets.essential(pet)) return false;
        if (source != null && source.is(DamageTypes.GENERIC_KILL)) return false;
        pet.setHealth(1f);
        if (source != null && (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.IS_FIRE))) toKeeper(pet);
        pet.getPersistentData().putLong(KEY, pet.level().getGameTime() + RECOVERY);
        DOWN.put(pet, Boolean.TRUE);
        pet.getNavigation().stop();
        pet.setTarget(null);
        sit(pet, true);
        UUID keeper = VillagerPets.keeperOf(pet);
        if (keeper != null && pet.level() instanceof ServerLevel level) Chronicles.addCounter(level.getServer(), keeper, DOWNED, 1);
        return true;
    }

    /** Damage hook: a downed pet takes no damage. */
    public static boolean blocksDamage(LivingEntity entity) {
        return isDowned(entity);
    }

    /** Join hook: a pet that was down when it unloaded lies back down. */
    public static void onJoin(Entity entity) {
        if (entity instanceof Mob pet && !entity.level().isClientSide && pet.getPersistentData().getLong(KEY) > 0) {
            DOWN.put(pet, Boolean.TRUE);
            sit(pet, true);
        }
    }

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0 || DOWN.isEmpty()) return;
        for (Mob pet : new ArrayList<>(DOWN.keySet())) {
            if (pet.isRemoved() || !pet.isAlive()) {
                DOWN.remove(pet);
                continue;
            }
            long now = pet.level().getGameTime();
            long until = pet.getPersistentData().getLong(KEY);
            // The keeper sitting with it brings it round sooner.
            LivingEntity keeper = VillagerPets.keeper(pet);
            if (keeper != null && keeper.distanceToSqr(pet) < 9 && until > now + KEEPER_NEAR_RECOVERY) {
                until = now + KEEPER_NEAR_RECOVERY;
                pet.getPersistentData().putLong(KEY, until);
            }
            if (until <= now) {
                DOWN.remove(pet);
                pet.getPersistentData().remove(KEY);
                pet.setHealth(Math.max(pet.getHealth(), pet.getMaxHealth() * 0.4f));
                sit(pet, false);
                continue;
            }
            pet.getNavigation().stop();
            if (pet.getHealth() < 1f) pet.setHealth(1f);
        }
    }

    private static void sit(Mob pet, boolean down) {
        if (pet instanceof TamableAnimal tamable) tamable.setInSittingPose(down);
    }

    /** A pet somewhere nobody can reach is carried to its keeper. */
    private static void toKeeper(Mob pet) {
        LivingEntity keeper = VillagerPets.keeper(pet);
        pet.fallDistance = 0f;
        pet.clearFire();
        if (keeper != null) pet.teleportTo(keeper.getX(), keeper.getY(), keeper.getZ());
    }
}
