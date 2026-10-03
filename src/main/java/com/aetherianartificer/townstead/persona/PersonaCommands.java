package com.aetherianartificer.townstead.persona;

import java.util.List;
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
 * {@code /townstead persona [list | spawn <id> | reset <id> | travel <id>]}. Operator only.
 * {@code spawn} brings a Persona to the village you stand in, next to you, ignoring its arrival
 * goals. {@code reset} forgets that Persona for you in this village: the villager stays as an
 * ordinary resident, your bond and your story progress with them are cleared. When no village
 * still has that Persona, what the world rolled for them is cleared too. {@code travel} sends the
 * Persona living in this village on the road, ready to arrive in another village at once.
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
                                .executes(c -> reset(c.getSource(), ResourceLocationArgument.getId(c, "id")))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("id", ResourceLocationArgument.id()).suggests(IDS)
                                .executes(c -> remove(c.getSource(), ResourceLocationArgument.getId(c, "id")))))
                .then(Commands.literal("travel")
                        .then(Commands.argument("id", ResourceLocationArgument.id()).suggests(IDS)
                                .executes(c -> travel(c.getSource(), ResourceLocationArgument.getId(c, "id")))))));
    }

    private static int list(CommandSourceStack source) {
        if (Personas.all().isEmpty()) source.sendSuccess(() -> Component.literal("No Personas are loaded."), false);
        PersonaInstances instances = PersonaInstances.get(source.getServer());
        for (PersonaDefinition persona : Personas.all().values()) {
            int living = instances.of(persona.id()).size();
            String line = persona.id() + " (" + persona.name() + "): " + persona.arrives().size() + " arrival goals, "
                    + living + " in the world";
            source.sendSuccess(() -> Component.literal(line), false);
            for (PersonaInstances.Instance instance : instances.of(persona.id())) {
                VillagerEntityMCA villager = PersonaService.find(source.getServer(), instance.villager());
                String where = villager == null ? "not loaded, village " + instance.village() + " in " + instance.dimension()
                        : villager.getBlockX() + " " + villager.getBlockY() + " " + villager.getBlockZ();
                source.sendSuccess(() -> Component.literal("  " + instance.name() + ": " + where), false);
            }
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
        if (PersonaService.takenElsewhere(source.getServer(), persona)) {
            return fail(source, persona.name() + " is one of a kind and already lives, or is on their way, somewhere else.");
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
        PersonaBonds.forgetGifts(player, id);
        removed |= StoryService.reset(player, Personas.storyId(id));
        if (instances.of(id).isEmpty()) instances.clearRolls(id);
        String what = removed ? "Reset " + id + " for you here." : "Nothing to reset for " + id + ".";
        source.sendSuccess(() -> Component.literal(what), false);
        return removed ? 1 : 0;
    }

    /**
     * Removes every copy of a Persona from the world: each loaded one is taken out, each one in an
     * unloaded place is unlinked and stays as an ordinary villager. Your bond and story with them
     * are cleared, and so is what the world rolled for them.
     */
    private static int remove(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PersonaInstances instances = PersonaInstances.get(source.getServer());
        int removed = 0, unloaded = 0;
        for (PersonaInstances.Instance instance : List.copyOf(instances.of(id))) {
            VillagerEntityMCA villager = PersonaService.find(source.getServer(), instance.villager());
            instances.remove(instance.villager());
            if (villager != null) {
                villager.discard();
                removed++;
            } else {
                unloaded++;
            }
        }
        PersonaBonds.remove(player, id);
        PersonaBonds.forgetGifts(player, id);
        StoryService.reset(player, Personas.storyId(id));
        instances.clearRolls(id);
        int gone = removed, left = unloaded;
        source.sendSuccess(() -> Component.literal("Removed " + gone + " of " + id
                + (left > 0 ? "; " + left + " in unloaded places stay as ordinary villagers" : "") + "."), false);
        return gone + left;
    }

    private static int travel(CommandSourceStack source, ResourceLocation id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        PersonaDefinition persona = Personas.byId(id);
        Village village = TownRange.at(player.serverLevel(), player.blockPosition()).orElse(null);
        if (persona == null || village == null) return fail(source, "Stand in the village where that Persona lives.");
        PersonaInstances.Instance instance = PersonaInstances.get(source.getServer())
                .in(id, player.serverLevel().dimension().location(), village.getId());
        VillagerEntityMCA villager = instance == null ? null : PersonaService.find(source.getServer(), instance.villager());
        if (villager == null) return fail(source, persona.name() + " does not live here, or is not loaded.");
        if (!PersonaMoves.send(source.getServer(), persona, instance, villager, "command", 0)) {
            return fail(source, "Could not send " + persona.name() + " on the road.");
        }
        source.sendSuccess(() -> Component.literal(persona.name() + " is on the road."), false);
        return 1;
    }

    private static int fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message));
        return 0;
    }
}
