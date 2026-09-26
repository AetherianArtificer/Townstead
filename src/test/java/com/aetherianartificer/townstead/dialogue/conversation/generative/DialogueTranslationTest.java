package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Translated dialogue lines must name real lines, use only slots the line has, and pick word forms safely. */
class DialogueTranslationTest {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]*)}");
    private static final Pattern PLAIN = Pattern.compile("[a-z0-9_]+");
    private static final Pattern SELECTOR = Pattern.compile("([a-z0-9_]+)\\.(gender|number):(.+)");
    private static final Set<String> VALUES = Set.of("m", "f", "n", "singular", "plural", "*");

    @Test void translatedLinesAreWellFormed() throws Exception {
        Map<String, DialoguePart> parts = new HashMap<>();
        for (List<DialoguePart> pool : ShippedDialogue.data().partsByPool().values())
            for (DialoguePart part : pool) if (part.key() != null) parts.put(part.key(), part);

        List<String> problems = new ArrayList<>();
        int checked = 0;
        for (Path file : langFiles()) {
            var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (var entry : json.entrySet()) {
                if (!entry.getKey().startsWith("dialogue.")) continue;
                checked++;
                String where = file.getParent().getParent().getFileName() + "/" + file.getFileName() + " " + entry.getKey();
                DialoguePart part = parts.get(entry.getKey());
                if (part == null) { problems.add(where + ": no such line"); continue; }
                problems.addAll(check(where, entry.getValue().getAsString(), part));
            }
        }
        assertEquals(List.of(), problems);
        assertTrue(checked > 0, "found no translations to check");
    }

    private static List<String> check(String where, String text, DialoguePart part) {
        List<String> out = new ArrayList<>();
        if (text.isBlank()) out.add(where + ": blank");
        if (text.chars().filter(c -> c == '{').count() != text.chars().filter(c -> c == '}').count())
            out.add(where + ": unbalanced braces");
        Set<String> allowed = new HashSet<>(part.args());
        allowed.addAll(Set.of("self", "other"));
        if (allowed.contains("mob") || allowed.contains("a_mob")) allowed.addAll(Set.of("mob", "a_mob"));
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            String body = m.group(1);
            if (PLAIN.matcher(body).matches()) {
                if (!allowed.contains(body)) out.add(where + ": prints {" + body + "}, which the line does not have");
                continue;
            }
            Matcher s = SELECTOR.matcher(body);
            if (!s.matches()) { out.add(where + ": malformed {" + body + "}"); continue; }
            boolean fallback = false;
            for (String option : s.group(3).split("\\|", -1)) {
                int eq = option.indexOf('=');
                String when = eq < 0 ? option : option.substring(0, eq);
                if (eq < 0 || !VALUES.contains(when)) out.add(where + ": bad option '" + option + "'");
                fallback |= when.equals("*");
            }
            if (!fallback) out.add(where + ": {" + body + "} has no * option");
        }
        return out;
    }

    private static List<Path> langFiles() throws Exception {
        List<Path> out = new ArrayList<>();
        for (Path root : ShippedDialogue.roots()) {
            Path data = root.resolve("data");
            if (!Files.isDirectory(data)) continue;
            try (var namespaces = Files.list(data)) {
                for (Path lang : namespaces.map(ns -> ns.resolve("lang")).filter(Files::isDirectory).toList()) {
                    try (var files = Files.list(lang)) {
                        files.filter(f -> f.toString().endsWith(".json") && !f.toString().endsWith(".source.json")
                                && !f.getFileName().toString().equals("en_us.json")).sorted().forEach(out::add);
                    }
                }
            }
        }
        return out;
    }
}
