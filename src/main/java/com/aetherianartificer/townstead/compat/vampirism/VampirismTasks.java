package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.calendar.ComponentSync;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads a player's Vampirism tasks for the Quest Ledger and sends them when they change: the tasks
 * each task master they have met offers, the ones they accepted with Vampirism's own count of each
 * requirement, and each task they ever finished (Vampirism remembers those by kind). Vampirism's client hears of tasks only while its
 * menu is open, so the ledger gets them from here. Everything is read the way Vampirism's own task
 * screen reads it; nothing is changed.
 */
public final class VampirismTasks {
    /** Vampirism keeps unique tasks on a board of this id rather than a task master's. */
    private static final UUID UNIQUE_TASKS = UUID.fromString("e2c6068a-8f0e-4d5b-822a-38ad6ecf98c9");
    private static final int CHECK_TICKS = 40;
    private static final Map<UUID, String> SENT = new ConcurrentHashMap<>();
    private static volatile boolean warned;

    private VampirismTasks() {}

    /** Every couple of seconds: sends the player's tasks when anything about them has changed. */
    public static void watch(ServerPlayer player) {
        if ((player.tickCount + player.getId()) % CHECK_TICKS != 0) return;
        if (!ModCompat.isLoaded(VampirismStateProviders.MOD_ID)) return;
        List<VampirismTasksS2CPayload.Task> tasks = collect(player);
        String signature = signature(tasks);
        String before = SENT.put(player.getUUID(), signature);
        if (signature.equals(before)) return;
        VampirismTasksS2CPayload payload = new VampirismTasksS2CPayload(tasks);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }

    /** The player left: forget what they were sent. */
    public static void forget(UUID player) {
        SENT.remove(player);
    }

    /** Opens Vampirism's own menu for the player, where tasks are handed in. */
    public static void openMenu(ServerPlayer player) {
        Object manager = manager(player);
        if (manager == null) return;
        try {
            manager.getClass().getMethod("openVampirismMenu").invoke(manager);
        } catch (Throwable ignored) {
            // Vampirism moved its menu; nothing opens.
        }
    }

