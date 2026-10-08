package com.aetherianartificer.townstead.pet;

import com.aetherianartificer.townstead.commands.CommandTargets;
import com.aetherianartificer.townstead.journey.Journeys;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Test commands: {@code /townstead pet adopt <entity_type>} gives the villager you look at a new pet;
 * {@code /townstead pet errand <days>} sends them on an errand.
 */
public final class PetCommands {
    private PetCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("pet")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("adopt").then(Commands.argument("entity_type", ResourceLocationArgument.id())
                        .executes(c -> adopt(c.getSource(), ResourceLocationArgument.getId(c, "entity_type")))))
                .then(Commands.literal("errand").then(Commands.argument("days", IntegerArgumentType.integer(0, 30))
                        .executes(c -> errand(c.getSource(), IntegerArgumentType.getInteger(c, "days")))))));
    }

    private static int adopt(CommandSourceStack source, ResourceLocation type) {
        ServerPlayer player = source.getPlayer();
        VillagerEntityMCA villager = player == null ? null : CommandTargets.lookedAtOrNearest(player, null);
        var entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(type).orElse(null);
        if (villager == null || entityType == null || VillagerPets.adoptNew(source.getLevel(), entityType, villager) == null) {
            source.sendFailure(Component.literal("Could not give that villager a " + type + "."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(villager.getName().getString() + " has a new " + type.getPath() + "."), true);
        return 1;
    }

    private static int errand(CommandSourceStack source, int days) {
        ServerPlayer player = source.getPlayer();
        VillagerEntityMCA villager = player == null ? null : CommandTargets.lookedAtOrNearest(player, null);
        if (villager == null) {
            source.sendFailure(Component.literal("Look at a villager first."));
            return 0;
        }
        String name = villager.getName().getString();
        if (!Journeys.errand(villager, days, null)) {
            source.sendFailure(Component.literal("Could not send " + name + " on an errand."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(name + " left on an errand for " + days + " day(s)."), true);
        return 1;
    }
}
