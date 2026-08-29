package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.profession.skill.LearnedSkills;
import com.aetherianartificer.townstead.villager.ProfessionXpStore;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Career readout and choice commands over the same server-rendered tree the Career screen
 * uses ({@link CareerTreeRows}). {@code screen} opens the graphical tree; the Archives' Scribe
 * villager opens it diegetically.
 */
public final class CareerCommand {
    private CareerCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("townstead").then(CareerDebugCommands.attach(
                Commands.literal("career")
                .executes(ctx -> inspect(ctx.getSource(), ctx.getSource().getPlayer()))
                .then(Commands.literal("screen")
                        .executes(ctx -> screen(ctx.getSource(), null))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> screen(ctx.getSource(), living(
                                        EntityArgument.getEntity(ctx, "target"))))))
                .then(Commands.literal("inspect")
                        .executes(ctx -> inspect(ctx.getSource(), focusOrSelf(ctx.getSource())))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(ctx -> inspect(ctx.getSource(), living(
                                        EntityArgument.getEntity(ctx, "target"))))))
                .then(Commands.literal("choose")
                        .then(Commands.argument("skill", StringArgumentType.string())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        SkillDefs.all().keySet().stream().map(ResourceLocation::toString), builder))
                                .executes(ctx -> choose(ctx.getSource(), focusOrSelf(ctx.getSource()),
                                        ResourceLocation.tryParse(StringArgumentType.getString(ctx, "skill"))))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .requires(source -> source.hasPermission(2))
                                        .executes(ctx -> choose(ctx.getSource(), living(
                                                        EntityArgument.getEntity(ctx, "target")),
                                                ResourceLocation.tryParse(StringArgumentType.getString(ctx, "skill"))))))))));
    }

    private static int screen(CommandSourceStack source, LivingEntity target) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("The Career screen needs a player."));
            return 0;
        }
        if (target == null || target == player) {
            CareerTreeOpener.send(player);
        } else {
            CareerTreeOpener.send(player, target, true);
        }
        return 1;
    }

    private static int inspect(CommandSourceStack source, LivingEntity entity) {
        if (entity == null || CareerProfiles.of(entity) == null || xpStore(entity) == null) {
            source.sendFailure(Component.literal("No character with a Career found."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("=== Career: " + entity.getName().getString() + " ==="), false);
        for (CareerTreeRows.Row row : CareerTreeRows.build(source.getServer(), entity)) {
            String indent = "  ".repeat(row.depth());
            String suffix = row.skillId().isEmpty()
                    ? "" : " (/townstead career choose " + row.skillId() + ")";
            source.sendSuccess(() -> Component.literal(indent + row.text() + suffix), false);
        }
        return 1;
    }

    private static int choose(CommandSourceStack source, LivingEntity entity, ResourceLocation skillId) {
        if (entity == null || skillId == null) {
            source.sendFailure(Component.literal("No character or valid skill selected."));
            return 0;
        }
        LearnedSkills.Result result = CareerChoices.chooseFromAcquired(entity, skillId);
        if (!result.ok()) {
            source.sendFailure(Component.literal("Cannot equip " + skillId + ": " + result.error()));
            return 0;
        }
        SkillDef skill = SkillDefs.byId(skillId);
        source.sendSuccess(() -> Component.literal("Equipped " +
                (skill == null ? skillId : skill.displayName().getString()) + " for " + entity.getName().getString()), false);
        return inspect(source, entity);
    }

    private static LivingEntity focusOrSelf(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return null;
        return player.serverLevel().getEntitiesOfClass(VillagerEntityMCA.class,
                        player.getBoundingBox().inflate(8.0), LivingEntity::isAlive).stream()
                .min(java.util.Comparator.comparingDouble(player::distanceToSqr))
                .map(LivingEntity.class::cast).orElse(player);
    }

    private static LivingEntity living(Entity entity) {
        return entity instanceof LivingEntity value ? value : null;
    }

    private static ProfessionXpStore xpStore(LivingEntity entity) {
        if (entity instanceof VillagerEntityMCA villager) {
            return TownsteadVillagers.get(villager).professionMemory();
        }
        if (entity instanceof Player player) return PlayerCareers.xpStore(player);
        return null;
    }
}
