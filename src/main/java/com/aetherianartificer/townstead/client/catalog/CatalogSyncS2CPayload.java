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

/**
 * Server → client: everything {@code CatalogDataLoader} produced from the datapack reload that
 * the catalog screen reads (groups, per-building node-item/hide overrides, data theme) plus the
 * building-spirit contributions shown in the details panel. On a dedicated server the client
 * never runs the datapack reload, so without this sync the screen falls back to icon guesses
 * and empty groups. Sent on login and datapack reload alongside the origin catalog.
 */
//? if neoforge {
public record CatalogSyncS2CPayload(List<GroupDef> groups, Map<String, BuildingOverride> overrides,
                                    Theme theme, Map<String, Map<String, Integer>> spirits)
        implements CustomPacketPayload {
//?} else {
/*public record CatalogSyncS2CPayload(List<GroupDef> groups, Map<String, BuildingOverride> overrides,
                                    Theme theme, Map<String, Map<String, Integer>> spirits) {
*///?}

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
                BuildingSpiritIndex.snapshot());
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
        return new CatalogSyncS2CPayload(groups, overrides, theme, spirits);
    }

    private static Optional<ResourceLocation> parseOptional(String raw) {
        return raw.isEmpty() ? Optional.empty() : Optional.ofNullable(ResourceLocation.tryParse(raw));
    }
}
