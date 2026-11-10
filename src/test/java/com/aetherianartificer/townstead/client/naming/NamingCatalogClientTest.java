package com.aetherianartificer.townstead.client.naming;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamingCatalogClientTest {

    @Test void bucketIdsReadAsTitleCaseWords() {
        assertEquals("Iceland", NamingCatalogClient.prettify("iceland"));
        assertEquals("New Zealand", NamingCatalogClient.prettify("new_zealand"));
    }

    @Test void aSearchMatchesTheNameOrTheId() {
        assertTrue(NamingCatalogClient.matches("", "Great Britain", "mca:greatbritain"));
        assertTrue(NamingCatalogClient.matches(" brit ", "Great Britain", "mca:greatbritain"));
        assertTrue(NamingCatalogClient.matches("GREATB", "Great Britain", "mca:greatbritain"));
        assertFalse(NamingCatalogClient.matches("japan", "Great Britain", "mca:greatbritain"));
    }

    @Test void onlyTheLastPathSegmentIsShown() {
        assertEquals("Old Coast", NamingCatalogClient.prettify("northern/old_coast"));
    }
}
