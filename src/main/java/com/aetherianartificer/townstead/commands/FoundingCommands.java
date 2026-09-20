package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.politics.founding.DebugVillageSpawner;
import com.aetherianartificer.townstead.politics.founding.FoundingProfileApplier;
import com.aetherianartificer.townstead.politics.founding.FoundingProfileDefinition;
import com.aetherianartificer.townstead.politics.founding.FoundingProfiles;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Diagnostics and explicit profile application for recognized MCA villages. */
public final class FoundingCommands {
    private FoundingCommands() {}

    private static final SuggestionProvider<CommandSourceStack> PROFILES = (context, builder) ->
            SharedSuggestionProvider.suggest(FoundingProfiles.ids().stream()
                    .map(ResourceLocation::toString).sorted(), builder);

    private static final SuggestionProvider<CommandSourceStack> STRUCTURES = (context, builder) ->
            SharedSuggestionProvider.suggest(Stream.concat(
                            Stream.of("auto", "plains", "desert", "savanna", "snowy", "taiga"),
                            context.getSource().registryAccess().registryOrThrow(Registries.STRUCTURE)
                                    .keySet().stream().map(ResourceLocation::toString))
                    .distinct().sorted(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("founding")
                .then(Commands.literal("profiles").executes(command -> profiles(command.getSource())))
                .then(Commands.literal("inspect").executes(command -> inspect(command.getSource())))
                .then(Commands.literal("apply")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("profile", StringArgumentType.string())
                                .suggests(PROFILES)
                                .executes(command -> apply(command.getSource(),
                                        StringArgumentType.getString(command, "profile")))))));
        var villageCommand = Commands.literal("village")
                .requires(source -> source.hasPermission(2));
        var profileArgument = Commands.argument("profile", StringArgumentType.string())
                .suggests(PROFILES)
                .executes(command -> spawn(command.getSource(),
                        StringArgumentType.getString(command, "profile"), "auto", 8));
        profileArgument.then(Commands.argument("residents", IntegerArgumentType.integer(1, 32))
                .executes(command -> spawn(command.getSource(),
                        StringArgumentType.getString(command, "profile"), "auto",
                        IntegerArgumentType.getInteger(command, "residents"))));
        var structureArgument = Commands.argument("structure", StringArgumentType.word())
                .suggests(STRUCTURES)
                .executes(command -> spawn(command.getSource(),
                        StringArgumentType.getString(command, "profile"),
                        StringArgumentType.getString(command, "structure"), 8));
        structureArgument.then(Commands.argument("residents", IntegerArgumentType.integer(1, 32))
                .executes(command -> spawn(command.getSource(),
                        StringArgumentType.getString(command, "profile"),
                        StringArgumentType.getString(command, "structure"),
                        IntegerArgumentType.getInteger(command, "residents"))));
        profileArgument.then(Commands.literal("structure").then(structureArgument));
        var repopulateProfile = Commands.argument("profile", StringArgumentType.string())
                .suggests(PROFILES)
                .executes(command -> repopulate(command.getSource(),
                        StringArgumentType.getString(command, "profile"), 8));
        repopulateProfile.then(Commands.argument("residents", IntegerArgumentType.integer(1, 32))
                .executes(command -> repopulate(command.getSource(),
                        StringArgumentType.getString(command, "profile"),
                        IntegerArgumentType.getInteger(command, "residents"))));
        villageCommand.then(Commands.literal("repopulate").then(repopulateProfile));
        villageCommand.then(profileArgument);
        dispatcher.register(Commands.literal("townstead")
                .then(Commands.literal("debug").then(villageCommand)));
    }

    private static int profiles(CommandSourceStack source) {
        List<FoundingProfileDefinition> profiles = FoundingProfiles.all().stream()
                .sorted(Comparator.comparing(profile -> profile.id().toString())).toList();
        line(source, "command.townstead.founding.profiles", profiles.size());
        for (FoundingProfileDefinition profile : profiles) {
            line(source, "command.townstead.founding.profile", profile.id(), profile.displayName(),
                    profile.culture() == null ? "-" : profile.culture(),
                    profile.government() == null ? "-" : profile.government().organizationKind());
        }
        return profiles.isEmpty() ? 0 : profiles.size();
    }

    private static int inspect(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Optional<Village> village = villageAt(level, BlockPos.containing(source.getPosition()));
        if (village.isEmpty()) return noVillage(source);
        SettlementFoundingRecord record = PoliticalSavedData.get(level.getServer()).founding(
                new SettlementRef(level.dimension().location(), village.get().getId()));
        if (record == null) {
            line(source, "command.townstead.founding.unprofiled", village.get().getName());
            return 0;
        }
        line(source, "command.townstead.founding.inspect", village.get().getName(), record.profile(),
                record.culture() == null ? "-" : record.culture(), record.government(),
                record.foundingBiome() == null ? "-" : record.foundingBiome(), record.naturalWeight());
        return 1;
    }

    private static int apply(CommandSourceStack source, String raw) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        FoundingProfileDefinition profile = FoundingProfiles.get(id);
        if (profile == null) {
            line(source, "command.townstead.founding.unknown", raw);
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos position = BlockPos.containing(source.getPosition());
        Optional<Village> village = villageAt(level, position);
        if (village.isEmpty()) return noVillage(source);
        FoundingProfileApplier.Result result = FoundingProfileApplier.apply(level, village.get(), profile, position);
        if (!result.applied()) {
            line(source, "command.townstead.founding.failed", profile.id(), result.reason());
            return 0;
        }
        line(source, "command.townstead.founding.applied", profile.id(), village.get().getName(),
                result.polity(), result.government(), result.governmentMembersCreated(), result.naturalWeight());
        if (profile.culture() != null) line(source, "command.townstead.founding.residents_unchanged");
        return 1;
    }

    private static int spawn(CommandSourceStack source, String raw, String structure, int residents) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        FoundingProfileDefinition profile = FoundingProfiles.get(id);
        if (profile == null) {
            line(source, "command.townstead.founding.unknown", raw);
            return 0;
        }
        DebugVillageSpawner.Result result = DebugVillageSpawner.spawn(source, profile, structure, residents);
        if (!result.spawned() || result.village() == null || result.founding() == null) {
            failure(source, "command.townstead.founding.spawn_failed", profile.id(), spawnFailureDetail(result));
            return 0;
        }
        line(source, "command.townstead.founding.spawned", result.structure(), result.village().getName(),
                result.village().getId(), profile.id(), result.buildings(), result.residents(),
                result.founding().governmentMembersCreated());
        return 1;
    }

    private static int repopulate(CommandSourceStack source, String raw, int residents) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        FoundingProfileDefinition profile = FoundingProfiles.get(id);
        if (profile == null) {
            failure(source, "command.townstead.founding.unknown", raw);
            return 0;
        }
        DebugVillageSpawner.RepopulationResult result =
                DebugVillageSpawner.repopulate(source, profile, residents);
        if (!result.repopulated() || result.village() == null || result.founding() == null) {
            failure(source, "command.townstead.founding.repopulate_failed",
                    profile.id(), result.reason());
            return 0;
        }
        line(source, "command.townstead.founding.repopulated", result.village().getName(),
                result.village().getId(), profile.id(), result.removedResidents(), result.residents(),
                result.founding().governmentMembersCreated());
        return 1;
    }

    private static Optional<Village> villageAt(ServerLevel level, BlockPos pos) {
        try {
            return VillageManager.get(level).findNearestVillage(pos, Village.MERGE_MARGIN);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static int noVillage(CommandSourceStack source) {
        line(source, "command.townstead.founding.no_village");
        return 0;
    }

    private static void line(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.translatable(key, networkSafeTranslationArgs(args)), false);
    }

    private static void failure(CommandSourceStack source, String key, Object... args) {
        source.sendFailure(Component.translatable(key, networkSafeTranslationArgs(args)));
    }

    private static Component spawnFailureDetail(DebugVillageSpawner.Result result) {
        return switch (result.reason()) {
            case "near_village" -> Component.translatable(
                    "command.townstead.founding.spawn_reason.near_village",
                    DebugVillageSpawner.VILLAGE_RADIUS);
            case "unknown_structure" -> Component.translatable(
                    "command.townstead.founding.spawn_reason.unknown_structure",
                    result.structure() == null ? "?" : result.structure().toString());
            case "placement_failed" -> Component.translatable(
                    "command.townstead.founding.spawn_reason.placement_failed",
                    result.structure() == null ? "?" : result.structure().toString());
            case "mca_unrecognized" -> Component.translatable(
                    "command.townstead.founding.spawn_reason.mca_unrecognized");
            default -> Component.literal(result.reason());
        };
    }

    /**
     * Minecraft 1.21 only permits components and primitive values in a translatable component's
     * network payload. Registry identifiers and other useful command values must be rendered to
     * text before the component is sent or the packet encoder disconnects the receiving client.
     */
    static Object[] networkSafeTranslationArgs(Object... args) {
        Object[] safe = new Object[args.length];
        for (int index = 0; index < args.length; index++) {
            Object value = args[index];
            safe[index] = value == null
                    || value instanceof String
                    || value instanceof Number
                    || value instanceof Boolean
                    || value instanceof Component
                    ? value
                    : value.toString();
        }
        return safe;
    }
}
