package com.aetherianartificer.townstead.dialogue.contextual;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/** Per-speaker cooldown and weighted shuffle-bag selection with bounded recent-line memory. */
public final class DialogueSelector {
    private final Map<UUID, Memory> memories = new java.util.LinkedHashMap<>(16, 0.75F, true);

    public synchronized Optional<DialoguePalette.Line> select(UUID speaker, DialogueRequest request,
                                                               Iterable<DialoguePalette> palettes,
                                                               long now, Random random) {
        return select(speaker, request, palettes, now, random, false);
    }

    public synchronized Optional<DialoguePalette.Line> select(UUID speaker, DialogueRequest request,
                                                               Iterable<DialoguePalette> palettes,
                                                               long now, Random random,
                                                               boolean bypassCooldown) {
        List<DialoguePalette> matching = new ArrayList<>();
        int minimumInterval = 0;
        int historySize = 1;
        for (DialoguePalette palette : palettes) {
            if (!palette.intent().equals(request.intent())) continue;
            matching.add(palette);
            minimumInterval = Math.max(minimumInterval, palette.minIntervalTicks());
            historySize = Math.max(historySize, palette.historySize());
        }
        if (matching.isEmpty()) return Optional.empty();
        Memory memory = memories.computeIfAbsent(speaker, ignored -> new Memory());
        while (memories.size() > 4096) memories.remove(memories.keySet().iterator().next());
        if (memory.lastSpoke != Long.MIN_VALUE && (now < memory.lastSpoke || now - memory.lastSpoke > 24000L)) {
            memory.recent.clear(); memory.bags.clear(); memory.lastSpoke = Long.MIN_VALUE;
        }
        if (!bypassCooldown && memory.lastSpoke != Long.MIN_VALUE && now - memory.lastSpoke < minimumInterval) {
            return Optional.empty();
        }

        List<DialoguePalette.Line> eligible = new ArrayList<>();
        for (DialoguePalette palette : matching) {
            for (DialoguePalette.Line line : palette.lines()) if (line.eligible(request)) eligible.add(line);
        }
        if (eligible.isEmpty()) return Optional.empty();
        java.util.Set<String> used = memory.bags.computeIfAbsent(request.intent(), ignored -> new java.util.HashSet<>());
        while (memory.bags.size() > 32) memory.bags.remove(memory.bags.keySet().iterator().next());
        java.util.Set<String> allKeys = new java.util.HashSet<>();
        matching.forEach(p -> p.lines().forEach(line -> allKeys.add(line.key())));
        used.retainAll(allKeys);
        List<DialoguePalette.Line> unused = eligible.stream().filter(line -> !used.contains(line.key())).toList();
        if (unused.isEmpty()) {
            eligible.forEach(line -> used.remove(line.key()));
            unused = eligible;
        }
        List<DialoguePalette.Line> fresh = withoutRecent(unused, memory.recent);
        while (fresh.isEmpty() && !memory.recent.isEmpty()) {
            memory.recent.removeFirst();
            fresh = withoutRecent(unused, memory.recent);
        }
        DialoguePalette.Line selected = weighted(fresh.isEmpty() ? eligible : fresh, random);
        memory.lastSpoke = now;
        memory.recent.addLast(selected.key());
        used.add(selected.key());
        while (memory.recent.size() > historySize) memory.recent.removeFirst();
        return Optional.of(selected);
    }

    public synchronized void clear() { memories.clear(); }

    private static List<DialoguePalette.Line> withoutRecent(List<DialoguePalette.Line> lines, Deque<String> recent) {
        List<DialoguePalette.Line> out = new ArrayList<>();
        for (DialoguePalette.Line line : lines) if (!recent.contains(line.key())) out.add(line);
        return out;
    }

    private static DialoguePalette.Line weighted(List<DialoguePalette.Line> lines, Random random) {
        double total = 0;
        for (DialoguePalette.Line line : lines) total += line.weight();
        double roll = random.nextDouble() * total;
        for (DialoguePalette.Line line : lines) {
            roll -= line.weight();
            if (roll < 0) return line;
        }
        return lines.get(lines.size() - 1);
    }

    private static final class Memory {
        private long lastSpoke = Long.MIN_VALUE;
        private final Deque<String> recent = new ArrayDeque<>();
        private final Map<String, java.util.Set<String>> bags = new java.util.LinkedHashMap<>(16, 0.75F, true);
    }
}
