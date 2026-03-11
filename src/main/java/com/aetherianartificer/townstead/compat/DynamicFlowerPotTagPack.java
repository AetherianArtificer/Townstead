package com.aetherianartificer.townstead.compat;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.level.block.FlowerPotBlock;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

/**
 * Virtual datapack that dynamically generates the {@code townstead:flower_pot}
 * block tag by scanning the block registry for all {@link FlowerPotBlock} instances.
 * This ensures modded flower pots are automatically included.
 */
public final class DynamicFlowerPotTagPack {
    private static final ResourceLocation TAG_PATH = ResourceLocation.fromNamespaceAndPath(
            Townstead.MOD_ID, "tags/block/flower_pot.json");

    private DynamicFlowerPotTagPack() {}

    public static Pack create() {
        PackLocationInfo info = new PackLocationInfo(
                Townstead.MOD_ID + "_dynamic_tags",
                net.minecraft.network.chat.Component.literal("Townstead Dynamic Tags"),
                PackSource.BUILT_IN,
                Optional.empty()
        );
        return Pack.readMetaAndCreate(
                info,
                new Pack.ResourcesSupplier() {
                    @Override
                    public PackResources openPrimary(PackLocationInfo loc) {
                        return new FlowerPotTagPackResources(loc);
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

    private static byte[] generateTagJson() {
        JsonObject root = new JsonObject();
        root.addProperty("replace", false);
        JsonArray values = new JsonArray();
        BuiltInRegistries.BLOCK.entrySet().stream()
                .filter(e -> e.getValue() instanceof FlowerPotBlock)
                .map(e -> e.getKey().location().toString())
                .sorted()
                .forEach(values::add);
        root.add("values", values);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static class FlowerPotTagPackResources implements PackResources {
        private final PackLocationInfo info;

        FlowerPotTagPackResources(PackLocationInfo info) {
            this.info = info;
        }

        @Override
        public PackLocationInfo location() {
            return info;
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            if (path.length == 1 && "pack.mcmeta".equals(path[0])) {
                String meta = "{\"pack\":{\"pack_format\":34,\"description\":\"Townstead dynamic tags\"}}";
                byte[] bytes = meta.getBytes(StandardCharsets.UTF_8);
                return () -> new ByteArrayInputStream(bytes);
            }
            return null;
        }

        @Nullable
        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            if (type == PackType.SERVER_DATA && TAG_PATH.equals(location)) {
                return () -> new ByteArrayInputStream(generateTagJson());
            }
            return null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path,
                                  ResourceOutput output) {
            if (type == PackType.SERVER_DATA
                    && Townstead.MOD_ID.equals(namespace)
                    && TAG_PATH.getPath().startsWith(path)) {
                output.accept(TAG_PATH, () -> new ByteArrayInputStream(generateTagJson()));
            }
        }

        @SuppressWarnings("unchecked")
        @Nullable
        @Override
        public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) throws IOException {
            if (serializer == PackMetadataSection.TYPE) {
                return (T) new PackMetadataSection(
                        net.minecraft.network.chat.Component.literal("Townstead dynamic tags"),
                        34
                );
            }
            return null;
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return type == PackType.SERVER_DATA ? Set.of(Townstead.MOD_ID) : Set.of();
        }

        @Override
        public void close() {}
    }
}
