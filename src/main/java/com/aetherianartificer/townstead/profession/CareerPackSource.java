package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.Townstead;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
//? if >=1.21 {
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.world.level.validation.DirectoryValidator;
//?}
import net.minecraft.server.packs.repository.FolderRepositorySource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.slf4j.Logger;

/**
 * Townstead's global Career-pack source.
 *
 * <p>World data packs are normally discovered after the vanilla registries freeze. Townstead
 * scans Career packs from the profile-level {@code datapacks} directory used by launchers such
 * as CurseForge, and from the manual {@code config/townstead/career-packs} directory, before
 * that freeze. Complete pack folders nested under {@code kubejs/data} are also mounted, allowing
 * the same folder to carry its server data and assets. It then mounts them as required server-data
 * and client-resource packs. This lets one ordinary pack contain a real custom villager
 * profession, its Career documents, and its translations.</p>
 */
public final class CareerPackSource {

    private static final String DIRECTORY = "career-packs";
    private static final Logger LOGGER = LogUtils.getLogger();

    private CareerPackSource() {}

    public static Path directory() {
        return configDirectory().resolve(Townstead.MOD_ID).resolve(DIRECTORY);
    }

    /** Profile-level route used by CurseForge and common global data-pack loaders. */
    public static Path profileDataPackDirectory() {
        //? if >=1.21 {
        Path game = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
        //?} else {
        /*Path game = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
        *///?}
        return game.resolve("datapacks");
    }

    private static Path configDirectory() {
        //? if >=1.21 {
        Path config = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        //?} else {
        /*Path config = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
        *///?}
        return config;
    }

    /** Adds every installed Career pack as an always-enabled pack of the requested type. */
    public static void loadPacks(PackType type, Consumer<Pack> output) {
        loadPacks(directory(), type, output, false, "manual");
        loadPacks(profileDataPackDirectory(), type, output, true, "profile");
        Path kubeJsData = com.aetherianartificer.townstead.data.KubeJsPackSource.dataDirectory();
        if (Files.isDirectory(kubeJsData)) {
            // KubeJS mounts its loose data/<namespace> tree, not complete Career-pack ZIPs
            // dropped alongside those namespaces. Townstead must mount those ZIPs on BOTH sides:
            // the early registry scan can see their profession.json, but without the server-data
            // pack the later reload never sees the profession, work job, skills, or buildings.
            loadPacks(kubeJsData, type, output, false, "kubejs");
        }
    }

    private static void loadPacks(Path directory, PackType type, Consumer<Pack> output,
                                  boolean requireCareerDocument, String source) {
        try {
            Files.createDirectories(directory);
            //? if >=1.21 {
            DirectoryValidator validator = new DirectoryValidator(path -> true);
            FolderRepositorySource.discoverPacks(directory, validator, (path, resources) -> {
                if (requireCareerDocument && !isCareerPack(path)) return;
                String id = packId(path, type, source);
                PackLocationInfo info = new PackLocationInfo(
                        id,
                        Component.translatable("townstead.pack.career.name", displayName(path)),
                        PackSource.BUILT_IN,
                        Optional.empty());
                Pack pack = Pack.readMetaAndCreate(
                        info,
                        resources,
                        type,
                        new PackSelectionConfig(true, Pack.Position.TOP, false));
                if (pack != null) output.accept(pack);
            });
            //?} else {
            /*FolderRepositorySource.discoverPacks(directory, false, (path, resources) -> {
                if (requireCareerDocument && !isCareerPack(path)) return;
                Pack pack = Pack.readMetaAndCreate(
                        packId(path, type, source),
                        Component.translatable("townstead.pack.career.name", displayName(path)),
                        true,
                        resources,
                        type,
                        Pack.Position.TOP,
                        PackSource.BUILT_IN);
                if (pack != null) output.accept(pack);
            });
            *///?}
        } catch (IOException error) {
            Townstead.LOGGER.warn("Could not discover Townstead Career packs in {}", directory, error);
        }
    }

