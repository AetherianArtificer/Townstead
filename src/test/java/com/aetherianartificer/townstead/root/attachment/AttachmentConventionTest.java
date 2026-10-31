package com.aetherianartificer.townstead.root.attachment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** v2 attachment files read in Blockbench space and land on exactly v1's internal values. */
class AttachmentConventionTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    private static com.google.gson.JsonElement arr(String s) {
        return JsonParser.parseString(s);
    }

    @Test
    void v1FilesAreLeftAlone() {
        JsonObject v1 = json("""
                {"schema": "townstead:attachment/v1", "offset": [0, 24, 2], "rotation": [10, 20, 30]}""");
        assertSame(v1, AttachmentConvention.normalize(v1));
    }

    @Test
    void v2PlacementsBecomeTheInternalForm() {
        JsonObject v2 = json("""
                {"schema": "townstead:attachment/v2",
                 "offset": [1, -24, 2],
                 "rotation": [10, 20, 30],
                 "stages": {"baby": {"offset": [0, 3, 0], "scale": 0.5}},
                 "poses": {
                   "gliding": {"rotation": [5, 0, 0], "bones": {"left_wing": {"rotation": [10, 20, -65]}}},
                   "when": [{"if": {"type": "pheno:sneaking"}, "bones": {"tail": {"rotation": [0, 0, 15]}}}]
                 }}""");
        JsonObject internal = AttachmentConvention.normalize(v2);
        assertEquals(arr("[1, 24, 2]"), internal.get("offset"), "Y up becomes Minecraft's Y down");
        assertEquals(arr("[-10, 20, -30]"), internal.get("rotation"), "turns about X and Z reverse with Y");
        assertEquals(arr("[0, -3, 0]"), internal.getAsJsonObject("stages").getAsJsonObject("baby").get("offset"));
        JsonObject gliding = internal.getAsJsonObject("poses").getAsJsonObject("gliding");
        assertEquals(arr("[-5, 0, 0]"), gliding.get("rotation"));
        assertEquals(arr("[-10, -20, -65]"),
                gliding.getAsJsonObject("bones").getAsJsonObject("left_wing").get("rotation"),
                "bone rotations take Blockbench's own .geo.json export map");
        assertEquals(arr("[0, 0, 15]"), internal.getAsJsonObject("poses").getAsJsonArray("when").get(0)
                .getAsJsonObject().getAsJsonObject("bones").getAsJsonObject("tail").get("rotation"));
        assertEquals(arr("[1, -24, 2]"), v2.get("offset"), "the source file is not touched");
    }

    @Test
    void conversionsAreTheirOwnInverse() {
        float[] v = {3.5f, -7f, 12f};
        assertArrayEquals(v, AttachmentConvention.offset(AttachmentConvention.offset(v)));
        assertArrayEquals(v, AttachmentConvention.rotation(AttachmentConvention.rotation(v)));
        assertArrayEquals(v, AttachmentConvention.boneRotation(AttachmentConvention.boneRotation(v)));
    }

    @Test
    void pointFilesTakeV2Too() {
        JsonObject point = json("""
                {"schema": "townstead:attachment_point/v2", "bone": "head", "offset": [0, 8, 0], "rotation": [0, 0, 90]}""");
        JsonObject internal = AttachmentConvention.normalize(point);
        assertEquals(arr("[0, -8, 0]"), internal.get("offset"));
        assertEquals(arr("[0, 0, -90]"), internal.get("rotation"));
    }
}
