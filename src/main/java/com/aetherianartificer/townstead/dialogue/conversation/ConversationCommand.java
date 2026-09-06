package com.aetherianartificer.townstead.dialogue.conversation;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Previews use the real lifecycle and safety gates but never grant social rewards. */
public final class ConversationCommand {
    private ConversationCommand() {}
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("social")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("conversations").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal("Conversation topics: " + ConversationTopics.all().keySet()), false);
                    ConversationTopics.diagnostics().forEach(message -> context.getSource().sendFailure(Component.literal(message)));
                    return ConversationTopics.all().size();
                }))
                .then(Commands.literal("conversation")
                        .then(Commands.argument("first", EntityArgument.entity())
                                .then(Commands.argument("second", EntityArgument.entity())
                                        .executes(context -> pair(context, null, false))
                                        .then(Commands.argument("topic", ResourceLocationArgument.id())
                                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                        ConversationTopics.all().keySet().stream().map(ResourceLocation::toString), builder))
                                                .executes(context -> pair(context, ResourceLocationArgument.getId(context, "topic"), false))))))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("first", EntityArgument.entity())
                                .then(Commands.argument("second", EntityArgument.entity()).executes(context -> pair(context, null, true)))))));
    }
    private static int pair(CommandContext<CommandSourceStack> context, ResourceLocation topic, boolean inspect) throws CommandSyntaxException {
        if (!(EntityArgument.getEntity(context, "first") instanceof VillagerEntityMCA first)
                || !(EntityArgument.getEntity(context, "second") instanceof VillagerEntityMCA second)) {
            context.getSource().sendFailure(Component.literal("Select two MCA villagers.")); return 0;
        }
        if (inspect) {
            context.getSource().sendSuccess(() -> Component.literal(ConversationEngine.describe(first, second)), false);
            var facts = ConversationEngine.context(first, second);
            context.getSource().sendSuccess(() -> Component.literal("Context: " + facts.context() + "; relationships: " + facts.relationship()), false);
            var social = com.aetherianartificer.townstead.social.RelationshipService.data(first.getServer());
            long today = com.aetherianartificer.townstead.calendar.TownsteadCalendar.worldDay(first.getServer());
            var relationship = social.relationships().view(first.getUUID(), second.getUUID(), today);
            var descriptions = com.aetherianartificer.townstead.social.RelationshipDescriptors.matching(
                    new com.aetherianartificer.townstead.pheno.condition.ConditionContext(first, second));
            context.getSource().sendSuccess(() -> Component.literal("Descriptions: " + descriptions.stream()
                    .map(value -> value.displayLiteral() + " [" + value.id() + "]").toList()), false);
            context.getSource().sendSuccess(() -> Component.literal("Qualities: " + relationship.qualities()), false);
            context.getSource().sendSuccess(() -> Component.literal("Social inclinations: "
                    + com.aetherianartificer.townstead.social.SocialInclinations.all().values().stream().map(value ->
                    value.displayLiteral()+"="+String.format(java.util.Locale.ROOT,"%.1f",
                            com.aetherianartificer.townstead.social.SocialInclinations.score(first,value))+" / "+
                            String.format(java.util.Locale.ROOT,"%.1f",
                                    com.aetherianartificer.townstead.social.SocialInclinations.score(second,value))).toList()), false);
            relationship.contributions().stream()
                    .sorted(java.util.Comparator.comparingLong(com.aetherianartificer.townstead.social.RelationshipLedger.Contribution::day).reversed())
                    .limit(12).forEach(value -> context.getSource().sendSuccess(() -> Component.literal(
                            "  " + value.quality() + " " + (value.amount() > 0 ? "+" : "") + value.amount()
                                    + " day=" + value.day() + " halfLife=" + value.halfLifeDays()
                                    + " source=" + value.source() + " operation=" + value.operationId()), false));
            var memories = social.memoriesFor(first.getUUID()).stream()
                    .filter(value -> second.getUUID().equals(value.otherParty()))
                    .sorted(java.util.Comparator.comparingLong(com.aetherianartificer.townstead.chronicle.model.VillagerMemory::lastDay).reversed())
                    .limit(12).toList();
            context.getSource().sendSuccess(() -> Component.literal("Memories: " + memories.stream().map(value ->
                    com.aetherianartificer.townstead.social.SocialMemories.byId(value.memoryKey()).displayLiteral()
                            + " [" + value.memoryKey() + "] strength=" + value.strength()
                            + " valence=" + value.valence() + " day=" + value.lastDay()
                            + " source=" + value.source()).toList()), false);
            var bonds = social.bonds().entries(first.getUUID()).stream()
                    .filter(value -> value.involves(second.getUUID())).toList();
            context.getSource().sendSuccess(() -> Component.literal("Bonds: " + bonds.stream().map(value ->
                    value.kind() + " " + (value.active() ? "active" : "ended day " + value.endDay())
                            + " formedBy=" + value.formedBy()).toList()), false);
            ConversationTopics.all().values().forEach(candidate -> {
                var frame = new com.aetherianartificer.townstead.pheno.condition.ConditionContext(first, second);
                double eligibility = candidate.gate().weight(facts, condition -> condition.test(frame),
                        value -> value.get(com.aetherianartificer.townstead.pheno.selector.SelectorContext.of(frame)));
                context.getSource().sendSuccess(() -> Component.literal(candidate.id() + ": gate weight=" + eligibility), false);
            });
            return 1;
        }
        boolean started = ConversationEngine.request(first, second, topic, true);
        if (started) context.getSource().sendSuccess(() -> Component.literal("Conversation preview started; no memories, opinion or mood rewards will be granted."), false);
        else context.getSource().sendFailure(Component.literal("No conversation started. Check proximity, sight, schedules, existing dialogue and topic eligibility with /townstead social inspect."));
        return started ? 1 : 0;
    }
}
