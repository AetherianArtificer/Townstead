package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * {@code /townstead compat}: test tools for each mod Townstead works with, one branch per mod,
 * there only when that mod is installed. Bare, it lists the branches this game has.
 */
public final class CompatCommands {
    private static final List<String> ACTIVE = new ArrayList<>();

    private CompatCommands() {}

    /** The entities a command was pointed at. */
    @FunctionalInterface
    public interface Targets {
        List<LivingEntity> get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        ACTIVE.clear();
        LiteralArgumentBuilder<CommandSourceStack> compat = Commands.literal("compat")
                .requires(source -> source.hasPermission(2))
                .executes(context -> list(context.getSource()));
        if (ModCompat.isLoaded("vampirism")) {
            compat.then(com.aetherianartificer.townstead.compat.vampirism.VampirismCommands.branch());
            ACTIVE.add("vampirism");
            if (ModCompat.isLoaded("werewolves")) {
                compat.then(com.aetherianartificer.townstead.compat.werewolves.WerewolfCommands.branch());
                ACTIVE.add("werewolves");
            }
        }
        dispatcher.register(Commands.literal("townstead").then(compat));
    }

    private static int list(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(ACTIVE.isEmpty()
                ? "No compat commands: none of their mods are installed."
                : "Compat commands: " + String.join(", ", ACTIVE)).withStyle(ChatFormatting.GOLD), false);
        return ACTIVE.size();
    }

    /**
     * Adds two ways to name targets under {@code parent}: {@code looking} (the villager you look
     * at, else the nearest) and an entity selector. {@code tail} finishes each one.
     */
    public static void withTargets(ArgumentBuilder<CommandSourceStack, ?> parent,
                                   BiConsumer<ArgumentBuilder<CommandSourceStack, ?>, Targets> tail) {
        ArgumentBuilder<CommandSourceStack, ?> looking = Commands.literal("looking");
        tail.accept(looking, context -> {
            Entity villager = CommandTargets.lookedAtOrNearest(context.getSource().getPlayerOrException(), null);
            if (villager == null) {
                throw new SimpleCommandExceptionType(Component.literal("No villager in sight or nearby.")).create();
            }
            return List.of((LivingEntity) villager);
        });
        parent.then(looking);
        ArgumentBuilder<CommandSourceStack, ?> selected = Commands.argument("targets", EntityArgument.entities());
        tail.accept(selected, context -> {
            List<LivingEntity> out = new ArrayList<>();
            for (Entity entity : EntityArgument.getEntities(context, "targets")) {
                if (entity instanceof LivingEntity living) out.add(living);
            }
            return out;
        });
        parent.then(selected);
    }

    /** A line of feedback. */
    public static void say(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    /** A line of feedback for something that did not happen. */
    public static void warn(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text).withStyle(ChatFormatting.YELLOW), false);
    }

    public static String name(Entity entity) {
        return entity.getDisplayName().getString();
    }
}
