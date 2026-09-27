package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Which open states a Root's people can have, declared as {@code "states": {"townstead_state:vampire": false}}
 * on a Root, lineage, ancestry or species. The most specific declaration wins (Root, then lineage,
 * then ancestry, then species), so a Skeletownie ancestry can rule out vampirism once and a single
 * Root can still opt back in. An undeclared state is allowed.
 */
public final class RootStates {
    public enum Kind { ROOT, LINEAGE, ANCESTRY, SPECIES }

    private static final Map<Kind, Map<ResourceLocation, Map<ResourceLocation, Boolean>>> DECLARED =
            new EnumMap<>(Kind.class);

    private RootStates() {}

    /** The {@code states} block of one identity file; empty when it declares none. */
    public static Map<ResourceLocation, Boolean> parse(JsonObject json) {
        if (!json.has("states") || !json.get("states").isJsonObject()) return Map.of();
        Map<ResourceLocation, Boolean> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("states").entrySet()) {
            ResourceLocation state = DataPackLang.parseId(entry.getKey());
            JsonElement value = entry.getValue();
            if (state == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                throw new IllegalArgumentException("'states' maps state ids to true or false");
            }
            out.put(state, value.getAsBoolean());
        }
        return out;
    }

    public static synchronized void replace(Kind kind, Map<ResourceLocation, Map<ResourceLocation, Boolean>> declared) {
        DECLARED.put(kind, Map.copyOf(declared));
    }

    /** The most specific declaration for {@code state} along {@code rootId}'s chain, or null when none. */
    public static @Nullable Boolean declared(@Nullable ResourceLocation rootId, ResourceLocation state) {
        Root root = rootId == null ? null : RootRegistry.byId(rootId);
        if (root == null) return null;
        Boolean answer = lookup(Kind.ROOT, root.id(), state);
        if (answer != null) return answer;
        ResourceLocation ancestry = root.ancestry();
        if (root.lineage() != null) {
            answer = lookup(Kind.LINEAGE, root.lineage(), state);
            if (answer != null) return answer;
            Lineage lineage = LineageRegistry.byId(root.lineage());
            if (lineage != null) ancestry = lineage.ancestry();
        }
        if (ancestry != null) {
            answer = lookup(Kind.ANCESTRY, ancestry, state);
            if (answer != null) return answer;
        }
        ResourceLocation species = RootRegistry.effectiveSpecies(root.id());
        return species == null ? null : lookup(Kind.SPECIES, species, state);
    }

    /** Whether {@code entity}'s Root permits {@code state}; entities with no Root are unrestricted. */
    public static boolean allows(LivingEntity entity, ResourceLocation state) {
        String raw = entity instanceof VillagerEntityMCA villager
                ? com.aetherianartificer.townstead.villager.TownsteadVillagers.get(villager).life().rootId()
                : entity instanceof Player player ? PlayerRoot.getRootId(player) : null;
        if (raw == null || raw.isEmpty()) return true;
        return !Boolean.FALSE.equals(declared(ResourceLocation.tryParse(raw), state));
    }

    private static @Nullable Boolean lookup(Kind kind, ResourceLocation id, ResourceLocation state) {
        Map<ResourceLocation, Map<ResourceLocation, Boolean>> byId = DECLARED.get(kind);
        if (byId == null) return null;
        Map<ResourceLocation, Boolean> states = byId.get(id);
        return states == null ? null : states.get(state);
    }
}
