package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.root.RootLook;
import com.aetherianartificer.townstead.root.outfit.RootOutfits;
import com.google.gson.JsonParser;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Which of its root's outfits an entity wears right now. Each outfit's pheno {@code when} is tested
 * against the entity once per tick (profession, weather, anything a condition reads client-side);
 * the renderer grafts the result onto the rig.
 */
public final class RigOutfitState {

    // Parsed conditions by their JSON text (an empty or unparseable gate reads as always).
    private static final Map<String, Optional<Condition>> CONDITIONS = new HashMap<>();
    private static final Map<Integer, Worn> STATE = new HashMap<>();
    private static final int MAX_TRACKED = 512;

    private record Worn(int tick, RootLook look, List<RootOutfits.Outfit> outfits) {}

    private RigOutfitState() {}

    public static List<RootOutfits.Outfit> worn(LivingEntity entity) {
        RootLook look = RootCatalogClient.look(RootClientStore.resolve(entity));
        if (look == null || look.outfits().isEmpty()) return List.of();
        Worn cached = STATE.get(entity.getId());
        if (cached != null && cached.tick() == entity.tickCount && cached.look() == look) return cached.outfits();
        ConditionContext ctx = new ConditionContext(entity);
        List<RootOutfits.Outfit> out = new ArrayList<>();
        for (RootOutfits.Outfit outfit : look.outfits()) {
            if (active(outfit.whenJson(), ctx)) out.add(outfit);
        }
        if (STATE.size() >= MAX_TRACKED) STATE.clear();
        List<RootOutfits.Outfit> worn = out.isEmpty() ? List.of() : List.copyOf(out);
        STATE.put(entity.getId(), new Worn(entity.tickCount, look, worn));
        return worn;
    }

    /** Whether a pheno gate holds; shared with the rig clip rules. Empty = always. */
    static boolean active(String whenJson, ConditionContext ctx) {
        if (whenJson == null || whenJson.isEmpty()) return true;
        Optional<Condition> condition = CONDITIONS.computeIfAbsent(whenJson, json -> {
            try {
                return Optional.ofNullable(Conditions.parse(JsonParser.parseString(json)));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
        return condition.map(c -> c.test(ctx)).orElse(true);
    }
}
