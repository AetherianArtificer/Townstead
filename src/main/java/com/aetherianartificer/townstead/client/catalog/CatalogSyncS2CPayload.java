package com.aetherianartificer.townstead.client.catalog;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader.BuildingOverride;
import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader.GroupDef;
import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader.Theme;
import com.aetherianartificer.townstead.spirit.BuildingSpiritIndex;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * Server → client: everything {@code CatalogDataLoader} produced from the datapack reload that
 * the catalog screen reads (groups, per-building node-item/hide overrides, data theme) plus the
 * building-spirit contributions shown in the details panel. On a dedicated server the client
 * never runs the datapack reload, so without this sync the screen falls back to icon guesses
 * and empty groups. Sent on login and datapack reload alongside the origin catalog.
 */
//? if neoforge {
public record CatalogSyncS2CPayload(List<GroupDef> groups, Map<String, BuildingOverride> overrides,
                                    Theme theme, Map<String, Map<String, Integer>> spirits,
                                    List<DecorationSummary> decorations, Set<String> hangoutBuildings)
        implements CustomPacketPayload {
//?} else {
/*public record CatalogSyncS2CPayload(List<GroupDef> groups, Map<String, BuildingOverride> overrides,
                                    Theme theme, Map<String, Map<String, Integer>> spirits,
                                    List<DecorationSummary> decorations, Set<String> hangoutBuildings) {
*///?}

    public record Ingredient(String selector, int count) {}
    public record Variant(List<String> anchors, List<Ingredient> requirements) {
        public Variant { anchors = List.copyOf(anchors); requirements = List.copyOf(requirements); }
    }
    public record DecorationSummary(ResourceLocation id, ResourceLocation icon, int radius,
                                   int recognized, List<Variant> variants, boolean hangout) {
        public DecorationSummary { variants = List.copyOf(variants); }
    }

    private static Set<String> hangoutBuildingSnapshot() {
        Set<String> ids = new LinkedHashSet<>();
        for (var venue : com.aetherianartificer.townstead.hangout.HangoutData.venues().values())
            ids.addAll(venue.buildings());
        return Set.copyOf(ids);
    }

    //? if neoforge {
    public static final Type<CatalogSyncS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "catalog_sync_s2c"));

    public static final StreamCodec<FriendlyByteBuf, CatalogSyncS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), CatalogSyncS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "catalog_sync_s2c");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "catalog_sync_s2c");
    *///?}

    /** Capture the server's current catalog state for one send. */
    public static CatalogSyncS2CPayload snapshot() {
        return new CatalogSyncS2CPayload(List.copyOf(CatalogDataLoader.groups()),
                CatalogDataLoader.overridesSnapshot(), CatalogDataLoader.dataTheme(),
                BuildingSpiritIndex.snapshot(), decorationSnapshot(Map.of()), hangoutBuildingSnapshot());
    }

    /** Catalog plus recognition counts for the village whose Blueprint was refreshed. */
    public static CatalogSyncS2CPayload snapshot(net.minecraft.server.level.ServerLevel level,
                                                  net.conczin.mca.server.world.data.Village village) {
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        if (level != null && village != null) {
            var centerVector = village.getCenter();
            var center = new net.minecraft.core.BlockPos(centerVector.getX(), centerVector.getY(), centerVector.getZ());
            var box = village.getBox();
            int radius = Math.max(Math.max(center.getX() - box.minX(), box.maxX() - center.getX()),
                    Math.max(center.getZ() - box.minZ(), box.maxZ() - center.getZ())) + 24;
            for (var instance : com.aetherianartificer.townstead.decoration.DecorationSavedData.get(level)
                    .within(center, Math.min(320, radius * 2))) {
                if (instance.anchor().getX() < box.minX() - 24 || instance.anchor().getX() > box.maxX() + 24
                        || instance.anchor().getZ() < box.minZ() - 24 || instance.anchor().getZ() > box.maxZ() + 24) continue;
                counts.merge(instance.decorationId(), 1, Integer::sum);
            }
        }
        return new CatalogSyncS2CPayload(List.copyOf(CatalogDataLoader.groups()),
                CatalogDataLoader.overridesSnapshot(), CatalogDataLoader.dataTheme(),
                BuildingSpiritIndex.snapshot(), decorationSnapshot(counts), hangoutBuildingSnapshot());
    }

    private static List<DecorationSummary> decorationSnapshot(Map<ResourceLocation, Integer> counts) {
        List<DecorationSummary> out = new ArrayList<>();
        for (com.aetherianartificer.townstead.decoration.DecorationDefinition definition
                : com.aetherianartificer.townstead.decoration.Decorations.all()) {
            List<Variant> variants = definition.variants().stream().map(variant -> new Variant(
                    variant.anchors().stream().map(anchor -> anchor.raw()).toList(),
                    variant.requires().stream().map(req -> new Ingredient(req.raw(), req.count())).toList())).toList();
            boolean hangout = com.aetherianartificer.townstead.hangout.HangoutData.venues().values()
                    .stream().anyMatch(venue -> venue.decorations().contains(definition.id()));
            ResourceLocation icon = definition.icon() == null
                    ? definition.anchors().get(0).blockId() : definition.icon();
            //? if >=1.21 {
            if (icon == null) icon = ResourceLocation.fromNamespaceAndPath("minecraft", "barrier");
            //?} else {
            /*if (icon == null) icon = new ResourceLocation("minecraft", "barrier");
            *///?}
            out.add(new DecorationSummary(definition.id(), icon, definition.radius(),
                    counts.getOrDefault(definition.id(), 0), variants, hangout));
        }
        out.sort(java.util.Comparator.comparing(entry -> entry.id().toString()));
        return List.copyOf(out);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(groups.size());
        for (GroupDef g : groups) {
            buf.writeUtf(g.id());
            buf.writeUtf(g.label());
            buf.writeUtf(g.matchPrefix());
            buf.writeUtf(g.layout());
            buf.writeUtf(g.tierPrefix());
            buf.writeInt(g.priority());
            buf.writeVarInt(g.supersedes().size());
            for (String buildingType : g.supersedes()) buf.writeUtf(buildingType);
        }
        buf.writeVarInt(overrides.size());
        for (Map.Entry<String, BuildingOverride> e : overrides.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeUtf(e.getValue().nodeItem().map(ResourceLocation::toString).orElse(""));
            buf.writeBoolean(e.getValue().hide());
        }
        buf.writeUtf(theme.backgroundTexture().map(ResourceLocation::toString).orElse(""));
        buf.writeInt(theme.frameColor());
        buf.writeInt(theme.panelColor());
        buf.writeInt(theme.titleBarColor());
        buf.writeInt(theme.graphBackgroundColor());
        buf.writeInt(theme.detailsBackgroundColor());
        buf.writeInt(theme.borderColor());
        buf.writeInt(theme.gridColor());
        buf.writeBoolean(theme.showGrid());
        buf.writeInt(theme.nodeFillColor());
        buf.writeInt(theme.nodeHoverFillColor());
        buf.writeInt(theme.nodeSelectedFillColor());
        buf.writeInt(theme.nodeBorderColor());
        buf.writeInt(theme.nodeHoverBorderColor());
        buf.writeInt(theme.nodeSelectedBorderColor());
        buf.writeInt(theme.builtNodeFillColor());
        buf.writeInt(theme.builtNodeHoverFillColor());
        buf.writeInt(theme.builtNodeSelectedFillColor());
        buf.writeInt(theme.builtNodeBorderColor());
        buf.writeInt(theme.builtNodeHoverBorderColor());
        buf.writeInt(theme.builtNodeSelectedBorderColor());
        buf.writeVarInt(spirits.size());
        for (Map.Entry<String, Map<String, Integer>> e : spirits.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue().size());
            for (Map.Entry<String, Integer> s : e.getValue().entrySet()) {
                buf.writeUtf(s.getKey());
                buf.writeVarInt(s.getValue());
            }
        }
        buf.writeVarInt(decorations.size());
        for (DecorationSummary set : decorations) {
            buf.writeResourceLocation(set.id());
            buf.writeResourceLocation(set.icon());
            buf.writeVarInt(set.radius());
            buf.writeVarInt(set.recognized());
            buf.writeBoolean(set.hangout());
            buf.writeVarInt(set.variants().size());
            for (Variant variant : set.variants()) {
                buf.writeVarInt(variant.anchors().size());
                for (String anchor : variant.anchors()) buf.writeUtf(anchor);
                buf.writeVarInt(variant.requirements().size());
                for (Ingredient ingredient : variant.requirements()) {
                    buf.writeUtf(ingredient.selector());
                    buf.writeVarInt(ingredient.count());
                }
            }
        }
        buf.writeVarInt(hangoutBuildings.size());
        for (String building : hangoutBuildings.stream().sorted().toList()) buf.writeUtf(building);
    }

    public static CatalogSyncS2CPayload read(FriendlyByteBuf buf) {
        int gn = buf.readVarInt();
        List<GroupDef> groups = new ArrayList<>(gn);
        for (int i = 0; i < gn; i++) {
            String id = buf.readUtf();
            String label = buf.readUtf();
            String matchPrefix = buf.readUtf();
            String layout = buf.readUtf();
            String tierPrefix = buf.readUtf();
            int priority = buf.readInt();
            int supersedesCount = buf.readVarInt();
            List<String> supersedes = new ArrayList<>(supersedesCount);
            for (int j = 0; j < supersedesCount; j++) supersedes.add(buf.readUtf());
            groups.add(new GroupDef(id, label, matchPrefix, layout, tierPrefix, priority, supersedes));
        }
        int on = buf.readVarInt();
        Map<String, BuildingOverride> overrides = new LinkedHashMap<>();
        for (int i = 0; i < on; i++) {
            String type = buf.readUtf();
            Optional<ResourceLocation> nodeItem = parseOptional(buf.readUtf());
            overrides.put(type, new BuildingOverride(nodeItem, buf.readBoolean()));
        }
        Theme theme = new Theme(parseOptional(buf.readUtf()),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readBoolean(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt());
        int sn = buf.readVarInt();
        Map<String, Map<String, Integer>> spirits = new LinkedHashMap<>();
        for (int i = 0; i < sn; i++) {
            String type = buf.readUtf();
            int cn = buf.readVarInt();
            Map<String, Integer> contributions = new LinkedHashMap<>();
            for (int j = 0; j < cn; j++) contributions.put(buf.readUtf(), buf.readVarInt());
            spirits.put(type, contributions);
        }
        int osn = buf.readVarInt();
        List<DecorationSummary> decorations = new ArrayList<>(osn);
        for (int i = 0; i < osn; i++) {
            ResourceLocation id = buf.readResourceLocation();
            ResourceLocation icon = buf.readResourceLocation();
            int radius = buf.readVarInt();
            int recognized = buf.readVarInt();
            boolean hangout = buf.readBoolean();
            int variantCount = readCount(buf);
            List<Variant> variants = new ArrayList<>();
            for (int v = 0; v < variantCount; v++) {
                int anchorCount = readCount(buf);
                List<String> anchors = new ArrayList<>();
                for (int j = 0; j < anchorCount; j++) anchors.add(buf.readUtf());
                int requirementCount = readCount(buf);
                List<Ingredient> requirements = new ArrayList<>();
                for (int j = 0; j < requirementCount; j++)
                    requirements.add(new Ingredient(buf.readUtf(), buf.readVarInt()));
                variants.add(new Variant(anchors, requirements));
            }
            decorations.add(new DecorationSummary(id, icon, radius, recognized, variants, hangout));
        }
        int hangoutCount = readCount(buf);
        Set<String> hangouts = new LinkedHashSet<>();
        for (int i = 0; i < hangoutCount; i++) hangouts.add(buf.readUtf());
        return new CatalogSyncS2CPayload(groups, overrides, theme, spirits, decorations, Set.copyOf(hangouts));
    }

    private static int readCount(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > 16384) throw new IllegalArgumentException("Invalid catalog collection size: " + count);
        return count;
    }

    private static Optional<ResourceLocation> parseOptional(String raw) {
        return raw.isEmpty() ? Optional.empty() : Optional.ofNullable(ResourceLocation.tryParse(raw));
    }
}
