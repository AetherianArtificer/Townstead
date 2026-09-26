package com.aetherianartificer.townstead.client.animation.nativeclip;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class BedrockPerformanceClipTest {
    @Test void aSipPlaysOnceAndReleasesTheArmAfterward() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/townstead_performance/animations/townstead/sip.animation.json")) {
            assertNotNull(stream);
            var clip = BedrockPerformanceClip.parse(JsonParser.parseReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject()).get("animation.sip");
            assertEquals(BedrockPerformanceClip.Loop.ONCE, clip.loop());
            assertEquals(0F, BedrockPerformanceSampler.blend(clip, clip.durationTicks() + 1, 500));
        }
    }

    @ParameterizedTest
    @CsvSource({"wave,32", "clap,48", "nod,20", "shrug,30", "cheer,40", "cheer_excited,40", "startled,30", "yawn,44", "shake_head,24", "point,30", "toast,28"})
    void loadsTheActualBlockbenchExport(String name, float duration) throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/townstead_performance/animations/townstead/" + name + ".animation.json")) {
            assertNotNull(stream);
            var clips = BedrockPerformanceClip.parse(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            var wave = clips.get("animation." + name);
            assertNotNull(wave);
            assertEquals(duration, wave.durationTicks());
            assertEquals(BedrockPerformanceClip.Loop.ONCE, wave.loop());
            for (var track : wave.bones().values()) {
                if (track.rotation() != null) assertArrayEquals(new float[]{0,0,0}, track.rotation().sample(duration), .001F);
                if (track.position() != null) assertArrayEquals(new float[]{0,0,0}, track.position().sample(duration), .001F);
                if (track.scale() != null) assertArrayEquals(new float[]{1,1,1}, track.scale().sample(duration), .001F);
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"laugh,24", "laugh_demure,24", "beckon,32", "animated_story,48", "attentive,36", "eat,28", "recline,60", "recline_lounger,60", "relaxed_lean,48", "stool_sit,40", "tap_foot,16", "whisper,40", "ponder,48", "cry,40", "shiver,40", "sweat,60"})
    void loadsLoopingExportsWithMatchingEndpointPoses(String name, float duration) throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/townstead_performance/animations/townstead/" + name + ".animation.json")) {
            assertNotNull(stream);
            var clip = BedrockPerformanceClip.parse(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject()).get("animation." + name);
            assertNotNull(clip);
            assertEquals(duration, clip.durationTicks());
            assertEquals(BedrockPerformanceClip.Loop.LOOP, clip.loop());
            for (var bone : clip.bones().values()) {
                if (bone.rotation() != null) assertArrayEquals(bone.rotation().sample(0), bone.rotation().sample(duration), .001F);
                if (bone.position() != null) assertArrayEquals(bone.position().sample(0), bone.position().sample(duration), .001F);
            }
        }
    }

    private BedrockPerformanceClip parse(String body) {
        return BedrockPerformanceClip.parse(JsonParser.parseString("{\"format_version\":\"1.8.0\",\"animations\":{\"animation.wave\":"
                + body + "}}").getAsJsonObject()).get("animation.wave");
    }

    @Test void preservesFractionalTimingAndSortsKeys() {
        var clip = parse("""
                {"animation_length":1.6,"bones":{"right_arm":{"rotation":{
                "0.175":[20,0,0],"0.025":[0,0,0]}}}}
                """);
        assertEquals(32F, clip.durationTicks());
        assertEquals(10F, clip.bones().get("right_arm").rotation().sample(2)[0], .0001F);
        assertEquals(BedrockPerformanceClip.Loop.ONCE, clip.loop());
    }

    @Test void honorsPrePostDiscontinuitiesAtExactTime() {
        var track = parse("""
                {"bones":{"head":{"scale":{"0":1,"0.5":{"pre":1,"post":2},"1":3}}}}
                """).bones().get("head").scale();
        assertArrayEquals(new float[]{1,1,1}, track.sample(9.99F), .0001F);
        assertArrayEquals(new float[]{2,2,2}, track.sample(10), .0001F);
        assertArrayEquals(new float[]{2.5F,2.5F,2.5F}, track.sample(15), .0001F);
    }

    @Test void interpolatesCatmullRomWithoutQuantizingFrames() {
        var channel = parse("""
                {"bones":{"head":{"rotation":{"0":[0,0,0],"0.5":{"post":[10,0,0],"lerp_mode":"catmullrom"},
                "1":{"post":[10,0,0],"lerp_mode":"catmullrom"},"1.5":[0,0,0]}}}}
                """).bones().get("head").rotation();
        assertEquals(11.25F, channel.sample(15)[0], .0001F);
    }

    @Test void supportsConstantUniformScaleAndHold() {
        var clip = parse("""
                {"loop":"hold_on_last_frame","bones":{"torso":{"scale":[1.2]}}}
                """);
        assertEquals(BedrockPerformanceClip.Loop.HOLD, clip.loop());
        assertArrayEquals(new float[]{1.2F,1.2F,1.2F}, clip.bones().get("body").scale().sample(100), .0001F);
    }

    @Test void rejectsUnsupportedInputsInsteadOfSilentlyChangingTheMotion() {
        for (String body : new String[]{
                "{\"bones\":{\"head\":{\"rotation\":[\"query.anim_time\",0,0]}}}",
                "{\"bones\":{\"head\":{\"rotation\":{\"NaN\":[0,0,0]}}}}",
                "{\"bones\":{\"head\":{\"rotation\":{\"0\":[0,0,0],\"0.0\":[1,0,0]}}}}",
                "{\"animation_length\":0.1,\"bones\":{\"head\":{\"rotation\":{\"1\":[0,0,0]}}}}",
                "{\"bones\":{\"right_forearm\":{\"rotation\":[0,10,0]}}}",
                "{\"bones\":{\"typo\":{\"rotation\":[0,0,0]}}}"
        }) assertThrows(IllegalArgumentException.class, () -> parse(body), body);
    }

    @Test void keepsFullExportedNames() {
        assertEquals("test:person.wave", NativeClipRegistry.bedrockId("test", "animation.person.wave").toString());
        assertEquals("test:wave", NativeClipRegistry.bedrockId("test", "animation.wave").toString());
    }
}