    /**
     * Visits the data root of each installed directory or ZIP pack. The visitor runs while a
     * ZIP file system is open and therefore must finish reading before it returns.
     */
    static void visitDataRoots(Consumer<Path> visitor) {
        visitDataRoots(directory(), false, visitor);
        visitDataRoots(profileDataPackDirectory(), true, visitor);
        visitDataRoots(com.aetherianartificer.townstead.data.KubeJsPackSource.dataDirectory(),
                false, visitor);
    }

    private static void visitDataRoots(Path directory, boolean requireCareerDocument,
                                       Consumer<Path> visitor) {
        if (!Files.isDirectory(directory)) return;
        try (var entries = Files.list(directory)) {
            for (Path entry : entries.sorted(Comparator.comparing(path ->
                    path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER)).toList()) {
                if (Files.isDirectory(entry)) {
                    if (Files.isRegularFile(entry.resolve("pack.mcmeta"))) {
                        Path data = entry.resolve("data");
                        if (!requireCareerDocument || containsCareerDefinition(data)) {
                            visitor.accept(data);
                        }
                    }
                } else if (isZip(entry)) {
                    try (FileSystem zip = FileSystems.newFileSystem(entry)) {
                        Path root = zip.getRootDirectories().iterator().next();
                        if (Files.isRegularFile(root.resolve("pack.mcmeta"))) {
                            Path data = root.resolve("data");
                            if (!requireCareerDocument || containsCareerDefinition(data)) {
                                visitor.accept(data);
                            }
                        }
                    } catch (Exception error) {
                        Townstead.LOGGER.warn("Could not scan Townstead Career pack {}", entry, error);
                    }
                }
            }
        } catch (IOException error) {
            Townstead.LOGGER.warn("Could not list Townstead Career packs in {}", directory, error);
        }
    }

    private static boolean isZip(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path) && name.endsWith(".zip");
    }

    static boolean isCareerPack(Path entry) {
        if (Files.isDirectory(entry)) {
            return Files.isRegularFile(entry.resolve("pack.mcmeta"))
                    && containsCareerDefinition(entry.resolve("data"));
        }
        if (!isZip(entry)) return false;
        try (ZipFile zip = new ZipFile(entry.toFile())) {
            return zip.getEntry("pack.mcmeta") != null && containsCareerDefinition(zip);
        } catch (Exception error) {
            LOGGER.warn("Could not inspect potential Townstead Career pack {}", entry, error);
            return false;
        }
    }

    private static boolean containsCareerDefinition(ZipFile zip) throws IOException {
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory() || !isProfessionDocument(entry.getName())) continue;
            try (var reader = new InputStreamReader(
                    zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                if (ScannedProfessions.hasProfessionSchema(reader)) return true;
            }
        }
        return false;
    }

    private static boolean isProfessionDocument(String name) {
        String[] parts = name.replace('\\', '/').split("/");
        if (parts.length < 4 || !"data".equals(parts[0])
                || !"profession".equals(parts[2])) return false;
        return parts.length == 4 && parts[3].endsWith(".json")
                || parts.length == 5 && "profession.json".equals(parts[4]);
    }

    static boolean containsCareerDefinition(Path data) {
        if (!Files.isDirectory(data)) return false;
        try (var namespaces = Files.list(data)) {
            for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                Path professions = namespace.resolve("profession");
                if (!Files.isDirectory(professions)) continue;
                try (var entries = Files.list(professions)) {
                    for (Path entry : entries.toList()) {
                        Path document = Files.isDirectory(entry)
                                ? entry.resolve("profession.json")
                                : entry;
                        if (Files.isRegularFile(document)
                                && document.getFileName().toString().endsWith(".json")
                                && ScannedProfessions.hasProfessionSchema(document)) {
                            return true;
                        }
                    }
                }
            }
        } catch (IOException error) {
            Townstead.LOGGER.warn("Could not inspect Career-pack data root {}", data, error);
        }
        return false;
    }

    private static String packId(Path path, PackType type, String source) {
        String safe = path.getFileName().toString().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "_");
        return Townstead.MOD_ID + "_career_" + source + "_" + safe + "_"
                + type.name().toLowerCase(Locale.ROOT);
    }

    private static String displayName(Path path) {
        String name = path.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(".zip")
                ? name.substring(0, name.length() - ".zip".length())
                : name;
    }
}
