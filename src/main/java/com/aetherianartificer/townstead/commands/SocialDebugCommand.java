package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.dialogue.contextual.ContextualDialogue;
import com.aetherianartificer.townstead.dialogue.contextual.DialogueDirector;
import com.aetherianartificer.townstead.expression.ExpressionCues;
import com.aetherianartificer.townstead.expression.ExpressionService;
import com.aetherianartificer.townstead.performance.PerformanceProviders;
import com.aetherianartificer.townstead.performance.PerformanceRequest;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/** Focused, operator-only probes for the social presentation stack. */
public final class SocialDebugCommand {
    private static final double LOOK_RANGE = 16.0;
    private static final String DEBUG_CHANNEL = "townstead_social_debug";
    private static final List<String> NATIVE_CLIPS = List.of(
            "animated_story", "attentive", "beckon", "cheer", "cheer_excited", "clap", "cry", "eat", "laugh", "laugh_demure", "nod",
            "point", "ponder", "recline", "recline_lounger", "relaxed_lean", "shake_head", "shiver", "shrug", "sip", "startled", "sweat",
            "stool_sit", "tap_foot", "toast", "wave", "whisper", "yawn");

    private SocialDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        com.aetherianartificer.townstead.dialogue.conversation.ConversationCommand.register(dispatcher);
        SuggestionProvider<CommandSourceStack> cues = (c, b) -> SharedSuggestionProvider.suggest(
                ExpressionCues.all().keySet().stream().map(ResourceLocation::toString), b);
        SuggestionProvider<CommandSourceStack> intents = (c, b) -> SharedSuggestionProvider.suggest(
                ContextualDialogue.all().values().stream().map(p -> p.intent()).distinct(), b);
        SuggestionProvider<CommandSourceStack> clips = (c, b) -> SharedSuggestionProvider.suggest(
                NATIVE_CLIPS.stream().map(id -> "townstead_performance:" + id), b);

