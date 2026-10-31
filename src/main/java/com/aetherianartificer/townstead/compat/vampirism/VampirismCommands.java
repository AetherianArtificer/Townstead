package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.aspect.AspectPicks;
import com.aetherianartificer.townstead.commands.CompatCommands;
import com.aetherianartificer.townstead.pheno.state.EntityStateDefinition;
import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * {@code /townstead compat vampirism}: make someone a vampire or a dhampir, cure them, and show
 * their vampire states.
 */
public final class VampirismCommands {
    private static final ResourceLocation FACTION = ResourceLocation.tryParse("vampirism:vampire");
    private static final ResourceLocation VAMPIRE = ResourceLocation.tryParse("townstead_state:vampire");
    private static final ResourceLocation DHAMPIR = ResourceLocation.tryParse("townstead_state:dhampir");
    private static final ResourceLocation THRALL = ResourceLocation.tryParse("townstead_state:thrall");
    private static final ResourceLocation BLOOD = ResourceLocation.tryParse("townstead_state:vampire_blood");

    private VampirismCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("vampirism");

        LiteralArgumentBuilder<CommandSourceStack> make = Commands.literal("make");
        CompatCommands.withTargets(make, (node, targets) -> node
                .then(Commands.literal("vampire")
                        .executes(c -> make(c, targets, VAMPIRE, 1))
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 14))
                                .executes(c -> make(c, targets, VAMPIRE, IntegerArgumentType.getInteger(c, "level")))))
                .then(Commands.literal("dhampir").executes(c -> make(c, targets, DHAMPIR, 1))));
        root.then(make);

        LiteralArgumentBuilder<CommandSourceStack> cure = Commands.literal("cure");
        CompatCommands.withTargets(cure, (node, targets) -> node.executes(c -> cure(c, targets)));
        root.then(cure);

        LiteralArgumentBuilder<CommandSourceStack> status = Commands.literal("status");
        CompatCommands.withTargets(status, (node, targets) -> node.executes(c -> status(c, targets)));
        root.then(status);
        return root;
    }

    private static int make(CommandContext<CommandSourceStack> c, CompatCommands.Targets targets,
                            ResourceLocation aspect, int level) throws CommandSyntaxException {
        EntityStateDefinition definition = EntityStates.definition(aspect);
        if (definition == null || definition.aspect() == null) {
            CompatCommands.warn(c.getSource(), "The " + aspect.getPath() + " state is not loaded.");
            return 0;
        }
        int done = 0;
        for (LivingEntity target : targets.get(c)) {
            if (AspectPicks.become(target, definition, level, false)) {
                done++;
                CompatCommands.say(c.getSource(), CompatCommands.name(target) + " is a " + aspect.getPath()
                        + (aspect.equals(VAMPIRE) ? ", level " + level : "") + ".");
            } else {
                CompatCommands.warn(c.getSource(), CompatCommands.name(target) + " cannot be a " + aspect.getPath() + ".");
            }
        }
        return done;
    }

    private static int cure(CommandContext<CommandSourceStack> c, CompatCommands.Targets targets) throws CommandSyntaxException {
        int done = 0;
        for (LivingEntity target : targets.get(c)) {
            boolean was = EntityStates.resolve(target, VAMPIRE).active() || EntityStates.resolve(target, DHAMPIR).active();
            if (!was) {
                CompatCommands.warn(c.getSource(), CompatCommands.name(target) + " is neither a vampire nor a dhampir.");
                continue;
            }
            if (target instanceof Player player && FACTION.equals(VampirismStateProviders.factionOf(player))) {
                VampirismStateProviders.leaveFaction(player);
            }
            EntityStates.clear(target, VAMPIRE, null);
            EntityStates.clear(target, DHAMPIR, null);
            done++;
            CompatCommands.say(c.getSource(), CompatCommands.name(target) + " is cured.");
        }
        return done;
    }

    private static int status(CommandContext<CommandSourceStack> c, CompatCommands.Targets targets) throws CommandSyntaxException {
        int done = 0;
        for (LivingEntity target : targets.get(c)) {
            StringBuilder line = new StringBuilder(CompatCommands.name(target)).append(":");
            boolean any = false;
            for (ResourceLocation state : new ResourceLocation[]{VAMPIRE, DHAMPIR, THRALL, BLOOD}) {
                if (EntityStates.definition(state) == null) continue;
                EntityStates.Resolved resolved = EntityStates.resolve(target, state);
                if (!resolved.active()) continue;
                line.append(any ? "," : "").append(' ').append(state.getPath()).append(' ')
                        .append(Math.round(resolved.amount() * 10) / 10.0);
                if (resolved.tier() != null) line.append(" (").append(resolved.tier()).append(')');
                any = true;
            }
            if (!any) line.append(" none of the vampire states.");
            else done++;
            CompatCommands.say(c.getSource(), line.toString());
        }
        return done;
    }
}
