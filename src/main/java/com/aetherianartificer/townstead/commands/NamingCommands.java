package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.CultureAssignment;
import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.naming.NameParts;
import com.aetherianartificer.townstead.naming.Naming;
import com.aetherianartificer.townstead.naming.NamingRegisterSavedData;
import com.aetherianartificer.townstead.naming.NamingRegisters;
import com.aetherianartificer.townstead.naming.NamingTradition;
import com.aetherianartificer.townstead.naming.VillagerNames;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code /townstead naming} reads out how a villager came to be called what they are called.
 *
 * <p>Culture, naming tradition, name list and family name are each settled once and then never
 * mentioned again, and every way they can come to nothing looks identical from outside. A villager
 * with no surname may belong to no culture, to a culture whose tradition declares no family names,
 * or to a tradition whose name list is not loaded. This command tells those apart.</p>
 *
 * <p>The writing half exists for the same reason. A culture normally arrives through a founder, a
 * parent or a village, which can mean waiting several in-game years to learn whether a pack works
 * at all. Assigning one outright, or clearing one so it settles again, answers that in seconds.</p>
 */
public final class NamingCommands {

    private NamingCommands() {}

    private static final SuggestionProvider<CommandSourceStack> CULTURES = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    Cultures.allIds().stream().map(ResourceLocation::toString).sorted(), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("naming")
                .executes(c -> report(c.getSource(), null))
                .then(Commands.literal("village").executes(c -> village(c.getSource())))
                .then(Commands.literal("cultures").executes(c -> cultures(c.getSource())))
                .then(Commands.literal("set")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("culture", StringArgumentType.string())
                                .suggests(CULTURES)
                                .executes(c -> set(c.getSource(), null,
                                        StringArgumentType.getString(c, "culture")))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(c -> set(c.getSource(),
                                                EntityArgument.getEntity(c, "target"),
                                                StringArgumentType.getString(c, "culture"))))))
                .then(Commands.literal("set-village")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("culture", StringArgumentType.string())
                                .suggests(CULTURES)
                                .executes(c -> setVillage(c.getSource(),
                                        StringArgumentType.getString(c, "culture")))))
                .then(Commands.literal("clear")
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> clear(c.getSource(), null))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(c -> clear(c.getSource(),
                                        EntityArgument.getEntity(c, "target")))))
                .then(Commands.argument("target", EntityArgument.entity())
                        .executes(c -> report(c.getSource(),
                                EntityArgument.getEntity(c, "target"))))));
    }

    // -- report --

    private static int report(CommandSourceStack source, @Nullable Entity target) {
        VillagerEntityMCA villager = resolve(source, target);
        if (villager == null) return 0;

        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        NameParts parts = VillagerNames.parts(villager);

        line(source, "command.townstead.naming.report.who", villager.getName().getString());
        line(source, "command.townstead.naming.report.full", parts.fullName());

        // Two axes, reported separately on purpose: a villager is almost always named by their
        // region and belongs to no culture at all, and conflating the two is what this avoids.
        NamingTradition tradition = Naming.traditionOf(villager);
        if (tradition == null) {
            line(source, "command.townstead.naming.report.no_tradition");
        } else {
            line(source, "command.townstead.naming.report.tradition",
                    tradition.id().toString(),
                    tradition.family().type().name().toLowerCase(Locale.ROOT));
        }

        Culture culture = Cultures.get(life.culture());
        if (culture == null) {
            line(source, "command.townstead.naming.report.no_culture");
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "command.townstead.naming.report.culture",
                    culture.id().toString(), culture.displayName()), false);
        }

        String register = NamingRegisters.recorded(villager);
        line(source, "command.townstead.naming.report.list",
                life.nameList().isEmpty() ? "-" : life.nameList());
        line(source, "command.townstead.naming.report.register", register.isEmpty() ? "-" : register);
        line(source, "command.townstead.naming.report.family", parts.hasFamily() ? parts.family() : "-");
        return 1;
    }

    // -- village --

    private static int village(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Optional<Village> village = villageAt(level, BlockPos.containing(source.getPosition()));
        if (village.isEmpty()) {
            line(source, "command.townstead.naming.village.none");
            return 0;
        }
        String culture = NamingRegisterSavedData.get(level.getServer())
                .villageCulture(level.dimension().location(), village.get().getId());
        line(source, "command.townstead.naming.village.culture",
                village.get().getName(), culture.isEmpty() ? "-" : culture);
        return 1;
    }

    // -- cultures --

    private static int cultures(CommandSourceStack source) {
        List<String> authored = new ArrayList<>();
        for (ResourceLocation id : Cultures.authoredIds()) authored.add(id.toString());
        authored.sort(null);

        int implicit = Math.max(0, Cultures.allIds().size() - authored.size());
        line(source, "command.townstead.naming.cultures.heading", authored.size(), implicit);
        if (authored.isEmpty()) {
            line(source, "command.townstead.naming.cultures.none");
        } else {
            for (String id : authored) line(source, "command.townstead.naming.cultures.entry", id);
        }
        return 1;
    }

    // -- set --

    private static int set(CommandSourceStack source, @Nullable Entity target, String culture) {
        VillagerEntityMCA villager = resolve(source, target);
        if (villager == null) return 0;
        if (!Cultures.exists(culture)) {
            line(source, "command.townstead.naming.unknown_culture", culture);
            return 0;
        }

        // The name list and the family name were both derived from the previous culture, and
        // neither is derived again while a value is still recorded, so both are cleared with it.
        // A family name a player typed is the exception: it was chosen rather than derived, so
        // changing somebody's culture does not take their name away. Use clear for that.
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        life.setCulture(culture);
        life.setNameList("");
        life.setNamingTradition("");
        if (!life.familyNameFixed()) life.setFamilyName("");
        TownsteadVillagers.flush(villager);
        com.aetherianartificer.townstead.naming.TraditionAssignment.ensure(source.getLevel(), villager);
        VillagerNames.publish(villager);

        line(source, "command.townstead.naming.set", villager.getName().getString(), culture);
        return report(source, villager);
    }

    private static int setVillage(CommandSourceStack source, String culture) {
        ServerLevel level = source.getLevel();
        if (!Cultures.exists(culture)) {
            line(source, "command.townstead.naming.unknown_culture", culture);
            return 0;
        }
        Optional<Village> village = villageAt(level, BlockPos.containing(source.getPosition()));
        if (village.isEmpty()) {
            line(source, "command.townstead.naming.village.none");
            return 0;
        }
        CultureAssignment.assignVillage(level, village.get().getId(), culture);
        // Residents keep what they already recorded: a village changing its mind does not rename
        // the people living in it. Clear a villager to have them settle again.
        line(source, "command.townstead.naming.village.set", village.get().getName(), culture);
        return 1;
    }

    // -- clear --

    private static int clear(CommandSourceStack source, @Nullable Entity target) {
        VillagerEntityMCA villager = resolve(source, target);
        if (villager == null) return 0;

        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        life.setCulture("");
        life.setNameList("");
        life.setNamingTradition("");
        life.setFamilyName("");
        life.setFamilyNameFixed(false);
        TownsteadVillagers.flush(villager);

        CultureAssignment.ensure(source.getLevel(), villager);
        com.aetherianartificer.townstead.naming.TraditionAssignment.ensure(source.getLevel(), villager);
        VillagerNames.publish(villager);

        line(source, "command.townstead.naming.cleared", villager.getName().getString());
        return report(source, villager);
    }

    // -- shared --

    private static @Nullable VillagerEntityMCA resolve(CommandSourceStack source, @Nullable Entity target) {
        if (target != null) {
            if (target instanceof VillagerEntityMCA villager) return villager;
            line(source, "command.townstead.naming.not_a_villager");
            return null;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            line(source, "command.townstead.naming.no_target");
            return null;
        }
        VillagerEntityMCA villager = CommandTargets.lookedAtOrNearest(player, null);
        if (villager == null) line(source, "command.townstead.naming.no_target");
        return villager;
    }

    private static Optional<Village> villageAt(ServerLevel level, BlockPos pos) {
        try {
            return VillageManager.get(level).findNearestVillage(pos, Village.MERGE_MARGIN);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static void line(CommandSourceStack source, String key, Object... args) {
        source.sendSuccess(() -> Component.translatable(key, args), false);
    }
}
