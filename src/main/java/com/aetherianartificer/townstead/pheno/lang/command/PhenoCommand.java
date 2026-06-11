package com.aetherianartificer.townstead.pheno.lang.command;

import com.aetherianartificer.townstead.origin.gene.GeneRegistry;
import com.aetherianartificer.townstead.pheno.capability.Capabilities;
import com.aetherianartificer.townstead.pheno.capability.CapabilityContribution;
import com.aetherianartificer.townstead.pheno.capability.CapabilityView;
import com.aetherianartificer.townstead.pheno.capability.Resolved;
import com.aetherianartificer.townstead.pheno.capability.ValueKind;
import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Severity;
import com.aetherianartificer.townstead.pheno.lang.normalize.PhenoNormalizer;
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
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
                                .executes(c -> expand(c.getSource(), StringArgumentType.getString(c, "gene"))))));
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
