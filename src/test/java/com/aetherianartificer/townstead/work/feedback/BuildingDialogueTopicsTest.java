package com.aetherianartificer.townstead.work.feedback;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class BuildingDialogueTopicsTest {
    @Test
    void topicsAreMatchedByExactBuildingTypeAndSubject() {
        BuildingDialogueTopics.replaceAll(Map.of(
                "example:workshop", Set.of("industry", "market")));

        assertTrue(BuildingDialogueTopics.matches("example:workshop", "industry"));
        assertFalse(BuildingDialogueTopics.matches("example:workshop", "pastoral"));
        assertFalse(BuildingDialogueTopics.matches("example:other", "industry"));
    }
}
