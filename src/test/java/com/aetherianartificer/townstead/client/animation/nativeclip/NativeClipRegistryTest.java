package com.aetherianartificer.townstead.client.animation.nativeclip;

import com.aetherianartificer.townstead.client.animation.emote.ParsedEmote;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeClipRegistryTest {
    @Test
    void parsesCanonicalDegreesAndBendChannels() {
        ParsedEmote clip = NativeClipRegistry.parse(ResourceLocation.tryParse("test:stool"),
                JsonParser.parseString("""
                    {"schema":"townstead_performance:native_clip/v1","duration_ticks":40,"loop":true,
                     "bones":{"right_leg":{"rotation":[{"tick":0,"value":[-73,6,2]}],
                     "bend":[{"tick":0,"value":[85,0]}]}}}
                    """).getAsJsonObject());
        assertEquals(ParsedEmote.LoopType.LOOP, clip.loopType());
        assertEquals(40, clip.stopTick());
        assertEquals((float) Math.toRadians(-73), clip.bones().get("right_leg").xRot().get(0).value(), .0001F);
        assertTrue(clip.bones().get("right_leg").bendKeyed());
    }

    @Test
    void rejectsKeyframesPastDuration() {
        assertThrows(IllegalArgumentException.class, () -> NativeClipRegistry.parse(
                ResourceLocation.tryParse("test:bad"), JsonParser.parseString("""
                    {"schema":"townstead_performance:native_clip/v1","duration_ticks":10,
                     "bones":{"head":{"rotation":[{"tick":11,"value":[0,0,0]}]}}}
                    """).getAsJsonObject()));
    }
}
