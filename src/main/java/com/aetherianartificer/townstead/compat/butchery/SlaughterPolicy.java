package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.TownsteadConfig;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * Central gatekeeper for autonomous villager slaughter. Filters candidate
 * targets against a hardcoded whitelist of common farm animals, excludes
 * babies, named mobs, and tamed pets, and enforces a per-villager throttle.
 *
 * <p>Humanoid slaughter stays firmly off for now. The design doc config key
 * {@code butchery.allowHumanoidSlaughter} will plug in here once the proper
 * {@link com.aetherianartificer.townstead.TownsteadConfig} integration lands;
 * the default (false) is authoritative until then.
 */
public final class SlaughterPolicy {
    /** Safe defaults used when Butchery is absent (config fields are null in that case). */
    private static final boolean DEFAULT_ENABLED = true;
    private static final boolean DEFAULT_ALLOW_HUMANOID = false;
    private static final int DEFAULT_THROTTLE_TICKS = 2400;

    /** Whitelisted farm animal species → Butchery carcass block path. */
    private static final Map<EntityType<?>, String> SPECIES_TO_CARCASS = Map.of(
            EntityType.COW, "cow_carcass",
            EntityType.PIG, "pig_carcass",
            EntityType.SHEEP, "sheep_carcass",
            EntityType.CHICKEN, "chicken_carcass",
            EntityType.RABBIT, "rabbit_carcass"
    );

    /** Never-kill list, independent of humanoid config. */
    private static final Set<EntityType<?>> NEVER_KILL = Set.of(
            EntityType.VILLAGER,
            EntityType.IRON_GOLEM,
            EntityType.SNOW_GOLEM,
            EntityType.CAT,
            EntityType.WOLF,
            EntityType.PARROT,
            EntityType.ALLAY,
            EntityType.HORSE,
            EntityType.DONKEY,
            EntityType.MULE
    );

    private SlaughterPolicy() {}

    public static boolean slaughterEnabled() {
        if (TownsteadConfig.ENABLE_VILLAGER_SLAUGHTER == null) return DEFAULT_ENABLED;
        return TownsteadConfig.ENABLE_VILLAGER_SLAUGHTER.get();
    }

    public static boolean allowHumanoid() {
        if (TownsteadConfig.ALLOW_HUMANOID_SLAUGHTER == null) return DEFAULT_ALLOW_HUMANOID;
        return TownsteadConfig.ALLOW_HUMANOID_SLAUGHTER.get();
    }

    public static int throttleTicks() {
        if (TownsteadConfig.VILLAGER_SLAUGHTER_THROTTLE_TICKS == null) return DEFAULT_THROTTLE_TICKS;
        return TownsteadConfig.VILLAGER_SLAUGHTER_THROTTLE_TICKS.get();
    }

    /** True if the villager may slaughter this particular entity right now. */
    public static boolean canSlaughter(VillagerEntityMCA butcher, LivingEntity target) {
        if (!slaughterEnabled()) return false;
        if (target == null || !target.isAlive()) return false;
        if (!(target instanceof Animal animal)) return false;
        if (animal.isBaby()) return false;
        if (animal.hasCustomName()) return false;
        if (NEVER_KILL.contains(animal.getType())) return false;
        if (!SPECIES_TO_CARCASS.containsKey(animal.getType())) return false;
        // Tamed / ownable animals (wolf, cat) already excluded by NEVER_KILL.
        if (butcher == null || butcher.getUUID().equals(animal.getUUID())) return false;
        return true;
    }

    /** Returns the carcass block ResourceLocation for this mob, or null if not supported. */
    @Nullable
    public static ResourceLocation carcassIdFor(EntityType<?> type) {
        String path = SPECIES_TO_CARCASS.get(type);
        if (path == null) return null;
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("butchery", path);
        //?} else {
        /*return new ResourceLocation("butchery", path);
        *///?}
    }
}
