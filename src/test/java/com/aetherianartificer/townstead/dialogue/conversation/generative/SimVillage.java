package com.aetherianartificer.townstead.dialogue.conversation.generative;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A small made-up village that lives across many conversations: a fixed roster, a place, weather by day, needs,
 * news that only some people know, mob sightings, relationships and a memory of what each pair already discussed.
 * Subjects exist only when the village supports them, as they do in game.
 */
final class SimVillage implements LineComposer.Recency {
    private static final String[] NAMES = {"Aldo", "Brina", "Corin", "Dela", "Edric", "Fenna", "Garrit", "Hesta", "Ivo",
            "Jorun", "Katla", "Lenz", "Mirela", "Nico"};
    private static final String[] PERSONALITIES = {"friendly", "flirty", "playful", "gloomy", "sensitive", "greedy", "odd",
            "crabby", "extroverted", "introverted", "relaxed", "anxious", "peaceful", "upbeat", "grumpy", "peppy"};
    private static final String[] STAGES = {"adult", "adult", "adult", "adult", "child", "teen", "senior", "senior"};
    private static final String[] BUILDINGS = {"well", "market", "smithy", "tavern", "cafe", "bridge", "orchard", "mill",
            "quest_board", "chapel"};
    /** name, number, biome tag. */
    private static final String[][] BIOMES = {
            {"the plains", "plural", "c:is_plains"}, {"the forest", "singular", "minecraft:is_forest"},
            {"the desert", "singular", "c:is_desert"}, {"the taiga", "singular", "minecraft:is_taiga"},
            {"the swamp", "singular", "c:is_swamp"}, {"the savanna", "singular", "minecraft:is_savanna"},
            {"the snowy plains", "plural", "c:is_snowy"}};
    private static final String[] MOBS = {"zombie", "skeleton", "creeper", "spider", "witch", "enderman", "husk", "drowned", "pillager"};
    private static final String[] NEEDS = {"hungry", "thirsty", "tired", "cold", "hot"};
    private static final String[] EVENTS = {"argument", "mastery", "marriage"};

    record Villager(UUID id, String name, String personality, String stage) {}
    record Story(String variant, Villager who, @Nullable Villager other2, int day, Set<UUID> knownBy) {}
    record Sighting(String mob, Villager witness, int day) {}
    /** A hangout place: the coarse kind that state gates use, and the amenities and tags that line requirements read. */
    record Venue(String name, String kind, Set<String> amenities, Set<String> tags) {}
    private static final Venue[] VENUES = {
            new Venue("tavern", "tavern", Set.of("seating", "food", "drink"), Set.of("lively", "social", "food", "drink")),
            new Venue("tea house", "cafe", Set.of("seating", "drink", "tea_service"), Set.of("cozy", "quiet", "tea", "drink")),
            new Venue("pavilion", "outdoors", Set.of("conversation", "shelter"), Set.of("social", "communal", "outdoors")),
            new Venue("well", "outdoors", Set.of("conversation", "water"), Set.of("quiet", "social", "outdoors")),
            new Venue("market", "outdoors", Set.of("conversation", "market"), Set.of("lively", "social", "outdoors")),
            new Venue("grove", "outdoors", Set.of("conversation", "shade"), Set.of("quiet", "social", "outdoors"))};

    final Random random;
    final List<Villager> roster = new ArrayList<>();
    final String[] biome;
    final Set<String> buildings = new LinkedHashSet<>();
    final List<Story> stories = new ArrayList<>();
    final List<Sighting> sightings = new ArrayList<>();
    private final Map<UUID, String> needs = new HashMap<>();
    /** Zombie infection progress, 0 to 1, as MCA keeps it. */
    final Map<UUID, Float> infection = new HashMap<>();
    final Set<UUID> turned = new HashSet<>();
    final List<Story> infectionNews = new ArrayList<>();
    private final Map<Long, Double> affection = new HashMap<>();
    private final Map<Long, List<ResourceLocation>> discussed = new HashMap<>();
    private final Map<Long, Integer> meetings = new HashMap<>();
    private final Map<Long, Integer> greeted = new HashMap<>();
    private final Map<ResourceLocation, Set<String>> used = new HashMap<>();
    int day;
    String weather = "clear";
    @Nullable Venue venue;
    boolean night;
    /** The culture every villager here belongs to, which picks their voice. Empty for the common voice. */
    String culture = "";

