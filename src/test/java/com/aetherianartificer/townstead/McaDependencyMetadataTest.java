package com.aetherianartificer.townstead;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McaDependencyMetadataTest {
    //? if neoforge {
    @Test
    void neoforgeMetadataAllowsOnlySnapshotAndTargetRelease() throws IOException {
        String version = System.getProperty("townstead.mcaVersion");
        String developmentVersion = System.getProperty("townstead.mcaDevelopmentVersion");
        assertNotNull(version, "Gradle must provide the pinned MCA version");
        assertNotNull(developmentVersion, "Gradle must provide the pinned MCA development version");

        String metadata;
        try (var stream = getClass().getResourceAsStream("/META-INF/neoforge.mods.toml")) {
            assertNotNull(stream, "processed NeoForge metadata must be on the test classpath");
            metadata = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(metadata.contains("mcaTargetVersion = \"" + version + "\""));

        int dependency = metadata.indexOf("modId = \"mca\"");
        assertTrue(dependency >= 0, "MCA dependency block is missing");
        String mcaBlock = metadata.substring(dependency);
        assertTrue(mcaBlock.contains("versionRange = \"[" + developmentVersion + "],[" + version + "]\""),
                "MCA must allow only the exact release and its pinned development binary");

        String guardProperties;
        try (var stream = getClass().getResourceAsStream("/META-INF/townstead-mca.properties")) {
            assertNotNull(stream, "processed MCA guard properties must be on the test classpath");
            guardProperties = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(guardProperties.contains("targetVersion=" + version));
        assertTrue(guardProperties.contains("developmentVersion=" + developmentVersion));
    }
    //?}
}