        dispatcher.register(Commands.literal("townstead").then(Commands.literal("social")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list")
                        .then(Commands.literal("expressions").executes(c -> listExpressions(c.getSource())))
                        .then(Commands.literal("dialogue").executes(c -> listDialogue(c.getSource())))
                        .then(Commands.literal("performances").executes(c -> listPerformances(c.getSource()))))
                .then(Commands.literal("expression")
                        .then(Commands.argument("cue", ResourceLocationArgument.id()).suggests(cues)
                                .executes(c -> expression(c.getSource(),
                                        ResourceLocationArgument.getId(c, "cue"), autoTarget(c.getSource())))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(c -> expression(c.getSource(),
                                                ResourceLocationArgument.getId(c, "cue"),
                                                EntityArgument.getEntity(c, "target"))))))
                .then(Commands.literal("dialogue")
                        .then(Commands.argument("intent", ResourceLocationArgument.id()).suggests(intents)
                                .executes(c -> dialogue(c.getSource(),
                                        normalizedId(ResourceLocationArgument.getId(c, "intent"), "social"),
                                        autoTarget(c.getSource())))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(c -> dialogue(c.getSource(),
                                                normalizedId(ResourceLocationArgument.getId(c, "intent"), "social"),
                                                EntityArgument.getEntity(c, "target"))))))
                .then(Commands.literal("performance")
                        .then(Commands.argument("clip", ResourceLocationArgument.id()).suggests(clips)
                                .executes(c -> performance(c.getSource(),
                                        ResourceLocationArgument.getId(c, "clip"), autoTarget(c.getSource()), 80))
                                .then(Commands.argument("target", EntityArgument.entity())
                                        .executes(c -> performance(c.getSource(),
                                                ResourceLocationArgument.getId(c, "clip"),
                                                EntityArgument.getEntity(c, "target"), 80))
                                        .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 1200))
                                                .executes(c -> performance(c.getSource(),
                                                        ResourceLocationArgument.getId(c, "clip"),
                                                        EntityArgument.getEntity(c, "target"),
                                                        IntegerArgumentType.getInteger(c, "ticks")))))))
                .then(Commands.literal("stop")
                        .executes(c -> stop(c.getSource(), autoTarget(c.getSource())))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(c -> stop(c.getSource(), EntityArgument.getEntity(c, "target")))))));
    }

    private static int listExpressions(CommandSourceStack source) {
        List<String> ids = ExpressionCues.all().keySet().stream().map(ResourceLocation::toString).sorted().toList();
        return list(source, "command.townstead.social.list.expressions", ids);
    }

    private static int listDialogue(CommandSourceStack source) {
        List<String> ids = ContextualDialogue.all().values().stream().map(p -> p.intent()).distinct().sorted().toList();
        return list(source, "command.townstead.social.list.dialogue", ids);
    }

    private static int listPerformances(CommandSourceStack source) {
        return list(source, "command.townstead.social.list.performances",
                NATIVE_CLIPS.stream().map(id -> "townstead_performance:" + id).sorted().toList());
    }

    private static int list(CommandSourceStack source, String heading, List<String> values) {
        source.sendSuccess(() -> Component.translatable(heading, values.size()), false);
        for (String value : values) source.sendSuccess(
                () -> Component.translatable("command.townstead.social.list.entry", value), false);
        return values.size();
    }

    private static int expression(CommandSourceStack source, ResourceLocation requested, Entity target) {
        if (!(target instanceof LivingEntity living)) return invalidTarget(source);
        ResourceLocation cue = normalized(requested, "townstead_expressions");
        if (cue == null || !ExpressionCues.all().containsKey(cue)) {
            source.sendFailure(Component.translatable("command.townstead.social.expression.unknown",
                    requested == null ? "" : requested.toString()));
            return 0;
        }
        LivingEntity counterpart = source.getPlayer();
        if (!ExpressionService.emitDebug(living, cue, counterpart)) {
            source.sendFailure(Component.translatable("command.townstead.social.expression.failed", cue.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.townstead.social.expression.success",
                cue.toString(), living.getDisplayName()), false);
        return 1;
    }

    private static int dialogue(CommandSourceStack source, String intent, Entity target) {
        if (!(target instanceof VillagerEntityMCA villager)) return invalidTarget(source);
        if (!DialogueDirector.speakDebug(villager, intent, source.getPlayer())) {
            source.sendFailure(Component.translatable("command.townstead.social.dialogue.failed", intent));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.townstead.social.dialogue.success",
                intent, villager.getDisplayName()), false);
        return 1;
    }

    private static int performance(CommandSourceStack source, ResourceLocation requested, Entity target, int ticks) {
        if (!(target instanceof LivingEntity living) || !(living.level() instanceof ServerLevel level)) {
            return invalidTarget(source);
        }
        ResourceLocation clip = normalized(requested, "townstead_performance");
        if (clip == null) {
            source.sendFailure(Component.translatable("command.townstead.social.performance.invalid",
                    requested == null ? "" : requested.toString()));
            return 0;
        }
        var handle = PerformanceProviders.play(level, new PerformanceRequest(living, clip,
                DEBUG_CHANNEL, ticks, 1000, PerformanceRequest.Fallback.NONE));
        if (handle == null) {
            source.sendFailure(Component.translatable("command.townstead.social.performance.failed", clip.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.townstead.social.performance.success",
                clip.toString(), living.getDisplayName(), ticks), false);
        return 1;
    }

    private static int stop(CommandSourceStack source, Entity target) {
        if (!(target instanceof LivingEntity living)) return invalidTarget(source);
        PerformanceProviders.stop(living, DEBUG_CHANNEL);
        source.sendSuccess(() -> Component.translatable("command.townstead.social.stop.success",
                living.getDisplayName()), false);
        return 1;
    }

    private static int invalidTarget(CommandSourceStack source) {
        source.sendFailure(Component.translatable("command.townstead.social.invalid_target"));
        return 0;
    }

    private static ResourceLocation normalized(ResourceLocation requested, String defaultNamespace) {
        if (requested == null) return null;
        return "minecraft".equals(requested.getNamespace())
                ? ResourceLocation.tryParse(defaultNamespace + ":" + requested.getPath())
                : requested;
    }

    private static String normalizedId(ResourceLocation requested, String defaultNamespace) {
        ResourceLocation normalized = normalized(requested, defaultNamespace);
        return normalized == null ? "" : normalized.toString();
    }

    /** Crosshair preference with nearest-MCA fallback, matching the existing reaction probe. */
    private static Entity autoTarget(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return null;
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 end = eye.add(look.scale(LOOK_RANGE));
        AABB sweep = new AABB(eye, end).inflate(2.0);
        List<VillagerEntityMCA> villagers = player.serverLevel().getEntitiesOfClass(VillagerEntityMCA.class, sweep);
        VillagerEntityMCA looked = villagers.stream()
                .filter(v -> {
                    Vec3 delta = v.position().subtract(eye);
                    double along = delta.dot(look);
                    return along > 0 && along <= LOOK_RANGE
                            && v.position().distanceTo(eye.add(look.scale(along))) <= Math.max(0.7, v.getBbWidth());
                })
                .min(Comparator.comparingDouble(v -> v.position().subtract(eye).dot(look))).orElse(null);
        if (looked != null) return looked;
        return villagers.stream().filter(v -> player.distanceToSqr(v) <= LOOK_RANGE * LOOK_RANGE)
                .min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }
}
