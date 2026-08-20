package com.aetherianartificer.townstead.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A data-declared statement about what villagers may treat as storage
 * ({@code data/<ns>/storage_role/*.json}).
 *
 * <pre>
 * {
 *   "schema": "townstead:storage_role/v1",
 *   "mods": "farm_and_charm",
 *   "role": "not_storage",
 *   "blocks": ["farm_and_charm:stove", "#examplemod:machines"]
 * }
 * </pre>
 *
 * <p>Getting this wrong is visible in game: a block wrongly read as storage becomes somewhere
 * villagers dump spare items and pull staged ingredients back out of, and a block wrongly read
 * as a machine is simply never used.</p>
 */
public record StorageRoleDef(
        ResourceLocation id,
        Role role,
        Set<ResourceLocation> blocks,
        List<ResourceLocation> blockTags) {

    public enum Role {
        /** A shelf: villagers may read from it and deposit into it. */
        STORAGE,
        /** A machine, or simply off limits. */
        NOT_STORAGE;

        static @Nullable Role parse(String raw) {
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "storage" -> STORAGE;
                case "not_storage" -> NOT_STORAGE;
                default -> null;
            };
        }
    }

    public StorageRoleDef {
        blocks = Set.copyOf(blocks);
        blockTags = List.copyOf(blockTags);
    }

    /** Whether this statement speaks about this block. */
    public boolean matches(@Nullable BlockState state) {
        if (state == null) return false;
        ResourceLocation blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(state.getBlock());
        if (blockId != null && blocks.contains(blockId)) return true;
        for (ResourceLocation tag : blockTags) {
            if (state.is(TagKey.create(Registries.BLOCK, tag))) return true;
        }
        return false;
    }

    /** Null for a malformed document, which is refused rather than half-read. */
    static @Nullable StorageRoleDef parse(ResourceLocation id, JsonObject obj) {
        Role role = Role.parse(GsonHelper.getAsString(obj, "role", ""));
        if (role == null) return null;

        Set<ResourceLocation> blocks = new LinkedHashSet<>();
        List<ResourceLocation> tags = new ArrayList<>();
        if (!obj.has("blocks") || !obj.get("blocks").isJsonArray()) return null;
        for (JsonElement e : obj.getAsJsonArray("blocks")) {
            if (!e.isJsonPrimitive()) return null;
            String raw = e.getAsString();
            ResourceLocation parsed = ResourceLocation.tryParse(
                    raw.startsWith("#") ? raw.substring(1) : raw);
            if (parsed == null) return null;
            if (raw.startsWith("#")) tags.add(parsed); else blocks.add(parsed);
        }
        // An empty list is a statement nothing can satisfy, not a statement about everything.
        if (blocks.isEmpty() && tags.isEmpty()) return null;
        return new StorageRoleDef(id, role, blocks, tags);
    }
}
