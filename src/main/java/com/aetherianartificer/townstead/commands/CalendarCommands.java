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
        for (String id : CalendarProfileChoices.listAll()) builder.suggest(id);
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
        // Accept either a JSON-registered profile or one currently supplied
        // by a DynamicProfileSource. Without this second check, set-profile
        // would reject every runtime-synthesized id.
        boolean known = CalendarProfileRegistry.byId(id) != null
                || DynamicProfileSources.listKnownIds().contains(id);
        if (!known) {
            source.sendFailure(Component.literal("Unknown profile: " + id
                    + " (available: " + String.join(", ", CalendarProfileChoices.listAll()) + ")"));
            return 0;
        }
        TownsteadCalendar.setProfileOverride(server, id);
        source.sendSuccess(() -> Component.literal("Calendar profile set to " + id + "."), true);
        return 1;
    }

    private static int setDay(CommandSourceStack source, int worldDay) {
        MinecraftServer server = source.getServer();
        // Goes through TownsteadCalendar so the change is broadcast to clients;
        // setting the counter directly on the saved data leaves the displayed
        // date stale on every connected client.
        TownsteadCalendar.setWorldDay(server, worldDay);
        CalendarDate today = TownsteadCalendar.today(server);
        source.sendSuccess(() -> Component.literal(
                "World day counter set to " + worldDay + ". Displayed date is now "
                        + formatShortDate(today) + "."),
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
            source.sendFailure(Component.literal("Could not set " + year + "-" + month + "-" + day
                    + ": month/day out of range for the active profile (or no profile loaded)."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(
                "Calendar set to " + formatShortDate(result) + describeWeekday(server, result) + "."),
                true);
        return 1;
    }

    private static int matchToday(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        CalendarDate result = TownsteadCalendar.matchToday(server);
        source.sendSuccess(() -> Component.literal(
                "Calendar synced to today: " + formatShortDate(result) + describeWeekday(server, result) + "."),
                true);
        return 1;
    }

    /** " (Geos)"-style suffix naming the resulting weekday, or "" if the profile declares none. */
    private static String describeWeekday(MinecraftServer server, CalendarDate date) {
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        if (profile == null || profile.weekdays() == null) return "";
        java.util.List<WeekdayDef> weekdays = profile.weekdays();
        int idx = date.dayOfWeek();
        if (idx < 0 || idx >= weekdays.size()) return "";
        return " (" + weekdays.get(idx).longName().getString() + ")";
    }

    private static int getTimeMode(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        String override = data.timeModeOverride();
        String configMode = TownsteadConfig.getCalendarTimeMode();
        String effective = override != null ? override : configMode;
        String msg = override != null
                ? "Calendar time mode: " + effective + " (world override; config default: " + configMode + ")"
                : "Calendar time mode: " + effective + " (from config)";
        source.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int setTimeMode(CommandSourceStack source, String mode) {
        MinecraftServer server = source.getServer();
        TownsteadCalendar.setTimeModeOverride(server, mode);
        String msg = mode == null
                ? "Calendar time-mode override cleared. Now using config default: "
                        + TownsteadConfig.getCalendarTimeMode() + "."
                : "Calendar time mode set to: " + mode + " (saved with this world).";
        source.sendSuccess(() -> Component.literal(msg), true);
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
