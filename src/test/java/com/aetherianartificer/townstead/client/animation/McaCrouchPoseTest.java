package com.aetherianartificer.townstead.client.animation;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.world.entity.LivingEntity;
import com.aetherianartificer.townstead.TestBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McaCrouchPoseTest {
    @BeforeAll static void bootstrap() { TestBootstrap.ensure(); }

    @Test void fallbackRestoresActualCrouchGeometryAndDoesNotDoubleBendTheArms() {
        var root = LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0), 64, 64).bakeRoot();
        var model = new HumanoidModel<LivingEntity>(root);
        model.crouching = true;
        model.leftArm.xRot = 0.4f;
        model.rightArm.xRot = 0.4f;
        McaAnimationBridge.restoreHostCrouch(model);
        assertEquals(0.5f, model.body.xRot);
        assertEquals(3.2f, model.body.y);
        assertEquals(4.2f, model.head.y);
        assertEquals(5.2f, model.leftArm.y);
        assertEquals(5.2f, model.rightArm.y);
        assertEquals(12.2f, model.leftLeg.y);
        assertEquals(4f, model.leftLeg.z);
        assertEquals(4f, model.rightLeg.z);
        assertEquals(0.4f, model.leftArm.xRot);
        assertEquals(0.4f, model.rightArm.xRot);
    }
}
