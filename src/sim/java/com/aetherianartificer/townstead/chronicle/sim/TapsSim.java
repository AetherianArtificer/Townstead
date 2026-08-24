package com.aetherianartificer.townstead.chronicle.sim;

import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Chronicle trigger vocabulary authored by loaded templates. Work Jobs and task engines
 * own their completion ids, so this report must not maintain a second Java list of them.
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

        SimOutput.heading("chronicle triggers: data-authored bindings");
        byTrigger.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                System.out.printf(Locale.ROOT, "  %-42s %s%n", entry.getKey(), names(entry.getValue())));
        System.out.printf(Locale.ROOT, "%n%d trigger bindings are authored.%n", byTrigger.size());

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
