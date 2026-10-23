package com.aetherianartificer.townstead.persona;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Takes a Persona's family name back out of text that was written down with it, for a player who
 * has not been told it yet.
 *
 * <p>Names are copied into some records when they happen, chronicle entries above all, and older
 * records carry the full name of a Persona whose family name is told only. Text read back to one
 * player goes through this on its way out.</p>
 */
public final class PersonaNameMask {

    private PersonaNameMask() {}

    /** Rewrites a told-only Persona's full name to their given name wherever {@code player} has not been told it. */
    public static UnaryOperator<String> forPlayer(ServerPlayer player) {
        return build(player.server, persona -> !PersonaService.familyKnown(persona, player));
    }

    /** As {@link #forPlayer}, for text that can reach anyone: every told-only Persona is their given name. */
    public static UnaryOperator<String> forAnyone(@Nullable MinecraftServer server) {
        if (server == null) return UnaryOperator.identity();
        return build(server, PersonaDefinition::familyToldOnly);
    }

    /** Every string value of {@code params} through {@code mask}. */
    public static Map<String, String> params(Map<String, String> params, UnaryOperator<String> mask) {
        Map<String, String> out = new LinkedHashMap<>();
        params.forEach((key, value) -> out.put(key, mask.apply(value)));
        return out;
    }

    private static UnaryOperator<String> build(MinecraftServer server, Predicate<PersonaDefinition> hidden) {
        Map<String, String> names = new LinkedHashMap<>();
        PersonaInstances instances = PersonaInstances.get(server);
        for (Map.Entry<ResourceLocation, PersonaDefinition> entry : Personas.all().entrySet()) {
            if (!hidden.test(entry.getValue())) continue;
            String given = instances.rolled(entry.getKey(), "name.given");
            String family = instances.rolled(entry.getKey(), "name.family");
            if (given != null && family != null) names.put(given, family);
        }
        return of(names);
    }

    /** Rewrites each full name, in either order, to the given name. Keys are given names, values family names. */
    static UnaryOperator<String> of(Map<String, String> names) {
        Map<String, String> swaps = new LinkedHashMap<>();
        names.forEach((given, family) -> {
            if (given.isBlank() || family.isBlank()) return;
            swaps.put(given + " " + family, given);
            swaps.put(family + " " + given, given);
        });
        if (swaps.isEmpty()) return UnaryOperator.identity();
        return text -> {
            if (text == null || text.isEmpty()) return text;
            String out = text;
            for (Map.Entry<String, String> swap : swaps.entrySet()) out = out.replace(swap.getKey(), swap.getValue());
            return out;
        };
    }
}
