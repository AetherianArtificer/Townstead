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
 * the renderer grafts the result onto the rig. An outfit with a {@code transition} stays grafted
 * while it grows in or shrinks away, and {@link #applyScales} sizes its pieces for the frame.
 */
public final class RigOutfitState {

    // Parsed conditions by their JSON text (an empty or unparseable gate reads as always).
    private static final Map<String, Optional<Condition>> CONDITIONS = new HashMap<>();
    private static final Map<Integer, Worn> STATE = new HashMap<>();
    private static final int MAX_TRACKED = 512;

    private record Worn(int tick, RootLook look, String clothes, List<RootOutfits.Outfit> outfits,
                        Map<String, Fade> fades) {}

    /** How far an outfit is in: 0 gone, 1 fully worn, at the last tick and the one before. */
    private static final class Fade {
        float level;
        float previous;

        Fade(float level) {
            this.level = level;
            this.previous = level;
        }
    }

    private RigOutfitState() {}

    public static List<RootOutfits.Outfit> worn(LivingEntity entity) {
        RootLook look = RootCatalogClient.look(RootClientStore.resolve(entity));
        if (look == null || look.outfits().isEmpty()) return List.of();
        Worn cached = STATE.get(entity.getId());
        // A clothes change re-tests at once: the editor draws one entity in several outfits per frame.
        String clothes = clothesOf(entity);
        if (cached != null && cached.tick() == entity.tickCount && cached.look() == look
                && cached.clothes().equals(clothes)) {
            return cached.outfits();
        }
        // A new look (or a first sight) starts each outfit where its gate is, with no fade.
        boolean fresh = cached == null || cached.look() != look;
        Map<String, Fade> fades = fresh ? new HashMap<>() : cached.fades();
        int elapsed = fresh ? 0 : Math.max(0, entity.tickCount - cached.tick());
        ConditionContext ctx = new ConditionContext(entity);
        List<RootOutfits.Outfit> out = new ArrayList<>();
        for (RootOutfits.Outfit outfit : look.outfits()) {
            float target = active(outfit.whenJson(), ctx) ? 1f : 0f;
            Fade fade = fades.computeIfAbsent(outfit.id(), id -> new Fade(target));
            fade.previous = fade.level;
            if (outfit.transition() <= 0) {
                fade.level = target;
            } else {
                float step = (float) elapsed / outfit.transition();
                fade.level = target > fade.level ? Math.min(target, fade.level + step) : Math.max(target, fade.level - step);
            }
            if (fade.level > 0f || fade.previous > 0f) out.add(outfit);
        }
        if (STATE.size() >= MAX_TRACKED) STATE.clear();
        List<RootOutfits.Outfit> worn = out.isEmpty() ? List.of() : List.copyOf(out);
        STATE.put(entity.getId(), new Worn(entity.tickCount, look, clothes, worn, fades));
        return worn;
    }

    private static String clothesOf(LivingEntity entity) {
        var villager = net.conczin.mca.client.model.CommonVillagerModel.getVillager(entity);
        String clothes = villager == null ? null : villager.getClothes();
        return clothes == null ? "" : clothes;
    }

    /** Size each growing or shrinking outfit's pieces for this frame, after the clips have posed the rig. */
    public static void applyScales(LivingEntity entity, net.minecraft.client.model.geom.ModelPart root, float partialTick) {
        Worn worn = STATE.get(entity.getId());
        if (worn == null || root == null) return;
        for (RootOutfits.Outfit outfit : worn.outfits()) {
            Fade fade = worn.fades().get(outfit.id());
            if (fade == null || (fade.level >= 1f && fade.previous >= 1f)) continue;
            float t = fade.previous + (fade.level - fade.previous) * partialTick;
            float size = Math.max(0.01f, t * t * (3f - 2f * t));
            for (RootOutfits.Piece piece : outfit.pieces()) {
                for (String bone : piece.bones()) {
                    net.minecraft.client.model.geom.ModelPart[] path = RigModels.bonePath(root, bone);
                    if (path == null) continue;
                    net.minecraft.client.model.geom.ModelPart part = path[path.length - 1];
                    part.xScale *= size;
                    part.yScale *= size;
                    part.zScale *= size;
                }
            }
        }
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
