package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.root.RootAssignment;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.RootServerLogic;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * {@code /townstead root set <targets> <root>}, {@code /townstead root get <target>}, and
 * {@code /townstead root discover|forget <root>} for Discoverable Roots.
 * Set goes through {@link RootAssignment}, the same path as the public API.
 */
public final class RootCommand {

    private RootCommand() {}

    private static final SuggestionProvider<CommandSourceStack> ROOT_IDS = (c, b) -> {
        List<String> ids = new ArrayList<>();
        RootRegistry.all().forEach(r -> {
            if (!com.aetherianartificer.townstead.root.RootRules.isOff(r.id())) ids.add("\"" + r.id() + "\"");
        });
        return SharedSuggestionProvider.suggest(ids, b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("root")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(ROOT_IDS)
                                        .executes(c -> set(c.getSource(),
                                                EntityArgument.getEntities(c, "targets"),
                                                StringArgumentType.getString(c, "id"))))))
                .then(Commands.literal("get")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(c -> get(c.getSource(), EntityArgument.getEntity(c, "target")))))
                .then(Commands.literal("discover")
                        .then(Commands.argument("id", StringArgumentType.string()).suggests(ROOT_IDS)
                                .executes(c -> discover(c.getSource(), StringArgumentType.getString(c, "id"), true))))
                .then(Commands.literal("forget")
                        .then(Commands.argument("id", StringArgumentType.string()).suggests(ROOT_IDS)
                                .executes(c -> discover(c.getSource(), StringArgumentType.getString(c, "id"), false))))));
    }

    private static int discover(CommandSourceStack source, String rawId, boolean discover) {
        ResourceLocation id = RootServerLogic.resolveKnown(rawId);
        if (id == null) {
            source.sendFailure(Component.translatable("command.townstead.root.unknown", rawId));
            return 0;
        }
        boolean changed = discover
                ? com.aetherianartificer.townstead.root.RootDiscovery.discover(source.getServer(), id, source.getTextName())
                : com.aetherianartificer.townstead.root.RootDiscovery.forget(source.getServer(), id);
        String key = "command.townstead.root." + (discover ? "discover" : "forget");
        if (!changed) {
            source.sendFailure(Component.translatable(key + ".unchanged", id.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(key, id.toString()), true);
        return 1;
    }

    private static int set(CommandSourceStack source, Collection<? extends Entity> targets, String rawId) {
        ResourceLocation id = RootServerLogic.resolveKnown(rawId);
        if (id == null) {
            source.sendFailure(Component.translatable("command.townstead.root.unknown", rawId));
            return 0;
        }
        Entity last = null;
        int eligible = 0;
        int changed = 0;
        for (Entity entity : targets) {
            if (RootAssignment.currentRoot(entity) == null) continue;
            eligible++;
            last = entity;
            if (RootAssignment.assign(entity, id)) changed++;
        }
        if (eligible == 0) {
            source.sendFailure(Component.translatable("command.townstead.root.invalid_target"));
            return 0;
        }
        if (changed == 0) {
            source.sendFailure(Component.translatable("command.townstead.root.unchanged", id.toString()));
            return 0;
        }
        Entity only = last;
        int count = changed;
        boolean single = count == 1 && eligible == 1;
        source.sendSuccess(() -> single
                ? Component.translatable("command.townstead.root.set.single", only.getDisplayName(), id.toString())
                : Component.translatable("command.townstead.root.set.multiple", count, id.toString()), true);
        return changed;
    }

    private static int get(CommandSourceStack source, Entity entity) {
        String rootId = RootAssignment.currentRoot(entity);
        if (rootId == null) {
            source.sendFailure(Component.translatable("command.townstead.root.invalid_target"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.townstead.root.get",
                entity.getDisplayName(), rootId), false);
        return 1;
    }
}
