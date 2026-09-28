package com.aetherianartificer.townstead.persona;

import com.aetherianartificer.townstead.story.Stories;
import com.aetherianartificer.townstead.story.StoryService;
import com.aetherianartificer.townstead.village.TownRange;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /townstead persona [list | spawn <id> | reset <id>]}. Operator only.
 * {@code spawn} brings a Persona to the village you stand in, next to you, ignoring its arrival
 * goals. {@code reset} forgets that Persona for you in this village: the villager stays as an
 * ordinary resident, your bond and your story progress with them are cleared.
 */
public final class PersonaCommands {
    private PersonaCommands() {}

    private static final SuggestionProvider<CommandSourceStack> IDS = (ctx, builder) ->
            SharedSuggestionProvider.suggestResource(Personas.all().keySet(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("persona")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(Commands.literal("spawn")
                        .then(Commands.argument("id", ResourceLocationArgument.id()).suggests(IDS)
                                .executes(c -> spawn(c.getSource(), ResourceLocationArgument.getId(c, "id")))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("id", ResourceLocationArgument.id()).suggests(IDS)
                                .executes(c -> reset(c.getSource(), ResourceLocationArgument.getId(c, "id")))))));
    }

    private static int list(CommandSourceStack source) {
        if (Personas.all().isEmpty()) source.sendSuccess(() -> Component.literal("No Personas are loaded."), false);
        PersonaInstances instances = PersonaInstances.get(source.getServer());
        for (PersonaDefinition persona : Personas.all().values()) {
            int living = instances.of(persona.id()).size();
            String line = persona.id() + " (" + persona.name() + "): " + persona.arrives().size() + " arrival goals, "
                    + living + " in the world";
            source.sendSuccess(() -> Component.literal(line), false);
        }
        for (var entry : Stories.problems().entrySet()) {
            if (!entry.getKey().getPath().startsWith("persona/")) continue;
            for (String problem : entry.getValue()) {
                ChatFormatting color = problem.startsWith("error:") ? ChatFormatting.RED : ChatFormatting.YELLOW;
                source.sendSuccess(() -> Component.literal(entry.getKey() + " " + problem).withStyle(color), false);
            }
        }
        return Personas.all().size();
    }

    private static int spawn(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PersonaDefinition persona = Personas.byId(id);
        if (persona == null) return fail(source, "No Persona " + id + ". See /townstead persona list.");
        Village village = TownRange.at(player.serverLevel(), player.blockPosition()).orElse(null);
        if (village == null) return fail(source, "Stand in a village first.");
        if (PersonaInstances.get(source.getServer()).in(id, player.serverLevel().dimension().location(), village.getId()) != null) {
            return fail(source, persona.name() + " already lives in this village. Use /townstead persona reset " + id + " first.");
        }
        VillagerEntityMCA villager = PersonaService.spawn(persona, player, village, true);
        if (villager == null) return fail(source, "Could not spawn " + persona.name() + " here.");
        source.sendSuccess(() -> Component.literal("Spawned " + persona.name() + "."), false);
        return 1;
    }

    private static int reset(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PersonaInstances instances = PersonaInstances.get(source.getServer());
        Village village = TownRange.at(player.serverLevel(), player.blockPosition()).orElse(null);
        boolean removed = false;
        if (village != null) {
            PersonaInstances.Instance instance = instances.in(id, player.serverLevel().dimension().location(), village.getId());
            if (instance != null) removed = instances.remove(instance.villager());
        }
        removed |= PersonaBonds.remove(player, id);
        removed |= StoryService.reset(player, Personas.storyId(id));
        String what = removed ? "Reset " + id + " for you here." : "Nothing to reset for " + id + ".";
        source.sendSuccess(() -> Component.literal(what), false);
        return removed ? 1 : 0;
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