    SimVillage(Random random) {
        this.random = random;
        List<String> names = new ArrayList<>(List.of(NAMES));
        Collections.shuffle(names, random);
        for (String name : names.subList(0, 8 + random.nextInt(5)))
            roster.add(new Villager(UUID.randomUUID(), name, pick(PERSONALITIES), pick(STAGES)));
        biome = BIOMES[random.nextInt(BIOMES.length)];
        for (String building : BUILDINGS) if (random.nextInt(3) > 0) buildings.add(building);
        for (Villager a : roster) for (Villager b : roster)
            if (a.id().compareTo(b.id()) < 0) affection.put(pair(a, b), random.nextGaussian() * 15 + 5);
        startDay();
    }

    /** Moves to the next day: new weather and needs, maybe some news or a mob sighting. */
    void nextDay() {
        day++;
        startDay();
    }

    private void startDay() {
        boolean cold = biome[2].contains("snowy") || biome[2].contains("taiga");
        weather = cold ? pick("snow", "snow", "clear", "clear") : pick("clear", "clear", "clear", "rain", "rain", "thunder");
        needs.clear();
        for (Villager v : roster) if (random.nextInt(4) == 0) needs.put(v.id(), pick(NEEDS));
        stories.removeIf(s -> day - s.day() > 14);
        sightings.removeIf(s -> day - s.day() > 1);
        if (random.nextInt(3) == 0) {
            Villager who = pick(roster), other = pick(roster);
            String variant = pick(EVENTS);
            Set<UUID> known = new HashSet<>(Set.of(who.id()));
            if (!variant.equals("mastery") && other != who) known.add(other.id());
            stories.add(new Story(variant, who, variant.equals("mastery") || other == who ? null : other, day, known));
        }
        if (random.nextInt(4) == 0) sightings.add(new Sighting(pick(MOBS), pick(roster), day));
        advanceInfections();
        // News spreads a little overnight.
        for (Story story : stories) for (Villager v : roster) if (random.nextInt(5) == 0) story.knownBy().add(v.id());
        for (Story story : infectionNews) for (Villager v : roster) if (random.nextInt(4) == 0) story.knownBy().add(v.id());
    }

    /** A day of infection: a fever grows, some are cured, some turn, and a zombie sometimes bites someone new. */
    private void advanceInfections() {
        infectionNews.removeIf(s -> day - s.day() > 7);
        for (Villager v : roster) {
            Float progress = infection.get(v.id());
            if (progress == null) continue;
            if (progress >= 0.2f && random.nextInt(3) == 0) {
                infection.remove(v.id());
                infectionNews.add(new Story("cured", v, null, day, new HashSet<>(Set.of(v.id()))));
            } else if (progress + 0.2f > 1f) {
                infection.remove(v.id());
                turned.add(v.id());
                infectionNews.add(new Story("turned", v, null, day, new HashSet<>()));
            } else {
                infection.put(v.id(), progress + 0.2f);
            }
        }
        boolean zombie = sightings.stream().anyMatch(s -> s.day() == day && s.mob().contains("zombie") || s.mob().equals("husk") || s.mob().equals("drowned"));
        if (random.nextInt(zombie ? 2 : 6) == 0) {
            bite(pick(roster));
        }
    }

    /** A zombie bites this villager, unless they already carry the infection or have turned. */
    void bite(Villager bitten) {
        if (turned.contains(bitten.id()) || infection.containsKey(bitten.id())) return;
        infection.put(bitten.id(), 0.05f);
        infectionNews.add(new Story("bitten", bitten, null, day, new HashSet<>(Set.of(bitten.id()))));
    }

    float infection(Villager v) { return infection.getOrDefault(v.id(), 0f); }

    /** Records the talk: the listener now knows what was told, and the pair remembers the topics. */
    void talked(Villager a, Villager b, List<LineComposer.Subject> told, List<ResourceLocation> topics) {
        long key = pair(a, b);
        meetings.merge(key, 1, Integer::sum);
        greeted.put(key, day);
        discussed.computeIfAbsent(key, k -> new ArrayList<>()).addAll(topics);
        for (LineComposer.Subject subject : told) if (subject.source() instanceof Story story) {
            story.knownBy().add(a.id());
            story.knownBy().add(b.id());
        }
        affection.merge(key, 1.0, Double::sum);
    }

