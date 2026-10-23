package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarProfileChoices;
import com.aetherianartificer.townstead.calendar.CalendarProfileRegistry;
import com.aetherianartificer.townstead.calendar.DynamicProfileSources;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.calendar.WeekdayDef;
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
 * {@code /townstead calendar get | set-year <N> | set-profile <id> |
 * set-day <day> | set-date <year> <month> <day> | match-today |
 * time-mode [...]}. Read access is unrestricted; mutators require op level 2.
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
                                        .executes(c -> setDay(c.getSource(), IntegerArgumentType.getInteger(c, "worldDay")))))
                        .then(Commands.literal("set-date")
                                .requires(s -> s.hasPermission(2))
                                .then(Commands.argument("year", IntegerArgumentType.integer())
                                        .then(Commands.argument("month", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("day", IntegerArgumentType.integer(1))
                                                        .executes(c -> setDate(c.getSource(),
                                                                IntegerArgumentType.getInteger(c, "year"),
                                                                IntegerArgumentType.getInteger(c, "month"),
                                                                IntegerArgumentType.getInteger(c, "day")))))))
                        .then(Commands.literal("match-today")
                                .requires(s -> s.hasPermission(2))
                                .executes(c -> matchToday(c.getSource())))
                        .then(Commands.literal("time-mode")
                                .executes(c -> getTimeMode(c.getSource()))
                                .then(Commands.literal("normal")
                                        .requires(s -> s.hasPermission(2))
                                        .executes(c -> setTimeMode(c.getSource(), "normal")))
                                .then(Commands.literal("real_clock")
                                        .requires(s -> s.hasPermission(2))
                                        .executes(c -> setTimeMode(c.getSource(), "real_clock")))
                                .then(Commands.literal("default")
                                        .requires(s -> s.hasPermission(2))
                                        .executes(c -> setTimeMode(c.getSource(), null))))));
    }

    private static final SuggestionProvider<CommandSourceStack> PROFILE_SUGGESTIONS =
            (ctx, builder) -> suggestProfiles(builder);

    private static CompletableFuture<Suggestions> suggestProfiles(SuggestionsBuilder builder) {
        // Includes "auto", all JSON-registered profiles, and every id any
        // DynamicProfileSource currently advertises. De-duplicated, stable order.
        for (String id : CalendarProfileChoices.listAll()) builder.suggest("\"" + id + "\"");
        return builder.buildFuture();
    }

    private static int get(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        CalendarDate today = TownsteadCalendar.today(server);
        Component profileId = profile != null ? Component.literal(profile.id().toString())
                : Component.translatable("townstead.command.calendar.none_loaded");
        source.sendSuccess(() -> formatDate(profileId, today, data), false);
        return 1;
    }

    private static int setYear(CommandSourceStack source, int displayYear) {
        MinecraftServer server = source.getServer();
        TownsteadCalendar.rebaseToDisplayYear(server, displayYear);
        CalendarDate today = TownsteadCalendar.today(server);
        source.sendSuccess(() -> Component.translatable(
                "townstead.command.calendar.set_year", today.year()),
                true);
        return 1;
    }

    private static int setProfile(CommandSourceStack source, String idString) {
        MinecraftServer server = source.getServer();
        if (idString.equalsIgnoreCase("auto")) {
            TownsteadCalendar.setProfileOverride(server, null);
            CalendarProfile p = TownsteadCalendar.activeProfile(server);
            Component resolved = p != null ? Component.literal(p.id().toString())
                    : Component.translatable("townstead.command.calendar.none_loaded");
            source.sendSuccess(() -> Component.translatable(
                    "townstead.command.calendar.profile.cleared", resolved),
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
            source.sendFailure(Component.translatable("townstead.command.calendar.profile.invalid", idString));
            return 0;
        }
        // Accept either a JSON-registered profile or one currently supplied
        // by a DynamicProfileSource. Without this second check, set-profile
        // would reject every runtime-synthesized id.
        boolean known = CalendarProfileRegistry.byId(id) != null
                || DynamicProfileSources.listKnownIds().contains(id);
        if (!known) {
            source.sendFailure(Component.translatable("townstead.command.calendar.profile.unknown",
                    id.toString(), String.join(", ", CalendarProfileChoices.listAll())));
            return 0;
        }
        TownsteadCalendar.setProfileOverride(server, id);
        source.sendSuccess(() -> Component.translatable(
                "townstead.command.calendar.profile.set", id.toString()), true);
        return 1;
    }

    private static int setDay(CommandSourceStack source, int worldDay) {
        MinecraftServer server = source.getServer();
        // Goes through TownsteadCalendar so the change is broadcast to clients;
        // setting the counter directly on the saved data leaves the displayed
        // date stale on every connected client.
        TownsteadCalendar.setWorldDay(server, worldDay);
        CalendarDate today = TownsteadCalendar.today(server);
        source.sendSuccess(() -> Component.translatable(
                "townstead.command.calendar.set_day", worldDay, formatShortDate(today)),
                true);
        return 1;
    }

    private static int setDate(CommandSourceStack source, int year, int month, int day) {
        MinecraftServer server = source.getServer();
        // Display-only: sets the year via the epoch offset and the month/day via
        // an in-year counter nudge, so villager ages are preserved. The weekday
        // falls where the calendar's own cycle puts it.
        CalendarDate result = TownsteadCalendar.setToDate(server, year, month, day);
        if (result == null) {
            source.sendFailure(Component.translatable("townstead.command.calendar.set_date.invalid",
                    year + "-" + month + "-" + day));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(
                "townstead.command.calendar.set_date", formatShortDate(result), describeWeekday(server, result)),
                true);
        return 1;
    }

    private static int matchToday(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        CalendarDate result = TownsteadCalendar.matchToday(server);
        source.sendSuccess(() -> Component.translatable(
                "townstead.command.calendar.match_today", formatShortDate(result), describeWeekday(server, result)),
                true);
        return 1;
    }

    /** " (Geos)"-style suffix naming the resulting weekday, or "" if the profile declares none. */
    private static Component describeWeekday(MinecraftServer server, CalendarDate date) {
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        if (profile == null || profile.weekdays() == null) return Component.empty();
        java.util.List<WeekdayDef> weekdays = profile.weekdays();
        int idx = date.dayOfWeek();
        if (idx < 0 || idx >= weekdays.size()) return Component.empty();
        return Component.translatable("townstead.command.calendar.weekday_suffix",
                weekdays.get(idx).longName());
    }

    private static int getTimeMode(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        String override = data.timeModeOverride();
        String configMode = TownsteadConfig.getCalendarTimeMode();
        String effective = override != null ? override : configMode;
        source.sendSuccess(() -> override != null
                ? Component.translatable("townstead.command.calendar.time_mode.override", effective, configMode)
                : Component.translatable("townstead.command.calendar.time_mode.config", effective), false);
        return 1;
    }

    private static int setTimeMode(CommandSourceStack source, String mode) {
        MinecraftServer server = source.getServer();
        TownsteadCalendar.setTimeModeOverride(server, mode);
        source.sendSuccess(() -> mode == null
                ? Component.translatable("townstead.command.calendar.time_mode.cleared",
                        TownsteadConfig.getCalendarTimeMode())
                : Component.translatable("townstead.command.calendar.time_mode.set", mode), true);
        return 1;
    }

    private static Component formatDate(Component profileId, CalendarDate date, WorldCalendarSavedData data) {
        Component season = date.season() == null ? Component.empty()
                : Component.translatable("townstead.command.calendar.status.season",
                        date.season().name().toLowerCase());
        return Component.translatable("townstead.command.calendar.status", profileId, date.year(),
                date.monthIndex(), date.dayOfMonth(), date.dayOfYear(), date.dayOfWeek(), season,
                data.worldDayCounter(), data.epochYearOffset());
    }

    private static String formatShortDate(CalendarDate date) {
        return date.year() + "-" + date.monthIndex() + "-" + date.dayOfMonth();
    }
}
