package com.aetherianartificer.townstead.emote;

import com.aetherianartificer.townstead.Townstead;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * {@code /townstead emote play <id> [target]} and {@code /townstead emote stop
 * [target]}. With no target, applies to the calling player. With a target, server
 * validates the entity is a player or {@link VillagerEntityMCA} before broadcasting
 * an {@link EmoteTriggerS2CPayload} to its trackers.
 */
public final class EmoteCommand {
    private EmoteCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(
                Commands.literal("townstead").then(Commands.literal("emote")
                        .then(Commands.literal("play")
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .executes(c -> playSelf(c.getSource(), StringArgumentType.getString(c, "id")))
                                        .then(Commands.argument("target", EntityArgument.entity())
                                                .executes(c -> playOnTarget(
                                                        c.getSource(),
                                                        StringArgumentType.getString(c, "id"),
                                                        EntityArgument.getEntity(c, "target"))))))
                        .then(Commands.literal("stop")
                                .executes(c -> stopSelf(c.getSource()))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(c -> stopOnTarget(
                                                c.getSource(),
                                                EntityArgument.getEntity(c, "target")))))));
    }

    private static int playSelf(CommandSourceStack source, String idString) {
        ServerPlayer sp = source.getPlayer();
        if (sp == null) {
            source.sendFailure(Component.translatable("command.townstead.emote.player_required"));
            return 0;
        }
        ResourceLocation id = parseId(source, idString);
        if (id == null) return 0;
        AiEmoteScheduler.playEmote(sp, id);
        com.aetherianartificer.townstead.reaction.trigger.event.GestureBroadcaster.broadcast(
                sp.serverLevel(), sp, id.getPath());
        source.sendSuccess(() -> Component.translatable("command.townstead.emote.playing.self", id), false);
        return 1;
    }

    private static int playOnTarget(CommandSourceStack source, String idString, Entity target) {
        ResourceLocation id = parseId(source, idString);
        if (id == null) return 0;
        if (!(target instanceof Player) && !(target instanceof VillagerEntityMCA)) {
            source.sendFailure(Component.translatable("command.townstead.emote.invalid_target"));
            return 0;
        }
        AiEmoteScheduler.playEmote((LivingEntity) target, id);
        if (target.level() instanceof net.minecraft.server.level.ServerLevel level) {
            com.aetherianartificer.townstead.reaction.trigger.event.GestureBroadcaster.broadcast(
                    level, target, id.getPath());
        }
        source.sendSuccess(() -> Component.translatable(
                "command.townstead.emote.playing.target", id, target.getDisplayName()), false);
        return 1;
    }

    private static int stopSelf(CommandSourceStack source) {
        ServerPlayer sp = source.getPlayer();
        if (sp == null) {
            source.sendFailure(Component.translatable("command.townstead.emote.player_required"));
            return 0;
        }
        AiEmoteScheduler.stopEmote(sp);
        source.sendSuccess(() -> Component.translatable("command.townstead.emote.stopped.self"), false);
        return 1;
    }

    private static int stopOnTarget(CommandSourceStack source, Entity target) {
        if (!(target instanceof Player) && !(target instanceof VillagerEntityMCA)) {
            source.sendFailure(Component.translatable("command.townstead.emote.invalid_target"));
            return 0;
        }
        AiEmoteScheduler.stopEmote((LivingEntity) target);
        source.sendSuccess(() -> Component.translatable(
                "command.townstead.emote.stopped.target", target.getDisplayName()), false);
        return 1;
    }

    private static ResourceLocation parseId(CommandSourceStack source, String raw) {
        try {
            //? if neoforge {
            return ResourceLocation.parse(raw);
            //?} else {
            /*return new ResourceLocation(raw);
            *///?}
        } catch (Exception e) {
            source.sendFailure(Component.translatable("command.townstead.emote.invalid_id", raw));
            Townstead.LOGGER.debug("Invalid emote id from command: {}", raw);
            return null;
        }
    }
}
