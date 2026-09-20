package com.aetherianartificer.townstead.client.animation;

import com.aetherianartificer.townstead.root.Hold;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AnimationTargetMapTest {
    @AfterEach void clear() { AnimationTargetMap.clearCache(); }

    static HumanoidModel<LivingEntity> model() {
        var parts = new HashMap<String, ModelPart>();
        for (String name : List.of("head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg")) {
            parts.put(name, part());
        }
        return new HumanoidModel<>(new ModelPart(List.of(), parts));
    }

    static ModelPart part() { return new ModelPart(List.of(), Map.of()); }

    @Test void reusesMappingsButAlwaysSeesLivePosesAndNewModels() {
        var model = model();
        var map = AnimationTargetMap.forMcaModel(model);
        assertSame(map, AnimationTargetMap.forMcaModel(model));
        model.head.xRot = 1.25F;
        assertEquals(1.25F, map.resolve("head").orElseThrow().xRot);
        assertSame(model.body, map.resolve("torso").orElseThrow());
        assertNotSame(map, AnimationTargetMap.forMcaModel(model()));
        AnimationTargetMap.clearCache();
        assertNotSame(map, AnimationTargetMap.forMcaModel(model));
    }

    @Test void refreshesWhenRigDefinitionsOrChildrenChange() {
        var children = new HashMap<String, ModelPart>();
        children.put("skull", part());
        children.put("other", part());
        var root = new ModelPart(List.of(), children);
        var definition = rig(Map.of("head", "skull"));
        var map = AnimationTargetMap.forRig(root, definition);
        assertSame(map, AnimationTargetMap.forRig(root, definition));
        assertSame(children.get("skull"), map.resolve("head").orElseThrow());
        var replacement = part();
        children.put("skull", replacement);
        var refreshed = AnimationTargetMap.forRig(root, definition);
        assertNotSame(map, refreshed);
        assertSame(replacement, refreshed.resolve("head").orElseThrow());
        var remapped = AnimationTargetMap.forRig(root, rig(Map.of("head", "other")));
        assertSame(children.get("other"), remapped.resolve("head").orElseThrow());
        children.remove("skull");
        assertTrue(AnimationTargetMap.forRig(root, definition).resolve("head").isEmpty());
    }

    @Test void evictsOldPreviewModelsAtTheCacheBound() {
        AnimationTargetMap.clearCache();
        var first = model();
        var map = AnimationTargetMap.forMcaModel(first);
        for (int i = 0; i < 128; i++) AnimationTargetMap.forMcaModel(model());
        assertNotSame(map, AnimationTargetMap.forMcaModel(first));
        assertSame(first.head, AnimationTargetMap.forMcaModel(first).resolve("head").orElseThrow());
    }

    private static RigDefinition rig(Map<String, String> bones) {
        return new RigDefinition("test:rig", RigDefinition.ModelType.GEOMETRY, "test:rig", "main",
                "test:texture", bones, RigDefinition.ArmorType.NONE, null, null, null, null, null,
                List.of(), Hold.NONE, false, Map.of(), null, Set.of(), null, null);
    }
}
