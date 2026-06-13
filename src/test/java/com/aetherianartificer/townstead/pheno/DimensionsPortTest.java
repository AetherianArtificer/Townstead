package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.origin.port.ApoliBiEntityConditionTranslator;
import com.aetherianartificer.townstead.origin.port.ApoliConditionTranslator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Apugli dimensions / compare_dimensions (live bounding-box size) port onto the native conditions. */
class DimensionsPortTest {

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void entityDimensionsMapsAxisAndThreshold() {
        JsonObject out = ApoliConditionTranslator.translate(
                obj("{ 'type':'apugli:dimensions', 'dimensions':['height'], 'comparison':'>=', 'compare_to':2.0 }"));
        assertNotNull(out);
        assertEquals("pheno:dimensions", out.get("type").getAsString());
        assertEquals("height", out.get("which").getAsString());
        assertEquals(2.0, out.get("min").getAsDouble(), "comparison >= compare_to becomes min");
        assertFalse(out.has("max"));
    }

    @Test
    void bothAxesWhenTheSetHasWidthAndHeight() {
        JsonObject out = ApoliConditionTranslator.translate(
                obj("{ 'type':'apugli:dimensions', 'dimensions':['width','height'], 'comparison':'<', 'compare_to':1.0 }"));
        assertNotNull(out);
        assertEquals("both", out.get("which").getAsString());
        assertEquals(1.0, out.get("max").getAsDouble(), "comparison < compare_to becomes max");
    }

    @Test
    void compareDimensionsCarriesAxisAndOperator() {
        JsonObject out = ApoliBiEntityConditionTranslator.translate(
                obj("{ 'type':'apugli:compare_dimensions', 'dimensions':['width'], 'comparison':'>' }"));
        assertNotNull(out);
        assertEquals("pheno:compare_dimensions", out.get("type").getAsString());
        assertEquals("width", out.get("which").getAsString());
        assertEquals(">", out.get("comparison").getAsString());
    }

    @Test
    void scaleMapsPehkuiScaleTypeToAxisAndThreshold() {
        JsonObject out = ApoliConditionTranslator.translate(
                obj("{ 'type':'apugli:scale', 'scale_type':'pehkui:height', 'comparison':'>=', 'compare_to':1.5 }"));
        assertNotNull(out);
        assertEquals("pheno:scale", out.get("type").getAsString());
        assertEquals("height", out.get("which").getAsString());
        assertEquals(1.5, out.get("min").getAsDouble());
    }

    @Test
    void compareScalesCarriesAxisAndOperator() {
        JsonObject out = ApoliBiEntityConditionTranslator.translate(
                obj("{ 'type':'apugli:compare_scales', 'scale_types':['pehkui:width'], 'comparison':'>' }"));
        assertNotNull(out);
        assertEquals("pheno:compare_scales", out.get("type").getAsString());
        assertEquals("width", out.get("which").getAsString());
        assertEquals(">", out.get("comparison").getAsString());
    }
}
