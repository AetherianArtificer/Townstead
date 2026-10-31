package com.aetherianartificer.townstead.profession;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CareerPackSourceTest {

    @Test
    void profilePackRecognitionRequiresATownsteadProfessionDocument(@TempDir Path root)
            throws Exception {
        Path pack = root.resolve("installed-pack");
        Path profession = pack.resolve("data/example/profession/beekeeper/profession.json");
        Files.createDirectories(profession.getParent());
        Files.writeString(pack.resolve("pack.mcmeta"),
                "{\"pack\":{\"description\":\"test\",\"pack_format\":48}}");
        Files.writeString(profession,
                "{\"schema\":\"another_mod:profession/v1\"}");

        assertFalse(CareerPackSource.isCareerPack(pack));

        Files.writeString(profession,
                "{\"schema\":\"townstead:profession/v2\"}");
        assertTrue(CareerPackSource.isCareerPack(pack));
    }

    @Test
    void curseForgeStyleZipIsRecognised(@TempDir Path root) throws Exception {
        Path zip = root.resolve("beekeeper.zip");
        try (OutputStream file = Files.newOutputStream(zip);
             ZipOutputStream output = new ZipOutputStream(file)) {
            put(output, "pack.mcmeta",
                    "{\"pack\":{\"description\":\"test\",\"pack_format\":48}}");
            put(output, "data/townstead_example/profession/beekeeper/profession.json",
                    "{\"schema\":\"townstead:profession/v2\"}");
        }

        assertTrue(CareerPackSource.isCareerPack(zip));
    }

    @Test
    void onlyPacksWithAssetsReachTheResourcePackList(@TempDir Path root) throws Exception {
        Path dataOnly = root.resolve("runic-roots");
        Files.createDirectories(dataOnly.resolve("data/runic_roots/root"));
        Path withAssets = root.resolve("careers");
        Files.createDirectories(withAssets.resolve("assets/my_pack/lang"));
        assertFalse(CareerPackSource.hasAssets(dataOnly));
        assertTrue(CareerPackSource.hasAssets(withAssets));

        Path zip = root.resolve("zipped.zip");
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip))) {
            put(output, "pack.mcmeta", "{}");
            put(output, "assets/my_pack/lang/en_us.json", "{}");
        }
        assertTrue(CareerPackSource.hasAssets(zip));
    }

    private static void put(ZipOutputStream output, String path, String contents) throws Exception {
        output.putNextEntry(new ZipEntry(path));
        output.write(contents.getBytes(StandardCharsets.UTF_8));
        output.closeEntry();
    }
}
