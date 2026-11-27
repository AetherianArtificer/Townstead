package com.aetherianartificer.townstead.client.attachment.geo;

import com.aetherianartificer.townstead.root.attachment.AttachmentGeoSegmenter;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentGeoSegmenterTest {

    /** A single-bone hanging tail (the testing-pack shape): pivot at the top, cube 2x8x2 below it. */
    private static JsonObject hangingTail() {
        return JsonParser.parseString("""
                { "minecraft:geometry": [ { "description": { "texture_width": 16, "texture_height": 16 },
                  "bones": [ { "name": "tail", "pivot": [0, 0, 0],
                    "cubes": [ { "origin": [-1, -8, 0], "size": [2, 8, 2], "uv": [0, 0] } ] } ] } ] }
                """).getAsJsonObject();
    }

    private static JsonArray bonesOf(JsonObject root) {
        return root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
    }

    @Test
    void slicesIntoChainedSegmentsRootedAtPivot() {
        JsonObject out = AttachmentGeoSegmenter.segment(hangingTail(), "tail", 4, "auto");
        JsonArray bones = bonesOf(out);
        assertEquals(4, bones.size());
        assertEquals("tail", bones.get(0).getAsJsonObject().get("name").getAsString());
        for (int i = 1; i < 4; i++) {
            JsonObject bone = bones.get(i).getAsJsonObject();
            assertEquals("tail__seg" + (i + 1), bone.get("name").getAsString());
            assertEquals(i == 1 ? "tail" : "tail__seg" + i, bone.get("parent").getAsString());
            // Root is at the pivot end (top, y=0), so cut planes descend: -2, -4, -6.
            assertEquals(-2f * i, bone.getAsJsonArray("pivot").get(1).getAsFloat(), 1e-4);
        }
        for (int i = 0; i < 4; i++) {
            JsonArray cubes = bones.get(i).getAsJsonObject().getAsJsonArray("cubes");
            assertEquals(1, cubes.size());
            JsonObject cube = cubes.get(0).getAsJsonObject();
            assertEquals(2f, cube.getAsJsonArray("size").get(1).getAsFloat(), 1e-4);
            JsonObject faces = cube.getAsJsonObject("uv");
            // Caps only on the outermost slices: top cap rides the root, bottom cap the tip.
            assertEquals(i == 0, faces.has("up"), "up cap on slice " + i);
            assertEquals(i == 3, faces.has("down"), "down cap on slice " + i);
            assertTrue(faces.has("north") && faces.has("south") && faces.has("east") && faces.has("west"));
        }
        // Slice 0 is the TOP quarter (y -2..0): its side-face V strip starts at the top of
        // the box side band (v = sz = 2) and spans 2 texels.
        JsonObject rootNorth = bones.get(0).getAsJsonObject().getAsJsonArray("cubes")
                .get(0).getAsJsonObject().getAsJsonObject("uv").getAsJsonObject("north");
        assertEquals(2f, rootNorth.getAsJsonArray("uv").get(1).getAsFloat(), 1e-4);
        assertEquals(2f, rootNorth.getAsJsonArray("uv_size").get(1).getAsFloat(), 1e-4);
    }

    @Test
    void missingBoneReturnsUnchangedCopy() {
        JsonObject in = hangingTail();
        JsonObject out = AttachmentGeoSegmenter.segment(in, "nope", 4, "auto");
        assertEquals(in, out);
        assertFalse(out == in);
    }

    @Test
    void rotatedCubeRidesWholeInItsSlice() {
        JsonObject in = hangingTail();
        JsonObject cube = bonesOf(in).get(0).getAsJsonObject().getAsJsonArray("cubes").get(0).getAsJsonObject();
        JsonArray rotation = new JsonArray();
        rotation.add(0);
        rotation.add(0);
        rotation.add(15);
        cube.add("rotation", rotation);
        JsonObject out = AttachmentGeoSegmenter.segment(in, "tail", 4, "auto");
        JsonArray bones = bonesOf(out);
        assertEquals(4, bones.size());
        int totalCubes = 0;
        for (int i = 0; i < 4; i++) totalCubes += bones.get(i).getAsJsonObject().getAsJsonArray("cubes").size();
        assertEquals(1, totalCubes, "unsliceable cube must land whole in exactly one slice");
    }
}
