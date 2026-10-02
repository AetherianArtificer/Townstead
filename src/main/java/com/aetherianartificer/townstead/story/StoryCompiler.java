package com.aetherianartificer.townstead.story;

import com.bladecoder.ink.compiler.Compiler;
import com.bladecoder.ink.compiler.IFileHandler;
import com.bladecoder.ink.runtime.Container;
import com.bladecoder.ink.runtime.Error.ErrorType;
import com.bladecoder.ink.runtime.INamedContent;
import com.bladecoder.ink.runtime.Story;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Compiles a story folder's {@code .ink} files as one story. Every file is included, so writers
 * never manage INCLUDE lines, and the helper functions are declared for them unless a file
 * declares its own.
 */
final class StoryCompiler {
    private StoryCompiler() {}

    /** Helpers the host binds for every story, with their Ink parameter lists. */
    static final Map<String, String> EXTERNALS = Map.ofEntries(
            Map.entry("check", "what"),
            Map.entry("count", "what"),
            Map.entry("who", "role"),
            Map.entry("here", "role"),
            Map.entry("career_path", ""),
            Map.entry("is", "name, what"),
            Map.entry("building", "kind"),
            Map.entry("roll", "persona, name"),
            Map.entry("persona_name", "persona"),
            Map.entry("most_harvested", ""),
            Map.entry("rel", "quality"),
            Map.entry("trust", "amount"),
            Map.entry("contribute", "quality, amount, reason"),
            Map.entry("memory", "id"),
            Map.entry("act", "id"),
            Map.entry("mod", "id"),
            Map.entry("can_build", "building"),
            Map.entry("demeanor", ""),
            Map.entry("contract_offer", "pool"),
            Map.entry("contract_about", "pool"),
            Map.entry("contract_skip", "pool"),
            Map.entry("contract_accept", "pool"),
            Map.entry("contract_ready", "pool"),
            Map.entry("contract_active", "pool"),
            Map.entry("contract_turn_in", "pool"));

    private static final String ROOT = "<story>";

    record Knot(String name, List<String> tags, List<String> stitches) {}

    /** The runtime returns null, not an empty list, for a knot with no tags. */
    private static List<String> tagsOrNone(List<String> tags) {
        return tags == null ? List.of() : tags;
    }

    record Result(String json, Map<String, Knot> knots, List<String> errors, List<String> warnings) {
        boolean ok() { return json != null && errors.isEmpty(); }
    }

    /** {@code files} maps a path relative to the story folder to its source, in include order. */
    static Result compile(Map<String, String> files) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        StringBuilder root = new StringBuilder();
        for (Map.Entry<String, String> external : EXTERNALS.entrySet()) {
            Pattern declared = Pattern.compile("(?m)^\\s*EXTERNAL\\s+" + external.getKey() + "\\s*\\(");
            if (files.values().stream().noneMatch(source -> declared.matcher(source).find())) {
                root.append("EXTERNAL ").append(external.getKey()).append('(').append(external.getValue()).append(")\n");
            }
        }
        for (String path : files.keySet()) root.append("INCLUDE ").append(path).append('\n');
        root.append("-> DONE\n");

        Compiler.Options options = new Compiler.Options();
        options.sourceFilename = ROOT;
        options.countAllVisits = true;
        options.errorHandler = (message, type) -> (type == ErrorType.Error ? errors : warnings).add(clean(message));
        options.fileHandler = new IFileHandler() {
            @Override public String resolveInkFilename(String name) { return name; }
            @Override public String loadInkFileContents(String name) throws IOException {
                String source = files.get(name);
                if (source == null) throw new IOException("no file " + name);
                return source;
            }
        };
        try {
            Story story = new Compiler(root.toString(), options).compile();
            if (story == null || !errors.isEmpty()) return new Result(null, Map.of(), errors, warnings);
            Map<String, Knot> knots = new LinkedHashMap<>();
            for (Map.Entry<String, INamedContent> entry : story.getMainContentContainer().getNamedContent().entrySet()) {
                if (!(entry.getValue() instanceof Container container) || entry.getKey().contains(" ")) continue;
                List<String> stitches = new ArrayList<>();
                for (Map.Entry<String, INamedContent> inner : container.getNamedContent().entrySet()) {
                    if (inner.getValue() instanceof Container) stitches.add(inner.getKey());
                }
                knots.put(entry.getKey(), new Knot(entry.getKey(), tagsOrNone(story.tagsForContentAtPath(entry.getKey())), stitches));
            }
            return new Result(story.toJson(), knots, errors, warnings);
        } catch (Exception e) {
            if (errors.isEmpty()) errors.add(e.getMessage() == null ? e.toString() : e.getMessage());
            return new Result(null, Map.of(), errors, warnings);
        }
    }

    private static String clean(String message) {
        return message.replaceFirst("^(ERROR|WARNING|TODO): ", "");
    }
}