    /** The subjects this speaker could raise with this listener, for one subject kind. */
    List<LineComposer.Subject> subjects(ResourceLocation kind, Villager speaker, Villager listener) {
        List<LineComposer.Subject> out = new ArrayList<>();
        switch (kind.getPath()) {
            case "weather" -> out.add(new LineComposer.Subject(kind, weather, "neutral", Map.of(), Set.of(), null));
            case "biome" -> out.add(new LineComposer.Subject(kind, "", "neutral", Map.of("biome",
                    new LineComposer.SlotValue(biome[0], null, null, Map.of("number", biome[1]))), Set.of(), null));
            case "joke", "venue" -> out.add(new LineComposer.Subject(kind, "", "neutral", Map.of(), Set.of(), null));
            case "need" -> {
                String need = needs.get(speaker.id());
                if (need != null) out.add(new LineComposer.Subject(kind, need, "negative", Map.of(), Set.of(), null,
                        need.equals("cold") || need.equals("hot") ? 0.8 : 0.4 + random.nextDouble() * 0.4));
            }
            case "company" -> {
                double value = affection(speaker, listener);
                if (value >= 10) out.add(new LineComposer.Subject(kind, value >= 30 ? "close" : "friend", "positive",
                        Map.of(), Set.of(), null, 0.5));
            }
            case "mob" -> {
                for (Sighting s : sightings) {
                    if (s.witness() == listener) continue;
                    String article = "aeiou".indexOf(s.mob().charAt(0)) >= 0 ? "an " : "a ";
                    out.add(new LineComposer.Subject(kind, "", "negative", Map.of(
                            "mob", new LineComposer.SlotValue(s.mob(), null, null, Map.of()),
                            "a_mob", new LineComposer.SlotValue(article + s.mob(), null, null, Map.of()),
                            "who", person(s.witness())),
                            Set.of(s.witness() == speaker ? "witnessed" : "heard"), s, day == s.day() ? 0.9 : 0.5));
                }
            }
            case "infection" -> {
                float own = infection(speaker), theirs = infection(listener);
                boolean hides = Set.of("crabby", "grumpy", "greedy", "shy", "introverted", "confident").contains(speaker.personality());
                if (own > 0 && own <= 0.6f && (own >= 0.2f || !hides))
                    out.add(new LineComposer.Subject(kind, own >= 0.2f ? "fever" : "bitten", "negative",
                            Map.of("who", person(speaker)), Set.of("witnessed"), null, own >= 0.2f ? 0.8 : 0.6));
                if (theirs >= 0.2f && theirs <= 0.6f)
                    out.add(new LineComposer.Subject(kind, "fever", "negative", Map.of("who", person(listener)),
                            Set.of("witnessed"), null, 0.9));
            }
            case "infection_news" -> {
                for (Story story : infectionNews) {
                    if (!story.knownBy().contains(speaker.id())) continue;
                    if (story.variant().equals("bitten") && infection(story.who()) <= 0) continue;
                    boolean shared = story.knownBy().contains(listener.id()) || story.who() == listener;
                    Set<String> provenance = new HashSet<>(Set.of(story.who() == speaker ? "witnessed" : "heard"));
                    if (shared) provenance.add("shared");
                    out.add(new LineComposer.Subject(kind, story.variant(), story.variant().equals("cured") ? "positive" : "negative",
                            Map.of("who", person(story.who())), provenance, story, shared ? -0.3 : day - story.day() <= 1 ? 0.8 : 0));
                }
            }
            case "event" -> {
                for (Story story : stories) {
                    if (!story.knownBy().contains(speaker.id())) continue;
                    boolean involvesListener = story.who() == listener || story.other2() == listener;
                    boolean involvesSpeaker = story.who() == speaker || story.other2() == speaker;
                    if (involvesListener && involvesSpeaker) continue;
                    Map<String, LineComposer.SlotValue> slots = new LinkedHashMap<>();
                    slots.put("who", person(story.who()));
                    if (story.other2() != null) slots.put("other2", person(story.other2()));
                    Set<String> provenance = new HashSet<>();
                    provenance.add(involvesSpeaker || random.nextInt(3) == 0 ? "witnessed" : "heard");
                    boolean shared = story.knownBy().contains(listener.id());
                    if (shared) provenance.add("shared");
                    double urgency = shared ? -0.3 : day - story.day() <= 1 ? 0.8 : 0;
                    out.add(new LineComposer.Subject(kind, story.variant(),
                            story.variant().equals("argument") ? "negative" : "positive", slots, provenance, story, urgency));
                }
            }
            default -> { }
        }
        return out;
    }

    /** Sets the time and, for a hangout, the place of the next conversation. Hangouts start in the afternoon, as MEET does. */
    void setting(boolean hangout) {
        venue = hangout ? VENUES[random.nextInt(VENUES.length)] : null;
        night = venue != null && !venue.kind().equals("outdoors") ? random.nextInt(3) == 0 : random.nextInt(5) == 0;
    }

