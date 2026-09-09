package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.root.gene.Allele;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: an entity's expressed (dominant) gene alleles, so the client can
 * render that individual's actual genetics (attachments, glow, hidden features)
 * rather than the origin-typical set. The resolved hair policy rides the same
 * per-individual sync so a named mixed heritage can override its founder root.
 * Encodings are {@link Allele#encode()} strings;
 * {@code entityId == -1} is the player's own. Updates {@code RootClientStore}.
 */
//? if neoforge {
public record ExpressedGenesS2CPayload(int entityId, List<String> genes, boolean hair,
        List<com.aetherianartificer.townstead.root.appearance.HairColorRange> hairColorRanges,
        List<com.aetherianartificer.townstead.root.appearance.HairColorChoice> hairColors,
        List<com.aetherianartificer.townstead.root.appearance.HairGradient> hairGradients) implements CustomPacketPayload {
//?} else {
/*public record ExpressedGenesS2CPayload(int entityId, java.util.List<String> genes, boolean hair,
        java.util.List<com.aetherianartificer.townstead.root.appearance.HairColorRange> hairColorRanges,
        java.util.List<com.aetherianartificer.townstead.root.appearance.HairColorChoice> hairColors,
        java.util.List<com.aetherianartificer.townstead.root.appearance.HairGradient> hairGradients) {
*///?}

    //? if neoforge {
    public static final Type<ExpressedGenesS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "expressed_genes_s2c"));

    public static final StreamCodec<FriendlyByteBuf, ExpressedGenesS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), ExpressedGenesS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "expressed_genes_s2c");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "expressed_genes_s2c");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeVarInt(genes.size());
        for (String gene : genes) buf.writeUtf(gene);
        buf.writeBoolean(hair);
        buf.writeVarInt(hairColorRanges.size());
        for (var range : hairColorRanges) {
            buf.writeFloat(range.darkness().min());
            buf.writeFloat(range.darkness().max());
            buf.writeFloat(range.redness().min());
            buf.writeFloat(range.redness().max());
            buf.writeVarInt(range.weight());
        }
        buf.writeVarInt(hairColors.size());
        for (var color : hairColors) {
            buf.writeInt(color.rgb());
            buf.writeVarInt(color.weight());
            buf.writeUtf(color.name());
            buf.writeUtf(color.nameKey());
        }
        buf.writeVarInt(hairGradients.size());
        for (var gradient : hairGradients) {
            buf.writeVarInt(gradient.stops().size());
            for (int stop : gradient.stops()) buf.writeInt(stop);
            buf.writeVarInt(gradient.weight());
            buf.writeEnum(gradient.space());
            buf.writeUtf(gradient.name());
            buf.writeUtf(gradient.nameKey());
        }
    }

    public static ExpressedGenesS2CPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        int count = buf.readVarInt();
        List<String> genes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) genes.add(buf.readUtf());
        boolean hair = buf.readBoolean();
        int rangeCount = buf.readVarInt();
        List<com.aetherianartificer.townstead.root.appearance.HairColorRange> ranges = new ArrayList<>(rangeCount);
        for (int i = 0; i < rangeCount; i++) {
            ranges.add(new com.aetherianartificer.townstead.root.appearance.HairColorRange(
                    new GeneRange(buf.readFloat(), buf.readFloat()),
                    new GeneRange(buf.readFloat(), buf.readFloat()), buf.readVarInt()));
        }
        int colorCount = buf.readVarInt();
        List<com.aetherianartificer.townstead.root.appearance.HairColorChoice> colors = new ArrayList<>(colorCount);
        for (int i = 0; i < colorCount; i++) {
            colors.add(new com.aetherianartificer.townstead.root.appearance.HairColorChoice(
                    buf.readInt(), buf.readVarInt(), buf.readUtf(), buf.readUtf()));
        }
        int gradientCount = buf.readVarInt();
        List<com.aetherianartificer.townstead.root.appearance.HairGradient> gradients = new ArrayList<>(gradientCount);
        for (int i = 0; i < gradientCount; i++) {
            int stopCount = buf.readVarInt();
            List<Integer> stops = new ArrayList<>(stopCount);
            for (int j = 0; j < stopCount; j++) stops.add(buf.readInt());
            gradients.add(new com.aetherianartificer.townstead.root.appearance.HairGradient(
                    stops, buf.readVarInt(),
                    buf.readEnum(com.aetherianartificer.townstead.root.appearance.HairGradient.Space.class),
                    buf.readUtf(), buf.readUtf()));
        }
        return new ExpressedGenesS2CPayload(entityId, genes, hair, ranges, colors, gradients);
    }

    /** Build the payload for a living entity (villager or player), keyed by {@code entityId}. */
    public static ExpressedGenesS2CPayload forEntity(int entityId, LivingEntity entity) {
        com.aetherianartificer.townstead.root.gene.Genotype genotype;
        Heritage heritage = null;
        ResourceLocation rootId = null;
        if (entity instanceof VillagerEntityMCA villager) {
            var life = TownsteadVillagers.get(villager).life();
            genotype = life.genotype();
            if (life.hasHeritage()) heritage = life.heritage();
            rootId = ResourceLocation.tryParse(life.rootId());
        } else if (entity instanceof Player player) {
            genotype = PlayerRoot.getGenotype(player);
            rootId = ResourceLocation.tryParse(PlayerRoot.getRootId(player));
            heritage = RootRegistry.seedHeritage(rootId == null ? RootRegistry.DEFAULT_ID : rootId);
        } else {
            genotype = new com.aetherianartificer.townstead.root.gene.Genotype();
        }
        List<String> genes = new ArrayList<>();
        for (Allele allele : Heredity.expressedAlleles(genotype)) {
            genes.add(Heredity.scaleByHeritage(allele, heritage).encode());
            // Companions ride along their parent's expression server-side (GenePowerSource);
            // mirror them here so client-resolved render genes (opacity, attachments granted
            // as companions) see them too.
            if (allele.geneId() == null) continue;
            for (ResourceLocation companion
                    : com.aetherianartificer.townstead.root.gene.GeneRegistry.companionsOf(allele.geneId())) {
                genes.add(Allele.of(companion, null).encode());
            }
        }
        var hair = com.aetherianartificer.townstead.root.appearance.HairResolver.resolve(rootId, heritage);
        return new ExpressedGenesS2CPayload(entityId, genes, hair.enabled(), hair.colorRanges(),
                hair.colors(), hair.gradients());
    }
}
