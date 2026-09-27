package com.aetherianartificer.townstead.root.outfit;

import com.aetherianartificer.townstead.root.Ancestry;
import com.aetherianartificer.townstead.root.AncestryRegistry;
import com.aetherianartificer.townstead.root.Lineage;
import com.aetherianartificer.townstead.root.LineageRegistry;
import com.aetherianartificer.townstead.root.Root;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Outfits a body wears by circumstance: each outfit grafts bones cut from other geometry files (a
 * hat, a watering can, an umbrella) onto the rig while its pheno {@code when} holds, e.g. a
 * profession or rain. Declared on any root-chain level and merged by id down the chain (species,
 * ancestry, lineage, root), so a root can replace or add to what its species wears.
 *
 * <pre>
 * "outfits": {
 *   "farmer": {
 *     "when": { "type": "pheno:profession", "profession": "minecraft:farmer" },
 *     "pieces": [ { "model": "ribbits:geo/gardener_ribbit.geo.json", "bones": ["gardener_hat"] } ]
 *   }
 * }
 * </pre>
 *
 * <p>A piece's bones keep their authored parent, so they must be cut from a model that shares the
 * rig's skeleton. They draw with the rig's texture and ride its clips.</p>
 */
public final class RootOutfits {

    /** Bones cut from one geometry file (grafted with their descendants). */
    public record Piece(String model, List<String> bones) {}

    /** One outfit: worn while {@code whenJson} holds (empty = always). Empty pieces = wears nothing. */
    public record Outfit(String id, String whenJson, List<Piece> pieces) {}

    private static volatile Map<ResourceLocation, Map<String, Outfit>> SPECIES = Map.of();
    private static volatile Map<ResourceLocation, Map<String, Outfit>> ANCESTRY = Map.of();
    private static volatile Map<ResourceLocation, Map<String, Outfit>> LINEAGE = Map.of();
    private static volatile Map<ResourceLocation, Map<String, Outfit>> ROOT = Map.of();

    private RootOutfits() {}

    public static void setSpecies(Map<ResourceLocation, Map<String, Outfit>> next) { SPECIES = Map.copyOf(next); }
    public static void setAncestry(Map<ResourceLocation, Map<String, Outfit>> next) { ANCESTRY = Map.copyOf(next); }
    public static void setLineage(Map<ResourceLocation, Map<String, Outfit>> next) { LINEAGE = Map.copyOf(next); }
    public static void setRoot(Map<ResourceLocation, Map<String, Outfit>> next) { ROOT = Map.copyOf(next); }

    /** The {@code outfits} block of a root-chain document, by id; empty when it declares none. */
    public static Map<String, Outfit> parse(@Nullable JsonObject owner) {
        if (owner == null || !owner.has("outfits") || !owner.get("outfits").isJsonObject()) return Map.of();
        Map<String, Outfit> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : owner.getAsJsonObject("outfits").entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject outfit = entry.getValue().getAsJsonObject();
            List<Piece> pieces = new ArrayList<>();
            if (outfit.has("pieces") && outfit.get("pieces").isJsonArray()) {
                for (JsonElement element : outfit.getAsJsonArray("pieces")) {
                    if (!element.isJsonObject()) continue;
                    JsonObject piece = element.getAsJsonObject();
                    String model = GsonHelper.getAsString(piece, "model", "");
                    List<String> bones = new ArrayList<>();
                    if (piece.has("bones") && piece.get("bones").isJsonArray()) {
                        for (JsonElement bone : piece.getAsJsonArray("bones")) bones.add(bone.getAsString());
                    }
                    if (!model.isEmpty() && !bones.isEmpty()) pieces.add(new Piece(model, List.copyOf(bones)));
                }
            }
            String when = outfit.has("when") ? outfit.get("when").toString() : "";
            out.put(entry.getKey(), new Outfit(entry.getKey(), when, List.copyOf(pieces)));
        }
        return out;
    }

    /**
     * The outfits a root wears, merged species -> ancestry -> lineage -> root by id (a later level
     * replaces an earlier one's outfit of the same id). Outfits with no pieces are dropped, which is
     * how a root takes off an inherited outfit.
     */
    public static List<Outfit> resolve(@Nullable ResourceLocation rootId) {
        Root root = RootRegistry.resolveOrDefault(rootId);
        ResourceLocation lineageId = root == null ? null : root.lineage();
        ResourceLocation ancestryId = root == null ? null : root.ancestry();
        if (ancestryId == null && lineageId != null) {
            Lineage lineage = LineageRegistry.byId(lineageId);
            if (lineage != null) ancestryId = lineage.ancestry();
        }
        ResourceLocation speciesId = RootRegistry.effectiveSpecies(rootId);
        if (speciesId == null && ancestryId != null) {
            Ancestry ancestry = AncestryRegistry.byId(ancestryId);
            if (ancestry != null) speciesId = ancestry.species();
        }
        Map<String, Outfit> merged = new LinkedHashMap<>();
        merge(merged, SPECIES, speciesId);
        merge(merged, ANCESTRY, ancestryId);
        merge(merged, LINEAGE, lineageId);
        merge(merged, ROOT, root == null ? null : root.id());
        List<Outfit> out = new ArrayList<>();
        for (Outfit outfit : merged.values()) {
            if (!outfit.pieces().isEmpty()) out.add(outfit);
        }
        return out;
    }

    private static void merge(Map<String, Outfit> into, Map<ResourceLocation, Map<String, Outfit>> level,
                              @Nullable ResourceLocation id) {
        if (id == null) return;
        Map<String, Outfit> outfits = level.get(id);
        if (outfits != null) into.putAll(outfits);
    }
}
