package com.aetherianartificer.townstead.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class KubeJsPackSourceTest {

    @Test
    void exposesDataAndAssetsFromLooseKubeJsTree(@TempDir Path kubeJs) throws Exception {
        write(kubeJs, "data/example/root/citizen.json", "{\"kind\":\"root\"}");
        write(kubeJs, "data/example/profession/baker/profession.json", "{\"kind\":\"profession\"}");
        write(kubeJs, "data/example/extended_buildings/bakery.json", "{\"kind\":\"building\"}");
        write(kubeJs, "data/Invalid Namespace/root/ignored.json", "{}");
        write(kubeJs, "assets/example/lang/en_us.json", "{\"example.name\":\"Example\"}");
        write(kubeJs, "assets/example/textures/gui/catalog.png", "not-really-a-png");
        write(kubeJs, "data/townstead_beekeeping/pack.mcmeta",
                "{\"pack\":{\"description\":\"Beekeeping\",\"pack_format\":48}}");
        write(kubeJs, "data/townstead_beekeeping/data/townstead_beekeeping/root/apiarist.json", "{}");
        write(kubeJs, "data/townstead_beekeeping/assets/townstead_beekeeping/lang/en_us.json", "{}");

        try (PackResources resources = KubeJsPackSource.resources(kubeJs)) {
            assertEquals(Set.of("example"), resources.getNamespaces(PackType.SERVER_DATA));
            assertEquals(Set.of("example"), resources.getNamespaces(PackType.CLIENT_RESOURCES));

            Set<String> listed = new LinkedHashSet<>();
            resources.listResources(PackType.SERVER_DATA, "example", "", (id, supplier) ->
                    listed.add(id.toString()));
            assertEquals(Set.of(
                    "example:root/citizen.json",
                    "example:profession/baker/profession.json",
                    "example:extended_buildings/bakery.json"), listed);

            //? if >=1.21 {
            ResourceLocation root = ResourceLocation.fromNamespaceAndPath(
                    "example", "root/citizen.json");
            //?} else {
            /*ResourceLocation root = new ResourceLocation("example", "root/citizen.json");
            *///?}
            var supplier = resources.getResource(PackType.SERVER_DATA, root);
            assertNotNull(supplier);
            try (InputStream input = supplier.get()) {
                assertEquals("{\"kind\":\"root\"}",
                        new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertNull(resources.getResource(PackType.CLIENT_RESOURCES, root));

            Set<String> assets = new LinkedHashSet<>();
            resources.listResources(PackType.CLIENT_RESOURCES, "example", "", (id, asset) ->
                    assets.add(id.toString()));
            assertEquals(Set.of(
                    "example:lang/en_us.json",
                    "example:textures/gui/catalog.png"), assets);

            //? if >=1.21 {
            ResourceLocation locale = ResourceLocation.fromNamespaceAndPath(
                    "example", "lang/en_us.json");
            //?} else {
            /*ResourceLocation locale = new ResourceLocation("example", "lang/en_us.json");
            *///?}
            assertNotNull(resources.getResource(PackType.CLIENT_RESOURCES, locale));
            assertNull(resources.getResource(PackType.SERVER_DATA, locale));
            assertNotNull(resources.getRootResource("pack.mcmeta"));
        }
    }

    private static void write(Path data, String relative, String contents) throws Exception {
        Path file = data.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, contents);
    }
}
