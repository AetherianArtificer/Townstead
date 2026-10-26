package com.aetherianartificer.townstead.client.animation.emote;

import com.aetherianartificer.townstead.client.animation.nativeclip.BedrockPerformanceClip;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.google.gson.JsonParser;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class GenericNativePoseTest {
    private final ModelPart arm, leg, body, root;

    GenericNativePoseTest() throws Exception {
        var bake = com.aetherianartificer.townstead.client.species.RigModels.class
                .getDeclaredMethod("bakeGeometry", com.google.gson.JsonObject.class);
        bake.setAccessible(true);
        root = (ModelPart) bake.invoke(null, JsonParser.parseString("""
                {"minecraft:geometry":[{"description":{"texture_width":16,"texture_height":16},"bones":[
                {"name":"main","pivot":[0,0,0]},
                {"name":"arm","parent":"main","pivot":[0,0,0]},
                {"name":"leg","parent":"main","pivot":[0,0,0]}]}]}
                """).getAsJsonObject());
        body = root.getChild("main"); arm = body.getChild("arm"); leg = body.getChild("leg");
    }

    private RigDefinition.EmoteChannel channel(String name, float gain, boolean absolute) {
        return new RigDefinition.EmoteChannel(name, absolute ? RigDefinition.EmoteMode.ABSOLUTE : RigDefinition.EmoteMode.ADDITIVE,
                new int[]{0,1,2}, new float[]{1,1,1}, new float[3], new float[]{gain,gain,gain}, true,
                new float[]{-1,-1,-1}, new float[]{1,1,1}, List.of(), false, 1F);
    }
    private RigDefinition.EmoteMap map(boolean absolute) {
        return new RigDefinition.EmoteMap(RigDefinition.BodyMotion.FULL,
                Map.of("right_arm", channel("arm", .5F, absolute), "right_leg", channel("leg", .5F, absolute),
                        "head", channel("main", .12F, absolute)), RigDefinition.EmotePolicy.NONE);
    }
    private BedrockPerformanceClip clip(String loop, String bones) {
        return BedrockPerformanceClip.parse(JsonParser.parseString("""
                {"format_version":"1.8.0","animations":{"reaction":{"animation_length":2,"loop":%s,"bones":%s}}}
                """.formatted(loop,bones)).getAsJsonObject()).get("reaction");
    }
    @Test void nativeReactionFindsNestedLimbsAndRetargetsWithoutApplyingRootOrRigidElbows() {
        arm.xRot = .2F;
        var clip = clip("false", """
                {"right_arm":{"rotation":[60,0,0]},"right_forearm":{"rotation":[80,0,0]},
                 "head":{"rotation":[30,0,0]},"root":{"position":[0,10,0]}}
                """);
        GenericNativePose.apply(root,map(false),clip,10,100,1,false,false);
        assertEquals(.2F + Math.toRadians(30), arm.xRot, .0001);
        assertEquals(Math.toRadians(3.6), body.xRot, .0001);
        assertEquals(0, root.y);
    }
    @Test void gaitKeepsLegsAndExpiredOneShotsReleaseAllChannels() {
        var clip = clip("false", """
                {"right_arm":{"rotation":[60,0,0]},"right_leg":{"rotation":[60,0,0]}}
                """);
        leg.xRot=.4F;
        GenericNativePose.apply(root,map(false),clip,10,100,0,false,false);
        assertEquals(.4F,leg.xRot);
        float before=arm.xRot;
        GenericNativePose.apply(root,map(false),clip,41,100,1,false,false);
        assertEquals(before,arm.xRot);
    }
    @Test void positionOnlyTrackDoesNotEraseRotationAndHoldEndsWithPlayback() {
        var clip=clip("\"hold_on_last_frame\"", """
                {"right_arm":{"position":[2,4,6]}}
                """);
        arm.xRot=.7F;
        GenericNativePose.apply(root,map(true),clip,45,100,1,false,false);
        assertEquals(.7F,arm.xRot);
        assertEquals(1,arm.x); assertEquals(-2,arm.y); assertEquals(3,arm.z);
        arm.x=8;
        GenericNativePose.apply(root,map(true),clip,45,0,1,false,false);
        assertEquals(8,arm.x);
    }
}
