package com.aetherianartificer.townstead.clothing.adapt;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A built-in client resource pack of clothing textures made at load time from other mods' player
 * skins (see {@link ClothingAdapter}). Nothing of theirs ships in Townstead: each texture is read
 * from the installed mod's jar when first asked for. The list lives in
 * {@code assets/townstead/clothing_adapters.json}; a source whose mod is not installed is skipped.
 */
public final class AdaptedClothingPack {
    private static final String CONFIG = "assets/townstead/clothing_adapters.json";
    private static final Map<ResourceLocation, Source> SOURCES = load();
    private static final Map<ResourceLocation, byte[]> MADE = new ConcurrentHashMap<>();

    private record Source(String mod, ResourceLocation texture, boolean slim, int hatRows, int handRows) {}

    private AdaptedClothingPack() {}

    private static Map<ResourceLocation, Source> load() {
        Map<ResourceLocation, Source> out = new LinkedHashMap<>();
        try (InputStream in = AdaptedClothingPack.class.getClassLoader().getResourceAsStream(CONFIG)) {
            if (in == null) return Map.of();
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement element : GsonHelper.getAsJsonArray(root, "adapters")) {
                JsonObject adapter = element.getAsJsonObject();
                String mod = GsonHelper.getAsString(adapter, "mod");
                String into = GsonHelper.getAsString(adapter, "into");
                for (JsonElement raw : GsonHelper.getAsJsonArray(adapter, "sources")) {
                    JsonObject source = raw.getAsJsonObject();
                    ResourceLocation texture = ResourceLocation.tryParse(GsonHelper.getAsString(source, "texture"));
                    if (texture == null) continue;
                    String file = texture.getPath().substring(texture.getPath().lastIndexOf('/') + 1);
                    ResourceLocation id = ResourceLocation.tryParse(into + "/" + file);
                    if (id == null) continue;
                    out.put(id, new Source(mod, texture, GsonHelper.getAsBoolean(source, "slim", false),
                            GsonHelper.getAsInt(source, "hat_rows", 0), GsonHelper.getAsInt(source, "hand_rows", 3)));
                }
            }
        } catch (Exception e) {
            Townstead.LOGGER.warn("Clothing adapters not loaded: {}", e.getMessage());
            return Map.of();
        }
        return Map.copyOf(out);
    }

    private static List<ResourceLocation> active() {
        List<ResourceLocation> out = new ArrayList<>();
        SOURCES.forEach((id, source) -> {
            if (ModCompat.isLoaded(source.mod())) out.add(id);
        });
        return out;
    }

    private static @Nullable IoSupplier<InputStream> supplier(ResourceLocation id) {
        Source source = SOURCES.get(id);
        if (source == null || !ModCompat.isLoaded(source.mod())) return null;
        return () -> {
            byte[] bytes = MADE.get(id);
            if (bytes == null) {
                bytes = make(source);
                MADE.put(id, bytes);
            }
            return new ByteArrayInputStream(bytes);
        };
    }

    private static byte[] make(Source source) throws IOException {
        Path path = sourcePath(source);
        if (path == null || !Files.isRegularFile(path)) throw new IOException("Missing " + source.texture());
        BufferedImage image;
        try (InputStream in = Files.newInputStream(path)) {
            image = ImageIO.read(in);
        }
        if (image == null) throw new IOException("Unreadable " + source.texture());
        BufferedImage adapted = ClothingAdapter.adapt(image, source.slim(), source.hatRows(), source.handRows());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(adapted, "png", bytes);
        return bytes.toByteArray();
    }

    private static @Nullable Path sourcePath(Source source) {
        //? if neoforge {
        var info = net.neoforged.fml.ModList.get().getModFileById(source.mod());
        //?} else {
        /*var info = net.minecraftforge.fml.ModList.get().getModFileById(source.mod());
        *///?}
        if (info == null) return null;
        String[] parts = ("assets/" + source.texture().getNamespace() + "/" + source.texture().getPath()).split("/");
        return info.getFile().findResource(parts);
    }

    public static @Nullable Pack create() {
        if (active().isEmpty()) return null;
        //? if >=1.21 {
        net.minecraft.server.packs.PackLocationInfo info = new net.minecraft.server.packs.PackLocationInfo(
                Townstead.MOD_ID + "_adapted_clothing", Component.translatable("townstead.pack.adapted_clothing.name"),
                PackSource.BUILT_IN, Optional.empty());
        return Pack.readMetaAndCreate(info, new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(net.minecraft.server.packs.PackLocationInfo location) {
                return new Resources(location);
            }

            @Override
            public PackResources openFull(net.minecraft.server.packs.PackLocationInfo location, Pack.Metadata metadata) {
                return openPrimary(location);
            }
        }, PackType.CLIENT_RESOURCES, new net.minecraft.server.packs.PackSelectionConfig(true, Pack.Position.TOP, false));
        //?} else {
        /*return Pack.readMetaAndCreate(Townstead.MOD_ID + "_adapted_clothing",
                Component.translatable("townstead.pack.adapted_clothing.name"), true, Resources::new,
                PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN);
        *///?}
    }

    private static final class Resources implements PackResources {
        //? if >=1.21 {
        private final net.minecraft.server.packs.PackLocationInfo info;

        Resources(net.minecraft.server.packs.PackLocationInfo info) {
            this.info = info;
        }

        @Override
        public net.minecraft.server.packs.PackLocationInfo location() {
            return info;
        }
        //?} else {
        /*private final String id;

        Resources(String id) {
            this.id = id;
        }

        @Override
        public String packId() {
            return id;
        }
        *///?}

        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            return type == PackType.CLIENT_RESOURCES ? supplier(location) : null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type != PackType.CLIENT_RESOURCES) return;
            for (ResourceLocation id : active()) {
                if (!id.getNamespace().equals(namespace) || !id.getPath().startsWith(path)) continue;
                IoSupplier<InputStream> supplier = supplier(id);
                if (supplier != null) output.accept(id, supplier);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> @Nullable T getMetadataSection(MetadataSectionSerializer<T> serializer) {
            if (serializer != PackMetadataSection.TYPE) return null;
            //? if >=1.21 {
            return (T) new PackMetadataSection(Component.translatable("townstead.pack.adapted_clothing.description"), 34);
            //?} else {
            /*return (T) new PackMetadataSection(Component.translatable("townstead.pack.adapted_clothing.description"), 15);
            *///?}
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            if (type != PackType.CLIENT_RESOURCES) return Set.of();
            Set<String> out = new java.util.HashSet<>();
            for (ResourceLocation id : active()) out.add(id.getNamespace());
            return out;
        }

        @Override
        public void close() {}
    }
}
