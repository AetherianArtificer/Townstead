package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.chronicle.emit.ChronicleTapKeys;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Coverage: what the game can report against what any template listens for.
 * A key with no template is a story the world is telling that nobody writes
 * down; a template bound to no key can only ever appear in a fabricated past.
 */
public final class TapsSim {

    private TapsSim() {}

    public static int run(SimArgs args, SimTemplates.Loaded loaded) {
        Map<String, List<ChronicleEventTemplate>> byTrigger = new LinkedHashMap<>();
        List<ChronicleEventTemplate> pregenOnly = new ArrayList<>();
        for (ChronicleEventTemplate template : loaded.templates().values()) {
            if (template.trigger() == null) {
                pregenOnly.add(template);
                continue;
            }
            byTrigger.computeIfAbsent(template.trigger().toString(), k -> new ArrayList<>())
                    .add(template);
        }

        int covered = 0;
        int total = 0;
        SimOutput.heading("tap coverage: what the game reports vs what listens");
        for (Map.Entry<String, List<String>> group
                : new TreeMap<>(ChronicleTapKeys.BY_TYPE).entrySet()) {
            System.out.printf(Locale.ROOT, "%n%s%n", group.getKey());
            for (String key : group.getValue()) {
                List<ChronicleEventTemplate> bound =
                        byTrigger.getOrDefault(group.getKey() + "/" + key, List.of());
                total++;
                if (!bound.isEmpty()) covered++;
                System.out.printf(Locale.ROOT, "  %-28s %s%n", key,
                        bound.isEmpty() ? "-" : names(bound));
            }
        }

        System.out.printf(Locale.ROOT, "%n%d of %d semantic keys have a template.%n", covered, total);

        List<String> gameBound = new ArrayList<>();
        byTrigger.forEach((trigger, templates) -> {
            if (trigger.startsWith("game/")) {
                gameBound.add(trigger.substring("game/".length()) + " -> " + names(templates));
            }
        });
        if (!gameBound.isEmpty()) {
            SimOutput.section("Minecraft game events listened for (mod-neutral, no tap needed)");
            gameBound.stream().sorted().forEach(line -> System.out.println("  " + line));
        }
        if (!pregenOnly.isEmpty()) {
            SimOutput.section("pre-history only (no trigger, fabricated pasts only)");
            pregenOnly.forEach(t -> System.out.println("  " + t.id()));
        }
        return 0;
    }

    private static String names(List<ChronicleEventTemplate> templates) {
        List<String> ids = new ArrayList<>(templates.size());
        templates.forEach(t -> ids.add(t.id().getPath()));
        return String.join(", ", ids);
    }
}
