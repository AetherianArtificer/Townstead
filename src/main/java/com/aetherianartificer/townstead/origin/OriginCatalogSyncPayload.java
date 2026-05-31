package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
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
            buf.writeUtf(e.heritageName());
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
            String heritageName = buf.readUtf();
            int gn = buf.readVarInt();
            List<OriginCatalogEntry.Inherited> inherited = new ArrayList<>(gn);
            for (int j = 0; j < gn; j++) {
                inherited.add(new OriginCatalogEntry.Inherited(buf.readUtf(), buf.readFloat()));
            }
            int rn = buf.readVarInt();
            List<OriginCatalogEntry.GeneRangeView> ranges = new ArrayList<>(rn);
            for (int j = 0; j < rn; j++) {
                ranges.add(new OriginCatalogEntry.GeneRangeView(buf.readUtf(), buf.readFloat(), buf.readFloat()));
            }
            entries.add(new OriginCatalogEntry(id, name, singular, plural, backstory,
                    speciesName, ancestryName, heritageName, inherited, ranges));
        }
        int m = buf.readVarInt();
        List<GeneCatalogEntry> genes = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            genes.add(new GeneCatalogEntry(
                    buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                    buf.readUtf(), buf.readFloat(),
                    buf.readVarInt(), buf.readUtf(), buf.readVarInt()));
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
}
