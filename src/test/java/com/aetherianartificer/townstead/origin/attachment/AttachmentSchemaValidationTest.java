package com.aetherianartificer.townstead.origin.attachment;

import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostics;
import com.aetherianartificer.townstead.pheno.lang.schema.NodeSchema;
import com.aetherianartificer.townstead.pheno.lang.validate.PhenoValidator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachmentSchemaValidationTest {

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    private static Diagnostics validate(NodeSchema schema, String json) {
        Diagnostics diag = new Diagnostics();
        PhenoValidator.validateData(ResourceLocation.tryParse("townstead_test:a"), obj(json), schema, diag);
        return diag;
    }

    private static boolean has(Diagnostics diag, String jsonPath, String messageFragment) {
        for (Diagnostic d : diag.all()) {
            if (d.jsonPath().equals(jsonPath) && d.message().contains(messageFragment)) return true;
        }
        return false;
    }

    @Test
    void missingRequiredGeometryIsFlagged() {
        Diagnostics diag = validate(AttachmentSchemas.ATTACHMENT,
                "{ 'texture':'townstead_origins:ears' }");
        assertTrue(has(diag, "$.geometry", "Missing required field"),
                "an attachment without geometry must be flagged at $.geometry");
    }

    @Test
    void nonNumericScaleIsFlagged() {
        Diagnostics diag = validate(AttachmentSchemas.ATTACHMENT,
                "{ 'geometry':'a', 'texture':'b', 'scale':'big' }");
        assertTrue(has(diag, "$.scale", "number"),
                "a non-numeric scale must be flagged at $.scale");
    }

    @Test
    void wellFormedAttachmentHasNoFieldDiagnostics() {
        Diagnostics diag = validate(AttachmentSchemas.ATTACHMENT,
                "{ 'geometry':'a', 'texture':'b', 'target':{ 'tag':'ear' },"
                        + " 'offset':[0,0.5,0], 'scale':1.0, 'tint':'#FFFFFF' }");
        for (Diagnostic d : diag.all()) {
            assertFalse(d.message().contains("Missing required field") || d.message().contains("Expected"),
                    "well-formed attachment should produce no field diagnostics, got: " + d.render());
        }
    }

    @Test
    void pointTagsMustBeAList() {
        Diagnostics diag = validate(AttachmentSchemas.ATTACHMENT_POINT,
                "{ 'bone':'head', 'tags':'ear' }");
        assertTrue(has(diag, "$.tags", "list"),
                "a scalar tags value must be flagged at $.tags");
    }
}
