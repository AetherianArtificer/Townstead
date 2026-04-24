package com.aetherianartificer.townstead.enclosure;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry of building types flagged as {@code townsteadEnclosure} in their
 * JSON. Populated during resource reload from {@code CatalogDataLoader}. Each
 * entry knows its perimeter requirements (fence / fence-gate / wall counts
 * pulled from the type's {@code blocks} map), its interior signature (the
 * remaining {@code blocks} entries), and the min/max interior size from the
 * enclosure block.
 *
 * <p>Consumers:
 * <ul>
 *   <li>{@code EnclosureClassifier} — matches a scanned {@link Enclosure}
 *       against registered specs and picks the highest-priority fit.</li>
 *   <li>{@code BuildingValidateOpenAirMixin} — short-circuits MCA's flood-
 *       fill validation for any registered enclosure type.</li>
 * </ul>
 */
public final class EnclosureTypeIndex {
    private static final List<Spec> SPECS = new CopyOnWriteArrayList<>();

    public record Requirement(String raw, ResourceLocation blockId, TagKey<Block> tagKey, int count) {
        public boolean matches(BlockState state) {
            if (tagKey != null) return state.is(tagKey);
            if (blockId != null) {
                ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                return blockId.equals(key);
            }
            return false;
        }
    }

    public record Spec(
            String buildingType,
            int priority,
            int minInterior,
            int maxInterior,
            int fencesRequired,
            int fenceGatesRequired,
            int wallsRequired,
            List<Requirement> interiorSignatures) {
    }

    private EnclosureTypeIndex() {}

    public static void clear() {
        SPECS.clear();
    }

    public static void register(Spec spec) {
        if (spec == null || spec.buildingType() == null) return;
        SPECS.removeIf(s -> s.buildingType().equals(spec.buildingType()));
        SPECS.add(spec);
    }

    public static Collection<Spec> all() {
        return SPECS;
    }

    public static boolean isEnclosureType(String buildingType) {
        if (buildingType == null) return false;
        for (Spec s : SPECS) {
            if (s.buildingType().equals(buildingType)) return true;
        }
        return false;
    }

    /**
     * Parse a {@code blocks} map + {@code townsteadEnclosure} block into a
     * {@link Spec}. {@code blocks} entries matching {@code #minecraft:fences},
     * {@code #minecraft:fence_gates}, or {@code #minecraft:walls} become
     * perimeter requirements. Everything else becomes interior signatures.
     */
    public static Spec parseSpec(String buildingType, int priority,
            Map<String, Integer> blocks, int minInterior, int maxInterior) {
        int fences = 0;
        int gates = 0;
        int walls = 0;
        List<Requirement> interior = new ArrayList<>();
        for (Map.Entry<String, Integer> e : blocks.entrySet()) {
            String raw = e.getKey();
            int count = Math.max(1, e.getValue());
            if ("#minecraft:fences".equals(raw)) {
                fences = count;
            } else if ("#minecraft:fence_gates".equals(raw)) {
                gates = count;
            } else if ("#minecraft:walls".equals(raw)) {
                walls = count;
            } else {
                Requirement req = parseRequirement(raw, count);
                if (req != null) interior.add(req);
            }
        }
        return new Spec(buildingType, priority, minInterior, maxInterior,
                fences, gates, walls, List.copyOf(interior));
    }

    private static Requirement parseRequirement(String raw, int count) {
        if (raw == null || raw.isEmpty()) return null;
        if (raw.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(raw.substring(1));
            if (tagId == null) return null;
            return new Requirement(raw, null, TagKey.create(Registries.BLOCK, tagId), count);
        }
        ResourceLocation blockId = ResourceLocation.tryParse(raw);
        if (blockId == null) return null;
        return new Requirement(raw, blockId, null, count);
    }

    /** Small utility so scanners don't have to re-parse the common tag ids. */
    public static TagKey<Block> fencesTag() { return BlockTags.FENCES; }
    public static TagKey<Block> fenceGatesTag() { return BlockTags.FENCE_GATES; }
    public static TagKey<Block> wallsTag() { return BlockTags.WALLS; }

    /** Debug aid — stable order for logging. */
    public static List<Spec> snapshot() {
        return new ArrayList<>(SPECS);
    }

    /**
     * Does any registered enclosure spec declare {@code state} as an interior
     * signature block? Used by scans to selectively tally floor-level blocks
     * (dy=-1) — blood grates sit flush with the ground, so a pure
     * dy≥0 tally misses them, but indiscriminately tallying dy=-1 captures
     * grass/dirt as signal. This check is the middle ground: floor-level
     * blocks only enter the tally when some pen type actually cares.
     */
    public static boolean anySpecRequires(BlockState state) {
        if (state == null) return false;
        for (Spec s : SPECS) {
            for (Requirement r : s.interiorSignatures()) {
                if (r.matches(state)) return true;
            }
        }
        return false;
    }
}
