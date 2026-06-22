package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.api.TownsteadAPI;
import com.aetherianartificer.townstead.api.TownsteadQuery;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Read-only snapshot query command for datapack and modpack authors. */
public final class TownsteadQueryCommands {
    private TownsteadQueryCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("query")
                .then(Commands.literal("entity")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .executes(c -> query(c.getSource(),
                                                TownsteadAPI.entity(EntityArgument.getEntity(c, "target")),
                                                StringArgumentType.getString(c, "path"))))))
                .then(Commands.literal("target")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .executes(c -> query(c.getSource(),
                                        TownsteadAPI.entity(lookedAtEntity(c.getSource(), 32.0D)),
                                        StringArgumentType.getString(c, "path")))))
                .then(Commands.literal("nearest")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .executes(c -> query(c.getSource(),
                                        TownsteadAPI.entity(nearestVillager(c.getSource(), 32.0D)),
                                        StringArgumentType.getString(c, "path"))))
                        .then(Commands.literal("within")
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 256.0D))
                                        .then(Commands.argument("path", StringArgumentType.string())
                                                .executes(c -> query(c.getSource(),
                                                        TownsteadAPI.entity(nearestVillager(c.getSource(),
                                                                DoubleArgumentType.getDouble(c, "radius"))),
                                                        StringArgumentType.getString(c, "path")))))))
                .then(Commands.literal("me")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .executes(c -> query(c.getSource(),
                                        TownsteadAPI.entity(c.getSource().getPlayerOrException()),
                                        StringArgumentType.getString(c, "path")))))
                .then(Commands.literal("villager")
                        .then(Commands.argument("uuid", StringArgumentType.word())
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .executes(c -> query(c.getSource(),
                                                TownsteadAPI.entity(findEntity(
                                                        c.getSource().getServer(),
                                                        StringArgumentType.getString(c, "uuid"))),
                                                StringArgumentType.getString(c, "path"))))))
                .then(Commands.literal("calendar")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .executes(c -> query(c.getSource(),
                                        TownsteadAPI.calendar(c.getSource().getServer()),
                                        StringArgumentType.getString(c, "path")))))
                .then(Commands.literal("origin")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .executes(c -> query(c.getSource(),
                                                TownsteadAPI.origin(parseId(StringArgumentType.getString(c, "id"))),
                                                StringArgumentType.getString(c, "path"))))))
                .then(Commands.literal("gene")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .executes(c -> query(c.getSource(),
                                                TownsteadAPI.gene(parseId(StringArgumentType.getString(c, "id"))),
                                                StringArgumentType.getString(c, "path"))))))));
    }

    private static int query(CommandSourceStack source, Object snapshot, String path) {
        if (snapshot == null) {
            source.sendFailure(Component.literal("Townstead query: no snapshot available."));
            return 0;
        }
        Object value = TownsteadQuery.resolve(snapshot, path);
        source.sendSuccess(() -> Component.literal(path + " = ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(TownsteadQuery.render(value)).withStyle(ChatFormatting.AQUA)), false);
        return TownsteadQuery.resultValue(value);
    }

    private static ResourceLocation parseId(String value) {
        return ResourceLocation.tryParse(value);
    }

    private static Entity findEntity(MinecraftServer server, String uuidString) {
        UUID uuid;
        try {
            uuid = UUID.fromString(uuidString);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity != null) return entity;
        }
        return null;
    }

    private static Entity nearestVillager(CommandSourceStack source, double radius) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AABB box = player.getBoundingBox().inflate(radius);
        VillagerEntityMCA nearest = null;
        double best = radius * radius;
        for (VillagerEntityMCA villager : player.serverLevel().getEntitiesOfClass(VillagerEntityMCA.class, box)) {
            double dist = villager.distanceToSqr(player);
            if (dist > best) continue;
            best = dist;
            nearest = villager;
        }
        return nearest;
    }

    private static Entity lookedAtEntity(CommandSourceStack source, double range) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.getLookAngle().scale(range));
        AABB search = player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(1.0D);
        Entity best = null;
        double bestDistance = range * range;
        for (Entity entity : player.serverLevel().getEntities(player, search, e -> TownsteadAPI.entity(e) != null)) {
            AABB bounds = entity.getBoundingBox().inflate(entity.getPickRadius());
            java.util.Optional<Vec3> hit = bounds.clip(start, end);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance > bestDistance) continue;
            bestDistance = distance;
            best = entity;
        }
        return best;
    }
}
