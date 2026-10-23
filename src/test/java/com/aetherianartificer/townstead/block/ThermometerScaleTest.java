package com.aetherianartificer.townstead.block;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ThermometerScaleTest {
    @Test void winterTemperaturesUseTheColumnRatherThanBottomingOut() {
        assertEquals(0, ThermometerScale.at(-40));
        assertEquals(6, ThermometerScale.at(-10));
        assertEquals(8, ThermometerScale.at(0));
        assertEquals(12, ThermometerScale.at(20));
        assertEquals(18, ThermometerScale.at(50));
        assertEquals(0, ThermometerScale.at(-100));
        assertEquals(18, ThermometerScale.at(100));
    }

    @Test void stableNearStepBoundariesAndCatchesUpAfterLargeChanges() {
        assertEquals(6, ThermometerScale.update(6, -7.1f));
        assertEquals(7, ThermometerScale.update(6, -7f));
        assertEquals(7, ThermometerScale.update(7, -7.9f));
        assertEquals(6, ThermometerScale.update(7, -8f));
        assertEquals(16, ThermometerScale.update(6, 40));
        assertEquals(6, ThermometerScale.update(6, Float.NaN));
    }

    @Test void exportedColumnsFollowTheScaleAndRemainInsideTheGlass() throws Exception {
        double previous = 0;
        for (int level = 0; level <= ThermometerScale.MAX_LEVEL; level++) {
            String path = "assets/townstead/models/block/room_thermometer_level_" + level + ".json";
            try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
                int columns = 0;
                for (var item : model.getAsJsonArray("elements")) {
                    var element = item.getAsJsonObject();
                    String name = element.get("name").getAsString();
                    if (!name.equals("Red column") && !name.equals("Column glint")) continue;
                    double top = element.getAsJsonArray("to").get(1).getAsDouble();
                    assertEquals(5 + 7.25 * level / 18, top, 1e-6);
                    assertTrue(top >= 5 && top <= 12.5);
                    if (name.equals("Red column")) {
                        assertTrue(top > previous);
                        previous = top;
                    }
                    columns++;
                }
                assertEquals(2, columns);
            }
        }
    }
}
