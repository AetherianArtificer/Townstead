package com.aetherianartificer.townstead.block;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldPostPersistenceContractTest {
    @Test
    void dropsCarryTheirCompleteBlockEntityConfiguration() throws IOException {
        String block = source("src/main/java/com/aetherianartificer/townstead/block/FieldPostBlock.java");
        String blockEntity = source("src/main/java/com/aetherianartificer/townstead/block/FieldPostBlockEntity.java");

        assertTrue(block.contains("LootContextParams.BLOCK_ENTITY"),
                "Field Post drops must read the live block entity from the loot context");
        assertTrue(block.contains("blockItem.getBlock() instanceof FieldPostBlock"),
                "Only actual Field Post item drops should receive the plan");
        assertTrue(block.contains("fieldPost.saveToItem(drop"),
                "The native BlockItem data path must carry the plan into the placed block entity");

        for (String savedField : new String[]{
                "patternId", "tierCap", "radius", "priority", "autoSeedMode", "seedFilter",
                "waterEnabled", "maxWaterCells", "groomEnabled", "groomRadius",
                "rotationEnabled", "rotationPatterns", "rotationIndex", "ownerUuid", "ownerName"}) {
            assertTrue(blockEntity.contains("\"" + savedField + "\""), savedField);
        }
        assertTrue(blockEntity.contains("CellPlan.save(tag, cellPlan)"),
                "The painted cell plan is the essential part of the preserved configuration");
        assertTrue(blockEntity.contains("cellPlan = CellPlan.load(tag)"),
                "A placed Field Post must restore its painted cell plan");
    }

    private static String source(String relativePath) throws IOException {
        Path relative = Path.of(relativePath);
        for (Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath();
             root != null; root = root.getParent()) {
            Path path = root.resolve(relative);
            if (Files.isRegularFile(path)) {
                return Files.readString(path, StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Unable to locate " + relativePath);
    }
}
