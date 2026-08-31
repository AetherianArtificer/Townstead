package com.aetherianartificer.townstead.client.gui.career;

import com.aetherianartificer.townstead.profession.career.CareerGraphS2CPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CareerLayoutTest {

    @Test
    void pathLevelKeepsAuthoredOrderOnOneRankRow() {
        CareerGraphS2CPayload.PathTag path =
                new CareerGraphS2CPayload.PathTag("example", "Example", false);
        Map<String, int[]> positions = CareerLayout.placeCell(List.of(
                skill("townstead:cook/example/zeta", path),
                skill("townstead:cook/example/alpha", path),
                skill("townstead:cook/example/middle", path)), 42, 10, 58);

        int[] first = positions.get("townstead:cook/example/zeta");
        int[] second = positions.get("townstead:cook/example/alpha");
        int[] third = positions.get("townstead:cook/example/middle");

        assertTrue(first[0] < second[0]);
        assertTrue(second[0] < third[0]);
        assertEquals(first[1], second[1]);
        assertEquals(second[1], third[1]);
    }

    @Test
    void everySkillUsesTheSameAuthoredFrameSize() {
        CareerGraphS2CPayload.PathTag path =
                new CareerGraphS2CPayload.PathTag("chef", "Chef", false);
        assertEquals(NodeArt.markSize(skill("townstead:cook/chef/first", path)),
                NodeArt.markSize(skill("townstead:cook/chef/second", path)),
                "selection and parent position must not resize one option in a peer row");
    }

    private static CareerGraphS2CPayload.Node skill(
            String id, CareerGraphS2CPayload.PathTag path) {
        return new CareerGraphS2CPayload.Node(
                id, "townstead:cook", "townstead:cook",
                CareerGraphS2CPayload.KIND_SKILL, CareerGraphS2CPayload.STATE_READY,
                id, "", "minecraft:stick",
                1, 0, 0, 0, 0, 0,
                false, false, false, "", "", List.of(), List.of(),
                "Apprentice", 1, "", "", List.of(), List.of(), path);
    }
}