    boolean requirement(String requirement) {
        if (requirement.startsWith("!")) return !requirement(requirement.substring(1));
        if (requirement.startsWith("time:")) return requirement.equals(night ? "time:night" : "time:day");
        if (requirement.startsWith("venue.amenity:")) return venue != null && venue.amenities().contains(requirement.substring(14));
        if (requirement.startsWith("venue.tag:")) {
            String tag = requirement.substring(requirement.lastIndexOf(':') + 1);
            return venue != null && venue.tags().contains(tag);
        }
        if (requirement.startsWith("here.decoration:townstead:"))
            return buildings.contains(requirement.substring("here.decoration:townstead:".length()));
        if (requirement.startsWith("here.building_tag:townstead:"))
            return buildings.contains(requirement.substring("here.building_tag:townstead:".length()));
        if (requirement.startsWith("here.biome_tag:")) return requirement.substring("here.biome_tag:".length()).equals(biome[2]);
        return false;
    }

    double affection(Villager a, Villager b) { return affection.getOrDefault(pair(a, b), 0.0); }
    int meetings(Villager a, Villager b) { return meetings.getOrDefault(pair(a, b), 0); }
    boolean greetedToday(Villager a, Villager b) { return greeted.getOrDefault(pair(a, b), -1) == day; }
    long repeats(Villager a, Villager b, ResourceLocation topic) {
        return discussed.getOrDefault(pair(a, b), List.of()).stream().filter(topic::equals).count();
    }
    @Nullable String need(Villager v) { return needs.get(v.id()); }

    /** Two different villagers, chosen at random. */
    Villager[] pair() {
        List<Villager> living = roster.stream().filter(v -> !turned.contains(v.id())).toList();
        Villager a = pick(living), b = pick(living);
        while (b == a) b = pick(living);
        return new Villager[]{a, b};
    }

    /** One line about the village for the top of a transcript. */
    String describe() {
        StringBuilder out = new StringBuilder((culture.isEmpty() ? "" : culture + " ") + "village in " + biome[0] + ", with " + String.join(", ", buildings) + "\n");
        out.append("people: ");
        roster.forEach(v -> out.append(v.name()).append(" (").append(v.personality()).append(", ").append(v.stage()).append(") "));
        return out.append('\n').toString();
    }

    /** What is true today, for the day headers of a transcript. */
    String today() {
        StringBuilder out = new StringBuilder("day " + day + ", " + weather);
        List<String> wants = new ArrayList<>();
        for (Villager v : roster) if (needs.containsKey(v.id())) wants.add(v.name() + " " + needs.get(v.id()));
        if (!wants.isEmpty()) out.append("; needs: ").append(String.join(", ", wants));
        for (Story s : stories) out.append("; news (day ").append(s.day()).append("): ").append(s.who().name()).append(' ')
                .append(s.variant()).append(s.other2() == null ? "" : " with " + s.other2().name())
                .append(", known by ").append(s.knownBy().size());
        for (Villager v : roster) if (infection.containsKey(v.id()))
            out.append("; ").append(v.name()).append(" infected ").append(String.format(Locale.ROOT, "%.2f", infection(v)));
        for (Story s : infectionNews) out.append("; infection news (day ").append(s.day()).append("): ").append(s.who().name())
                .append(' ').append(s.variant()).append(", known by ").append(s.knownBy().size());
        for (Sighting s : sightings) out.append("; ").append(s.witness().name()).append(" saw a ").append(s.mob())
                .append(s.day() == day ? " last night" : " two nights ago");
        return out.toString();
    }

    private LineComposer.SlotValue person(Villager v) { return new LineComposer.SlotValue(v.name(), null, v.id(), Map.of()); }
    private static long pair(Villager a, Villager b) {
        long x = a.id().getMostSignificantBits(), y = b.id().getMostSignificantBits();
        return x < y ? x * 31 + y : y * 31 + x;
    }
    @SafeVarargs private <T> T pick(T... options) { return options[random.nextInt(options.length)]; }
    private <T> T pick(List<T> options) { return options.get(random.nextInt(options.size())); }

    @Override public boolean used(ResourceLocation pool, String key) { return used.getOrDefault(pool, Set.of()).contains(key); }
    @Override public void use(ResourceLocation pool, String key) { used.computeIfAbsent(pool, k -> new HashSet<>()).add(key); }
    @Override public void release(ResourceLocation pool, Collection<String> keys) { used.getOrDefault(pool, new HashSet<>()).removeAll(keys); }
}
