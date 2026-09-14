package com.aetherianartificer.townstead.client.animation;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ZombieAnimationSourceAdapterTest {
    @Test void mapsTheGamesZombieArmsToTheCorrectRigBonesAcrossIdleAndAttack() {
        var adapter = new ZombieAnimationSourceAdapter();
        for (boolean aggressive : List.of(false, true)) {
            for (float attack : new float[]{0f, 0.4f, 1f}) {
                var left = new ModelPart(List.of(), Map.of());
                var right = new ModelPart(List.of(), Map.of());
                AnimationUtils.animateZombieArms(left, right, aggressive, attack, 23f);
                var transforms = adapter.arms(attack, 23f, aggressive);
                assertEquals(List.of("right_arm", "left_arm"), transforms.stream().map(AnimationTransform::target).toList());
                for (int i = 0; i < 2; i++) {
                    var expected = i == 0 ? right : left;
                    var actual = transforms.get(i);
                    assertEquals(expected.xRot, actual.xRot());
                    assertEquals(expected.yRot, actual.yRot());
                    assertEquals(expected.zRot, actual.zRot());
                    assertEquals(AnimationTransform.Operation.SET, actual.operation());
                    assertFalse(actual.applyTranslation());
                }
            }
        }
    }
}
