package com.aetherianartificer.townstead.compat.werewolves;

import com.aetherianartificer.townstead.aspect.AspectPicks;
import com.aetherianartificer.townstead.commands.CompatCommands;
import com.aetherianartificer.townstead.compat.vampirism.VampirismStateProviders;
import com.aetherianartificer.townstead.pheno.state.EntityStateDefinition;
import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /townstead compat werewolves}: make and cure werewolves, turn a villager into either beast
 * form (or hold them in one, so each rig can be looked at by day), set rage and looks, jump to the
 * next full moon, and show where a werewolf stands.
 */
public final class WerewolfCommands {
    private static final ResourceLocation FACTION = ResourceLocation.tryParse("werewolves:werewolf");
    private static final String[] FORMS = {"human", "four_legged", "upright"};

    private WerewolfCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("werewolves");

        LiteralArgumentBuilder<CommandSourceStack> make = Commands.literal("make");
        CompatCommands.withTargets(make, (node, targets) -> node
                .executes(c -> make(c, targets, 1))
                .then(Commands.argument("level", IntegerArgumentType.integer(1, 14))
                        .executes(c -> make(c, targets, IntegerArgumentType.getInteger(c, "level")))));
        root.then(make);

        LiteralArgumentBuilder<CommandSourceStack> cure = Commands.literal("cure");
        CompatCommands.withTargets(cure, (node, targets) -> node.executes(c -> cure(c, targets)));
        root.then(cure);

        LiteralArgumentBuilder<CommandSourceStack> form = Commands.literal("form");
        CompatCommands.withTargets(form, (node, targets) -> {
            for (int i = 0; i < FORMS.length; i++) {
                final int value = i;
                node.then(Commands.literal(FORMS[i])
                        .executes(c -> form(c, targets, value, false))
                        .then(Commands.literal("hold").executes(c -> form(c, targets, value, true))));
            }
        });
        root.then(form);

        LiteralArgumentBuilder<CommandSourceStack> release = Commands.literal("release");
        CompatCommands.withTargets(release, (node, targets) -> node.executes(c -> release(c, targets)));
        root.then(release);

