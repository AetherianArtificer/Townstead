package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.pheno.state.StateProviders;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Reflection-only bridge exposing a Vampirism player's faction and lord levels as state
 * providers, so Pheno can react to what Vampirism runs without owning it. The handler lookup
 * returns {@code Optional} on 1.21.1 and {@code LazyOptional} on 1.20.1; both are unwrapped
 * here, and the rest of the API is identical across versions.
 */
public final class VampirismStateProviders {
    public static final String MOD_ID = "vampirism";
    private static final ResourceLocation VAMPIRE = ResourceLocation.tryParse("vampirism:vampire");
    private static final ResourceLocation HUNTER = ResourceLocation.tryParse("vampirism:hunter");
    /** Werewolves (TeamLapen) is a Vampirism faction, so the same handler reads it. */
    public static final String WEREWOLVES_MOD_ID = "werewolves";
    private static final ResourceLocation WEREWOLF = ResourceLocation.tryParse("werewolves:werewolf");

    private static volatile boolean probeAttempted;
    private static volatile boolean probeOk;
    private static Method getHandler;
    private static Method currentFaction;
    private static Method currentLevel;
    private static Method lordLevel;
    private static Method factionId;

    private VampirismStateProviders() {}

    public static void register() {
        if (!ModCompat.isLoaded(MOD_ID)) return;
        StateProviders.register(id("vampire_level"), entity -> level(entity, VAMPIRE, false));
        StateProviders.register(id("hunter_level"), entity -> level(entity, HUNTER, false));
        StateProviders.register(id("vampire_lord_level"), entity -> level(entity, VAMPIRE, true));
        StateProviders.register(id("hunter_lord_level"), entity -> level(entity, HUNTER, true));
        if (ModCompat.isLoaded(WEREWOLVES_MOD_ID)) {
            StateProviders.register(ResourceLocation.tryParse(WEREWOLVES_MOD_ID + ":werewolf_level"), entity -> level(entity, WEREWOLF, false));
            StateProviders.register(ResourceLocation.tryParse(WEREWOLVES_MOD_ID + ":werewolf_lord_level"), entity -> level(entity, WEREWOLF, true));
            StateProviders.register(ResourceLocation.tryParse(WEREWOLVES_MOD_ID + ":beast_form"), VampirismStateProviders::beastForm);
            StateProviders.register(ResourceLocation.tryParse(WEREWOLVES_MOD_ID + ":coat"), entity ->
                    entity instanceof Player player && level(player, WEREWOLF, false) != null ? (double) coat(player) + 1 : null);
            com.aetherianartificer.townstead.aspect.AspectOptionSetters.register(WEREWOLVES_MOD_ID + ":coat",
                    VampirismStateProviders::coat, VampirismStateProviders::setCoat);
            StateProviders.register(ResourceLocation.tryParse(WEREWOLVES_MOD_ID + ":eyes"), entity ->
                    entity instanceof Player player && level(player, WEREWOLF, false) != null ? (double) eyes(player) + 1 : null);
            com.aetherianartificer.townstead.aspect.AspectOptionSetters.register(WEREWOLVES_MOD_ID + ":eyes",
                    VampirismStateProviders::eyes, VampirismStateProviders::setEyes);
            StateProviders.register(ResourceLocation.tryParse(WEREWOLVES_MOD_ID + ":glow"), entity ->
                    entity instanceof Player player && level(player, WEREWOLF, false) != null ? (double) glow(player) + 1 : null);
            com.aetherianartificer.townstead.aspect.AspectOptionSetters.register(WEREWOLVES_MOD_ID + ":glow",
                    VampirismStateProviders::glow, VampirismStateProviders::setGlow);
        }
        if (ModCompat.isLoaded(AGEING_MOD_ID)) {
            StateProviders.register(ResourceLocation.tryParse(AGEING_MOD_ID + ":vampire_age_rank"), VampirismStateProviders::ageRank);
        }
    }

    // Vampiric Ageing: an Age Rank (1-5) past level 14. Its 1.20.1 build is a separate codebase.
    public static final String AGEING_MOD_ID = "vampiricageing";
    private static final String[][] AGE_LOOKUPS = {
            {"com.thedrofdoctoring.vampiricageing.capabilities.AgeingManager", "getAge"},
            {"com.doctor.vampiricageing.capabilities.VampiricAgeingCapabilityManager", "getAge"}};
    private static volatile Method ageLookup;
    private static volatile boolean ageProbed;

