package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.assign.Assignable;
import com.aetherianartificer.townstead.assign.AssignableProvider;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * The player's Vampirism actions on the ability wheel: whatever their faction has unlocked, which
 * covers vampires, hunters and every faction an addon builds on Vampirism (Werewolves). Pressing
 * one toggles it through Vampirism's own handler, so cooldowns, costs and rules stay Vampirism's.
 * Each action shows its own art from {@code <namespace>:textures/actions/<name>.png}.
 */
public final class VampirismActionAssignables implements AssignableProvider {
    /**
     * Actions a mod keeps off its own menu because they ride on a jump (Werewolves' leap): offered
     * here anyway, with the jump done for the player.
     */
    private static final java.util.Set<ResourceLocation> JUMPS = java.util.Set.of(ResourceLocation.tryParse("werewolves:leap"));

    private static volatile boolean probed;
    private static volatile boolean warned;
    private static Method currentFactionPlayer, actionHandler, unlocked, toggle,
            registry, cooldown, showInSelect, actionName, factionOf, factionName,
            percentage, activeById, canUse;
    private static Class<?> lastingType;
    /** What each player's actions looked like at the last check, so a change re-sends the wheel. */
    private static final java.util.Map<java.util.UUID, String> SEEN = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void collect(ServerPlayer player, List<Assignable> out) {
        Object handler = handler(player);
        if (handler == null) return;
        try {
            Object factionPlayer = factionPlayer(player);
            Component source = source(player);
            for (Object action : (Iterable<?>) unlocked.invoke(handler)) {
                ResourceLocation id = id(action);
                if (id == null) continue;
                if (!Boolean.TRUE.equals(showInSelect.invoke(action, player)) && !JUMPS.contains(id)) continue;
                int ticks = ((Number) cooldown.invoke(action, factionPlayer)).intValue();
                out.add(new Assignable(id, (Component) actionName.invoke(action),
                        id.getNamespace() + ":textures/actions/" + id.getPath() + ".png", source,
                        Assignable.Kind.ABILITY, Math.max(0, ticks), 0, "", 0, ""));
            }
        } catch (Throwable error) {
            // A Vampirism that has moved its API offers nothing here; say so once, so it is never silent.
            if (!warned) {
                warned = true;
                com.aetherianartificer.townstead.Townstead.LOGGER.warn("[Townstead] Vampirism actions are not available on the ability wheel: {}", error.toString());
            }
        }
    }

    @Override
    public boolean invoke(ServerPlayer player, ResourceLocation id) {
        Object handler = handler(player);
        if (handler == null) return false;
        try {
            for (Object action : (Iterable<?>) unlocked.invoke(handler)) {
                if (!id.equals(id(action))) continue;
                // Refused as Vampirism's own wheel refuses it: an action it greys out is not pressed.
                if (!Boolean.TRUE.equals(activeById.invoke(handler, id))) {
                    Object allowed = canUse.invoke(action, factionPlayer(player));
                    if (!"ALLOWED".equals(String.valueOf(allowed))) {
                        tell(player, String.valueOf(allowed));
                        return true;
                    }
                }
                Object result = toggle.invoke(handler, action, context());
                if (!"ALLOWED".equals(String.valueOf(result))) {
                    tell(player, String.valueOf(result));
                    return true;
                }
                // A leap is the Leap key held with Jump: switch it on as the mod's own handler does,
                // then jump, so one press from the wheel is the whole move.
                if (JUMPS.contains(id) && player.onGround()) {
                    player.onUpdateAbilities();
                    net.minecraft.world.phys.Vec3 look = player.getLookAngle();
                    player.setDeltaMovement(player.getDeltaMovement().add(look.x * 0.6, 0.6, look.z * 0.6));
                    player.hurtMarked = true;
                }
                return true;
            }
        } catch (Throwable ignored) {
            // Not ours after all, or Vampirism refused it.
        }
        return false;
    }

    @Override
    public @Nullable Live live(ServerPlayer player, ResourceLocation id) {
        Object handler = handler(player);
        if (handler == null) return null;
        try {
            Object factionPlayer = factionPlayer(player);
            for (Object action : (Iterable<?>) unlocked.invoke(handler)) {
                if (!id.equals(id(action))) continue;
                return live(handler, factionPlayer, action, id, player.level().getGameTime());
            }
        } catch (Throwable ignored) {
            // Vampirism keeps its own timers; with none to read, the wheel tracks nothing for it.
        }
        return null;
    }

