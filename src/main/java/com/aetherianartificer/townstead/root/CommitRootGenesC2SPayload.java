package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: commit the editor preview's MCA float genes (in {@link RootGenes} snapshot
 * order) and hair dye to the real target — the WYSIWYG roll an origin Apply produced on the editor dummy.
 * {@code entityId == -1} targets the sending player; otherwise the loaded villager with that
 * network id. This replaces routing an Apply through MCA's {@code syncVillagerData}, whose full
 * editor save also rewrites the target's family-tree entry (gender, typed-in parents) and, for
 * players, the whole stored MCA snapshot, from stale editor-buffer keys.
 */
//? if neoforge {
public record CommitRootGenesC2SPayload(int entityId, float[] genes, int hairColor) implements CustomPacketPayload {
//?} else {
/*public record CommitRootGenesC2SPayload(int entityId, float[] genes, int hairColor) {
*///?}

    //? if neoforge {
    public static final Type<CommitRootGenesC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "commit_root_genes_c2s"));

    public static final StreamCodec<FriendlyByteBuf, CommitRootGenesC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), CommitRootGenesC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "commit_root_genes_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "commit_root_genes_c2s");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeVarInt(genes.length);
        for (float gene : genes) buf.writeFloat(gene);
        buf.writeInt(hairColor);
    }

    public static CommitRootGenesC2SPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        int count = Math.min(buf.readVarInt(), 64);
        float[] genes = new float[Math.max(count, 0)];
        for (int i = 0; i < genes.length; i++) genes[i] = buf.readFloat();
        return new CommitRootGenesC2SPayload(entityId, genes, buf.readInt());
    }
}
