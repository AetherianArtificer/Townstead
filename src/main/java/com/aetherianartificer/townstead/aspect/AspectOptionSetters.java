package com.aetherianartificer.townstead.aspect;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 * Where a player's aspect look lives when a mod keeps it (a werewolf's coat lives in Werewolves),
 * so the Aspects page reads and writes the mod's own value and the two always agree.
 */
public final class AspectOptionSetters {
    public record Setter(ToIntFunction<Player> get, ObjIntConsumer<Player> set) {}

    private static final Map<String, Setter> SETTERS = new ConcurrentHashMap<>();

    private AspectOptionSetters() {}

    public static void register(String id, ToIntFunction<Player> get, ObjIntConsumer<Player> set) {
        SETTERS.put(id, new Setter(get, set));
    }

    public static Setter get(String id) {
        return id == null ? null : SETTERS.get(id);
    }
}
