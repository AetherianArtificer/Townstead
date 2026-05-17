package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarProfileRegistry;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.concurrent.CompletableFuture;

/**
 * {@code /townstead calendar get | set-year <N> | set-profile <id> | set-day <day>}.
 * Read access is unrestricted; mutators require op level 2.
 */
public final class CalendarCommands {
    private CalendarCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(
                Commands.literal("townstead").then(Commands.literal("calendar")
                        .then(Commands.literal("get").executes(c -> get(c.getSource())))
                        .then(Commands.literal("set-year")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("year", IntegerArgumentType.integer())
                                        .executes(c -> setYear(c.getSource(), IntegerArgumentType.getInteger(c, "year")))))
                        .then(Commands.literal("set-profile")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .suggests(PROFILE_SUGGESTIONS)
                                        .executes(c -> setProfile(c.getSource(), StringArgumentType.getString(c, "id")))))
                        .then(Commands.literal("set-day")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("worldDay", IntegerArgumentType.integer())
                                        .executes(c -> setDay(c.getSource(), IntegerArgumentType.getInteger(c, "worldDay")))))));
    }

    private static final SuggestionProvider<CommandSourceStack> PROFILE_SUGGESTIONS =
            (ctx, builder) -> suggestProfiles(builder);

    private static CompletableFuture<Suggestions> suggestProfiles(SuggestionsBuilder builder) {
        builder.suggest("auto");
        for (String id : CalendarProfileRegistry.idStrings()) builder.suggest(id);
        return builder.buildFuture();
    }

    private static int get(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        CalendarDate today = TownsteadCalendar.today(server);
        String profileId = profile != null ? profile.id().toString() : "<none loaded>";
        source.sendSuccess(() -> Component.literal(formatDate(profileId, today, data)), false);
        return 1;
    }

    private static int setYear(CommandSourceStack source, int displayYear) {
        MinecraftServer server = source.getServer();
        TownsteadCalendar.rebaseToDisplayYear(server, displayYear);
        CalendarDate today = TownsteadCalendar.today(server);
        source.sendSuccess(() -> Component.literal(
                "Calendar year set to " + today.year() + " (offset adjusted, counters unchanged)."),
                true);
        return 1;
    }

    private static int setProfile(CommandSourceStack source, String idString) {
        MinecraftServer server = source.getServer();
        if (idString.equalsIgnoreCase("auto")) {
            TownsteadCalendar.setProfileOverride(server, null);
            CalendarProfile p = TownsteadCalendar.activeProfile(server);
            String resolved = p != null ? p.id().toString() : "<none loaded>";
            source.sendSuccess(() -> Component.literal(
                    "Calendar override cleared. Auto-resolved profile: " + resolved + "."),
                    true);
            return 1;
        }
        ResourceLocation id;
        try {
            //? if >=1.21 {
            id = ResourceLocation.parse(idString);
            //?} else {
            /*id = new ResourceLocation(idString);
            *///?}
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Invalid profile id: " + idString));
            return 0;
        }
        CalendarProfile profile = CalendarProfileRegistry.byId(id);
        if (profile == null) {
            source.sendFailure(Component.literal("Unknown profile: " + id
                    + " (loaded profiles: " + String.join(", ", CalendarProfileRegistry.idStrings()) + ")"));
            return 0;
        }
        TownsteadCalendar.setProfileOverride(server, id);
        source.sendSuccess(() -> Component.literal("Calendar profile set to " + id + "."), true);
        return 1;
    }

    private static int setDay(CommandSourceStack source, int worldDay) {
        MinecraftServer server = source.getServer();
        WorldCalendarSavedData.get(server).setWorldDayCounter(worldDay);
        CalendarDate today = TownsteadCalendar.today(server);
        source.sendSuccess(() -> Component.literal(
                "World day counter set to " + worldDay + ". Displayed date is now "
                        + formatShortDate(today) + "."),
                true);
        return 1;
    }

    private static String formatDate(String profileId, CalendarDate date, WorldCalendarSavedData data) {
        return "Townstead calendar, profile=" + profileId
                + ", year=" + date.year()
                + ", month=" + date.monthIndex()
                + ", day=" + date.dayOfMonth()
                + ", dayOfYear=" + date.dayOfYear()
                + ", dayOfWeek=" + date.dayOfWeek()
                + (date.season() != null ? ", season=" + date.season().name().toLowerCase() : "")
                + " (worldDay=" + data.worldDayCounter()
                + ", epochOffset=" + data.epochYearOffset() + ")";
    }

    private static String formatShortDate(CalendarDate date) {
        return date.year() + "-" + date.monthIndex() + "-" + date.dayOfMonth();
    }
}
