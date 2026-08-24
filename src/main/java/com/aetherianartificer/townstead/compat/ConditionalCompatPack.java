package com.aetherianartificer.townstead.compat;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21 {
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
//?}
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Virtual resource pack that conditionally serves compat building types
 * and block tags only when the relevant mod is present.
 * Files are stored in the JAR under {@code townstead_compat/} and
 * served under {@code data/mca/} (building types) and {@code data/townstead/}
 * (block tags) at runtime.
 */
public final class ConditionalCompatPack {

    /** Build-generated inventory; adding a compat building JSON never requires a Java edit. */
    private static final List<CompatEntry> COMPAT_ENTRIES = loadCompatEntries();

    private static List<CompatEntry> loadCompatEntries() {
        List<CompatEntry> entries = new ArrayList<>();
        InputStream input = ConditionalCompatPack.class.getClassLoader()
                .getResourceAsStream("townstead_compat/index.txt");
        if (input == null) return List.of();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            reader.lines().map(String::trim).filter(path -> !path.isEmpty())
                    .forEach(path -> addCompat(entries, path));
        } catch (IOException ignored) {
            return List.of();
        }
        return List.copyOf(entries);
    }

    private static void addCompat(List<CompatEntry> entries, String path) {
            // Building types live under data/mca/
            //? if >=1.21 {
            ResourceLocation servePath = ResourceLocation.fromNamespaceAndPath("mca", path);
            //?} else {
            /*ResourceLocation servePath = new ResourceLocation("mca", path);
            *///?}
            String classpathPath = "townstead_compat/" + path;
            String typePath = path.substring("building_types/".length(), path.length() - ".json".length());
            entries.add(new CompatEntry(servePath, classpathPath, typePath));
    }

    private record CompatEntry(ResourceLocation servePath, String classpathPath, String typePath) {}

    private ConditionalCompatPack() {}

    //? if >=1.21 {
    public static Pack create() {
        PackLocationInfo info = new PackLocationInfo(
                Townstead.MOD_ID + "_compat_data",
                Component.literal("Townstead Compat Building Types"),
                PackSource.BUILT_IN,
                Optional.empty()
        );
        return Pack.readMetaAndCreate(
                info,
                new Pack.ResourcesSupplier() {
                    @Override
                    public PackResources openPrimary(PackLocationInfo loc) {
                        return new CompatPackResources(loc);
                    }

                    @Override
                    public PackResources openFull(PackLocationInfo loc, Pack.Metadata meta) {
                        return openPrimary(loc);
                    }
                },
                PackType.SERVER_DATA,
                new PackSelectionConfig(true, Pack.Position.TOP, false)
        );
    }
    //?} else {
    /*public static Pack create() {
        return Pack.readMetaAndCreate(
                Townstead.MOD_ID + "_compat_data",
                Component.literal("Townstead Compat Building Types"),
                true,
                id -> new CompatPackResources(id),
                PackType.SERVER_DATA,
                Pack.Position.TOP,
                PackSource.BUILT_IN
        );
    }
    *///?}

    private static List<CompatEntry> getActiveEntries() {
        List<CompatEntry> active = new ArrayList<>();
        for (CompatEntry entry : COMPAT_ENTRIES) {
            if (ModCompat.isCompatAvailable(entry.typePath())) {
                active.add(entry);
            }
        }
        return active;
    }

    private static class CompatPackResources implements PackResources {
        //? if >=1.21 {
        private final PackLocationInfo info;

        CompatPackResources(PackLocationInfo info) {
            this.info = info;
        }

        @Override
        public PackLocationInfo location() {
            return info;
        }
        //?} else {
        /*private final String id;

        CompatPackResources(String id) {
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
            return null;
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            if (type != PackType.SERVER_DATA) return null;
            for (CompatEntry entry : getActiveEntries()) {
                if (entry.servePath().equals(location)) {
                    return () -> {
                        InputStream is = ConditionalCompatPack.class.getClassLoader()
                                .getResourceAsStream(entry.classpathPath());
                        if (is == null) throw new IOException("Missing compat resource: " + entry.classpathPath());
                        return is;
                    };
                }
            }
            return null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path,
                                  ResourceOutput output) {
            if (type != PackType.SERVER_DATA) return;
            for (CompatEntry entry : getActiveEntries()) {
                if (entry.servePath().getNamespace().equals(namespace)
                        && entry.servePath().getPath().startsWith(path)) {
                    output.accept(entry.servePath(), () -> {
                        InputStream is = ConditionalCompatPack.class.getClassLoader()
                                .getResourceAsStream(entry.classpathPath());
                        if (is == null) throw new IOException("Missing compat resource: " + entry.classpathPath());
                        return is;
                    });
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Nullable
        @Override
        public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
            if (serializer == PackMetadataSection.TYPE) {
                //? if >=1.21 {
                return (T) new PackMetadataSection(
                        Component.literal("Townstead compat building types"), 48);
                //?} else {
                /*return (T) new PackMetadataSection(
                        Component.literal("Townstead compat building types"), 15);
                *///?}
            }
            return null;
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            if (type != PackType.SERVER_DATA) return Set.of();
            Set<String> namespaces = new HashSet<>();
            for (CompatEntry entry : getActiveEntries()) {
                namespaces.add(entry.servePath().getNamespace());
            }
            return namespaces;
        }

        @Override
        public void close() {}
    }
}
