package com.aetherianartificer.townstead.compat.vampirism;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

/**
 * Garlic works on vampire villagers as it does on Vampirism's own vampires: in a chunk a garlic
 * diffuser covers, they take Vampirism's garlic effect (and blindness where it is strong) and
 * walk out of it. Reflection only.
 */
public final class VampireGarlic {
    private static final ResourceLocation GARLIC = ResourceLocation.tryParse("vampirism:garlic");
    private static final int INTERVAL = 20;
    private static final int EFFECT_TICKS = 100;
    private static volatile boolean resolved;
    private static volatile Method strengthAt;
    private static volatile Method strengthValue;

    private VampireGarlic() {}

    public static void tick(VillagerEntityMCA villager) {
        if ((villager.tickCount + villager.getId()) % INTERVAL != 0 || !VampireVillagers.isVampire(villager)) return;
        int strength = strength(villager);
        if (strength <= 0) return;
        //? if neoforge {
        BuiltInRegistries.MOB_EFFECT.getHolder(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.MOB_EFFECT, GARLIC))
                .ifPresent(effect -> villager.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, strength - 1, true, true)));
        //?} else {
        /*var effect = BuiltInRegistries.MOB_EFFECT.get(GARLIC);
        if (effect != null) villager.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, strength - 1, true, true));
        *///?}
        if (strength >= 3) villager.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EFFECT_TICKS / 2, 0, true, false));
        leave(villager);
    }

    /** Walks out of the garlic chunk, away from its center. */
    private static void leave(VillagerEntityMCA villager) {
        ChunkPos chunk = villager.chunkPosition();
        Vec3 center = new Vec3(chunk.getMiddleBlockX(), villager.getY(), chunk.getMiddleBlockZ());
        Vec3 away = villager.position().subtract(center);
        if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
        Vec3 to = villager.position().add(away.normalize().scale(16));
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(BlockPos.containing(to), 0.7f, 1));
    }

    private static int strength(Entity entity) {
        if (!resolve()) return 0;
        try {
            Object value = strengthAt.invoke(null, entity, entity.level());
            return value == null ? 0 : (int) strengthValue.invoke(value);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static boolean resolve() {
        if (resolved) return strengthAt != null;
        try {
            Class<?> helper = Class.forName("de.teamlapen.vampirism.util.Helper");
            strengthAt = helper.getMethod("getGarlicStrength", Entity.class, LevelAccessor.class);
            strengthValue = Class.forName("de.teamlapen.vampirism.api.EnumStrength").getMethod("getStrength");
        } catch (Throwable ignored) {
            strengthAt = null;
        }
        resolved = true;
        return strengthAt != null;
    }
}
