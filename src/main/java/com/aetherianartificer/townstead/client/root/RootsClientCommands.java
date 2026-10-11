package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.RootSetC2SPayload;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
//? if neoforge {
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
@EventBusSubscriber(modid = "townstead", value = Dist.CLIENT)
//?} else {
/*import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
@Mod.EventBusSubscriber(modid = "townstead", value = Dist.CLIENT)
*///?}
/**
 * {@code /townstead-roots-client [entityId]}: what this client holds for an entity's Root (yourself
 * by default): the synced root under its id and the self key, the expressed genes and carried
 * variants, the proportions gene the model uses, and for a player, MCA's stand-in villager (whose
 * genetics scale the player's villager model). Printed and written to the log.
 */
public final class RootsClientCommands {
    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("townstead-roots-client")
                .executes(c -> report(-1))
                .then(Commands.argument("entityId", IntegerArgumentType.integer(0))
                        .executes(c -> report(IntegerArgumentType.getInteger(c, "entityId")))));
    }

    private static int report(int id) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return 0;
        var entity = id < 0 ? mc.player : mc.level.getEntity(id);
        if (!(entity instanceof LivingEntity living)) return 0;
        StringBuilder out = new StringBuilder("ROOTS CLIENT ").append(living.getName().getString())
                .append(" #").append(living.getId()).append(' ').append(living.getUUID());
        out.append("\nroot[id]=").append(RootClientStore.get(living.getId()))
                .append(" root[SELF]=").append(RootClientStore.get(RootSetC2SPayload.SELF))
                .append(" resolve=").append(RootClientStore.resolve(living))
                .append(" expresses=").append(RootClientStore.expresses(living))
                .append(" synced=").append(RootClientStore.hasExpressionSync(living));
        out.append("\nexpressed=").append(new java.util.TreeSet<>(RootClientStore.expressedGenes(living)));
        out.append("\nvariants=").append(new java.util.TreeMap<>(RootClientStore.carriedVariants(living.getId())));
        out.append("\nproportions=").append(proportions(RootClientStore.resolve(living)));
        out.append("\nbodyScale=").append(com.aetherianartificer.townstead.root.rig.BodyScale.of(living));
        if (living instanceof net.conczin.mca.entity.VillagerEntityMCA villager) {
            out.append("\nmcaWidth=").append(villager.getGenetics().getGene(net.conczin.mca.entity.ai.Genetics.WIDTH))
                    .append(" mcaSize=").append(villager.getGenetics().getGene(net.conczin.mca.entity.ai.Genetics.SIZE));
        }
        //? if neoforge {
        if (living instanceof net.minecraft.world.entity.player.Player player) {
            var standIn = net.conczin.mca.MCAClient.getPlayerData(player.getUUID()).orElse(null);
            if (standIn == null) {
                out.append("\nmcaStandIn=none");
            } else {
                var genetics = standIn.getGenetics();
                out.append("\nmcaStandIn=").append(standIn.getClass().getSimpleName())
                        .append(" width=").append(genetics.getGene(net.conczin.mca.entity.ai.Genetics.WIDTH))
                        .append(" size=").append(genetics.getGene(net.conczin.mca.entity.ai.Genetics.SIZE));
                if (standIn instanceof net.conczin.mca.entity.VillagerEntityMCA standInVillager) {
                    out.append("\nmcaStandIn#").append(standInVillager.getId())
                            .append(" root[standIn]=").append(RootClientStore.get(standInVillager.getId()))
                            .append(" snapshotRoot=").append(com.aetherianartificer.townstead.villager.TownsteadVillagerState
                                    .snapshotRootId(standInVillager))
                            .append("\nstandInExpressed=").append(new java.util.TreeSet<>(
                                    RootClientStore.expressedGenes(standInVillager)));
                }
            }
        }
        //?}
        String report = out.toString();
        mc.player.displayClientMessage(Component.literal(report), false);
        org.slf4j.LoggerFactory.getLogger("townstead/roots-client").info(report);
        return 1;
    }

    private static String proportions(String rootId) {
        if (rootId.isEmpty()) return "none (no root)";
        RootCatalogEntry origin = RootCatalogClient.origin(rootId);
        if (origin == null) return "none (root not in catalog)";
        for (RootCatalogEntry.Inherited inherited : origin.inheritedGenes()) {
            GeneCatalogEntry gene = RootCatalogClient.gene(inherited.geneId());
            if (gene != null && gene.isProportions()) return gene.id();
        }
        return "none (root has no proportions gene)";
    }
}