    private static @Nullable Object factionPlayer(ServerPlayer player) {
        Object handler = VampirismStateProviders.handlerOf(player);
        if (handler == null) return null;
        try {
            Object optional = handler.getClass().getMethod("getCurrentFactionPlayer").invoke(handler);
            return optional instanceof Optional<?> o ? o.orElse(null) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static @Nullable Object manager(ServerPlayer player) {
        Object factionPlayer = factionPlayer(player);
        if (factionPlayer == null) return null;
        try {
            return Class.forName("de.teamlapen.vampirism.api.entity.player.IFactionPlayer")
                    .getMethod("getTaskManager").invoke(factionPlayer);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** The player's tasks, as the ledger shows them. Empty for anyone in no Vampirism faction. */
    @SuppressWarnings("unchecked")
    static List<VampirismTasksS2CPayload.Task> collect(ServerPlayer player) {
        List<VampirismTasksS2CPayload.Task> out = new ArrayList<>();
        Object factionPlayer = factionPlayer(player);
        Object manager = manager(player);
        if (factionPlayer == null || manager == null) return out;
        try {
            net.minecraft.core.Registry<Object> registry = (net.minecraft.core.Registry<Object>) field(manager, "registry");
            Map<UUID, Object> boards = (Map<UUID, Object>) field(manager, "taskWrapperMap");
            Method completed = method(manager, "getCompletedRequirements", Collection.class);
            Method canComplete = method(manager, "canCompleteTask", null);
            for (Map.Entry<UUID, Object> board : new ArrayList<>(boards.entrySet())) {
                Object wrapper = board.getValue();
                Collection<Object> instances = new ArrayList<>((Collection<Object>) wrapper.getClass().getMethod("getTaskInstances").invoke(wrapper));
                Map<UUID, Map<ResourceLocation, Integer>> progress =
                        (Map<UUID, Map<ResourceLocation, Integer>>) completed.invoke(manager, instances);
                boolean unique = UNIQUE_TASKS.equals(board.getKey());
                BlockPos pos = null;
                if (!unique) {
                    Object seen = wrapper.getClass().getMethod("getLastSeenPos").invoke(wrapper);
                    if (seen instanceof Optional<?> o && o.orElse(null) instanceof BlockPos p) pos = p;
                }
                for (Object instance : instances) {
                    VampirismTasksS2CPayload.Task task = task(player, factionPlayer, manager, registry, canComplete,
                            instance, unique ? "" : board.getKey().toString(), pos,
                            progress.getOrDefault((UUID) instance.getClass().getMethod("getId").invoke(instance), Map.of()));
                    if (task != null) out.add(task);
                }
            }
            for (Object key : (Collection<Object>) field(manager, "completedTasks")) {
                if (!(key instanceof ResourceKey<?> done)) continue;
                Object definition = registry.get(done.location());
                if (definition == null) continue;
                out.add(new VampirismTasksS2CPayload.Task(done.location().toString(), text(title(definition)),
                        text(description(definition)), "", VampirismTasksS2CPayload.DONE, false, 0, false, 0, 0, 0,
                        List.of(), rewards(definition, null)));
            }
        } catch (Throwable error) {
            if (!warned) {
                warned = true;
                com.aetherianartificer.townstead.Townstead.LOGGER.warn("[Townstead] Vampirism tasks are not available in the Quest Ledger: {}", error.toString());
            }
        }
        return out;
    }

    private static @Nullable VampirismTasksS2CPayload.Task task(ServerPlayer player, Object factionPlayer, Object manager,
                                                                 net.minecraft.core.Registry<Object> registry, Method canComplete,
                                                                 Object instance, String board, @Nullable BlockPos pos,
                                                                 Map<ResourceLocation, Integer> progress) {
        try {
            ResourceKey<?> key = (ResourceKey<?>) instance.getClass().getMethod("getTask").invoke(instance);
            Object definition = registry.get(key.location());
            if (definition == null) return null;
            boolean accepted = Boolean.TRUE.equals(instance.getClass().getMethod("isAccepted").invoke(instance));
            boolean unique = board.isEmpty();
            long deadline = accepted && !unique ? ((Number) instance.getClass().getMethod("getTaskTimeStamp").invoke(instance)).longValue() : 0;
            boolean ready = accepted && Boolean.TRUE.equals(canComplete.invoke(manager, instance));
            List<VampirismTasksS2CPayload.Requirement> requirements = new ArrayList<>();
            Object requirement = definition.getClass().getMethod("getRequirement").invoke(definition);
            for (Object req : (List<?>) requirement.getClass().getMethod("getAll").invoke(requirement)) {
                requirements.add(requirement(factionPlayer, req, progress));
            }
            String id = String.valueOf(instance.getClass().getMethod("getId").invoke(instance));
            return new VampirismTasksS2CPayload.Task(id, text(title(definition)), text(description(definition)), board,
                    accepted ? VampirismTasksS2CPayload.ACCEPTED : VampirismTasksS2CPayload.OFFERED, ready, deadline,
                    pos != null, pos == null ? 0 : pos.getX(), pos == null ? 0 : pos.getY(), pos == null ? 0 : pos.getZ(),
                    List.copyOf(requirements), rewards(definition, instance));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** A requirement worded as Vampirism's task screen words it: its kind, then what, as a count. */
    private static VampirismTasksS2CPayload.Requirement requirement(Object factionPlayer, Object req,
                                                                    Map<ResourceLocation, Integer> progress) throws ReflectiveOperationException {
        Object type = invokeAny(req, "getType");
        String kindKey = String.valueOf(invokeAny(type, "getTranslationKey"));
        int total = ((Number) invokeAny(req, "getAmount", factionPlayer)).intValue();
        ResourceLocation id = (ResourceLocation) invokeAny(req, "id");
        int current = progress.getOrDefault(id, 0);
        String itemId = "";
        String[] label;
        if ("ITEMS".equals(String.valueOf(type)) && invokeAny(req, "getStat", factionPlayer) instanceof Item item) {
            itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            label = new String[]{"", ""};
        } else {
            label = text((Component) invokeAny(req, "description"));
        }
        return new VampirismTasksS2CPayload.Requirement(new String[]{kindKey, ""}, label, itemId, current, total);
    }

    /** What a task gives: the item Vampirism set aside for this instance, else the reward's own words. */
    private static List<VampirismTasksS2CPayload.Reward> rewards(Object definition, @Nullable Object instance) {
        try {
            if (instance != null) {
                Object reward = instance.getClass().getMethod("getReward").invoke(instance);
                for (Method m : reward.getClass().getMethods()) {
                    if (m.getName().equals("reward") && m.getParameterCount() == 0 && m.getReturnType() == ItemStack.class) {
                        ItemStack stack = (ItemStack) m.invoke(reward);
                        if (!stack.isEmpty()) {
                            return List.of(new VampirismTasksS2CPayload.Reward(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                                    stack.getCount(), new String[]{"", ""}));
                        }
                    }
                }
            }
            Object reward = definition.getClass().getMethod("getReward").invoke(definition);
            Component words = (Component) invokeAny(reward, "description");
            return words == null ? List.of() : List.of(new VampirismTasksS2CPayload.Reward("", 0, text(words)));
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private static Component title(Object definition) throws ReflectiveOperationException {
        return (Component) definition.getClass().getMethod("getTitle").invoke(definition);
    }

    private static @Nullable Component description(Object definition) throws ReflectiveOperationException {
        Object value = definition.getClass().getMethod("getDescription").invoke(definition);
        return value instanceof Optional<?> o ? (Component) o.orElse(null) : null;
    }

    private static String[] text(@Nullable Component component) {
        return component == null ? new String[]{"", ""} : ComponentSync.extract(component);
    }

    private static String signature(List<VampirismTasksS2CPayload.Task> tasks) {
        StringBuilder out = new StringBuilder();
        for (VampirismTasksS2CPayload.Task t : tasks) {
            out.append(t.id()).append(':').append(t.state()).append(t.ready() ? 'r' : '-').append(t.deadline())
                    .append(t.hasPos() ? t.x() + "," + t.y() + "," + t.z() : "");
            for (VampirismTasksS2CPayload.Requirement r : t.requirements()) out.append('|').append(r.current()).append('/').append(r.total());
            out.append(';');
        }
        return out.toString();
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
                // Look further up.
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** A public method of this name with one parameter (of {@code param}'s kind when given). */
    private static Method method(Object target, String name, @Nullable Class<?> param) throws NoSuchMethodException {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != 1) continue;
            if (param == null || m.getParameterTypes()[0].isAssignableFrom(param)) return m;
        }
        throw new NoSuchMethodException(name);
    }

    /** Calls the public method of this name that takes these arguments, whatever its declared types. */
    private static Object invokeAny(Object target, String name, Object... args) throws ReflectiveOperationException {
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            m.setAccessible(true);
            return m.invoke(target, args);
        }
        throw new NoSuchMethodException(name);
    }
}