    /**
     * Vampirism's own numbers for an action, as its wheel reads them: the share of its run or its
     * cooldown left (positive running, negative cooling), and whether it may be used now.
     */
    private static Live live(Object handler, Object factionPlayer, Object action, ResourceLocation id, long now)
            throws ReflectiveOperationException {
        boolean lasting = lastingType != null && lastingType.isInstance(action);
        boolean on = lasting && Boolean.TRUE.equals(activeById.invoke(handler, id));
        float share = ((Number) percentage.invoke(handler, action)).floatValue();
        int cooldownTicks = Math.max(0, ((Number) cooldown.invoke(action, factionPlayer)).intValue());
        long activeUntil = 0;
        int activeTicks = 0;
        if (on) {
            int duration = duration(action, factionPlayer);
            // A form runs until switched off: Vampirism gives it an endless duration.
            if (duration <= 0 || duration >= 1_000_000) {
                activeUntil = Long.MAX_VALUE;
            } else {
                activeTicks = duration;
                activeUntil = now + Math.round(Math.max(0f, share) * duration);
            }
        }
        long readyAt = share < 0 && cooldownTicks > 0 ? now + Math.round(-share * cooldownTicks) : 0;
        boolean usable = "ALLOWED".equals(String.valueOf(canUse.invoke(action, factionPlayer)));
        return new Live(lasting, on, activeUntil, activeTicks, readyAt, cooldownTicks, usable);
    }

