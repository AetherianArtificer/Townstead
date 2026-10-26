package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.aetherianartificer.townstead.dialogue.conversation.ConversationTopic;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Loads the conversation data Townstead ships, the way the reload listener reads it. */
final class ShippedDialogue {
    private ShippedDialogue() {}

    static Path resources() throws Exception {
        return Path.of(Objects.requireNonNull(ShippedDialogue.class.getClassLoader().getResource("data/townstead")).toURI())
                .getParent().getParent();
    }

    /** Pack roots from {@code TOWNSTEAD_DIALOGUE_PACKS} (separated by {@code ;}), loaded after the shipped data. */
    static List<Path> roots() throws Exception {
        List<Path> roots = new ArrayList<>(List.of(resources()));
        String extra = System.getenv("TOWNSTEAD_DIALOGUE_PACKS");
        if (extra != null) for (String raw : extra.split(";")) if (!raw.isBlank()) roots.add(Path.of(raw.trim()));
        return roots;
    }

    static GenerativeDialogue.Data data() throws Exception {
        Map<String, Map<ResourceLocation, JsonObject>> docs = new LinkedHashMap<>();
        for (String family : GenerativeDialogue.FAMILIES) {
            Map<ResourceLocation, JsonObject> documents = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
            for (Path pack : roots()) read(pack.resolve("data"), family, documents);
            docs.put(family, documents);
        }
        return GenerativeDialogue.build(docs);
    }

    private static void read(Path data, String family, Map<ResourceLocation, JsonObject> documents) throws Exception {
        if (!Files.isDirectory(data)) return;
        try (var namespaces = Files.list(data)) {
            for (Path namespace : namespaces.toList()) {
                Path root = namespace.resolve(family);
                if (!Files.isDirectory(root)) continue;
                try (var files = Files.walk(root)) {
                    for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                        String rel = root.relativize(file).toString().replace('\\', '/');
                        ResourceLocation id = ResourceLocation.tryParse(namespace.getFileName() + ":" + rel.substring(0, rel.length() - 5));
                        documents.put(id, JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                    }
                }
            }
        }
    }

    /** The generative topics in the townstead namespace. Set-piece topics need Pheno registries and are tested elsewhere. */
    static Map<ResourceLocation, ConversationTopic> topics() throws Exception {
        Map<ResourceLocation, ConversationTopic> out = new LinkedHashMap<>();
        Path dir = resources().resolve("data/townstead/conversation");
        try (var files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                ResourceLocation id = ResourceLocation.tryParse("townstead:" + name);
                out.put(id, ConversationTopic.parse(id, JsonParser.parseString(Files.readString(file)).getAsJsonObject()));
            }
        }
        return out;
    }
}
