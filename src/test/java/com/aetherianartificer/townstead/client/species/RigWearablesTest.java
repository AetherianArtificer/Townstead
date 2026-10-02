package com.aetherianartificer.townstead.client.species;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RigWearablesTest {
    @Test void shoeFitKeepsHeightAndGroundContactThenRestoresArmorPose() {
        var leg = new ModelPart(List.of(), Map.of());
        leg.setPos(2,21.9F,-.5F);
        leg.xScale=.75F; leg.yScale=1F/6; leg.zScale=.75F;
        var restore = RigWearables.fitPart(leg, new float[]{2,21.9F,-.5F,0,0,0},
                new com.aetherianartificer.townstead.root.rig.RigDefinition.Adjust(
                        new float[]{0,-5.1F,0},new float[3],1,new float[]{.75F,.6F,.75F}));
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        leg.translateAndRotate(pose);
        var toeTop = pose.last().pose().transformPosition(new org.joml.Vector3f(0,9F/16,-3F/16));
        var sole = pose.last().pose().transformPosition(new org.joml.Vector3f(0,12F/16,-3F/16));
        assertEquals(24F/16, sole.y, .0001F, "sole stays at the rig's ground plane");
        assertEquals(1.8F/16, sole.y-toeTop.y, .0001F, "shoe must not inherit compressed armour shins");
        restore.run();
        assertEquals(21.9F,leg.y); assertEquals(1F/6,leg.yScale);
    }

    @Test void sashFitCompressesHeightWithoutNarrowingTheBelt() {
        var body = new ModelPart(List.of(), Map.of());
        var restore = RigWearables.fitPart(body,new float[]{0,22,-1,0,0,0},
                new com.aetherianartificer.townstead.root.rig.RigDefinition.Adjust(
                        new float[]{0,-6,.5F}, new float[3],1,new float[]{1.05F,.3F,1.9F}));
        assertEquals(8.4F,8*body.xScale,.0001F);
        assertEquals(5F,24-(body.y+10*body.yScale),.0001F);
        assertEquals(2.3F,24-(body.y+19*body.yScale),.0001F,"sash tail stays above the feet");
        restore.run();
        assertEquals(1,body.yScale);
    }
    @Test void fittedWearableScalesDoNotLeakToTheNextEntityUsingTheSameHost() {
        var parts = new HashMap<String,ModelPart>();
        for (String name : List.of("head","hat","body","left_arm","right_arm","left_leg","right_leg"))
            parts.put(name,new ModelPart(List.of(), Map.of()));
        var host = new HumanoidModel<>(new ModelPart(List.of(),parts));
        host.head.xScale = 1.2F; host.body.yScale = .9F;
        RigWearables.suppressHostBoots(host,true);
        host.head.xScale=.5F; host.body.yScale=.5F; host.leftArm.zScale=.4F;
        RigWearables.restoreHostScales(host);
        assertEquals(1.2F,host.head.xScale); assertEquals(.9F,host.body.yScale);
        assertEquals(1F,host.leftLeg.xScale); assertEquals(1F,host.leftArm.zScale);
        host.body.yScale=.8F;
        RigWearables.restoreHostScales(host);
        assertEquals(.8F,host.body.yScale,"a later normal pose must not be overwritten by old snapshots");
    }
}
