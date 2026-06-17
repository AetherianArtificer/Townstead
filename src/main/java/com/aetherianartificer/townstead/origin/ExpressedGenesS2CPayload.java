package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.origin.gene.Allele;
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
 * rather than the origin-typical set. Encodings are {@link Allele#encode()} strings;
 * {@code entityId == -1} is the player's own. Updates {@code OriginClientStore}.
 */
//? if neoforge {
public record ExpressedGenesS2CPayload(int entityId, List<String> genes) implements CustomPacketPayload {
//?} else {
/*public record ExpressedGenesS2CPayload(int entityId, java.util.List<String> genes) {
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
    }

    public static ExpressedGenesS2CPayload read(FriendlyByteBuf buf) {
        int entityId = buf.readInt();
        int count = buf.readVarInt();
        List<String> genes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) genes.add(buf.readUtf());
        return new ExpressedGenesS2CPayload(entityId, genes);
    }

    /** Build the payload for a living entity (villager or player), keyed by {@code entityId}. */
    public static ExpressedGenesS2CPayload forEntity(int entityId, LivingEntity entity) {
        com.aetherianartificer.townstead.origin.gene.Genotype genotype;
        if (entity instanceof VillagerEntityMCA villager) {
            genotype = TownsteadVillagers.get(villager).life().genotype();
        } else if (entity instanceof Player player) {
            genotype = PlayerOrigin.getGenotype(player);
        } else {
            genotype = new com.aetherianartificer.townstead.origin.gene.Genotype();
        }
        List<String> genes = new ArrayList<>();
        for (Allele allele : Heredity.expressedAlleles(genotype)) genes.add(allele.encode());
        return new ExpressedGenesS2CPayload(entityId, genes);
    }
}
