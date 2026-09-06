package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.ProfessionPaths;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.profession.skill.LearnedSkills;
import com.aetherianartificer.townstead.villager.ProfessionProgress;
import com.aetherianartificer.townstead.villager.ProfessionProgressions;
import com.aetherianartificer.townstead.villager.ProfessionXpStore;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Operator tools for testing the Career board: move a career's level (which is where skill points
 * come from), and refund a career outright.
 *
 * <p>There is no "add N loose points" here because the economy has no points ledger to add to.
 * {@link SkillPoints} derives the balance every time it is asked: earned is the sum of
 * {@code skill_points} across the levels reached, spent is the summed cost of the skills learned
 * from that career, and available is the difference. That is deliberate, and it is why the balance
 * cannot drift from the save. So granting points means granting the levels that pay them, which is
 * also what you want when testing: a point you cannot spend because the skill is gated three levels
 * up tests nothing.</p>
 *
 * <p>All of this is permission level 2 and mutates persisted state directly. Levels moved down
 * leave skills learned above them; the readout says so rather than quietly unlearning anything.</p>
 */
public final class CareerDebugCommands {

    private CareerDebugCommands() {}

    /** Hangs the operator subcommands off the existing {@code /townstead career} node. */
    static LiteralArgumentBuilder<CommandSourceStack> attach(
            LiteralArgumentBuilder<CommandSourceStack> career) {
        return career
                .then(Commands.literal("points")
                        .executes(ctx -> points(ctx.getSource(), self(ctx.getSource())))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> points(ctx.getSource(),
                                        living(EntityArgument.getEntity(ctx, "target")))))
                        .then(Commands.literal("grant")
                                .requires(source -> source.hasPermission(2))
                                .then(careerArg().then(countArg()
                                        .executes(ctx -> move(ctx, true, null))
                                        .then(targetArg()
                                                .executes(ctx -> move(ctx, true,
                                                        EntityArgument.getEntity(ctx, "target")))))))
                        .then(Commands.literal("revoke")
                                .requires(source -> source.hasPermission(2))
                                .then(careerArg().then(countArg()
                                        .executes(ctx -> move(ctx, false, null))
                                        .then(targetArg()
                                                .executes(ctx -> move(ctx, false,
                                                        EntityArgument.getEntity(ctx, "target"))))))))
                .then(Commands.literal("level")
                        .requires(source -> source.hasPermission(2))
                        .then(careerArg().then(Commands.argument("level", IntegerArgumentType.integer(1))
                                .executes(ctx -> level(ctx.getSource(), self(ctx.getSource()),
                                        def(ctx.getSource(), ctx),
                                        IntegerArgumentType.getInteger(ctx, "level")))
                                .then(targetArg().executes(ctx -> level(ctx.getSource(),
                                        living(EntityArgument.getEntity(ctx, "target")),
                                        def(ctx.getSource(), ctx),
                                        IntegerArgumentType.getInteger(ctx, "level")))))))
                .then(Commands.literal("respec")
                        .requires(source -> source.hasPermission(2))
                        .then(careerArg()
                                .executes(ctx -> respec(ctx.getSource(), self(ctx.getSource()),
                                        def(ctx.getSource(), ctx)))
                                .then(targetArg().executes(ctx -> respec(ctx.getSource(),
                                        living(EntityArgument.getEntity(ctx, "target")),
                                        def(ctx.getSource(), ctx))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ResourceLocation>
            careerArg() {
        return Commands.argument("career", ResourceLocationArgument.id())
                .suggests((ctx, builder) -> SharedSuggestionProvider.suggestResource(
                        ProfessionDefs.all().keySet(), builder));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer>
            countArg() {
        return Commands.argument("count", IntegerArgumentType.integer(1));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, net.minecraft.commands.arguments.selector.EntitySelector>
            targetArg() {
        return Commands.argument("target", EntityArgument.entity());
    }

    // ── Readout ────────────────────────────────────────────────────────────

    private static int points(CommandSourceStack source, LivingEntity entity) {
        if (entity == null) {
            source.sendFailure(Component.translatable("townstead.command.career.debug.no_living_target"));
            return 0;
        }
        List<ProfessionDef> careers = careersOf(entity);
        if (careers.isEmpty()) {
            source.sendFailure(Component.translatable(
                    "townstead.command.career.debug.no_career", entity.getName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("townstead.command.career.debug.insight_heading",
                        entity.getName(), SkillPoints.available(entity), SkillPoints.earned(entity),
                        SkillPoints.spent(entity))
                .withStyle(ChatFormatting.GOLD), false);
        for (ProfessionDef def : careers) {
            source.sendSuccess(() -> line(entity, def), false);
        }
        return careers.size();
    }

    private static Component line(LivingEntity entity, ProfessionDef def) {
        ProfessionXpStore store = CareerTreeRows.storeOf(entity);
        int level = store == null ? 1 : ProfessionProgress.getTier(store, def.id());
        int earned = SkillPoints.earned(entity, def);
        ProfessionPaths.Path committed = ProfessionPaths.committedPath(
                def.id(), LearnedSkills.learned(entity)::contains);
        if (committed == null) {
            return Component.translatable("townstead.command.career.debug.line.no_path",
                    def.displayName(), level, ProfessionProgressions.spec(def.id()).maxTier(),
                    store == null ? 0 : ProfessionProgress.getXp(store, def.id()), earned,
                    SkillPoints.spent(entity, def));
        }
        return Component.translatable("townstead.command.career.debug.line.path",
                def.displayName(), level, ProfessionProgressions.spec(def.id()).maxTier(),
                store == null ? 0 : ProfessionProgress.getXp(store, def.id()), earned,
                SkillPoints.spent(entity, def), committed.displayName());
    }

    // ── Grant / revoke, through the level track ────────────────────────────

    private static int move(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
                            boolean grant, Entity target) throws
            com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        LivingEntity entity = target == null ? self(source) : living(target);
        ProfessionDef def = def(source, ctx);
        int count = IntegerArgumentType.getInteger(ctx, "count");
        if (entity == null || def == null) return 0;
        ProfessionXpStore store = CareerTreeRows.storeOf(entity);
        if (store == null) {
            source.sendFailure(Component.translatable(
                    "townstead.command.career.debug.no_progression", entity.getName()));
            return 0;
        }
        int before = ProfessionProgress.getTier(store, def.id());
        int earnedBefore = def.skillPointsThrough(before);
        int maxLevel = ProfessionProgressions.spec(def.id()).maxTier();
        int wanted;
        if (grant) {
            wanted = before;
            while (wanted < maxLevel && def.skillPointsThrough(wanted) < earnedBefore + count) {
                wanted++;
            }
        } else {
            int spent = SkillPoints.spent(entity);
            int available = SkillPoints.available(entity);
            if (count > available) {
                source.sendFailure(Component.translatable(
                        "townstead.command.career.debug.revoke.spent", Math.max(0, available)));
                return 0;
            }
            int earnedOutsideCareer = SkillPoints.earned(entity) - earnedBefore;
            wanted = before;
            // Step down to the highest level that still pays for everything already bought, so a
            // revoke can never strand the target with more spent than earned.
            while (wanted > 1 && def.skillPointsThrough(wanted) > earnedBefore - count
                    && earnedOutsideCareer + def.skillPointsThrough(wanted - 1) >= spent) {
                wanted--;
            }
        }
        int reached = ProfessionProgress.setLevel(store, def.id(), wanted);
        int delta = def.skillPointsThrough(reached) - earnedBefore;
        source.sendSuccess(() -> Component.translatable("townstead.command.career.debug.level_changed",
                        def.displayName(), before, reached, String.format("%+d", delta))
                .withStyle(ChatFormatting.GREEN), true);
        if (Math.abs(delta) < count) {
            source.sendSuccess(() -> Component.translatable(grant
                            ? "townstead.command.career.debug.track_limit.top"
                            : "townstead.command.career.debug.track_limit",
                    reached, Math.abs(delta), count)
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        warnAboveLevel(source, entity, def, reached);
        source.sendSuccess(() -> indent(line(entity, def)), false);
        return 1;
    }

    private static int level(CommandSourceStack source, LivingEntity entity, ProfessionDef def,
                             int level) {
        if (entity == null || def == null) return 0;
        ProfessionXpStore store = CareerTreeRows.storeOf(entity);
        if (store == null) {
            source.sendFailure(Component.translatable(
                    "townstead.command.career.debug.no_progression", entity.getName()));
            return 0;
        }
        int before = ProfessionProgress.getTier(store, def.id());
        int reached = ProfessionProgress.setLevel(store, def.id(), level);
        source.sendSuccess(() -> Component.translatable("townstead.command.career.debug.level_set",
                def.displayName(), before, reached, def.levelName(reached))
                .withStyle(ChatFormatting.GREEN), true);
        warnAboveLevel(source, entity, def, reached);
        source.sendSuccess(() -> indent(line(entity, def)), false);
        return reached;
    }

    /**
     * Dropping a level does not unlearn anything, so say plainly when learned skills are now
     * sitting above the level that should gate them. They stay active; the board will just show
     * a state no normal play could reach.
     */
    private static void warnAboveLevel(CommandSourceStack source, LivingEntity entity,
                                       ProfessionDef def, int level) {
        Set<ResourceLocation> learned = LearnedSkills.learned(entity);
        int stranded = 0;
        for (ResourceLocation skillId : def.skills()) {
            if (!learned.contains(skillId)) continue;
            SkillDef skill = SkillDefs.byId(skillId);
            if (skill != null && skill.tier() > level) stranded++;
        }
        if (stranded == 0) return;
        int count = stranded;
        source.sendSuccess(() -> Component.translatable(count == 1
                        ? "townstead.command.career.debug.skills_above.one"
                        : "townstead.command.career.debug.skills_above.many", count)
                .withStyle(ChatFormatting.YELLOW), false);
    }

    // ── Respec ─────────────────────────────────────────────────────────────

    /**
     * Refunds a whole career: every skill learned from it is force-forgotten, cascading to
     * anything that required it, which returns the points (spent is derived from what is learned)
     * and releases the one-path commitment along with the gateway.
     */
    private static int respec(CommandSourceStack source, LivingEntity entity, ProfessionDef def) {
        if (entity == null || def == null) return 0;
        Set<ResourceLocation> learned = LearnedSkills.learned(entity);
        List<ResourceLocation> owned = new ArrayList<>();
        for (ResourceLocation skillId : def.skills()) {
            if (learned.contains(skillId)) owned.add(skillId);
        }
        if (owned.isEmpty()) {
            source.sendFailure(Component.translatable("townstead.command.career.debug.respec.empty",
                    entity.getName(), def.displayName()));
            return 0;
        }
        int spentBefore = SkillPoints.spent(entity, def);
        int removed = 0;
        for (ResourceLocation skillId : owned) {
            // A cascade may already have taken this one on an earlier pass.
            if (!LearnedSkills.has(entity, skillId)) continue;
            LearnedSkills.ForgetResult result = LearnedSkills.forceForget(entity, skillId);
            if (result.ok()) removed += result.removed().size();
        }
        int refunded = spentBefore - SkillPoints.spent(entity, def);
        int total = removed;
        source.sendSuccess(() -> Component.translatable("townstead.command.career.debug.respec.success",
                def.displayName(), total, refunded).withStyle(ChatFormatting.GREEN), true);
        source.sendSuccess(() -> indent(line(entity, def)), false);
        source.sendSuccess(() -> Component.translatable(
                "townstead.command.career.debug.respec.reopen")
                .withStyle(ChatFormatting.GRAY), false);
        return total;
    }

    // ── Shared ─────────────────────────────────────────────────────────────

    /**
     * Every career the target has any standing in: what they practice, what they have acquired,
     * and anything they have banked XP in but no longer work.
     */
    private static List<ProfessionDef> careersOf(LivingEntity entity) {
        CareerProfile profile = CareerProfiles.of(entity);
        if (profile == null) return List.of();
        java.util.LinkedHashSet<ResourceLocation> ids = new java.util.LinkedHashSet<>();
        if (profile.primaryVocation() != null) ids.add(profile.primaryVocation());
        ids.addAll(profile.acquiredCareers());
        ids.addAll(profile.careerHistory());
        List<ProfessionDef> defs = new ArrayList<>();
        for (ResourceLocation id : ids) {
            ProfessionDef def = ProfessionDefs.byId(id);
            if (def != null && !defs.contains(def)) defs.add(def);
        }
        return defs;
    }

    private static ProfessionDef def(CommandSourceStack source,
                                     com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "career");
        ProfessionDef def = ProfessionDefs.byId(id);
        if (def == null) source.sendFailure(Component.translatable(
                "townstead.command.career.debug.unknown", id.toString()));
        return def;
    }

    /** Debug commands default to the operator, never to a bystanding villager. */
    private static LivingEntity self(CommandSourceStack source) {
        LivingEntity player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable(
                    "townstead.command.career.debug.console_target"));
        }
        return player;
    }

    private static LivingEntity living(Entity entity) {
        return entity instanceof LivingEntity value ? value : null;
    }

    private static MutableComponent indent(Component component) {
        return Component.empty().append("  ").append(component);
    }
}
