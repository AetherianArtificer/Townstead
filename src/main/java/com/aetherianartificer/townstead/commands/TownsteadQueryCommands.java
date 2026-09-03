package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.api.TownsteadAPI;
import com.aetherianartificer.townstead.api.TownsteadBuildingSnapshot;
import com.aetherianartificer.townstead.api.TownsteadCalendarSnapshot;
import com.aetherianartificer.townstead.api.TownsteadGeneSnapshot;
import com.aetherianartificer.townstead.api.TownsteadQuery;
import com.aetherianartificer.townstead.api.TownsteadRootSnapshot;
import com.aetherianartificer.townstead.api.TownsteadVillagerSnapshot;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.gene.GeneRegistry;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Read-only snapshot query command for datapack and modpack authors. */
public final class TownsteadQueryCommands {
    private TownsteadQueryCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("query")
                .then(Commands.literal("entity")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .suggests(pathSuggest(TownsteadVillagerSnapshot.class))
                                        .executes(c -> query(c.getSource(),
                                                TownsteadAPI.entity(EntityArgument.getEntity(c, "target")),
                                                StringArgumentType.getString(c, "path"))))))
                .then(Commands.literal("target")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .suggests(pathSuggest(TownsteadVillagerSnapshot.class))
                                .executes(c -> query(c.getSource(),
                                        TownsteadAPI.entity(lookedAtEntity(c.getSource(), 32.0D)),
                                        StringArgumentType.getString(c, "path")))))
                .then(Commands.literal("nearest")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .suggests(pathSuggest(TownsteadVillagerSnapshot.class))
                                .executes(c -> query(c.getSource(),
                                        TownsteadAPI.entity(nearestVillager(c.getSource(), 32.0D)),
                                        StringArgumentType.getString(c, "path"))))
                        .then(Commands.literal("within")
                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 256.0D))
                                        .then(Commands.argument("path", StringArgumentType.string())
                                                .suggests(pathSuggest(TownsteadVillagerSnapshot.class))
                                                .executes(c -> query(c.getSource(),
                                                        TownsteadAPI.entity(nearestVillager(c.getSource(),
                                                                DoubleArgumentType.getDouble(c, "radius"))),
                                                        StringArgumentType.getString(c, "path")))))))
                .then(Commands.literal("me")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .suggests(pathSuggest(TownsteadVillagerSnapshot.class))
                                .executes(c -> query(c.getSource(),
                                        TownsteadAPI.entity(c.getSource().getPlayerOrException()),
                                        StringArgumentType.getString(c, "path")))))
                .then(Commands.literal("villager")
                        .then(Commands.argument("uuid", StringArgumentType.word())
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .suggests(pathSuggest(TownsteadVillagerSnapshot.class))
                                        .executes(c -> query(c.getSource(),
                                                TownsteadAPI.entity(findEntity(
                                                        c.getSource().getServer(),
                                                        StringArgumentType.getString(c, "uuid"))),
                                                StringArgumentType.getString(c, "path"))))))
                .then(Commands.literal("calendar")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .suggests(pathSuggest(TownsteadCalendarSnapshot.class))
                                .executes(c -> query(c.getSource(),
                                        TownsteadAPI.calendar(c.getSource().getServer()),
                                        StringArgumentType.getString(c, "path")))))
                .then(Commands.literal("building")
                        .then(Commands.argument("path", StringArgumentType.string())
                                .suggests(pathSuggest(TownsteadBuildingSnapshot.class))
                                .executes(c -> query(c.getSource(),
                                        TownsteadAPI.buildingAt(
                                                c.getSource().getPlayerOrException().serverLevel(),
                                                c.getSource().getPlayerOrException().blockPosition()),
                                        StringArgumentType.getString(c, "path")))))
                .then(Commands.literal("root")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        RootRegistry.all().stream().map(r -> quote(r.id().toString())), b))
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .suggests(pathSuggest(TownsteadRootSnapshot.class))
                                        .executes(c -> query(c.getSource(),
                                                TownsteadAPI.origin(parseId(StringArgumentType.getString(c, "id"))),
                                                StringArgumentType.getString(c, "path"))))))
                .then(Commands.literal("gene")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        GeneRegistry.all().stream().map(g -> quote(g.id().toString())), b))
                                .then(Commands.argument("path", StringArgumentType.string())
                                        .suggests(pathSuggest(TownsteadGeneSnapshot.class))
                                        .executes(c -> query(c.getSource(),
                                                TownsteadAPI.gene(parseId(StringArgumentType.getString(c, "id"))),
                                        StringArgumentType.getString(c, "path"))))))));
        BuildingDiagnosticsCommands.register(dispatcher, ctx);
    }

    private static int query(CommandSourceStack source, Object snapshot, String path) {
        if (snapshot == null) {
            source.sendFailure(Component.translatable("command.townstead.query.no_snapshot"));
            return 0;
        }
        Object value = TownsteadQuery.resolve(snapshot, path);
        source.sendSuccess(() -> Component.literal(TownsteadQuery.render(value)), false);
        return TownsteadQuery.resultValue(value);
    }

    private static ResourceLocation parseId(String value) {
        return ResourceLocation.tryParse(value);
    }

    private static String quote(String value) {
        return "\"" + value + "\"";
    }

    /** Suggests the dotted snapshot paths for a record type, each wrapped in quotes. */
    private static SuggestionProvider<CommandSourceStack> pathSuggest(Class<?> snapshotType) {
        List<String> quoted = new ArrayList<>();
        collectPaths(snapshotType, "", quoted, 0);
        return (c, b) -> SharedSuggestionProvider.suggest(quoted, b);
    }

    private static void collectPaths(Class<?> type, String prefix, List<String> out, int depth) {
        if (type == null || !type.isRecord() || depth > 4) return;
        for (RecordComponent component : type.getRecordComponents()) {
            String path = prefix.isEmpty() ? component.getName() : prefix + "." + component.getName();
            out.add(quote(path));
            Class<?> componentType = component.getType();
            if (componentType.isRecord()) {
                collectPaths(componentType, path, out, depth + 1);
            } else if (List.class.isAssignableFrom(componentType)) {
                Class<?> element = listElement(component.getGenericType());
                if (element != null && element.isRecord()) {
                    String indexed = path + ".0";
                    out.add(quote(indexed));
                    collectPaths(element, indexed, out, depth + 1);
                }
            }
        }
    }

    private static Class<?> listElement(Type generic) {
        if (generic instanceof ParameterizedType parameterized
                && parameterized.getActualTypeArguments().length == 1
                && parameterized.getActualTypeArguments()[0] instanceof Class<?> element) {
            return element;
        }
        return null;
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
