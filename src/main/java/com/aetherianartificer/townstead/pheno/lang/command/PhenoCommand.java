package com.aetherianartificer.townstead.pheno.lang.command;

import com.aetherianartificer.townstead.origin.gene.GeneRegistry;
import com.aetherianartificer.townstead.origin.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.pheno.capability.Capabilities;
import com.aetherianartificer.townstead.pheno.capability.CapabilityContribution;
import com.aetherianartificer.townstead.pheno.capability.CapabilityView;
import com.aetherianartificer.townstead.pheno.capability.Resolved;
import com.aetherianartificer.townstead.pheno.capability.ValueKind;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.profession.skill.LearnedSkills;
import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Severity;
import com.aetherianartificer.townstead.pheno.lang.normalize.PhenoNormalizer;
import com.aetherianartificer.townstead.pheno.lang.schema.SchemaGen;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * {@code /pheno} authoring commands (op level 2). {@code /pheno validate} reports the
 * diagnostics from the most recent datapack compile, located by resource id and JSON path, so
 * authors can find unusable types without launching into the world and watching genes silently
 * disappear. {@code expand} and {@code explain} are added in later stages.
 */
public final class PhenoCommand {

    private static final int MAX_LINES = 60;
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private PhenoCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(Commands.literal("pheno")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("validate").executes(c -> validate(c.getSource())))
                .then(Commands.literal("explain")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(c -> explain(c.getSource(), EntityArgument.getEntity(c, "target")))))
                .then(Commands.literal("expand")
                        .then(Commands.argument("gene", StringArgumentType.string())
                                .suggests(SUGGEST_GENES)
                                .executes(c -> expand(c.getSource(), StringArgumentType.getString(c, "gene")))))
                .then(Commands.literal("dump").executes(c -> dump(c.getSource())))
                .then(Commands.literal("parity")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(c -> parity(c.getSource(), EntityArgument.getEntity(c, "target")))))
                .then(Commands.literal("skills")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(c -> skills(c.getSource(), EntityArgument.getEntity(c, "target")))))
                .then(Commands.literal("learn")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("skill", StringArgumentType.string()).suggests(SUGGEST_SKILLS)
                                        .executes(c -> learn(c.getSource(), EntityArgument.getEntity(c, "target"),
                                                StringArgumentType.getString(c, "skill"), false))
                                        .then(Commands.literal("force").executes(c -> learn(c.getSource(),
                                                EntityArgument.getEntity(c, "target"),
                                                StringArgumentType.getString(c, "skill"), true))))))
                .then(Commands.literal("forget")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .then(Commands.argument("skill", StringArgumentType.string()).suggests(SUGGEST_SKILLS)
                                        .executes(c -> forget(c.getSource(), EntityArgument.getEntity(c, "target"),
                                                StringArgumentType.getString(c, "skill"), false))
                                        .then(Commands.literal("force").executes(c -> forget(c.getSource(),
                                                EntityArgument.getEntity(c, "target"),
                                                StringArgumentType.getString(c, "skill"), true)))))));
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_SKILLS = (c, b) ->
            SharedSuggestionProvider.suggest(SkillDefs.all().keySet().stream().map(ResourceLocation::toString), b);

    private static int skills(CommandSourceStack source, Entity target) {
        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("Pheno skills: target is not a living entity."));
            return 0;
        }
        Set<ResourceLocation> learned = LearnedSkills.learned(living);
        if (learned.isEmpty()) {
            source.sendSuccess(() -> Component.literal(target.getName().getString() + " has learned no skills.")
                    .withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Learned skills of " + target.getName().getString() + ":")
                .withStyle(ChatFormatting.GOLD), false);
        for (ResourceLocation id : learned) {
            source.sendSuccess(() -> Component.literal("  " + id).withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }

    private static int learn(CommandSourceStack source, Entity target, String skill, boolean force) {
        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("Pheno learn: target is not a living entity."));
            return 0;
        }
        ResourceLocation id = ResourceLocation.tryParse(skill);
        if (id == null) {
            source.sendFailure(Component.literal("Pheno learn: '" + skill + "' is not a valid id."));
            return 0;
        }
        LearnedSkills.Result result = force ? LearnedSkills.forceLearn(living, id) : LearnedSkills.learn(living, id);
        if (result.ok()) {
            source.sendSuccess(() -> Component.literal(target.getName().getString()
                    + (force ? " force-learned " : " learned ") + id).withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendFailure(Component.literal("Cannot learn " + id + ": " + result.error()));
        return 0;
    }

    private static int forget(CommandSourceStack source, Entity target, String skill, boolean force) {
        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("Pheno forget: target is not a living entity."));
            return 0;
        }
        ResourceLocation id = ResourceLocation.tryParse(skill);
        if (id == null) {
            source.sendFailure(Component.literal("Pheno forget: '" + skill + "' is not a valid id."));
            return 0;
        }
        LearnedSkills.ForgetResult result = force ? LearnedSkills.forceForget(living, id) : LearnedSkills.forget(living, id);
        if (!result.ok()) {
            source.sendFailure(Component.literal("Cannot forget " + id + ": " + result.error()));
            return 0;
        }
        String dependents = result.removed().size() > 1
                ? " (cascaded: " + result.removed() + ")" : "";
        source.sendSuccess(() -> Component.literal(target.getName().getString() + " forgot " + id + dependents)
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * Cross-checks the additive capability layer against a direct legacy gene scan for ability
     * flags. This is the parity gate the plan calls for before any applier is made
     * capability-authoritative: it runs on demand (no hot-path cost) and confirms the mirrored
     * capabilities match what the genes actually express.
     */
    private static int parity(CommandSourceStack source, Entity target) {
        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("Pheno parity: target is not a living entity."));
            return 0;
        }
        ConditionContext ctx = new ConditionContext(living);

        // Modifier parity: the capability apply-to-base fold vs a direct sequential gene fold on a
        // unit base. They match for the common single-op case; a mismatch is the fold-order delta
        // (capability does all adds then all multiplies; the legacy applier folded in gene order).
        java.util.Map<com.aetherianartificer.townstead.pheno.capability.CapabilityKey, java.util.List<com.aetherianartificer.townstead.origin.gene.types.ModifierGeneType.Instance>> byKey =
                new java.util.LinkedHashMap<>();
        for (Power power : Powers.active(living)) {
            if (power.component() instanceof com.aetherianartificer.townstead.origin.gene.types.ModifierGeneType.Instance m) {
                if (m.condition() != null && !m.condition().test(ctx)) continue;
                com.aetherianartificer.townstead.pheno.capability.CapabilityKey key =
                        com.aetherianartificer.townstead.origin.modifier.ModifierCapability.key(m.modifier(), m.discriminator());
                byKey.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(m);
            }
        }
        for (var entry : byKey.entrySet()) {
            double sequential = 1.0;
            for (var m : entry.getValue()) {
                for (var mod : m.mods()) {
                    sequential = switch (mod.op()) {
                        case ADD -> sequential + mod.value();
                        case SET -> mod.value();
                        case MIN -> Math.min(sequential, mod.value());
                        case MAX -> Math.max(sequential, mod.value());
                        case MULTIPLY -> sequential * mod.value();
                    };
                }
            }
            final double seq = sequential;
            final double cap = Capabilities.applyToBase(living, 1.0, entry.getKey());
            final String path = entry.getKey().id().getPath();
            if (Math.abs(seq - cap) < 1.0e-4) {
                source.sendSuccess(() -> Component.literal("  modifier OK " + path + " = " + cap)
                        .withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendSuccess(() -> Component.literal("  modifier ORDER-DELTA " + path
                        + ": sequential=" + seq + " capability=" + cap)
                        .withStyle(ChatFormatting.YELLOW), false);
            }
        }

        Set<String> legacy = new TreeSet<>();
        for (Power power : Powers.active(living)) {
            if (power.component() instanceof AbilityGeneType.Instance ability) {
                if (ability.condition() == null || ability.condition().test(ctx)) {
                    legacy.add(ability.ability().key());
                }
            }
        }
        Set<String> capability = new TreeSet<>();
        for (Resolved r : Capabilities.resolve(living).map().values()) {
            if (r.key().kind() == ValueKind.FLAG && r.flag()) {
                String id = r.key().id().toString();
                int marker = id.indexOf(":ability/");
                if (marker >= 0) capability.add(id.substring(marker + ":ability/".length()));
            }
        }
        if (legacy.equals(capability)) {
            source.sendSuccess(() -> Component.literal("Pheno parity OK: " + legacy.size()
                    + " ability flag(s) match the legacy gene scan.").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        Set<String> onlyLegacy = new TreeSet<>(legacy);
        onlyLegacy.removeAll(capability);
        Set<String> onlyCapability = new TreeSet<>(capability);
        onlyCapability.removeAll(legacy);
        source.sendSuccess(() -> Component.literal("Pheno parity MISMATCH:").withStyle(ChatFormatting.RED), false);
        if (!onlyLegacy.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  only in genes: " + onlyLegacy)
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        if (!onlyCapability.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  only in capabilities: " + onlyCapability)
                    .withStyle(ChatFormatting.YELLOW), false);
        }
        return 0;
    }

    private static int dump(CommandSourceStack source) {
        try {
            Path dir = phenoDir(source);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("types-manifest.json"),
                    PRETTY.toJson(SchemaGen.manifest()), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("reference.md"), SchemaGen.markdown(), StandardCharsets.UTF_8);
            Files.writeString(dir.resolve("gene.schema.json"),
                    PRETTY.toJson(SchemaGen.jsonSchema()), StandardCharsets.UTF_8);
            source.sendSuccess(() -> Component.literal("Pheno: wrote types-manifest.json, reference.md, "
                    + "gene.schema.json to " + dir).withStyle(ChatFormatting.GREEN), false);
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Pheno dump failed: " + ex.getMessage()));
            return 0;
        }
    }

    private static Path phenoDir(CommandSourceStack source) {
        //? if >=1.21 {
        Path base = source.getServer().getServerDirectory();
        //?} else {
        /*Path base = source.getServer().getServerDirectory().toPath();
        *///?}
        return base.resolve("pheno");
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GENES = (c, b) ->
            SharedSuggestionProvider.suggest(GeneRegistry.all().stream().map(g -> g.id().toString()), b);

    private static int expand(CommandSourceStack source, String geneId) {
        ResourceLocation id = ResourceLocation.tryParse(geneId);
        if (id == null) {
            source.sendFailure(Component.literal("Pheno expand: '" + geneId + "' is not a valid id."));
            return 0;
        }
        ResourceLocation file = ResourceLocation.tryParse(id.getNamespace() + ":gene/" + id.getPath() + ".json");
        Optional<Resource> resource = file == null ? Optional.empty()
                : source.getServer().getResourceManager().getResource(file);
        if (resource.isEmpty()) {
            source.sendFailure(Component.literal("Pheno expand: gene resource not found: " + geneId));
            return 0;
        }
        try (Reader reader = new InputStreamReader(resource.get().open(), StandardCharsets.UTF_8)) {
            JsonObject raw = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject canonical = PhenoNormalizer.normalize(raw);
            source.sendSuccess(() -> Component.literal("Canonical form of " + geneId + ":")
                    .withStyle(ChatFormatting.GOLD), false);
            String[] lines = PRETTY.toJson(canonical).split("\n");
            int shown = 0;
            for (String l : lines) {
                if (shown++ >= MAX_LINES) {
                    source.sendSuccess(() -> Component.literal("... (truncated; full output in latest.log)")
                            .withStyle(ChatFormatting.GRAY), false);
                    break;
                }
                String line = l;
                source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.WHITE), false);
            }
            return 1;
        } catch (Exception ex) {
            source.sendFailure(Component.literal("Pheno expand: failed to read " + geneId + ": " + ex.getMessage()));
            return 0;
        }
    }

    private static int explain(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("Pheno explain: target is not a living entity."));
            return 0;
        }
        CapabilityView view = Capabilities.resolve(living);
        if (view.map().isEmpty()) {
            source.sendSuccess(() -> Component.literal("Pheno: " + target.getName().getString()
                    + " contributes no capabilities.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Capabilities of " + target.getName().getString() + ":")
                .withStyle(ChatFormatting.GOLD), false);
        for (Resolved r : view.map().values()) {
            String value = r.key().kind() == ValueKind.FLAG
                    ? (r.flag() ? "on" : "off")
                    : String.format(Locale.ROOT, "%.3f", r.number());
            source.sendSuccess(() -> Component.literal("  " + r.key().id() + " = ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(value).withStyle(ChatFormatting.WHITE)), false);
            for (CapabilityContribution c : r.applied()) {
                source.sendSuccess(() -> Component.literal("      + " + contribution(c))
                        .withStyle(ChatFormatting.GREEN), false);
            }
            for (CapabilityContribution c : r.ignored()) {
                String why = c.active() ? "overridden" : "inactive";
                source.sendSuccess(() -> Component.literal("      - " + contribution(c) + " (" + why + ")")
                        .withStyle(ChatFormatting.DARK_GRAY), false);
            }
        }
        return 1;
    }

    private static String contribution(CapabilityContribution c) {
        String op = c.op().name().toLowerCase(Locale.ROOT);
        String val = c.key().kind() == ValueKind.FLAG ? "" : " " + String.format(Locale.ROOT, "%.3f", c.value());
        return c.provenance().render() + "  [" + op + val + "]";
    }

    private static int validate(CommandSourceStack source) {
        List<Diagnostic> all = PhenoDiagnostics.all();
        if (all.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Pheno: no diagnostics, all loaded resources compiled clean.")
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        int errors = PhenoDiagnostics.count(Severity.ERROR);
        int warnings = PhenoDiagnostics.count(Severity.WARNING);
        source.sendSuccess(() -> Component.literal("Pheno diagnostics: ")
                .append(Component.literal(errors + " error" + (errors == 1 ? "" : "s")).withStyle(ChatFormatting.RED))
                .append(Component.literal(", "))
                .append(Component.literal(warnings + " warning" + (warnings == 1 ? "" : "s"))
                        .withStyle(ChatFormatting.YELLOW)), false);
        int shown = 0;
        for (Diagnostic d : all) {
            if (shown++ >= MAX_LINES) {
                int remaining = all.size() - MAX_LINES;
                source.sendSuccess(() -> Component.literal("... " + remaining + " more (see latest.log)")
                        .withStyle(ChatFormatting.GRAY), false);
                break;
            }
            source.sendSuccess(() -> line(d), false);
        }
        return errors > 0 ? 0 : 1;
    }

    private static Component line(Diagnostic d) {
        ChatFormatting colour = switch (d.severity()) {
            case ERROR -> ChatFormatting.RED;
            case WARNING -> ChatFormatting.YELLOW;
            case INFO -> ChatFormatting.AQUA;
            case HINT -> ChatFormatting.GRAY;
        };
        Component head = Component.literal(d.resource() + " " + d.jsonPath()).withStyle(ChatFormatting.GRAY);
        Component body = Component.literal(d.message()).withStyle(colour);
        Component out = Component.empty().append(head).append(Component.literal("  ")).append(body);
        if (d.suggestion() != null) {
            out = Component.empty().append(out)
                    .append(Component.literal("  " + d.suggestion()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }
}
