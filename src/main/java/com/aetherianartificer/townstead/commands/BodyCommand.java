package com.aetherianartificer.townstead.commands;

import com.aetherianartificer.townstead.root.attachment.BodyParts;
import com.aetherianartificer.townstead.root.attachment.BodyVolumes;
import com.aetherianartificer.townstead.root.attachment.WornAttachments;
import com.aetherianartificer.townstead.compat.mess.MessBridge;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;


/** Testing commands for what's on a body: {@code /townstead body trace|wash|hit|heal|show}. */
public final class BodyCommand {

    private static final SuggestionProvider<CommandSourceStack> TRACE_IDS = (c, b) -> {
        return QuotedSuggestions.suggest(MessBridge.traceIds(), b);
    };

    private BodyCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("body")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("trace")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("id", StringArgumentType.string()).suggests(TRACE_IDS)
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0f, 1f))
                                                .executes(c -> trace(c.getSource(), EntityArgument.getEntity(c, "target"),
                                                        StringArgumentType.getString(c, "id"),
                                                        FloatArgumentType.getFloat(c, "amount")))))))
                .then(Commands.literal("spray")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("liquid", StringArgumentType.string())
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0f, 100f))
                                                .executes(c -> spray(c.getSource(), EntityArgument.getEntity(c, "target"),
                                                        StringArgumentType.getString(c, "liquid"),
                                                        FloatArgumentType.getFloat(c, "amount")))))))
                .then(Commands.literal("meter")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("id", StringArgumentType.string())
                                        .then(Commands.argument("value", IntegerArgumentType.integer())
                                                .executes(c -> meter(c.getSource(), EntityArgument.getEntity(c, "target"),
                                                        StringArgumentType.getString(c, "id"),
                                                        IntegerArgumentType.getInteger(c, "value")))))))
                .then(Commands.literal("wash")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("strength", IntegerArgumentType.integer(1, 4))
                                        .executes(c -> wash(c.getSource(), EntityArgument.getEntity(c, "target"),
                                                IntegerArgumentType.getInteger(c, "strength"))))))
                .then(Commands.literal("hit")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("attachment", StringArgumentType.string())
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0f))
                                                .executes(c -> hit(c.getSource(), EntityArgument.getEntity(c, "target"),
                                                        StringArgumentType.getString(c, "attachment"),
                                                        FloatArgumentType.getFloat(c, "amount")))))))
                .then(Commands.literal("heal")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(c -> heal(c.getSource(), EntityArgument.getEntity(c, "target")))))
                .then(Commands.literal("show")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(c -> show(c.getSource(), EntityArgument.getEntity(c, "target")))))));
    }

    /** Tries a liquid without having to hurt anyone: for checking one while writing it. */
    private static int spray(CommandSourceStack source, net.minecraft.world.entity.Entity target, String liquid, float amount) {
        if (!(target instanceof LivingEntity living)) return 0;
        if (!MessBridge.hasMaterial(liquid)) {
            source.sendFailure(Component.literal("No liquid named " + liquid));
            return 0;
        }
        MessBridge.spray(living, liquid, living.position().add(0.0, living.getBbHeight() * 0.6, 0.0),
                living.getLookAngle(), amount);
        return 1;
    }

    private static int trace(CommandSourceStack source, Entity target, String id, float amount) {
        if (!(target instanceof LivingEntity living)) return 0;
        MessBridge.addTrace(living, id, amount);
        source.sendSuccess(() -> Component.literal(id + " on " + target.getName().getString() + ": " + MessBridge.traceAmount(living, id)), false);
        return 1;
    }

    private static int meter(CommandSourceStack source, Entity target, String id, int value) {
        net.minecraft.resources.ResourceLocation meter = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (!(target instanceof LivingEntity living) || meter == null) return 0;
        com.aetherianartificer.townstead.root.ability.ResourceValues.set(living, meter, value);
        int now = com.aetherianartificer.townstead.root.ability.ResourceValues.get(living, meter);
        source.sendSuccess(() -> Component.literal(id + " on " + target.getName().getString() + ": " + now), false);
        return 1;
    }

    private static int wash(CommandSourceStack source, Entity target, int strength) {
        if (!(target instanceof LivingEntity living)) return 0;
        MessBridge.wash(living, strength, 1f, "");
        source.sendSuccess(() -> Component.literal("Washed " + target.getName().getString() + " at strength " + strength), false);
        return 1;
    }

    private static int hit(CommandSourceStack source, Entity target, String attachment, float amount) {
        if (!(target instanceof LivingEntity living)) return 0;
        if (!BodyParts.strike(living, attachment, amount)) {
            source.sendFailure(Component.literal(attachment + " can't be hit"));
            return 0;
        }
        int stage = BodyParts.stage(living, attachment);
        source.sendSuccess(() -> Component.literal(attachment + " on " + target.getName().getString() + ": stage " + stage), false);
        return 1;
    }

    private static int heal(CommandSourceStack source, Entity target) {
        if (!(target instanceof LivingEntity living)) return 0;
        BodyParts.restore(living, "");
        source.sendSuccess(() -> Component.literal("Restored the parts of " + target.getName().getString()), false);
        return 1;
    }

    private static int show(CommandSourceStack source, Entity target) {
        if (!(target instanceof LivingEntity living)) return 0;
        StringBuilder out = new StringBuilder(target.getName().getString()).append(":");
        for (var def : WornAttachments.declared(living)) {
            if (def.body().empty()) continue;
            out.append(" ").append(def.id()).append(" stage=").append(BodyParts.stage(living, def.id()));
        }
        out.append(" boxes=").append(BodyVolumes.all(living).size());
        out.append(" folded=").append(BodyVolumes.folded(living));
        for (String id : MessBridge.traceIds()) {
            float amount = MessBridge.traceAmount(living, id);
            if (amount > 0f) out.append(" ").append(id).append("=").append(String.format("%.2f", amount));
        }
        String text = out.toString();
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
