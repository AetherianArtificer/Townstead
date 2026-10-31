package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Writes every loaded part to {@code build/parts.json} in a normalized form, for comparing two data
 * formats. Runs only when {@code TOWNSTEAD_DUMP_PARTS} is set.
 */
class PartDumpTest {
    @Test void dumpParts() throws Exception {
        Assumptions.assumeTrue(System.getenv("TOWNSTEAD_DUMP_PARTS") != null);
        List<Map<String, Object>> out = new ArrayList<>();
        for (List<DialoguePart> pool : ShippedDialogue.data().partsByPool().values()) {
            for (DialoguePart p : pool) {
                Map<String, Object> m = new TreeMap<>();
                m.put("pool", p.pool().toString());
                m.put("voice", p.voice().toString());
                m.put("key", p.key());
                m.put("text", p.english());
                m.put("weight", p.weight());
                m.put("act", p.act().name().toLowerCase(Locale.ROOT));
                m.put("asks", p.asks());
                m.put("answers", p.answers());
                m.put("opens", p.opens() == null ? null : p.opens().toString());
                m.put("responds", sorted(p.responds().stream().map(Object::toString).toList()));
                m.put("about", p.about());
                m.put("stance", p.stance());
                m.put("form", p.form());
                m.put("sets", new TreeMap<>(p.sets()));
                Map<String, List<String>> state = new TreeMap<>();
                p.state().forEach((k, v) -> state.put(k, sorted(v)));
                m.put("state", state);
                m.put("persp", new TreeMap<>(p.persp()));
                m.put("introduces", p.introduces());
                m.put("args", sorted(p.args()));
                m.put("subjects", sorted(p.subjects()));
                m.put("variants", sorted(p.variants()));
                m.put("registers", sorted(p.registers()));
                m.put("valence", sorted(p.valence()));
                m.put("provenance", sorted(p.provenance()));
                m.put("stages", sorted(p.stages()));
                m.put("personalities", sorted(p.personalities()));
                m.put("marked", p.marked());
                m.put("simple", p.simple());
                m.put("min_lines", p.minLines());
                m.put("requires", sorted(p.requires()));
                m.put("content_tags", sorted(p.contentTags()));
                out.add(m);
            }
        }
        Path file = Path.of("build", "parts.json");
        Files.createDirectories(file.getParent());
        Files.writeString(file, new GsonBuilder().serializeNulls().setPrettyPrinting().create().toJson(out));
    }

    private static List<String> sorted(Collection<String> values) {
        List<String> out = new ArrayList<>(values);
        Collections.sort(out);
        return out;
    }
}
