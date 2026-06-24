package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.origin.rig.RigDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Derives the first-person eye height for a rig from the bone its {@code camera.bone} names, so a body's
 * camera sits where its head actually is instead of the default humanoid 1.62 (a low spider body's eyes
 * sit near the ground). The bone's resting Y in the baked model maps to a world height through the same
 * feet-to-head transform the entity renderer applies ({@link #BASELINE} blocks at model y=0, dropping
 * one block per 16 model pixels), so {@code eye = BASELINE - boneY/16}, scaled by the rig's render scale.
 *
 * <p>Client-side only (the bone position lives in the baked model). The per-rig base height is constant,
 * so it is cached; {@code NaN} marks a rig with no camera bone so the lookup short-circuits.</p>
 */
public final class RigCamera {

    /** LivingEntityRenderer maps model y=0 to this world height (1.62 eyes sit a little above the head pivot). */
    private static final float BASELINE = 1.501f;

    private static final Map<String, Float> BASE_EYE = new ConcurrentHashMap<>();

    private RigCamera() {}

    /** Drop the cached per-rig heights so a fresh catalog sync re-derives them (rig defs may have changed). */
    public static void invalidate() {
        BASE_EYE.clear();
    }

    /** The rig-derived eye height for this entity in blocks, or null to keep the vanilla default. */
    public static Float eyeHeight(LivingEntity entity) {
        String rigBase = RigModels.rigBaseFor(entity);
        if (rigBase == null || rigBase.isEmpty()) return null;
        Float base = BASE_EYE.get(rigBase);
        if (base == null) {
            base = derive(rigBase);
            if (base == null) return null; // model not baked yet: retry next frame, don't cache
            BASE_EYE.put(rigBase, base);
        }
        if (base.isNaN()) return null; // rig declares no camera bone
        float scale = RigModels.scaleFor(entity);
        return base * (scale > 0f ? scale : 1f);
    }

    /** Base eye height for a rig, {@code NaN} when it declares none, null when its model isn't baked yet. */
    private static Float derive(String rigBase) {
        RigDefinition def = RigModels.definition(rigBase);
        if (def == null) return Float.NaN;
        String bone = def.cameraBone();
        if (bone == null || bone.isEmpty()) return Float.NaN;
        ModelPart part = RigModels.cameraBone(rigBase, bone);
        if (part == null) return null;
        return Mth.clamp(BASELINE - part.y / 16f, 0.1f, 3.0f);
    }
}
