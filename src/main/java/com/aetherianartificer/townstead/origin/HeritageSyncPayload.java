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
import java.util.UUID;

/**
 * Server → client: one villager's realized heritage for the read-only Heritage
 * screen, keyed by the villager's UUID. Everything is pre-resolved to display
 * strings (the client's data-pack registries are empty): the race name, the
 * supporting origin/lineage name, the ancestry-fraction breakdown, and the diploid
 * gene rows (each locus' two alleles and which one is expressed). Built by
 * {@link HeritageView}. An empty {@code raceName} means the villager could not be
 * resolved (e.g. unloaded), and the screen shows "unavailable".
 */
//? if neoforge {
public record HeritageSyncPayload(UUID villager, String raceName, String originName,
                                  List<AncestryShare> ancestry, List<GeneRow> genes)
        implements CustomPacketPayload {
//?} else {
/*public record HeritageSyncPayload(UUID villager, String raceName, String originName,
                                  List<AncestryShare> ancestry, List<GeneRow> genes) {
*///?}

    /** The "couldn't resolve this villager" reply (empty race name → screen shows unavailable). */
    public static HeritageSyncPayload unavailable(UUID villager) {
        return new HeritageSyncPayload(villager, "", "", List.of(), List.of());
    }

    /** One ancestry's share of the villager's heritage (descending). */
    public record AncestryShare(String name, float fraction) {}

    /**
     * One diploid gene's display row. {@code label} is the expressed gene's name (the
     * left side). {@code variant} is the expressed variant's name for a multi-variant
     * gene, empty for a plain present gene (whose name is already the label, so the
     * right side stays empty rather than repeating it). {@code carries} is the
     * recessively-carried allele's name when heterozygous, {@code "~"} for a
     * single-copy carrier (other copy absent), or empty when homozygous.
     * {@code category} drives the chip tint; {@code geneId} lets the client pull the
     * gene's catalog definition (description, dominance) for the tooltip + border.
     */
    public record GeneRow(String geneId, String label, String category, String variant, String carries) {}

    //? if neoforge {
    public static final Type<HeritageSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "heritage_sync"));

    public static final StreamCodec<FriendlyByteBuf, HeritageSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), HeritageSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "heritage_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "heritage_sync");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(villager);
        buf.writeUtf(raceName);
        buf.writeUtf(originName);
        buf.writeVarInt(ancestry.size());
        for (AncestryShare a : ancestry) {
            buf.writeUtf(a.name());
            buf.writeFloat(a.fraction());
        }
        buf.writeVarInt(genes.size());
        for (GeneRow g : genes) {
            buf.writeUtf(g.geneId());
            buf.writeUtf(g.label());
            buf.writeUtf(g.category());
            buf.writeUtf(g.variant());
            buf.writeUtf(g.carries());
        }
    }

    public static HeritageSyncPayload read(FriendlyByteBuf buf) {
        UUID villager = buf.readUUID();
        String raceName = buf.readUtf();
        String originName = buf.readUtf();
        int an = buf.readVarInt();
        List<AncestryShare> ancestry = new ArrayList<>(an);
        for (int i = 0; i < an; i++) {
            ancestry.add(new AncestryShare(buf.readUtf(), buf.readFloat()));
        }
        int gn = buf.readVarInt();
        List<GeneRow> genes = new ArrayList<>(gn);
        for (int i = 0; i < gn; i++) {
            genes.add(new GeneRow(buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf(), buf.readUtf()));
        }
        return new HeritageSyncPayload(villager, raceName, originName, ancestry, genes);
    }
}
