package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.root.ability.GeneAbilityTicker;
import com.aetherianartificer.townstead.root.attribute.GeneAttributeApplier;
import com.aetherianartificer.townstead.root.gene.Allele;
import com.aetherianartificer.townstead.root.gene.GeneExpression;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Polls live conditions with the ability ticker; sends render updates only when expression changes. */
public final class GeneExpressionSync {
    private record State(ResourceLocation dimension, List<Allele> alleles, List<Power> powers, long revision) {}
    private static final Map<LivingEntity, State> states = new WeakHashMap<>();

    private GeneExpressionSync() {}

    public static void tick(LivingEntity entity) {
        ResourceLocation dimension = entity.level().dimension().location();
        List<Allele> alleles = GeneExpression.activeAlleles(entity);
        State previous = states.get(entity);
        if (previous != null && dimension.equals(previous.dimension()) && alleles.equals(previous.alleles())
                && previous.revision() == Powers.sourceRevision()) return;
        if (previous != null) {
            GeneAttributeApplier.removeFor(entity, previous.powers());
            GeneAbilityTicker.resetPassives(entity);
        }
        Powers.invalidate(entity);
        states.put(entity, new State(dimension, alleles, List.copyOf(Powers.active(entity)), Powers.sourceRevision()));
        var payload = ExpressedGenesS2CPayload.forEntity(entity.getId(), entity);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(entity, payload);
        if (entity instanceof ServerPlayer player)
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(entity, payload);
        if (entity instanceof ServerPlayer player)
            com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }
}
