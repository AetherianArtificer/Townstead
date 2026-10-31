package com.aetherianartificer.townstead.pet;

import com.aetherianartificer.townstead.mixin.accessor.MobGoalsAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Animals kept by villagers. Vanilla only lets a player own a tame animal, so the keeper is stored on
 * the animal itself and two goals give it the behavior: it follows its keeper and defends them. A
 * tamable animal is also marked tame, so it wears its collar and leaves players be. Pets are rare:
 * a villager only gets one through a story or from a player, never on their own.
 *
 * <p>A pet can have a rest spot, where it waits while its keeper sleeps or is away, and can be
 * essential: downed instead of killed (see {@link PetDowned}).</p>
 */
public final class VillagerPets {
    private static final String KEEPER = "townstead:keeper";
    private static final String REST = "townstead:rest";
    private static final String REST_DIMENSION = "townstead:rest_dimension";
    static final String ESSENTIAL = "townstead:essential";

    private VillagerPets() {}

    /** Makes {@code animal} the pet of {@code keeper}. */
    public static void adopt(Mob animal, LivingEntity keeper) {
        adopt(animal, keeper, false);
    }

    /** Makes {@code animal} the pet of {@code keeper}; an essential pet is downed instead of killed. */
    public static void adopt(Mob animal, LivingEntity keeper, boolean essential) {
        animal.getPersistentData().putUUID(KEEPER, keeper.getUUID());
        if (essential) animal.getPersistentData().putBoolean(ESSENTIAL, true);
        animal.setPersistenceRequired();
        if (animal instanceof TamableAnimal tamable) {
            //? if >=1.21 {
            tamable.setTame(true, false);
            //?} else {
            /*tamable.setTame(true);
            *///?}
        }
        attach(animal);
    }

    /** Spawns a new animal of {@code type} beside {@code keeper} and adopts it. Null when it could not spawn. */
    public static @Nullable Mob adoptNew(ServerLevel level, EntityType<?> type, LivingEntity keeper) {
        return adoptNew(level, type, keeper, false);
    }

    public static @Nullable Mob adoptNew(ServerLevel level, EntityType<?> type, LivingEntity keeper, boolean essential) {
        Entity entity = type.create(level);
        if (!(entity instanceof Mob animal)) return null;
        BlockPos at = keeper.blockPosition();
        animal.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, keeper.getYRot(), 0);
        //? if >=1.21 {
        animal.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
        //?} else {
        /*animal.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null, null);
        *///?}
        adopt(animal, keeper, essential);
        return level.addFreshEntity(animal) ? animal : null;
    }

    public static @Nullable UUID keeperOf(Entity entity) {
        CompoundTag data = entity.getPersistentData();
        return data.hasUUID(KEEPER) ? data.getUUID(KEEPER) : null;
    }

    /** The keeper's pets that are loaded within {@code radius}. */
    public static List<Mob> petsOf(LivingEntity keeper, double radius) {
        UUID id = keeper.getUUID();
        return keeper.level().getEntitiesOfClass(Mob.class, keeper.getBoundingBox().inflate(radius),
                mob -> id.equals(keeperOf(mob)) && mob.isAlive());
    }

    public static boolean essential(Entity entity) {
        return entity.getPersistentData().getBoolean(ESSENTIAL) && keeperOf(entity) != null;
    }

    /** Where the pet waits while its keeper sleeps or is away. */
    public static void setRest(Mob animal, BlockPos pos) {
        animal.getPersistentData().putLong(REST, pos.asLong());
        animal.getPersistentData().putString(REST_DIMENSION, animal.level().dimension().location().toString());
    }

    /** The pet's rest spot in its current level, or null. */
    static @Nullable BlockPos rest(Mob animal) {
        CompoundTag data = animal.getPersistentData();
        if (!data.contains(REST)) return null;
        if (!animal.level().dimension().location().toString().equals(data.getString(REST_DIMENSION))) return null;
        return BlockPos.of(data.getLong(REST));
    }

    /** Join hook: gives a kept animal its goals each time it loads. */
    public static void onJoin(Entity entity) {
        if (entity instanceof Mob mob && keeperOf(mob) != null) attach(mob);
    }

    private static void attach(Mob animal) {
        if (!(animal instanceof PathfinderMob pathfinder) || attached(animal)) return;
        MobGoalsAccessor goals = (MobGoalsAccessor) animal;
        goals.townstead$goalSelector().addGoal(2, new RestAtSpotGoal(pathfinder));
        goals.townstead$goalSelector().addGoal(3, new FollowKeeperGoal(pathfinder));
        goals.townstead$targetSelector().addGoal(1, new DefendKeeperGoal(pathfinder));
    }

    /** Whether this instance already has the goals (they are not saved, so every load adds them again). */
    private static boolean attached(Mob animal) {
        return ((MobGoalsAccessor) animal).townstead$goalSelector().getAvailableGoals().stream()
                .anyMatch(wrapped -> wrapped.getGoal() instanceof FollowKeeperGoal);
    }

    /** The keeper, when loaded in the same level. */
    static @Nullable LivingEntity keeper(Mob animal) {
        UUID id = keeperOf(animal);
        if (id == null || !(animal.level() instanceof ServerLevel level)) return null;
        // A keeper travelling without their pets is away, as far as the pets are concerned.
        return level.getEntity(id) instanceof LivingEntity living && living.isAlive()
                && !com.aetherianartificer.townstead.journey.Companions.leavesPets(living) ? living : null;
    }
}