    /** A vampire player's Age Rank, or null for anyone else (an aged hunter is no vampire). */
    private static @Nullable Double ageRank(LivingEntity entity) {
        if (level(entity, VAMPIRE, false) == null || !probeAge()) return null;
        try {
            Object record = unwrap(ageLookup.invoke(null, entity));
            if (record == null) return null;
            int rank = ((Number) record.getClass().getMethod("getAge").invoke(record)).intValue();
            return rank > 0 ? (double) rank : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean probeAge() {
        if (ageProbed) return ageLookup != null;
        synchronized (VampirismStateProviders.class) {
            if (!ageProbed) {
                for (String[] lookup : AGE_LOOKUPS) {
                    try {
                        ageLookup = Class.forName(lookup[0]).getMethod(lookup[1], LivingEntity.class);
                        break;
                    } catch (Throwable ignored) {
                        // Try the other codebase.
                    }
                }
                ageProbed = true;
            }
            return ageLookup != null;
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryParse(MOD_ID + ":" + path);
    }

    /** The player's Vampirism faction handler, unwrapped on either version, or null. */
    static @Nullable Object handlerOf(Player player) {
        if (!ModCompat.isLoaded(MOD_ID) || !ensureProbe()) return null;
        try {
            return unwrap(getHandler.invoke(null, player));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * A werewolf player's form, as the Werewolves mod has it: 1 on four legs (survivalist or
     * four-legged beast), 2 upright, null in human form or for anyone else.
     */
    private static @Nullable Double beastForm(LivingEntity entity) {
        if (!(entity instanceof Player player) || level(player, WEREWOLF, false) == null) return null;
        try {
            Object holder = Class.forName("de.teamlapen.werewolves.entities.player.werewolf.WerewolfPlayer")
                    .getMethod("getOpt", Player.class).invoke(null, player);
            Object werewolf = holder instanceof Optional<?> optional ? optional.orElse(null) : unwrap(holder);
            if (werewolf == null) return null;
            Object form = werewolf.getClass().getMethod("getForm").invoke(werewolf);
            String name = String.valueOf(form.getClass().getMethod("getName").invoke(form));
            return switch (name) {
                case "beast" -> 2.0;
                case "survivalist", "beast4l" -> 1.0;
                default -> null;
            };
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object werewolfPlayer(Player player) {
        try {
            Object holder = Class.forName("de.teamlapen.werewolves.entities.player.werewolf.WerewolfPlayer")
                    .getMethod("getOpt", Player.class).invoke(null, player);
            return holder instanceof Optional<?> optional ? optional.orElse(null) : unwrap(holder);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object werewolfForm(String field) {
        try {
            return Class.forName("de.teamlapen.werewolves.api.entities.werewolf.WerewolfForm").getField(field).get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** The coat a werewolf player chose in the mod (their beast form's), 0 and up. */
    private static int coat(Player player) {
        return lookOf(player, "getSkinType");
    }

    private static int eyes(Player player) {
        return lookOf(player, "getEyeType");
    }

    /** Writes a coat to every beast form in the mod, so its own look and Townstead's agree. */
    private static void setCoat(Player player, int coat) {
        setLook(player, "setSkinType", coat);
    }

    private static void setEyes(Player player, int eyes) {
        setLook(player, "setEyeType", eyes);
    }

    /** 1 when the player's beast eyes glow in the mod, else 0. */
    private static int glow(Player player) {
        Object werewolf = werewolfPlayer(player);
        Object beast = werewolfForm("BEAST");
        if (werewolf == null || beast == null) return 1;
        try {
            for (java.lang.reflect.Method m : werewolf.getClass().getMethods()) {
                if (m.getName().equals("hasGlowingEyes") && m.getParameterCount() == 1) return Boolean.TRUE.equals(m.invoke(werewolf, beast)) ? 1 : 0;
            }
        } catch (Throwable ignored) {
            // Fall through.
        }
        return 1;
    }

    private static void setGlow(Player player, int on) {
        Object werewolf = werewolfPlayer(player);
        if (werewolf == null) return;
        try {
            for (java.lang.reflect.Method m : werewolf.getClass().getMethods()) {
                if (!m.getName().equals("setGlowingEyes") || m.getParameterCount() != 2) continue;
                for (String field : new String[]{"BEAST", "BEAST4L", "SURVIVALIST"}) {
                    Object form = werewolfForm(field);
                    if (form != null) m.invoke(werewolf, form, on != 0);
                }
                return;
            }
        } catch (Throwable ignored) {
            // The mod moved its API; the glow stays as it was.
        }
    }

    private static int lookOf(Player player, String getter) {
        Object werewolf = werewolfPlayer(player);
        Object beast = werewolfForm("BEAST");
        if (werewolf == null || beast == null) return 0;
        try {
            for (java.lang.reflect.Method m : werewolf.getClass().getMethods()) {
                if (m.getName().equals(getter) && m.getParameterCount() == 1) return Math.max(0, ((Number) m.invoke(werewolf, beast)).intValue());
            }
        } catch (Throwable ignored) {
            // Fall through.
        }
        return 0;
    }

    private static void setLook(Player player, String setter, int value) {
        Object werewolf = werewolfPlayer(player);
        if (werewolf == null) return;
        try {
            java.lang.reflect.Method set = null;
            for (java.lang.reflect.Method m : werewolf.getClass().getMethods()) {
                if (m.getName().equals(setter) && m.getParameterCount() == 2) set = m;
            }
            if (set == null) return;
            for (String field : new String[]{"BEAST", "BEAST4L", "SURVIVALIST"}) {
                Object form = werewolfForm(field);
                if (form != null) set.invoke(werewolf, form, value);
            }
        } catch (Throwable ignored) {
            // The mod moved its API; the look stays as it was.
        }
    }

    /** The id of the player's Vampirism faction (vampire, hunter, werewolf...), or null for none. */
    public static @Nullable ResourceLocation factionOf(Player player) {
        if (!ModCompat.isLoaded(MOD_ID) || !ensureProbe()) return null;
        try {
            Object handler = unwrap(getHandler.invoke(null, player));
            Object current = handler == null ? null : currentFaction.invoke(handler);
            return current == null ? null : (ResourceLocation) factionId.invoke(current);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Joins the player to {@code faction} at {@code level}, as Vampirism's own faction change does. */
    public static boolean joinFaction(Player player, ResourceLocation faction, int level) {
        if (!ModCompat.isLoaded(MOD_ID) || !ensureProbe()) return false;
        try {
            Object handler = unwrap(getHandler.invoke(null, player));
            if (handler == null) return false;
            Object registry = Class.forName("de.teamlapen.vampirism.api.VampirismAPI").getMethod("factionRegistry").invoke(null);
            Object target = registry.getClass().getMethod("getFactionByID", ResourceLocation.class).invoke(registry, faction);
            if (target == null) return false;
            Class<?> playable = Class.forName("de.teamlapen.vampirism.api.entity.factions.IPlayableFaction");
            if (!playable.isInstance(target)) return false;
            Object ok = handler.getClass().getMethod("setFactionAndLevel", playable, int.class).invoke(handler, target, level);
            return Boolean.TRUE.equals(ok);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Takes the player out of their Vampirism faction. */
    public static void leaveFaction(Player player) {
        if (!ModCompat.isLoaded(MOD_ID) || !ensureProbe()) return;
        try {
            Object handler = unwrap(getHandler.invoke(null, player));
            if (handler != null) handler.getClass().getMethod("leaveFaction", boolean.class).invoke(handler, false);
        } catch (Throwable ignored) {
            // Nothing to leave.
        }
    }

    /** A player who has joined the hunters, at any level. */
    public static boolean isHunter(LivingEntity entity) {
        return ModCompat.isLoaded(MOD_ID) && level(entity, HUNTER, false) != null;
    }

    /** The player's level in {@code faction}, or null when they are not in it (or at level 0). */
    private static @Nullable Double level(LivingEntity entity, ResourceLocation faction, boolean lord) {
        if (!(entity instanceof Player player) || !ensureProbe()) return null;
        try {
            Object handler = unwrap(getHandler.invoke(null, player));
            if (handler == null) return null;
            Object current = currentFaction.invoke(handler);
            if (current == null || !faction.equals(factionId.invoke(current))) return null;
            int value = ((Number) (lord ? lordLevel : currentLevel).invoke(handler)).intValue();
            return value > 0 ? (double) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object unwrap(@Nullable Object holder) throws ReflectiveOperationException {
        if (holder == null) return null;
        if (holder instanceof Optional<?> optional) return optional.orElse(null);
        // LazyOptional#resolve() yields a plain Optional.
        Object resolved = holder.getClass().getMethod("resolve").invoke(holder);
        return resolved instanceof Optional<?> optional ? optional.orElse(null) : null;
    }

    private static boolean ensureProbe() {
        if (probeAttempted) return probeOk;
        synchronized (VampirismStateProviders.class) {
            if (probeAttempted) return probeOk;
            try {
                Class<?> api = Class.forName("de.teamlapen.vampirism.api.VampirismAPI");
                Class<?> handler = Class.forName("de.teamlapen.vampirism.api.entity.factions.IFactionPlayerHandler");
                Class<?> faction = Class.forName("de.teamlapen.vampirism.api.entity.factions.IFaction");
                getHandler = api.getMethod("getFactionPlayerHandler", Player.class);
                currentFaction = handler.getMethod("getCurrentFaction");
                currentLevel = handler.getMethod("getCurrentLevel");
                lordLevel = handler.getMethod("getLordLevel");
                factionId = faction.getMethod("getID");
                probeOk = true;
            } catch (Throwable ignored) {
                probeOk = false;
            }
            probeAttempted = true;
            return probeOk;
        }
    }
}
