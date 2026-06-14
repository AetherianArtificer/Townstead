package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: set a target's carried variant for one variant gene (the editor's variant
 * picker, e.g. choosing a skin-tone palette option). {@code entityId == -1} targets the sending
 * player; otherwise the loaded villager with that network id. The server pins both genotype alleles
 * at the chosen variant (so the choice is stable and inherits) and re-syncs the expressed genes.
 */
//? if neoforge {
public record SetGeneVariantC2SPayload(int entityId, String geneId, String variantId) implements CustomPacketPayload {
//?} else {
/*public record SetGeneVariantC2SPayload(int entityId, String geneId, String variantId) {
*///?}

    //? if neoforge {
    public static final Type<SetGeneVariantC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "set_gene_variant_c2s"));

    public static final StreamCodec<FriendlyByteBuf, SetGeneVariantC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), SetGeneVariantC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "set_gene_variant_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "set_gene_variant_c2s");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeUtf(geneId);
        buf.writeUtf(variantId);
    }

    public static SetGeneVariantC2SPayload read(FriendlyByteBuf buf) {
        return new SetGeneVariantC2SPayload(buf.readInt(), buf.readUtf(), buf.readUtf());
    }
}
