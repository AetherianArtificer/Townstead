package com.aetherianartificer.townstead.data;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21 {
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
//?}
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Makes the loose KubeJS {@code data/<namespace>/...} and {@code assets/<namespace>/...}
 * layouts available as an ordinary data pack and resource pack.
 *
 * <p>KubeJS normally mounts this directory itself. Townstead supplies the equivalent pack only
 * when KubeJS is absent, avoiding two resource packs backed by the same files. The directory is
 * still exposed through {@link #directory()} when KubeJS is installed so boot-time scanners can
 * see definitions, such as professions, that must be known before ordinary datapack reloads.</p>
 */
public final class KubeJsPackSource {

    private static final String PACK_ID = Townstead.MOD_ID + "_kubejs_resources";

    private KubeJsPackSource() {}

    /** The KubeJS root under the active game directory. */
    public static Path directory() {
        //? if >=1.21 {
        Path game = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
        //?} else {
        /*Path game = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get();
        *///?}
        return game.resolve("kubejs");
    }

    /** The KubeJS-compatible loose data root (the equivalent of a datapack's {@code data/}). */
    public static Path dataDirectory() {
        return directory().resolve("data");
    }

    /** The KubeJS-compatible loose asset root (the equivalent of a resource pack's assets/). */
    public static Path assetsDirectory() {
        return directory().resolve("assets");
    }

    /**
     * Creates Townstead's fallback pack for the requested side, or {@code null} when KubeJS
     * already owns the directory or there is no matching loose content to load.
     */
    public static @Nullable Pack create(PackType type) {
        Path kubeJsRoot = directory();
        Path contentRoot = rootFor(kubeJsRoot, type);
        if (contentRoot == null || ModCompat.isLoaded("kubejs")
                || !Files.isDirectory(contentRoot)) return null;

        //? if >=1.21 {
        PackLocationInfo info = new PackLocationInfo(
                PACK_ID,
                Component.literal("KubeJS Resources (Townstead compatibility)"),
                PackSource.BUILT_IN,
                Optional.empty());
        return Pack.readMetaAndCreate(
                info,
                new Pack.ResourcesSupplier() {
                    @Override
                    public PackResources openPrimary(PackLocationInfo location) {
                        return new LoosePackResources(kubeJsRoot, location);
                    }

                    @Override
                    public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                        return openPrimary(location);
                    }
                },
                type,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        //?} else {
        /*return Pack.readMetaAndCreate(
                PACK_ID,
                Component.literal("KubeJS Resources (Townstead compatibility)"),
                true,
                id -> new LoosePackResources(kubeJsRoot, id),
                type,
                Pack.Position.TOP,
                PackSource.BUILT_IN);
        *///?}
    }

    /** Package-private resource view used by focused filesystem tests. */
    static PackResources resources(Path kubeJsRoot) {
        //? if >=1.21 {
        return new LoosePackResources(kubeJsRoot, new PackLocationInfo(
                PACK_ID, Component.literal("test"), PackSource.BUILT_IN, Optional.empty()));
        //?} else {
        /*return new LoosePackResources(kubeJsRoot, PACK_ID);
        *///?}
    }

    private static final class LoosePackResources implements PackResources {
        private final Path kubeJsRoot;

        //? if >=1.21 {
        private final PackLocationInfo info;

        private LoosePackResources(Path kubeJsRoot, PackLocationInfo info) {
            this.kubeJsRoot = kubeJsRoot.toAbsolutePath().normalize();
            this.info = info;
        }

        @Override
        public PackLocationInfo location() {
            return info;
        }
        //?} else {
        /*private final String id;

        private LoosePackResources(Path kubeJsRoot, String id) {
            this.kubeJsRoot = kubeJsRoot.toAbsolutePath().normalize();
            this.id = id;
        }

        @Override
        public String packId() {
            return id;
        }
        *///?}

        @Nullable
        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            if (path.length != 1 || !"pack.mcmeta".equals(path[0])) return null;
            byte[] bytes = metadataJson().getBytes(StandardCharsets.UTF_8);
            return () -> new ByteArrayInputStream(bytes);
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            Path contentRoot = rootFor(kubeJsRoot, type);
            if (contentRoot == null) return null;
            Path file = resourcePath(contentRoot, location);
            if (file == null || !Files.isRegularFile(file)) return null;
            return () -> Files.newInputStream(file);
        }

        @Override
        public void listResources(PackType type, String namespace, String path,
                                  ResourceOutput output) {
            Path contentRoot = rootFor(kubeJsRoot, type);
            if (contentRoot == null || !validNamespace(namespace)) return;
            Path namespaceRoot = contentRoot.resolve(namespace).normalize();
            if (isNestedPack(namespaceRoot)) return;
            Path start = namespaceRoot.resolve(path.replace('/', java.io.File.separatorChar)).normalize();
            if (!start.startsWith(namespaceRoot) || !Files.isDirectory(start)) return;

            try (var files = Files.walk(start)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    String relative = namespaceRoot.relativize(file).toString()
                            .replace(java.io.File.separatorChar, '/');
                    ResourceLocation location = location(namespace, relative);
                    if (location != null) output.accept(location, () -> Files.newInputStream(file));
                }
            } catch (IOException error) {
                Townstead.LOGGER.warn("Could not list KubeJS-compatible data under {}", start, error);
            }
        }

        @SuppressWarnings("unchecked")
        @Nullable
        @Override
        public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
            if (serializer != PackMetadataSection.TYPE) return null;
            //? if >=1.21 {
            return (T) new PackMetadataSection(
                    Component.literal("KubeJS resources loaded by Townstead"), 48);
            //?} else {
            /*return (T) new PackMetadataSection(
                    Component.literal("KubeJS resources loaded by Townstead"), 15);
            *///?}
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            Path contentRoot = rootFor(kubeJsRoot, type);
            if (contentRoot == null || !Files.isDirectory(contentRoot)) return Set.of();
            Set<String> namespaces = new LinkedHashSet<>();
            try (var directories = Files.list(contentRoot)) {
                directories.filter(Files::isDirectory)
                        .filter(path -> !isNestedPack(path))
                        .map(path -> path.getFileName().toString())
                        .filter(LoosePackResources::validNamespace)
                        .sorted()
                        .forEach(namespaces::add);
            } catch (IOException error) {
                Townstead.LOGGER.warn("Could not list KubeJS-compatible namespaces under {}",
                        contentRoot, error);
            }
            return Set.copyOf(namespaces);
        }

        @Override
        public void close() {}

        private static @Nullable Path resourcePath(Path contentRoot, ResourceLocation location) {
            Path namespaceRoot = contentRoot.resolve(location.getNamespace()).normalize();
            if (isNestedPack(namespaceRoot)) return null;
            Path file = namespaceRoot.resolve(location.getPath()
                    .replace('/', java.io.File.separatorChar)).normalize();
            return file.startsWith(namespaceRoot) ? file : null;
        }

        private static boolean validNamespace(String namespace) {
            return location(namespace, "probe") != null;
        }

        private static boolean isNestedPack(Path directory) {
            return Files.isRegularFile(directory.resolve("pack.mcmeta"));
        }

        private static @Nullable ResourceLocation location(String namespace, String path) {
            //? if >=1.21 {
            return ResourceLocation.tryBuild(namespace, path);
            //?} else {
            /*return ResourceLocation.tryParse(namespace + ":" + path);
            *///?}
        }

        private static String metadataJson() {
            //? if >=1.21 {
            return "{\"pack\":{\"pack_format\":48,\"description\":\"KubeJS resources loaded by Townstead\"}}";
            //?} else {
            /*return "{\"pack\":{\"pack_format\":15,\"description\":\"KubeJS resources loaded by Townstead\"}}";
            *///?}
        }
    }

    private static @Nullable Path rootFor(Path kubeJsRoot, PackType type) {
        if (type == PackType.SERVER_DATA) return kubeJsRoot.resolve("data").normalize();
        if (type == PackType.CLIENT_RESOURCES) return kubeJsRoot.resolve("assets").normalize();
        return null;
    }
}
