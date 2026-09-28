package com.aetherianartificer.townstead.politics.order;

import com.aetherianartificer.townstead.commands.CommandTargets;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Test commands for orders (any landless faction kind based at a town) until they arise in play:
 * {@code /townstead order found <kind>} makes the villager you look at the head of a new order at
 * this village and hands you what its founding gives; {@code /townstead order swear <kind>} swears
 * the villager you look at into the order of that kind based here.
 */
public final class OrderCommands {
    private static final SuggestionProvider<CommandSourceStack> KINDS = (context, builder) ->
            SharedSuggestionProvider.suggest(PoliticalDefinitions.snapshot().kinds().stream()
                    .filter(kind -> !kind.holdsLand()).map(kind -> kind.id().toString()), builder);

    private OrderCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("order")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("found").then(Commands.argument("kind", StringArgumentType.greedyString())
                        .suggests(KINDS).executes(context -> found(context.getSource(), StringArgumentType.getString(context, "kind")))))
                .then(Commands.literal("swear").then(Commands.argument("kind", StringArgumentType.greedyString())
                        .suggests(KINDS).executes(context -> swear(context.getSource(), StringArgumentType.getString(context, "kind")))))));
    }

    private static int found(CommandSourceStack source, String rawKind) {
        ServerPlayer player = source.getPlayer();
        Village town = town(source);
        if (player == null || town == null) return fail(source, "command.townstead.order.no_village");
        FactionKind kind = kind(rawKind);
        if (kind == null || kind.holdsLand()) return fail(source, "command.townstead.order.unknown_kind");
        VillagerEntityMCA leader = CommandTargets.lookedAtOrNearest(player, null);
        Faction order = Orders.found(source.getLevel(), town, kind.id(), leader == null ? null : leader.getUUID());
        if (order == null) return fail(source, "command.townstead.order.failed");
        for (ResourceLocation gift : kind.founding().gifts()) {
            BuiltInRegistries.ITEM.getOptional(gift).ifPresent(item -> {
                ItemStack stack = new ItemStack(item);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            });
        }
        if (!kind.founding().teaches().isEmpty()) {
            //? if >=1.21 {
            player.awardRecipesByKey(kind.founding().teaches());
            //?} else {
            /*player.awardRecipesByKey(kind.founding().teaches().toArray(new ResourceLocation[0]));
            *///?}
        }
        source.sendSuccess(() -> Component.translatable("command.townstead.order.founded", order.name(), town.getName(),
                leader == null ? Component.translatable("command.townstead.order.nobody") : leader.getName()), true);
        return 1;
    }

    private static int swear(CommandSourceStack source, String rawKind) {
        ServerPlayer player = source.getPlayer();
        Village town = town(source);
        if (player == null || town == null) return fail(source, "command.townstead.order.no_village");
        FactionKind kind = kind(rawKind);
        if (kind == null) return fail(source, "command.townstead.order.unknown_kind");
        Faction order = Orders.at(PoliticalSavedData.get(source.getServer()),
                new SettlementRef(source.getLevel().dimension().location(), town.getId()), kind.id());
        if (order == null) return fail(source, "command.townstead.order.none_here");
        VillagerEntityMCA villager = CommandTargets.lookedAtOrNearest(player, null);
        if (villager == null || !Orders.swear(source.getLevel(), order, villager.getUUID())) {
            return fail(source, "command.townstead.order.not_sworn");
        }
        source.sendSuccess(() -> Component.translatable("command.townstead.order.sworn", villager.getName(), order.name()), true);
        return 1;
    }

    private static FactionKind kind(String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw.trim());
        return id == null ? null : PoliticalDefinitions.snapshot().kind(id);
    }

    private static Village town(CommandSourceStack source) {
        return VillageManager.get(source.getLevel())
                .findNearestVillage(net.minecraft.core.BlockPos.containing(source.getPosition()), Village.MERGE_MARGIN).orElse(null);
    }

    private static int fail(CommandSourceStack source, String key) {
        source.sendFailure(Component.translatable(key));
        return 0;
    }
}
