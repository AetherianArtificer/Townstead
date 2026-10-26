package com.aetherianartificer.townstead.client.animation;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class McaModelPartApplierTest {
    @Test void normalAndDiagnosticPathsProduceIdenticalPoses() {
        var normal = AnimationTargetMapTest.model();
        var diagnostic = AnimationTargetMapTest.model();
        var transforms = List.of(
                AnimationTransform.rotate("head", 0.5F, -0.2F, 1F, AnimationTransform.Operation.ADD),
                AnimationTransform.translate("torso", 1F, 2F, -3F, AnimationTransform.Operation.SET),
                AnimationTransform.rotate("head", Float.NaN, 200F, Float.POSITIVE_INFINITY, AnimationTransform.Operation.SET),
                new AnimationTransform("body", null, null, null, null, 0.25F, null, 2F, 3F, 4F,
                        null, null, false, true, false, AnimationTransform.Operation.SET));
        var normalMap = AnimationTargetMap.forMcaModel(normal);
        var diagnosticMap = AnimationTargetMap.forMcaModel(diagnostic);
        McaModelPartApplier.apply("test", normalMap, transforms);
        var stats = McaModelPartApplier.applyWithStats("test", diagnosticMap, transforms);
        assertEquals(3, stats.appliedParts());
        assertEquals(3F, stats.largestDelta());
        for (String name : List.of("head", "body", "right_arm", "left_arm", "right_leg", "left_leg")) {
            var a = normalMap.resolve(name).orElseThrow();
            var b = diagnosticMap.resolve(name).orElseThrow();
            assertArrayEquals(new float[] {a.x, a.y, a.z, a.xRot, a.yRot, a.zRot, a.xScale, a.yScale, a.zScale},
                    new float[] {b.x, b.y, b.z, b.xRot, b.yRot, b.zRot, b.xScale, b.yScale, b.zScale});
        }
        assertEquals(0.5F, normal.head.xRot);
        assertEquals(-0.2F, normal.head.yRot);
        assertEquals(1F, normal.head.zRot);
        assertEquals(2F, normal.body.xScale);
    }
}
