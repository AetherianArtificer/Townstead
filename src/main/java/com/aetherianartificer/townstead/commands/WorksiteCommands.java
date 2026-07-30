package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteBindings;
import com.aetherianartificer.townstead.work.site.WorksiteNames;
import com.aetherianartificer.townstead.work.site.WorksiteRegister;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code /townstead worksite here | list | name <name>} — the registered places villagers work.
 *
 * <p>Worksites are created automatically, which is convenient right up to the moment a player wants
 * to know why a villager thinks a room is somewhere else. These commands are the surface that makes
 * an invisible register answerable: what is registered, which one am I standing in, and what is it
 * called.</p>
 */
public final class WorksiteCommands {

    private WorksiteCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(
                Commands.literal("townstead").then(Commands.literal("worksite")
                        .then(Commands.literal("here").executes(c -> here(c.getSource())))
                        .then(Commands.literal("list").executes(c -> list(c.getSource())))
                        .then(Commands.literal("orders").executes(c -> orders(c.getSource())))
                        .then(Commands.literal("name")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> name(c.getSource(),
                                                StringArgumentType.getString(c, "name")))))));
    }

    // ── here ──

    private static int here(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Worksite site = siteAt(level, BlockPos.containing(source.getPosition()));
        if (site == null) {
            source.sendSuccess(() -> Component.literal(
                    "No worksite registered here. One is created the first time a villager works a station."),
                    false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(describe(site)), false);
        return 1;
    }

    // ── list ──

    private static int list(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        WorksiteRegister register = WorksiteRegister.get(level.getServer());
        BlockPos origin = BlockPos.containing(source.getPosition());

        List<Worksite> here = new ArrayList<>();
        for (Worksite site : register.all()) {
            if (site.key().dimension().equals(level.dimension().location())) here.add(site);
        }
        if (here.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No worksites registered in this dimension."), false);
            return 0;
        }
        here.sort(Comparator.comparingLong(Worksite::id));

        source.sendSuccess(() -> Component.literal(here.size() + " worksite(s):"), false);
        for (Worksite site : here) {
            source.sendSuccess(() -> Component.literal("  " + describe(site)), false);
        }
        return here.size();
    }

    // ── orders ──

    private static int orders(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Worksite site = siteAt(level, BlockPos.containing(source.getPosition()), true);
        if (site == null) {
            source.sendFailure(Component.literal(
                    "No worksite here. Stand inside a recognised building, or on a workstation block."));
            return 0;
        }
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can open the orders screen."));
            return 0;
        }
        return com.aetherianartificer.townstead.work.order.OrdersOpener.open(player, site) ? 1 : 0;
    }

    // ── name ──

    private static int name(CommandSourceStack source, String requested) {
        ServerLevel level = source.getLevel();
        Worksite site = siteAt(level, BlockPos.containing(source.getPosition()), true);
        if (site == null) {
            source.sendFailure(Component.literal(
                    "No worksite here. Stand inside a recognised building, or on a workstation block."));
            return 0;
        }
        String cleaned = WorksiteNames.sanitise(requested);
        if (cleaned == null) {
            source.sendFailure(Component.literal("That name is empty once trimmed. Try another."));
            return 0;
        }
        site.setName(cleaned);
        WorksiteRegister.get(level.getServer()).setDirty();
        source.sendSuccess(() -> Component.literal("Renamed to " + cleaned + "."), true);
        return 1;
    }

    // ── Shared ──

    /**
     * The registered worksite covering this position. Shared with the Order Board and the
     * conversation door via {@code OrdersOpener}, so a command and a block standing in the same
     * room can never disagree about which place that is.
     */
    @Nullable
    private static Worksite siteAt(ServerLevel level, BlockPos pos) {
        return com.aetherianartificer.townstead.work.order.OrdersOpener.siteAt(level, pos, false);
    }

    @Nullable
    private static Worksite siteAt(ServerLevel level, BlockPos pos, boolean createIfMissing) {
        return com.aetherianartificer.townstead.work.order.OrdersOpener.siteAt(level, pos, createIfMissing);
    }

    private static String describe(Worksite site) {
        StringBuilder out = new StringBuilder();
        out.append('#').append(site.id()).append(' ').append(WorksiteNames.display(site));
        out.append(" (").append(site.key().binding().getPath());
        if (WorksiteBindings.ANCHOR.equals(site.key().binding())) {
            BlockPos pos = site.key().pos();
            out.append(" at ").append(pos.getX()).append(',').append(pos.getY()).append(',').append(pos.getZ());
        } else {
            out.append(" #").append(site.key().value());
        }
        if (site.villageId() != Worksite.NO_VILLAGE) out.append(", village ").append(site.villageId());
        out.append(')');
        return out.toString();
    }
}
