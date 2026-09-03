package com.aetherianartificer.townstead.reaction.command;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.reaction.Reaction;
import com.aetherianartificer.townstead.reaction.ReactionContext;
import com.aetherianartificer.townstead.reaction.ReactionDispatcher;
import com.aetherianartificer.townstead.reaction.ReactionRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Debug commands for the reaction system:
 * <ul>
 *   <li>{@code /townstead reaction list} prints all registered reactions.
 *   <li>{@code /townstead reaction play <id>} plays on the MCA villager
 *       the caller is looking at (or the nearest one within 16 blocks
 *       if the crosshair isn't on a villager).
 *   <li>{@code /townstead reaction play <id> <target>} plays on an
 *       explicit selector target.
 * </ul>
 */
public final class ReactionCommand {
    private static final double LOOK_RANGE = 16.0;

    private ReactionCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        SuggestionProvider<CommandSourceStack> reactionIds = ReactionCommand::suggestReactionIds;

        dispatcher.register(
                Commands.literal("townstead").then(Commands.literal("reaction")
                        .then(Commands.literal("list").executes(c -> list(c.getSource())))
                        .then(Commands.literal("play")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .suggests(reactionIds)
                                        .executes(c -> playAuto(
                                                c.getSource(),
                                                StringArgumentType.getString(c, "id")))
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(c -> playExplicit(
                                                        c.getSource(),
                                                        StringArgumentType.getString(c, "id"),
                                                        EntityArgument.getEntity(c, "target"))))))));
    }

    private static int list(CommandSourceStack source) {
        Collection<Reaction> all = ReactionRegistry.all();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.townstead.reaction.none"), false);
            return 0;
        }
        List<Reaction> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparing(r -> r.id().toString()));
        source.sendSuccess(() -> Component.translatable(
                "command.townstead.reaction.list.heading", sorted.size()), false);
        for (Reaction reaction : sorted) {
            Component line = reaction.displayName()
                    .map(key -> Component.translatable("command.townstead.reaction.list.entry",
                            reaction.id(), Component.translatableWithFallback(key, key),
                            reaction.bindings().size(), reaction.rawTriggers().size()))
                    .orElseGet(() -> Component.translatable("command.townstead.reaction.list.entry.unnamed",
                            reaction.id(), reaction.bindings().size(), reaction.rawTriggers().size()));
            source.sendSuccess(() -> line, false);
        }
        return sorted.size();
    }

    private static int playAuto(CommandSourceStack source, String idRaw) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.townstead.reaction.player_required"));
            return 0;
        }
        VillagerEntityMCA villager = pickLookedAtOrNearest(player);
        if (villager == null) {
            source.sendFailure(Component.translatable(
                    "command.townstead.reaction.no_nearby_villager", (int) LOOK_RANGE));
            return 0;
        }
        return play(source, villager, idRaw);
    }

    private static int playExplicit(CommandSourceStack source, String idRaw, Entity target) {
        if (!(target instanceof VillagerEntityMCA villager)) {
            source.sendFailure(Component.translatable("command.townstead.reaction.invalid_target"));
            return 0;
        }
        return play(source, villager, idRaw);
    }

    private static int play(CommandSourceStack source, VillagerEntityMCA villager, String idRaw) {
        ResourceLocation id = parseId(source, idRaw);
        if (id == null) return 0;
        if (ReactionRegistry.get(id).isEmpty()) {
            source.sendFailure(Component.translatable("command.townstead.reaction.unknown", id));
            return 0;
        }
        ServerLevel level = (ServerLevel) villager.level();
        boolean played = ReactionDispatcher.fire(level, (LivingEntity) villager, id,
                ReactionContext.command(villager.blockPosition()));
        if (played) {
            source.sendSuccess(() -> Component.translatable(
                            "command.townstead.reaction.playing", id, villager.getDisplayName()),
                    false);
            return 1;
        }
        source.sendFailure(Component.translatable("command.townstead.reaction.not_played", id));
        return 0;
    }

    /**
     * Look for an MCA villager along the player's gaze (cylinder around
     * the look ray); fall back to the nearest one inside {@link #LOOK_RANGE}
     * if the ray misses.
     */
    private static VillagerEntityMCA pickLookedAtOrNearest(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 endpoint = eye.add(look.scale(LOOK_RANGE));
        AABB sweep = new AABB(eye, endpoint).inflate(LOOK_RANGE);
        VillagerEntityMCA bestLook = null;
        double bestLookT = Double.POSITIVE_INFINITY;
        VillagerEntityMCA bestNear = null;
        double bestNearDist = Double.POSITIVE_INFINITY;
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, sweep)) {
            Vec3 toVillager = villager.position().subtract(eye);
            double along = toVillager.dot(look);
            if (along > 0 && along <= LOOK_RANGE) {
                Vec3 closestOnRay = eye.add(look.scale(along));
                double perp = villager.position().distanceTo(closestOnRay);
                if (perp <= Math.max(0.7, villager.getBbWidth())) {
                    if (along < bestLookT) {
                        bestLookT = along;
                        bestLook = villager;
                    }
                }
            }
            double dist = player.distanceToSqr(villager);
            if (dist < bestNearDist) {
                bestNearDist = dist;
                bestNear = villager;
            }
        }
        if (bestLook != null) return bestLook;
        if (bestNear != null && bestNearDist <= LOOK_RANGE * LOOK_RANGE) return bestNear;
        return null;
    }

    private static ResourceLocation parseId(CommandSourceStack source, String raw) {
        try {
            //? if neoforge {
            return ResourceLocation.parse(raw);
            //?} else {
            /*return new ResourceLocation(raw);
            *///?}
        } catch (Exception e) {
            source.sendFailure(Component.translatable("command.townstead.reaction.invalid_id", raw));
            Townstead.LOGGER.debug("Invalid reaction id from command: {}", raw);
            return null;
        }
    }

    private static CompletableFuture<Suggestions> suggestReactionIds(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (Reaction r : ReactionRegistry.all()) {
            String id = r.id().toString();
            if (id.toLowerCase().contains(remaining)) builder.suggest(id);
        }
        return builder.buildFuture();
    }
}