    /** A lasting action's run for this player, through whichever overload this Vampirism has. */
    private static int duration(Object action, Object factionPlayer) {
        for (Method method : action.getClass().getMethods()) {
            if (!"getDuration".equals(method.getName()) || method.getParameterCount() != 1) continue;
            if (!method.getParameterTypes()[0].isInstance(factionPlayer)) continue;
            try {
                return ((Number) method.invoke(action, factionPlayer)).intValue();
            } catch (Throwable ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** Vampirism's own message for a refusal, as it says it when its key is pressed. */
    private static void tell(ServerPlayer player, String refusal) {
        String key = switch (refusal) {
            case "NOT_UNLOCKED" -> "text.vampirism.action.not_unlocked";
            case "DISABLED" -> "text.vampirism.action.deactivated_by_serveradmin";
            case "COOLDOWN" -> "text.vampirism.action.cooldown_not_over";
            case "PERMISSION_DISALLOWED" -> "text.vampirism.action.permission_disallowed";
            default -> "text.vampirism.action.disallowed";
        };
        player.displayClientMessage(Component.translatable(key), true);
    }

    /**
     * Once a second: when a player's actions change on their own (a form ends at sunrise, a cooldown
     * runs out, a skill unlocks one), the wheel is sent again, so its slots never show old news.
     */
    public static void watch(ServerPlayer player) {
        if (player.tickCount % 20 != 0) return;
        Object handler = handler(player);
        if (handler == null) {
            if (SEEN.remove(player.getUUID()) != null) com.aetherianartificer.townstead.root.ability.ActiveAbilities.syncView(player);
            return;
        }
        StringBuilder state = new StringBuilder();
        try {
            Object factionPlayer = factionPlayer(player);
            for (Object action : (Iterable<?>) unlocked.invoke(handler)) {
                ResourceLocation id = id(action);
                if (id == null) continue;
                float share = ((Number) percentage.invoke(handler, action)).floatValue();
                state.append(id).append(share > 0 ? "+" : share < 0 ? "-" : "0")
                        .append("ALLOWED".equals(String.valueOf(canUse.invoke(action, factionPlayer))) ? "u" : "x").append(";");
            }
        } catch (Throwable ignored) {
            return;
        }
        String now = state.toString();
        String before = SEEN.put(player.getUUID(), now);
        if (before != null && !before.equals(now)) com.aetherianartificer.townstead.root.ability.ActiveAbilities.syncView(player);
    }

    /** The player left: forget what they had. */
    public static void forget(java.util.UUID player) {
        SEEN.remove(player);
    }

    private static @Nullable Object factionPlayer(ServerPlayer player) throws ReflectiveOperationException {
        Object handlerLookup = VampirismStateProviders.handlerOf(player);
        if (handlerLookup == null) return null;
        Object optional = currentFactionPlayer.invoke(handlerLookup);
        return optional instanceof Optional<?> o ? o.orElse(null) : null;
    }

    private static @Nullable Object handler(ServerPlayer player) {
        if (!ModCompat.isLoaded(VampirismStateProviders.MOD_ID) || !probe()) return null;
        try {
            Object factionPlayer = factionPlayer(player);
            return factionPlayer == null ? null : actionHandler.invoke(factionPlayer);
        } catch (Throwable ignored) {
            return null;
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static @Nullable ResourceLocation id(Object action) throws ReflectiveOperationException {
        Object reg = registry.invoke(Class.forName("de.teamlapen.vampirism.api.VampirismAPI")
                .getMethod("actionManager").invoke(null));
        // Through the public interface: the registry's own class is not public, so a lookup on it fails.
        if (reg instanceof net.minecraft.core.Registry registry) return registry.getKey(action);
        Object key = Class.forName("net.minecraftforge.registries.IForgeRegistry")
                .getMethod("getKey", Object.class).invoke(reg, action);
        return key instanceof ResourceLocation rl ? rl : null;
    }

    /** The heading on the wheel: the faction's own name, such as Werewolf. */
    private static Component source(ServerPlayer player) {
        try {
            Object factionPlayer = factionPlayer(player);
            Object faction = factionOf.invoke(factionPlayer);
            Object name = factionName.invoke(faction);
            if (name instanceof Component component) return component;
        } catch (Throwable ignored) {
            // Fall through to the plain heading.
        }
        return Component.literal("Vampirism");
    }

    /** No block or entity aimed at: the wheel fires an action as its key would. */
    private static Object context() throws ClassNotFoundException {
        Class<?> type = Class.forName("de.teamlapen.vampirism.api.entity.player.actions.IAction$ActivationContext");
        return java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getName()) {
                    case "targetBlock", "targetEntity" -> Optional.empty();
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    case "toString" -> "TownsteadWheelContext";
                    default -> null;
                });
    }

    private static boolean probe() {
        if (probed) return toggle != null;
        synchronized (VampirismActionAssignables.class) {
            if (probed) return toggle != null;
            try {
                Class<?> handlerType = Class.forName("de.teamlapen.vampirism.api.entity.factions.IFactionPlayerHandler");
                Class<?> factionPlayer = Class.forName("de.teamlapen.vampirism.api.entity.player.IFactionPlayer");
                Class<?> actionHandlerType = Class.forName("de.teamlapen.vampirism.api.entity.player.actions.IActionHandler");
                Class<?> action = Class.forName("de.teamlapen.vampirism.api.entity.player.actions.IAction");
                Class<?> context = Class.forName("de.teamlapen.vampirism.api.entity.player.actions.IAction$ActivationContext");
                Class<?> manager = Class.forName("de.teamlapen.vampirism.api.entity.player.actions.IActionManager");
                Class<?> faction = Class.forName("de.teamlapen.vampirism.api.entity.factions.IFaction");
                currentFactionPlayer = handlerType.getMethod("getCurrentFactionPlayer");
                actionHandler = factionPlayer.getMethod("getActionHandler");
                factionOf = factionPlayer.getMethod("getFaction");
                factionName = faction.getMethod("getName");
                unlocked = actionHandlerType.getMethod("getUnlockedActions");
                toggle = actionHandlerType.getMethod("toggleAction", action, context);
                cooldown = action.getMethod("getCooldown", factionPlayer);
                showInSelect = action.getMethod("showInSelectAction", net.minecraft.world.entity.player.Player.class);
                actionName = action.getMethod("getName");
                registry = manager.getMethod("getRegistry");
                percentage = actionHandlerType.getMethod("getPercentageForAction", action);
                activeById = actionHandlerType.getMethod("isActionActive", ResourceLocation.class);
                canUse = action.getMethod("canUse", factionPlayer);
                lastingType = Class.forName("de.teamlapen.vampirism.api.entity.player.actions.ILastingAction");
            } catch (Throwable ignored) {
                toggle = null;
            }
            probed = true;
            return toggle != null;
        }
    }
}
