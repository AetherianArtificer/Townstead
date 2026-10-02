package com.aetherianartificer.townstead.root.attachment;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
class AttachmentAnimationTest {
    @Test void ribbitVectorFramesRetainMotionAndEasing() {
        var warnings = new ArrayList<String>();
        var clips = AttachmentAnimation.parse(JsonParser.parseString("""
            {"animations":{"walk":{"loop":true,"animation_length":1,"bones":{"body":{
              "position":{"0":{"vector":[0,0,0]},"1":{"vector":[0,8,0],"easing":"easeInCubic"}}
            }}}}}
            """).getAsJsonObject(), warnings);
        var clip = clips.get("walk");
        assertEquals(AttachmentAnimation.Loop.LOOP, clip.loop);
        assertEquals(20, clip.lengthTicks);
        assertArrayEquals(new float[]{0,1,0}, clip.bones.get("body").position().sample(10,new float[3]), .0001f);
        assertTrue(warnings.isEmpty());
    }
    @Test void supportsConstantVectorsAndExistingBedrockFrames() {
        var clip = AttachmentAnimation.parse(JsonParser.parseString("""
          {"animations":{"pose":{"bones":{"arm":{"rotation":{"vector":[10,20,30]},
          "position":{"0":{"pre":[1,2,3],"post":[4,5,6]},"1":[8,9,10]},"scale":1.5}}}}}
          """).getAsJsonObject(), null).get("pose");
        var arm = clip.bones.get("arm");
        assertArrayEquals(new float[]{10,20,30}, arm.rotation().sample(5,new float[3]));
        assertArrayEquals(new float[]{6,7,8}, arm.position().sample(10,new float[3]));
        assertArrayEquals(new float[]{1.5f,1.5f,1.5f}, arm.scale().sample(5,new float[3]));
    }
}