        LiteralArgumentBuilder<CommandSourceStack> rage = Commands.literal("rage");
        CompatCommands.withTargets(rage, (node, targets) -> node
                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0, 300))
                        .executes(c -> rage(c, targets, DoubleArgumentType.getDouble(c, "amount")))));
        root.then(rage);

        LiteralArgumentBuilder<CommandSourceStack> looks = Commands.literal("looks");
        CompatCommands.withTargets(looks, (node, targets) -> node
                .then(Commands.argument("look", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(lookIds(), b))
                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                .executes(c -> looks(c, targets, StringArgumentType.getString(c, "look"),
                                        IntegerArgumentType.getInteger(c, "value"))))));
        root.then(looks);

        root.then(Commands.literal("moon").executes(c -> moon(c.getSource())));

        LiteralArgumentBuilder<CommandSourceStack> status = Commands.literal("status");
        CompatCommands.withTargets(status, (node, targets) -> node.executes(c -> status(c, targets)));
        root.then(status);
        return root;
    }

    private static EntityStateDefinition definition() {
        return EntityStates.definition(WerewolfVillagers.WEREWOLF);
    }

    private static boolean isWerewolf(LivingEntity entity) {
        EntityStates.Resolved state = EntityStates.resolve(entity, WerewolfVillagers.WEREWOLF);
        return state.active() && state.amount() >= 1;
    }

    private static List<String> lookIds() {
        List<String> out = new ArrayList<>();
        EntityStateDefinition definition = definition();
        if (definition != null && definition.aspect() != null) {
            for (EntityStateDefinition.Option option : definition.aspect().options()) out.add(option.id());
        }
        return out;
    }

    private static int make(CommandContext<CommandSourceStack> c, CompatCommands.Targets targets, int level) throws CommandSyntaxException {
        EntityStateDefinition definition = definition();
        if (definition == null || definition.aspect() == null) {
            CompatCommands.warn(c.getSource(), "The werewolf state is not loaded.");
            return 0;
        }
        int done = 0;
        for (LivingEntity target : targets.get(c)) {
            if (AspectPicks.become(target, definition, level, false)) {
                done++;
                CompatCommands.say(c.getSource(), CompatCommands.name(target) + " is a werewolf, level " + level + ".");
            } else {
                CompatCommands.warn(c.getSource(), CompatCommands.name(target) + " cannot be a werewolf.");
            }
        }
        return done;
    }

    private static int cure(CommandContext<CommandSourceStack> c, CompatCommands.Targets targets) throws CommandSyntaxException {
        int done = 0;
        for (LivingEntity target : targets.get(c)) {
            if (!isWerewolf(target)) {
                CompatCommands.warn(c.getSource(), CompatCommands.name(target) + " is not a werewolf.");
                continue;
            }
            if (target instanceof Player player && FACTION.equals(VampirismStateProviders.factionOf(player))) {
                VampirismStateProviders.leaveFaction(player);
            } else {
                if (target instanceof VillagerEntityMCA villager) WerewolfVillagers.release(villager);
                EntityStates.clear(target, WerewolfVillagers.WEREWOLF, null);
            }
            done++;
            CompatCommands.say(c.getSource(), CompatCommands.name(target) + " is no longer a werewolf.");
        }
        return done;
    }

    private static int form(CommandContext<CommandSourceStack> c, CompatCommands.Targets targets, int form, boolean hold) throws CommandSyntaxException {
        int done = 0;
        for (LivingEntity target : targets.get(c)) {
            if (!(target instanceof VillagerEntityMCA villager)) {
                CompatCommands.warn(c.getSource(), CompatCommands.name(target)
                        + ": players change form with the Werewolves form actions.");
                continue;
            }
            if (!isWerewolf(villager)) {
                CompatCommands.warn(c.getSource(), CompatCommands.name(villager) + " is not a werewolf.");
                continue;
            }
            if (hold) {
                WerewolfVillagers.hold(villager, form);
                CompatCommands.say(c.getSource(), CompatCommands.name(villager) + " is held " + FORMS[form] + ".");
            } else {
                WerewolfVillagers.release(villager);
                WerewolfVillagers.turn(villager, form);
                CompatCommands.say(c.getSource(), CompatCommands.name(villager) + " is " + FORMS[form] + ".");
                if (form > 0 && !WerewolfVillagers.isNight(villager.level())) {
                    CompatCommands.warn(c.getSource(), "It is day, so the change ends at once. Add hold to keep it.");
                }
            }
            done++;
        }
        return done;
    }

    private static int release(CommandContext<CommandSourceStack> c, CompatCommands.Targets targets) throws CommandSyntaxException {
        int done = 0;
        for (LivingEntity target : targets.get(c)) {
            if (target instanceof VillagerEntityMCA villager && WerewolfVillagers.held(villager) != null) {
                WerewolfVillagers.release(villager);
                done++;
                CompatCommands.say(c.getSource(), CompatCommands.name(villager) + "'s form follows the night again.");
            } else {
                CompatCommands.warn(c.getSource(), CompatCommands.name(target) + " is not held.");
            }
        }
        return done;
    }

    private static int rage(CommandContext<CommandSourceStack> c, CompatCommands.Targets targets, double amount) throws CommandSyntaxException {
        int done = 0;
        for (LivingEntity target : targets.get(c)) {
            if (!(target instanceof VillagerEntityMCA) || !isWerewolf(target)) {
                CompatCommands.warn(c.getSource(), CompatCommands.name(target) + " is not a werewolf villager.");
                continue;
            }
            EntityStates.set(target, WerewolfVillagers.RAGE, amount, 0, null);
            done++;
            CompatCommands.say(c.getSource(), CompatCommands.name(target) + " has " + (int) amount
                    + " rage. At night, past 150, they turn.");
        }
        return done;
    }

    private static int looks(CommandContext<CommandSourceStack> c, CompatCommands.Targets targets, String look, int value) throws CommandSyntaxException {
        EntityStateDefinition definition = definition();
        if (definition == null || definition.aspect() == null) return 0;
        EntityStateDefinition.Option option = null;
        for (EntityStateDefinition.Option candidate : definition.aspect().options()) {
            if (candidate.id().equals(look)) option = candidate;
        }
        if (option == null) {
            CompatCommands.warn(c.getSource(), "No look called " + look + ". Try: " + String.join(", ", lookIds()) + ".");
            return 0;
        }
        if (value > option.count()) {
            CompatCommands.warn(c.getSource(), look + " goes from 1 to " + option.count() + ".");
            return 0;
        }
        int done = 0;
        for (LivingEntity target : targets.get(c)) {
            if (!isWerewolf(target) || !AspectPicks.applyOption(target, definition, look, value - 1)) {
                CompatCommands.warn(c.getSource(), CompatCommands.name(target) + " is not a werewolf.");
                continue;
            }
            done++;
            CompatCommands.say(c.getSource(), CompatCommands.name(target) + ": " + look + " " + value + " of " + option.count() + ".");
        }
        return done;
    }

    private static int moon(CommandSourceStack source) {
        ServerLevel overworld = source.getServer().overworld();
        long time = WerewolfVillagers.nextFullMoonNight(overworld);
        for (ServerLevel level : source.getServer().getAllLevels()) level.setDayTime(time);
        CompatCommands.say(source, "It is the night of a full moon. Every werewolf turns.");
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> c, CompatCommands.Targets targets) throws CommandSyntaxException {
        int done = 0;
        for (LivingEntity target : targets.get(c)) {
            EntityStates.Resolved werewolf = EntityStates.resolve(target, WerewolfVillagers.WEREWOLF);
            if (!werewolf.active()) {
                CompatCommands.say(c.getSource(), CompatCommands.name(target) + ": not a werewolf.");
                continue;
            }
            done++;
            int form = (int) Math.round(EntityStates.resolve(target, WerewolfVillagers.FORM).amount());
            StringBuilder line = new StringBuilder(CompatCommands.name(target))
                    .append(": level ").append((int) Math.floor(werewolf.amount()))
                    .append(werewolf.tier() != null ? " (" + werewolf.tier() + ")" : "")
                    .append(", ").append(FORMS[Math.max(0, Math.min(2, form))]);
            if (target instanceof VillagerEntityMCA villager) {
                line.append(", rage ").append((int) EntityStates.resolve(villager, WerewolfVillagers.RAGE).amount());
                Double held = WerewolfVillagers.held(villager);
                long left = WerewolfVillagers.remaining(villager);
                if (held != null) line.append(", held ").append(FORMS[(int) Math.round(held)]);
                else if (left == Long.MAX_VALUE) line.append(", turned until the full moon sets");
                else if (left >= 0) line.append(", turns back in ").append(left / 20).append("s");
            }
            CompatCommands.say(c.getSource(), line.toString());
        }
        return done;
    }
}
