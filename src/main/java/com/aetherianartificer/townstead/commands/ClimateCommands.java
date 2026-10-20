package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.calendar.SeasonClock;
import com.aetherianartificer.townstead.climate.ClimateModel;
import com.aetherianartificer.townstead.climate.ClimateZones;
import com.aetherianartificer.townstead.climate.CropClimate;
import com.aetherianartificer.townstead.climate.DimensionClimate;
import com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver;
import com.aetherianartificer.townstead.temperature.GroundClimate;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;

import java.util.Locale;
import java.util.function.BiFunction;

/** Read-only climate diagnostics: the climate here, a biome's zone, and a biome's year. */
public final class ClimateCommands {
    private static final String[] SEASONS = {"Spring", "Summer", "Autumn", "Winter"};

    private ClimateCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("climate")
                .then(Commands.literal("here").executes(c -> here(c.getSource())))
                .then(Commands.literal("biome")
                        .executes(c -> describe(c.getSource(), biomeHere(c.getSource())))
                        .then(biomeArgument(ClimateCommands::describe)))
                .then(Commands.literal("year")
                        .executes(c -> year(c.getSource(), biomeHere(c.getSource())))
                        .then(biomeArgument(ClimateCommands::year)))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> biomeArgument(
            BiFunction<CommandSourceStack, Holder<Biome>, Integer> run) {
        return Commands.argument("biome", ResourceLocationArgument.id())
                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                        c.getSource().getLevel().registryAccess().registryOrThrow(Registries.BIOME).keySet(), b))
                .executes(c -> {
                    var holder = biomeArg(c);
                    if (holder == null) {
                        c.getSource().sendFailure(Component.literal("Unknown biome " + ResourceLocationArgument.getId(c, "biome")));
                        return 0;
                    }
                    return run.apply(c.getSource(), holder);
                });
    }

    private static Holder<Biome> biomeArg(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ResourceLocation id = ResourceLocationArgument.getId(c, "biome");
        return c.getSource().getLevel().registryAccess().registryOrThrow(Registries.BIOME)
                .getHolder(ResourceKey.create(Registries.BIOME, id)).orElse(null);
    }

    private static Holder<Biome> biomeHere(CommandSourceStack source) {
        return source.getLevel().getBiome(BlockPos.containing(source.getPosition()));
    }

    private static String name(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> k.location().toString()).orElse("unregistered biome");
    }

    private static void say(CommandSourceStack source, String text) {
        source.sendSuccess(() -> Component.literal(text), false);
    }

    private static int here(CommandSourceStack source) {
        var level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        var backend = TemperatureBridgeResolver.get();
        boolean townstead = TownsteadConfig.townsteadClimateAuthority();
        say(source, String.format(Locale.ROOT, "Air here %.1f C; backend %s; climate authority %s",
                TemperatureData.ambientCelsius(level, pos), backend.id(), townstead ? "townstead" : "backend"));
        var sample = ClimateModel.sample(level, pos);
        say(source, String.format(Locale.ROOT,
                "Open air %.1f C = mean %.1f + season %+.1f + day/night %+.1f + weather %+.1f, then snow %s, local %+.1f",
                sample.air(), sample.mean(), sample.seasonal(), sample.diurnal(), sample.weather(),
                sample.snow().falling() ? "falling" : sample.snow().lying() ? "lying" : "none",
                ClimateModel.localOffset(level, pos)));
        describe(source, level.getBiome(pos));
        say(source, SeasonClock.current(level.getServer()).map(clock -> String.format(Locale.ROOT,
                "Season clock %s: phase %.3f of a %d-day year, %s%s", clock.provider(), clock.phase(), clock.yearDays(),
                clock.season() == null ? "no season" : clock.season().name().toLowerCase(Locale.ROOT),
                com.aetherianartificer.townstead.climate.WetDry.current(clock).map(p -> String.format(Locale.ROOT,
                        "; %s season, wetness %.2f", p.wet() ? "wet" : "dry", p.wetness())).orElse("")))
                .orElse("Season clock: none (no season mod), so the year stays at its mean"));
        var dimension = DimensionClimate.of(level);
        say(source, String.format(Locale.ROOT, "Dimension %s: %s; seasons %s, day/night %s, weather %s, ground %s",
                level.dimension().location(),
                dimension.usesBiomes() ? "biome climates" : String.format(Locale.ROOT, "fixed %.1f C", dimension.fixedCelsius()),
                yes(dimension.seasons()), yes(dimension.dayCycle()), yes(dimension.weather()), yes(dimension.underground())));
        if (backend != com.aetherianartificer.townstead.compat.temperature.BuiltinTemperatureBridge.INSTANCE) {
            say(source, String.format(Locale.ROOT, "Backend %s runs its own seasons: %s; its own depth heat: %s",
                    backend.id(), yes(backend.modelsSeasons()), yes(backend.modelsGeothermal())));
        }
        say(source, GroundClimate.describe(level, pos));
        say(source, String.format(Locale.ROOT, "Crops here grow against %.1f C today", CropClimate.growingCelsius(level, pos, 0)));
        return 1;
    }

    private static int year(CommandSourceStack source, Holder<Biome> biome) {
        var level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());
        var clock = SeasonClock.current(level.getServer()).orElse(null);
        var zone = ClimateZones.of(biome);
        say(source, String.format(Locale.ROOT, "Year for %s at y=%d (zone %s), clear sky, noon / midnight:",
                name(biome), pos.getY(), zone.map(z -> z.id().toString()).orElse("none")));
        if (clock == null) {
            say(source, String.format(Locale.ROOT, "No season mod: every day is %.1f C / %.1f C",
                    ClimateModel.projected(level, pos, biome, null, 0, 6000),
                    ClimateModel.projected(level, pos, biome, null, 0, 18000)));
            return 1;
        }
        for (int i = 0; i < 24; i++) {
            double phase = (i + 0.5) / 24.0;
            float noon = ClimateModel.projected(level, pos, biome, clock, phase, 6000);
            float midnight = ClimateModel.projected(level, pos, biome, clock, phase, 18000);
            String marker = Math.abs(phase - clock.phase()) <= 0.5 / 24.0 ? "  <- now" : "";
            say(source, String.format(Locale.ROOT, "%s %d: %5.1f C / %5.1f C%s", SEASONS[i / 6], i % 6 + 1, noon, midnight, marker));
        }
        return 1;
    }

    private static String yes(boolean value) {
        return value ? "yes" : "no";
    }

    private static int describe(CommandSourceStack source, Holder<Biome> biome) {
        var resolution = ClimateZones.resolve(biome);
        var zone = ClimateZones.get(resolution.zone());
        String shape = zone.map(z -> String.format(Locale.ROOT,
                "; %s, swing +/-%.1f C, day/night +/-%.1f C, humidity %.2f",
                z.seasonKind().name().toLowerCase(Locale.ROOT), z.seasonalSwing(), z.diurnalRange(), z.humidity())).orElse("");
        say(source, String.format(Locale.ROOT, "%s: zone %s (%s: %s)%s", name(biome),
                resolution.zone() == null ? "none" : resolution.zone(), resolution.step(), resolution.detail(), shape));
        return resolution.zone() == null ? 0 : 1;
    }
}
