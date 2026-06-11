package com.aetherianartificer.townstead.pheno.lang.command;

import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.lang.compile.Severity;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * {@code /pheno} authoring commands (op level 2). {@code /pheno validate} reports the
 * diagnostics from the most recent datapack compile, located by resource id and JSON path, so
 * authors can find unusable types without launching into the world and watching genes silently
 * disappear. {@code expand} and {@code explain} are added in later stages.
 */
public final class PhenoCommand {

    private static final int MAX_LINES = 60;

    private PhenoCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(Commands.literal("pheno")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("validate").executes(c -> validate(c.getSource()))));
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
