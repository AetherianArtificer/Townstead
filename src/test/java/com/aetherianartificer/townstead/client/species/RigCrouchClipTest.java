package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.root.attachment.AttachmentAnimation;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RigCrouchClipTest {
    @Test void crouchWalkingAlternatesFeetWhileKeepingTheCrouchBodyPose() throws Exception {
        try (var stream = getClass().getResourceAsStream("/rig-armor/crouch.animation.json")) {
            var warnings = new ArrayList<String>();
            var clips = AttachmentAnimation.parse(JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject(), warnings);
            assertTrue(warnings.isEmpty(), warnings.toString());
            var walk = clips.get("crouch_walk");
            var idle = clips.get("crouch");
            assertEquals(24F, walk.lengthTicks, .001F);
            assertArrayEquals(idle.bones.get("body").position().sample(0,new float[3]),
                    walk.bones.get("body").position().sample(12,new float[3]));
            var right = walk.bones.get("right_leg");
            var left = walk.bones.get("left_leg");
            assertTrue(right.rotation().sample(0,new float[3])[0] > 0);
            assertTrue(right.rotation().sample(12,new float[3])[0] < 0);
            assertEquals(.3F,right.position().sample(6,new float[3])[1],.001F);
            assertEquals(0F,left.position().sample(6,new float[3])[1],.001F);
            assertEquals(.3F,left.position().sample(18,new float[3])[1],.001F);
        }
    }
}
