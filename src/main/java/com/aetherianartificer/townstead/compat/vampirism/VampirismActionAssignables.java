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
            registry, cooldown, showInSelect, actionName, factionOf, factionName;

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
                toggle.invoke(handler, action, context());
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
            } catch (Throwable ignored) {
                toggle = null;
            }
            probed = true;
            return toggle != null;
        }
    }
}
