package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RigArmPoseTest {
    private float wristX(ModelPart arm, boolean right) {
        var pose = new PoseStack();
        arm.translateAndRotate(pose);
        return pose.last().pose().transformPosition(new Vector3f((right ? -.1F : .1F)/16,3F/16,0)).x*16;
    }

    @Test void bothWristsClearTheTorsoAtIdleCrouchAndCarryWithoutAccumulating() throws Exception {
        var right = new ModelPart(List.of(), Map.of());
        var left = new ModelPart(List.of(), Map.of());
        right.setInitialPose(PartPose.offsetAndRotation(-3.9F,18.4F,0,0,0,(float)Math.toRadians(-27.5)));
        left.setInitialPose(PartPose.offsetAndRotation(3.9F,18.4F,0,0,0,(float)Math.toRadians(27.5)));
        var root = new ModelPart(List.of(),Map.of("right_arm",right,"left_arm",left));
        var model = new StaticRigModel<>(root);
        model.setupAnim(null,0,0,0,0,0);
        assertTrue(Math.abs(wristX(right,true)) < 4, "Uncorrected right wrist is inside the torso");
        assertTrue(Math.abs(wristX(left,false)) < 4, "Uncorrected left wrist is inside the torso");
        var neutral = new ArrayList<RigDefinition.PoseBone>();
        try (var stream = getClass().getResourceAsStream("/rig-armor/arm-neutral.json")) {
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : json.getAsJsonArray("bones")) {
                var b = entry.getAsJsonObject();
                float[] rotation = new float[3], offset = new float[3];
                for (int i=0;i<3;i++) {
                    rotation[i]=b.getAsJsonArray("rotation").get(i).getAsFloat();
                    offset[i]=b.getAsJsonArray("offset").get(i).getAsFloat();
                }
                neutral.add(new RigDefinition.PoseBone(b.get("bone").getAsString(),rotation,offset));
            }
        }
        for (int frame=0;frame<3;frame++) for (float idleRoll : new float[]{0,5,10})
            for (boolean crouch : new boolean[]{false,true}) for (boolean carrying : new boolean[]{false,true}) {
                model.setupAnim(null,0,0,0,0,0);
                RigClips.applyNeutral(root,neutral);
                right.zRot -= (float)Math.toRadians(idleRoll);
                left.zRot += (float)Math.toRadians(idleRoll);
                if (crouch) right.xRot = left.xRot = (float)Math.toRadians(-15);
                if (carrying) { RigClips.carryArm(right,false,0); RigClips.carryArm(left,false,0); }
                assertTrue(wristX(right,true)<-4.3F,"right hand must clear the chest shell");
                assertTrue(wristX(left,false)>4.3F,"left hand must clear the chest shell");
                assertEquals(-4.4F,right.x,.0001F); assertEquals(4.4F,left.x,.0001F);
            }
    }
}
