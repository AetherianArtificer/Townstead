package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.assign.Assignable;
import com.aetherianartificer.townstead.assign.AssignableProvider;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A lord's minion orders on the ability wheel, as Vampirism's minion wheel offers them: every task
 * open to their faction, given to all their minions, and Call All and Respawn. Each one does what
 * that wheel does, through Vampirism's own minion controller. Ids are {@code <ns>:minion/<task>}.
 */
public final class VampirismMinionAssignables implements AssignableProvider {
    private static final String PREFIX = "minion/";
    private static final String CALL_ALL = "call_all";
    private static final String RESPAWN = "respawn";

    @Override
    public void collect(ServerPlayer player, List<Assignable> out) {
        Object handler = lord(player);
        if (handler == null) return;
        Component source = Component.translatable("townstead.wheel.source.minions");
        try {
            Object faction = handler.getClass().getMethod("getCurrentFaction").invoke(handler);
            Object registry = registry();
            if (registry != null && faction != null) {
                Method key = method(registry, "getKey");
                for (Object task : (Iterable<?>) registry) {
                    if (!available(task, faction, handler)) continue;
                    if (!(key.invoke(registry, task) instanceof ResourceLocation taskId)) continue;
                    out.add(entry(taskId, (Component) task.getClass().getMethod("getName").invoke(task),
                            taskId.getNamespace() + ":textures/minion_tasks/" + taskId.getPath() + ".png", source));
                }
            }
        } catch (Throwable ignored) {
            // A Vampirism whose minion API has moved offers no tasks, only the calls below.
        }
        out.add(entry(ResourceLocation.tryBuild("vampirism", CALL_ALL),
                Component.translatable("text.vampirism.minion.call_all"), "vampirism:textures/minion_tasks/recall.png", source));
        out.add(entry(ResourceLocation.tryBuild("vampirism", RESPAWN),
                Component.translatable("text.vampirism.minion.respawn"), "vampirism:textures/minion_tasks/respawn.png", source));
    }

    @Override
    public boolean invoke(ServerPlayer player, ResourceLocation id) {
        if (!id.getPath().startsWith(PREFIX)) return false;
        Object handler = lord(player);
        if (handler == null) return false;
        String name = id.getPath().substring(PREFIX.length());
        try {
            Object controller = controller(player, handler);
            if (controller == null) return false;
            if ("vampirism".equals(id.getNamespace()) && CALL_ALL.equals(name)) {
                Collection<?> ids = (Collection<?>) method(controller, "recallMinions").invoke(controller, false);
                for (Object minion : ids) method(controller, "createMinionEntityAtPlayer").invoke(controller, minion, player);
                tellRecovering(player, controller);
                return true;
            }
            if ("vampirism".equals(id.getNamespace()) && RESPAWN.equals(name)) {
                Collection<?> ids = (Collection<?>) method(controller, "getUnclaimedMinions").invoke(controller);
                for (Object minion : ids) method(controller, "createMinionEntityAtPlayer").invoke(controller, minion, player);
                tellRecovering(player, controller);
                return true;
            }
            Object registry = registry();
            if (registry == null) return false;
            ResourceLocation taskId = ResourceLocation.tryBuild(id.getNamespace(), name);
            Method key = method(registry, "getKey");
            for (Object task : (Iterable<?>) registry) {
                if (!taskId.equals(key.invoke(registry, task))) continue;
                // -1 is every minion, as Vampirism's own wheel orders them.
                method(controller, "activateTask").invoke(controller, -1, task);
                return true;
            }
        } catch (Throwable ignored) {
            // Not a task this Vampirism knows.
        }
        return false;
    }

    private static Assignable entry(ResourceLocation taskId, Component name, String icon, Component source) {
        ResourceLocation id = ResourceLocation.tryBuild(taskId.getNamespace(), PREFIX + taskId.getPath());
        return new Assignable(id, name, icon, source, Assignable.Kind.ABILITY, 0, 0, "", 0, "");
    }

    /** The player's faction handler when they are a lord, the only ones with minions to order. */
    private static @Nullable Object lord(ServerPlayer player) {
        if (!ModCompat.isLoaded(VampirismStateProviders.MOD_ID)) return null;
        Object handler = VampirismStateProviders.handlerOf(player);
        if (handler == null) return null;
        try {
            int level = ((Number) handler.getClass().getMethod("getLordLevel").invoke(handler)).intValue();
            return level > 0 ? handler : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean available(Object task, Object faction, Object handler) {
        for (Method m : task.getClass().getMethods()) {
            if (!"isAvailable".equals(m.getName()) || m.getParameterCount() != 2) continue;
            try {
                return Boolean.TRUE.equals(m.invoke(task, faction, handler));
            } catch (Throwable ignored) {
                return false;
            }
        }
        return true;
    }

    /** Vampirism's minion task registry, under whichever name this version gives it. */
    private static @Nullable Object registry() {
        try {
            Class<?> registries = Class.forName("de.teamlapen.vampirism.api.VampirismRegistries");
            for (String field : new String[]{"MINION_TASK", "MINION_TASKS"}) {
                try {
                    Object supplier = registries.getField(field).get(null);
                    Object registry = supplier instanceof java.util.function.Supplier<?> s ? s.get() : supplier;
                    if (registry instanceof Iterable<?>) return registry;
                } catch (NoSuchFieldException ignored) {
                    // The other version's name.
                }
            }
        } catch (Throwable ignored) {
            // No registry to read.
        }
        return null;
    }

    private static @Nullable Object controller(ServerPlayer player, Object handler) throws ReflectiveOperationException {
        Class<?> worldData = Class.forName("de.teamlapen.vampirism.world.MinionWorldData");
        Object data = worldData.getMethod("getData", net.minecraft.server.level.ServerLevel.class).invoke(null, player.serverLevel());
        if (data instanceof Optional<?> optional) data = optional.orElse(null);
        if (data == null) return null;
        return method(data, "getOrCreateController").invoke(data, handler);
    }

    /** Vampirism's own "still recovering" line after a call, as its wheel says it. */
    private static void tellRecovering(ServerPlayer player, Object controller) throws ReflectiveOperationException {
        List<?> names = (List<?>) method(controller, "getRecoveringMinionNames").invoke(controller);
        if (names.size() == 1) {
            player.displayClientMessage(Component.translatable("text.vampirism.minion_is_still_recovering", names.get(0)), true);
        } else if (names.size() > 1) {
            player.displayClientMessage(Component.translatable("text.vampirism.n_minions_are_still_recovering", names.size()), true);
        }
    }

    /** The one public method of this name, whatever its parameter types. */
    private static Method method(Object target, String name) throws NoSuchMethodException {
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name)) return m;
        }
        throw new NoSuchMethodException(name);
    }
}
