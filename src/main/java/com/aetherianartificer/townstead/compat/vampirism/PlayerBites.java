package com.aetherianartificer.townstead.compat.vampirism;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * A Vampirism player's bite on an MCA villager, as the village sees it. An awake villager who is
 * not the biter's spouse or thrall reacts as MCA's villagers do to a blow from that player (a
 * line, lost hearts, the guards' warning), through a harmless bite that names the player. A
 * bite that drains the last blood is charged to the player, so a death by draining counts as a
 * killing for MCA's witnesses and family.
 */
public final class PlayerBites {
    private static final ResourceKey<DamageType> BITE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.tryParse("townstead:bite"));
    private static final ResourceKey<DamageType> BLOOD_LOSS = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.tryParse("vampirism:blood_loss"));
    private static final ThreadLocal<Player> BITER = new ThreadLocal<>();
    private static volatile Field playerField;

    private PlayerBites() {}

    /** Called as a Vampirism player starts one bite; {@code vampire} is its VampirePlayer. */
    public static void begin(Object vampire) {
        BITER.set(playerOf(vampire));
    }

    public static void end(LivingEntity victim) {
        Player player = BITER.get();
        BITER.remove();
        if (player != null && victim instanceof VillagerEntityMCA villager) react(villager, player);
    }

    /** The player whose bite is running, or null outside one. */
    public static @Nullable Player biter() {
        return BITER.get();
    }

    /** The draining blow, charged to the biter. False when the damage type is missing. */
    public static boolean drainKill(VillagerEntityMCA villager, Player player, float amount) {
        DamageSource source = source(villager, BLOOD_LOSS, player);
        return source != null && villager.hurt(source, amount);
    }

    private static void react(VillagerEntityMCA villager, Player player) {
        if (!villager.isAlive() || villager.isSleeping() || VampireVillagers.isVampire(villager)) return;
        if (villager.getRelationships().isMarriedTo(player.getUUID()) || Thralls.serves(player, villager)) return;
        Object pool = VampirismBlood.creature(villager);
        int max = pool == null ? 0 : VampirismBlood.maxBlood(pool);
        float drained = max > 0 ? 1f - VampirismBlood.blood(pool) / (float) max : 0f;
        DamageSource source = source(villager, BITE, player);
        if (source != null) villager.hurt(source, 0f);
        villager.getVillagerBrain().getMemoriesForPlayer(player).modHearts(-(3 + Math.round(5 * drained)));
    }

    private static @Nullable DamageSource source(Entity victim, ResourceKey<DamageType> key, Entity attacker) {
        return victim.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(key)
                .map(type -> new DamageSource(type, attacker)).orElse(null);
    }

    private static @Nullable Player playerOf(Object vampire) {
        try {
            Field field = playerField;
            if (field == null) {
                for (Class<?> type = vampire.getClass(); type != null && field == null; type = type.getSuperclass()) {
                    try {
                        field = type.getDeclaredField("player");
                    } catch (NoSuchFieldException ignored) {
                        // Declared higher up.
                    }
                }
                if (field == null) return null;
                field.setAccessible(true);
                playerField = field;
            }
            return field.get(vampire) instanceof Player player ? player : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
