package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.Heredity;
import com.aetherianartificer.townstead.root.PlayerRoot;
import com.aetherianartificer.townstead.root.ability.AbilityToggles;
import com.aetherianartificer.townstead.root.gene.Gene;
import com.aetherianartificer.townstead.root.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /townstead debug powers} — dumps the executing player's live power-resolution
 * chain, so a "gene does nothing" report separates into its possible causes: no
 * root applied, gene missing from the genotype (needs re-apply), the player-model
 * expression gate (genes only take effect in MCA's Villager model mode), or the
 * power resolving fine and the fault lying downstream. Server-side truth.
 */
public final class PowersDiagnosticCommand {

    private PowersDiagnosticCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("debug")
                .then(Commands.literal("powers").executes(c -> dump(c.getSource())))));
    }

    private static int dump(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        StringBuilder out = new StringBuilder();

        String rootId = PlayerRoot.getRootId(player);
        out.append("Root: ").append(rootId == null || rootId.isEmpty() ? "(none)" : rootId).append('\n');

        var expressed = Heredity.expressedGenes(ExpressedGenes.genotypeOf(player));
        out.append("Genotype expressed genes: ").append(expressed.size()).append('\n');
        for (Gene gene : expressed) {
            out.append("  ").append(gene.id()).append("  (").append(gene.instance().typeKey()).append(")\n");
        }

        String model;
        try {
            var data = net.conczin.mca.server.world.data.PlayerSaveData.get(player).getEntityData();
            model = data.contains("PlayerModel")
                    ? String.valueOf(data.getInt("PlayerModel")) : "(key absent, reads as 0)";
        } catch (Throwable t) {
            model = "(unreadable: " + t.getClass().getSimpleName() + ")";
        }
        out.append("PlayerModel raw: ").append(model).append("  [wall climbing requires VILLAGER = ")
                .append(net.conczin.mca.entity.VillagerLike.PlayerModel.VILLAGER.ordinal()).append("]\n");

        var powers = Powers.active(player);
        out.append("Active powers: ").append(powers.size()).append('\n');
        for (Power power : powers) {
            out.append("  ").append(power.id()).append("  (")
                    .append(power.component().getClass().getSimpleName()).append(')');
            boolean toggleKind = power.component() instanceof AbilityGeneType.Instance ability
                    && ability.mode() == AbilityGeneType.Mode.TOGGLE
                    || power.component() instanceof com.aetherianartificer.townstead.root.gene.types.ToggleGeneType.Instance;
            if (toggleKind) {
                out.append(AbilityToggles.isOnEffective(player, power.id()) ? "  [toggle ON]" : "  [toggle off]");
            }
            out.append('\n');
        }

        out.append("Root Ability key slots:\n");
        for (var entry : com.aetherianartificer.townstead.root.ability.ActiveAbilities.slotMap(player).entrySet()) {
            out.append("  ").append(entry.getKey()).append(" = ").append(entry.getValue().geneId())
                    .append(" (").append(entry.getValue().kind().name().toLowerCase(java.util.Locale.ROOT)).append(")\n");
        }

        String text = out.toString().stripTrailing();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
