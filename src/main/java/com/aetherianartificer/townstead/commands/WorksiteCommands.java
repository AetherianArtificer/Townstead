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
            source.sendSuccess(() -> Component.translatable("command.townstead.worksite.here.empty"),
                    false);
            return 0;
        }
        source.sendSuccess(() -> describe(site), false);
        // Which trades claim this place, and therefore what its catalogue may offer. An invisible
        // rule that decides what a screen shows needs somewhere to be read out loud.
        java.util.Set<net.minecraft.resources.ResourceLocation> types =
                com.aetherianartificer.townstead.work.site.WorksiteWork.typesAt(level, site,
                        com.aetherianartificer.townstead.work.site.Worksites.extentOf(level, site));
        source.sendSuccess(() -> types.isEmpty()
                ? Component.translatable("command.townstead.worksite.here.no_trade")
                : Component.translatable("command.townstead.worksite.here.trades",
                        types.stream().map(net.minecraft.resources.ResourceLocation::toString)
                                .sorted().collect(java.util.stream.Collectors.joining(", "))), false);
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
            source.sendSuccess(() -> Component.translatable("command.townstead.worksite.list.empty"), false);
            return 0;
        }
        here.sort(Comparator.comparingLong(Worksite::id));

        source.sendSuccess(() -> Component.translatable(
                "command.townstead.worksite.list.heading", here.size()), false);
        for (Worksite site : here) {
            source.sendSuccess(() -> describe(site), false);
        }
        return here.size();
    }

    // ── orders ──

    private static int orders(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Worksite site = siteAt(level, BlockPos.containing(source.getPosition()), true);
        if (site == null) {
            source.sendFailure(Component.translatable("command.townstead.worksite.not_found"));
            return 0;
        }
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            source.sendFailure(Component.translatable("command.townstead.worksite.orders.player_required"));
            return 0;
        }
        return com.aetherianartificer.townstead.work.order.OrdersOpener.open(player, site) ? 1 : 0;
    }

    // ── name ──

    private static int name(CommandSourceStack source, String requested) {
        ServerLevel level = source.getLevel();
        Worksite site = siteAt(level, BlockPos.containing(source.getPosition()), true);
        if (site == null) {
            source.sendFailure(Component.translatable("command.townstead.worksite.not_found"));
            return 0;
        }
        String cleaned = WorksiteNames.sanitise(requested);
        if (cleaned == null) {
            source.sendFailure(Component.translatable("command.townstead.worksite.name.empty"));
            return 0;
        }
        site.setName(cleaned);
        WorksiteRegister.get(level.getServer()).setDirty();
        source.sendSuccess(() -> Component.translatable(
                "command.townstead.worksite.name.success", cleaned), true);
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

    private static Component describe(Worksite site) {
        String binding = site.key().binding().getPath();
        boolean village = site.villageId() != Worksite.NO_VILLAGE;
        if (WorksiteBindings.ANCHOR.equals(site.key().binding())) {
            BlockPos pos = site.key().pos();
            return village
                    ? Component.translatable("command.townstead.worksite.describe.anchor.village",
                            site.id(), WorksiteNames.display(site), binding,
                            pos.getX(), pos.getY(), pos.getZ(), site.villageId())
                    : Component.translatable("command.townstead.worksite.describe.anchor",
                            site.id(), WorksiteNames.display(site), binding,
                            pos.getX(), pos.getY(), pos.getZ());
        }
        return village
                ? Component.translatable("command.townstead.worksite.describe.bound.village",
                        site.id(), WorksiteNames.display(site), binding,
                        site.key().value(), site.villageId())
                : Component.translatable("command.townstead.worksite.describe.bound",
                        site.id(), WorksiteNames.display(site), binding, site.key().value());
    }
}
