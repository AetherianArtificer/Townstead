package com.aetherianartificer.townstead.root.attachment;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PieceRootsTest {

    /** Each bone as name, parent (or empty), and whether it has cubes. */
    private static JsonObject model(String... bones) {
        StringBuilder json = new StringBuilder("{\"minecraft:geometry\":[{\"bones\":[");
        for (int i = 0; i < bones.length; i += 3) {
            if (i > 0) json.append(',');
            json.append("{\"name\":\"").append(bones[i]).append('"');
            if (!bones[i + 1].isEmpty()) json.append(",\"parent\":\"").append(bones[i + 1]).append('"');
            if (bones[i + 2].equals("cubes")) json.append(",\"cubes\":[{}]");
            json.append('}');
        }
        return JsonParser.parseString(json.append("]}]}").toString()).getAsJsonObject();
    }

    @Test
    void wrappersLeadToTheBranches() {
        JsonObject wings = model("Body", "", "", "Wings", "Body", "", "Left", "Wings", "cubes", "Left2", "Left", "cubes",
                "Right", "Wings", "cubes", "Marker", "Wings", "");
        assertEquals(List.of("Left", "Right"), HitShapes.pieceRoots(wings));
    }

    @Test
    void separateRootsArePieces() {
        assertEquals(List.of("tusk_l", "tusk_r"), HitShapes.pieceRoots(model("tusk_l", "", "cubes", "tusk_r", "", "cubes")));
    }

    @Test
    void aBoneWithCubesHoldsWhatItCarries() {
        assertEquals(List.of("tail"), HitShapes.pieceRoots(model("tail", "", "cubes", "a", "tail", "cubes", "b", "tail", "cubes")));
    }

    @Test
    void boxesKnowTheirPiece() {
        JsonObject pair = model("Left", "", "cubes", "Left2", "Left", "cubes", "Right", "", "cubes");
        Map<String, Integer> groups = HitShapes.groups(pair);
        assertArrayEquals(new int[]{0, 0, 1}, HitShapes.groupPieces(pair, groups, HitShapes.pieceRoots(pair)));
    }
}
