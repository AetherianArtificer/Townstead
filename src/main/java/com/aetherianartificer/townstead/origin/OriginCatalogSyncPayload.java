package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: the selectable origins (lineage names + granted gene ids) plus
 * a gene dictionary (id → display data) covering every gene any origin grants, so
 * the picker renders fully on a client whose datapack-driven registries are empty.
 * Sent on login and datapack reload.
 */
//? if neoforge {
public record OriginCatalogSyncPayload(List<OriginCatalogEntry> entries, List<GeneCatalogEntry> genes,
                                       List<TraitCatalogEntry> traits)
        implements CustomPacketPayload {
//?} else {
/*public record OriginCatalogSyncPayload(List<OriginCatalogEntry> entries, List<GeneCatalogEntry> genes,
                                       List<TraitCatalogEntry> traits) {
*///?}

    //? if neoforge {
    public static final Type<OriginCatalogSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "origin_catalog_sync"));

    public static final StreamCodec<FriendlyByteBuf, OriginCatalogSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), OriginCatalogSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "origin_catalog_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "origin_catalog_sync");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entries.size());
        for (OriginCatalogEntry e : entries) {
            buf.writeUtf(e.id());
            buf.writeUtf(e.name());
            buf.writeUtf(e.demonymSingular());
            buf.writeUtf(e.demonymPlural());
            buf.writeUtf(e.backstory());
            buf.writeUtf(e.speciesName());
            buf.writeUtf(e.ancestryName());
            buf.writeUtf(e.lineageName());
            buf.writeVarInt(e.inheritedGenes().size());
            for (OriginCatalogEntry.Inherited in : e.inheritedGenes()) {
                buf.writeUtf(in.geneId());
                buf.writeFloat(in.occurrence());
            }
            buf.writeVarInt(e.geneRanges().size());
            for (OriginCatalogEntry.GeneRangeView r : e.geneRanges()) {
                buf.writeUtf(r.key());
                buf.writeFloat(r.min());
                buf.writeFloat(r.max());
            }
            buf.writeUtf(e.nameKey());
            buf.writeUtf(e.demonymSingularKey());
            buf.writeUtf(e.demonymPluralKey());
            buf.writeUtf(e.backstoryKey());
            buf.writeUtf(e.speciesNameKey());
            buf.writeUtf(e.ancestryNameKey());
            buf.writeUtf(e.lineageNameKey());
        }
        buf.writeVarInt(genes.size());
        for (GeneCatalogEntry g : genes) {
            buf.writeUtf(g.id());
            buf.writeUtf(g.name());
            buf.writeUtf(g.description());
            buf.writeUtf(g.category());
            buf.writeVarInt(g.displayKind());
            buf.writeFloat(g.min());
            buf.writeFloat(g.max());
            buf.writeUtf(g.targetId());
            buf.writeFloat(g.amount());
            buf.writeVarInt(g.dominanceOrdinal());
            buf.writeUtf(g.locus());
            buf.writeVarInt(g.weight());
            buf.writeVarInt(g.variants().size());
            for (GeneCatalogEntry.Variant v : g.variants()) {
                buf.writeUtf(v.id());
                buf.writeUtf(v.label());
                buf.writeVarInt(v.weight());
                buf.writeUtf(v.labelKey());
            }
            buf.writeUtf(g.nameKey());
            buf.writeUtf(g.descriptionKey());
        }
        buf.writeVarInt(traits.size());
        for (TraitCatalogEntry t : traits) {
            buf.writeUtf(t.id());
            buf.writeFloat(t.chance());
            buf.writeFloat(t.inherit());
            buf.writeBoolean(t.usableOnPlayer());
            buf.writeBoolean(t.hidden());
        }
    }

    public static OriginCatalogSyncPayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<OriginCatalogEntry> entries = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            String id = buf.readUtf();
            String name = buf.readUtf();
            String singular = buf.readUtf();
            String plural = buf.readUtf();
            String backstory = buf.readUtf();
            String speciesName = buf.readUtf();
            String ancestryName = buf.readUtf();
            String lineageName = buf.readUtf();
            int gn = buf.readVarInt();
            List<OriginCatalogEntry.Inherited> inherited = new ArrayList<>(gn);
            for (int j = 0; j < gn; j++) {
                String igid = buf.readUtf();
                float iocc = buf.readFloat();
                inherited.add(new OriginCatalogEntry.Inherited(igid, iocc));
            }
            int rn = buf.readVarInt();
            List<OriginCatalogEntry.GeneRangeView> ranges = new ArrayList<>(rn);
            for (int j = 0; j < rn; j++) {
                ranges.add(new OriginCatalogEntry.GeneRangeView(buf.readUtf(), buf.readFloat(), buf.readFloat()));
            }
            String nameKey = buf.readUtf();
            String singularKey = buf.readUtf();
            String pluralKey = buf.readUtf();
            String backstoryKey = buf.readUtf();
            String speciesNameKey = buf.readUtf();
            String ancestryNameKey = buf.readUtf();
            String lineageNameKey = buf.readUtf();
            entries.add(new OriginCatalogEntry(id,
                    localize(nameKey, name),
                    localize(singularKey, singular),
                    localize(pluralKey, plural),
                    localize(backstoryKey, backstory),
                    localize(speciesNameKey, speciesName),
                    localize(ancestryNameKey, ancestryName),
                    localize(lineageNameKey, lineageName),
                    inherited, ranges,
                    nameKey, singularKey, pluralKey, backstoryKey,
                    speciesNameKey, ancestryNameKey, lineageNameKey));
        }
        int m = buf.readVarInt();
        List<GeneCatalogEntry> genes = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            String gid = buf.readUtf();
            String gname = buf.readUtf();
            String gdesc = buf.readUtf();
            String gcat = buf.readUtf();
            int kind = buf.readVarInt();
            float gmin = buf.readFloat();
            float gmax = buf.readFloat();
            String gtarget = buf.readUtf();
            float gamount = buf.readFloat();
            int gdom = buf.readVarInt();
            String glocus = buf.readUtf();
            int gweight = buf.readVarInt();
            int vn = buf.readVarInt();
            List<GeneCatalogEntry.Variant> variants = new ArrayList<>(vn);
            for (int j = 0; j < vn; j++) {
                String vid = buf.readUtf();
                String vlabel = buf.readUtf();
                int vweight = buf.readVarInt();
                String vlabelKey = buf.readUtf();
                variants.add(new GeneCatalogEntry.Variant(vid, localize(vlabelKey, vlabel), vweight, vlabelKey));
            }
            String gNameKey = buf.readUtf();
            String gDescKey = buf.readUtf();
            genes.add(new GeneCatalogEntry(gid, localize(gNameKey, gname), localize(gDescKey, gdesc),
                    gcat, kind, gmin, gmax,
                    gtarget, gamount, gdom, glocus, gweight, variants,
                    gNameKey, gDescKey));
        }
        int k = buf.readVarInt();
        List<TraitCatalogEntry> traits = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            traits.add(new TraitCatalogEntry(
                    buf.readUtf(), buf.readFloat(), buf.readFloat(),
                    buf.readBoolean(), buf.readBoolean()));
        }
        return new OriginCatalogSyncPayload(entries, genes, traits);
    }

    /**
     * Resolve a synced display string in the reading client's locale: when a
     * translate key travelled with it, render {@code translatableWithFallback}
     * (client lang table → localized, else the English fallback); otherwise the
     * value was a literal and is returned as-is. Read runs client-side (S2C), so
     * this resolves against the client's {@code Language}.
     */
    private static String localize(String key, String fallback) {
        return (key == null || key.isEmpty())
                ? fallback
                : Component.translatableWithFallback(key, fallback).getString();
    }
}
