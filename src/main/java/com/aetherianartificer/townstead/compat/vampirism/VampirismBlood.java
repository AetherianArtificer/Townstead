package com.aetherianartificer.townstead.compat.vampirism;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Vampirism's own blood and infection, reached by reflection so Townstead needs no compile
 * dependency: a creature's blood pool (drained by a bite, refilled by Vampirism over time) and
 * the target's {@code tryInfect}, which applies Vampirism's own rules and configs. The creature
 * lookup returns {@code Optional} on 1.21.1 and {@code LazyOptional} on 1.20.1.
 */
public final class VampirismBlood {
    private static volatile boolean probed;
    private static Method creatureLookup;
    private static Method playerLookup;

    private VampirismBlood() {}

    /** Vampirism's blood record for a creature, or null when it has none (or Vampirism is absent). */
    public static @Nullable Object creature(PathfinderMob mob) {
        if (!probe()) return null;
        try {
            return unwrap(creatureLookup.invoke(null, mob));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static int blood(Object creature) {
        try {
            return ((Number) creature.getClass().getMethod("getBlood").invoke(creature)).intValue();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** The creature's full blood pool; 0 when Vampirism gives its kind no blood. */
    public static int maxBlood(Object creature) {
        try {
            return ((Number) creature.getClass().getMethod("getMaxBlood").invoke(creature)).intValue();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static void setBlood(Object creature, int blood) {
        try {
            creature.getClass().getMethod("setBlood", int.class).invoke(creature, blood);
        } catch (Throwable ignored) {
            // Vampirism reshaped: the bite still feeds, the creature just keeps its blood.
        }
    }

    /** Whether a vampire player's blood bar has room; null when the player is no vampire. */
    public static @Nullable Boolean needsBlood(Player player) {
        if (!probe()) return null;
        try {
            Object vampire = playerLookup.invoke(null, player);
            if (vampire == null || ((Number) vampire.getClass().getMethod("getLevel").invoke(vampire)).intValue() <= 0) return null;
            int level = ((Number) vampire.getClass().getMethod("getBloodLevel").invoke(vampire)).intValue();
            Object stats = vampire.getClass().getMethod("getBloodStats").invoke(vampire);
            int max = ((Number) stats.getClass().getMethod("getMaxBlood").invoke(stats)).intValue();
            return level < max;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Fills a vampire player's blood bar through Vampirism's own drinking, so its events and
     * overflow handling run. False when the player is no vampire or Vampirism is absent.
     */
    public static boolean drink(Player player, int amount, float saturation) {
        if (amount <= 0 || !probe()) return false;
        try {
            Object vampire = playerLookup.invoke(null, player);
            if (vampire == null || ((Number) vampire.getClass().getMethod("getLevel").invoke(vampire)).intValue() <= 0) return false;
            Class<?> contextType = Class.forName("de.teamlapen.vampirism.entity.vampire.DrinkBloodContext");
            Object context = contextType.getConstructor(net.minecraft.world.item.ItemStack.class)
                    .newInstance(net.minecraft.world.item.ItemStack.EMPTY);
            for (Method method : vampire.getClass().getMethods()) {
                if (!method.getName().equals("drinkBlood") || method.getParameterCount() != 4) continue;
                method.invoke(vampire, amount, saturation, false, context);
                return true;
            }
        } catch (Throwable ignored) {
            // Vampirism reshaped: the drink gives nothing.
        }
        return false;
    }

    /** Vampirism's own infection roll for the bitten creature. */
    public static boolean infect(Object creature) {
        return tryInfect(creature);
    }

    public static boolean infect(Player player) {
        if (!probe()) return false;
        try {
            return tryInfect(playerLookup.invoke(null, player));
        } catch (Throwable ignored) {
            return false;
        }
    }

    // The biter is not one of Vampirism's own vampires, so it passes none; Vampirism then judges
    // the bite as a mob's (its disableMobBiteInfection setting applies).
    private static boolean tryInfect(@Nullable Object target) {
        if (target == null) return false;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals("tryInfect") || method.getParameterCount() != 1) continue;
            try {
                return Boolean.TRUE.equals(method.invoke(target, (Object) null));
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static @Nullable Object unwrap(@Nullable Object holder) throws ReflectiveOperationException {
        if (holder == null) return null;
        if (holder instanceof Optional<?> optional) return optional.orElse(null);
        Object resolved = holder.getClass().getMethod("resolve").invoke(holder);
        return resolved instanceof Optional<?> optional ? optional.orElse(null) : null;
    }

    private static boolean probe() {
        if (probed) return creatureLookup != null;
        synchronized (VampirismBlood.class) {
            if (!probed) {
                try {
                    creatureLookup = Class.forName("de.teamlapen.vampirism.api.VampirismAPI")
                            .getMethod("getExtendedCreatureVampirism", PathfinderMob.class);
                    playerLookup = Class.forName("de.teamlapen.vampirism.entity.player.vampire.VampirePlayer")
                            .getMethod("get", Player.class);
                } catch (Throwable ignored) {
                    creatureLookup = null;
                }
                probed = true;
            }
            return creatureLookup != null;
        }
    }
}
