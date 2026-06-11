package com.aetherianartificer.townstead.origin.port;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.GsonHelper;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * {@code /townstead port origins <name>} (op level 2): reads an Origins/Apoli source
 * dropped in {@code townstead/port-in/} (a mod jar, a content {@code .zip}, or a folder),
 * and writes a Townstead-shaped scaffold (species / ancestry / origin / gene JSON plus a
 * lang sidecar) to {@code townstead/port-out/<namespace>/} for the author to refine and
 * ship. It reads every namespace in the source at once (so cross-namespace power
 * references resolve); convertible passive powers become genes, everything else lands in
 * {@code port_report.txt} and as {@code _todo_professions} stubs. Nothing loads
 * automatically.
 */
public final class OriginsPortCommand {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    //? if >=1.21 {
    private static final int PACK_FORMAT = 48;
    //?} else {
    /*private static final int PACK_FORMAT = 15;
    *///?}

    private OriginsPortCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext ctx) {
        dispatcher.register(Commands.literal("townstead").then(Commands.literal("port").then(
                Commands.literal("origins")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("name", StringArgumentType.string())
                                .suggests(SUGGEST_SOURCES)
                                .executes(c -> port(c.getSource(), StringArgumentType.getString(c, "name")))))));
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_SOURCES = (c, b) -> {
        Path inDir = portIn(c.getSource().getServer());
        if (Files.isDirectory(inDir)) {
            try (Stream<Path> entries = Files.list(inDir)) {
                entries.map(p -> stripArchiveSuffix(p.getFileName().toString())).distinct().forEach(b::suggest);
            } catch (IOException ignored) {
                // suggestion failure shouldn't break completion
            }
        }
        return b.buildFuture();
    };

    private static int port(CommandSourceStack source, String name) {
        MinecraftServer server = source.getServer();
        Path inDir = portIn(server);
        Path source0 = resolveSource(inDir, name);
        if (source0 == null) {
            try {
                Files.createDirectories(inDir);
            } catch (IOException ignored) {
                // best effort
            }
            source.sendFailure(Component.literal("No source '" + name + "' in townstead/port-in. "
                    + "Drop an Origins jar/zip/folder there and tab-complete the name."));
            return 0;
        }

        Map<ResourceLocation, JsonObject> powers = new HashMap<>();
        Map<String, Map<ResourceLocation, JsonObject>> originsByNs = new LinkedHashMap<>();
        try {
            readSource(source0, powers, originsByNs);
        } catch (Exception e) {
            Townstead.LOGGER.error("Origins port: failed to read {}", source0, e);
            source.sendFailure(Component.literal("Couldn't read '" + name + "': " + e.getMessage()));
            return 0;
        }
        if (originsByNs.isEmpty()) {
            source.sendFailure(Component.literal("No origins found in '" + name
                    + "' (looked for data/<namespace>/origins/*.json)."));
            return 0;
        }

        Path outBase = portOut(server);
        int totalOrigins = 0;
        int totalGenes = 0;
        int totalSkips = 0;
        List<String> namespaces = new ArrayList<>();
        try {
            for (Map.Entry<String, Map<ResourceLocation, JsonObject>> ns : originsByNs.entrySet()) {
                int[] counts = portNamespace(ns.getKey(), ns.getValue(), powers, outBase.resolve(ns.getKey()));
                totalOrigins += counts[0];
                totalGenes += counts[1];
                totalSkips += counts[2];
                namespaces.add(ns.getKey());
            }
        } catch (Exception e) {
            Townstead.LOGGER.error("Origins port failed", e);
            source.sendFailure(Component.literal("Port failed: " + e.getMessage()));
            return 0;
        }

        int fOrigins = totalOrigins;
        int fGenes = totalGenes;
        int fSkips = totalSkips;
        source.sendSuccess(() -> Component.literal("Ported " + fOrigins + " origin(s) across "
                + namespaces.size() + " namespace(s) " + namespaces + ": " + fGenes + " genes, "
                + fSkips + " powers skipped to professions. Wrote townstead/port-out/. See port_report.txt; "
                + "refine before loading."), true);
        return 1;
    }

    /** Convert one namespace's origins into a datapack under {@code outRoot}; returns {origins, genes, skips}. */
    private static int[] portNamespace(String namespace, Map<ResourceLocation, JsonObject> origins,
                                       Map<ResourceLocation, JsonObject> powers, Path outRoot) throws Exception {
        // Clean regenerate: port-out is a disposable scaffold (copy it out to refine), so wipe any
        // prior run first rather than leaving orphaned files or clobbering them piecemeal.
        deleteTree(outRoot);
        Path dataNs = outRoot.resolve("data").resolve(namespace);
        Map<ResourceLocation, JsonObject> geneFiles = new LinkedHashMap<>();
        Map<ResourceLocation, JsonObject> recipes = new LinkedHashMap<>();
        Map<String, String> lang = new LinkedHashMap<>();
        List<String> report = new ArrayList<>();
        int originCount = 0;
        int skipCount = 0;

        for (Map.Entry<ResourceLocation, JsonObject> entry : origins.entrySet()) {
            ResourceLocation originId = entry.getKey();
            JsonObject originJson = entry.getValue();

            List<ResourceLocation> originGenes = new ArrayList<>();
            List<PowerToGeneConverter.Skip> originSkips = new ArrayList<>();
            for (JsonElement ref : GsonHelper.getAsJsonArray(originJson, "powers", new JsonArray())) {
                if (!ref.isJsonPrimitive()) continue;
                ResourceLocation powerId = ResourceLocation.tryParse(ref.getAsString());
                JsonObject power = powerId == null ? null : powers.get(powerId);
                if (power == null) {
                    originSkips.add(new PowerToGeneConverter.Skip(String.valueOf(ref.getAsString()),
                            "?", "power JSON not found in source"));
                    continue;
                }
                List<PowerToGeneConverter.ConvertedGene> converted = new ArrayList<>();
                PowerToGeneConverter.convert(namespace, powerId, power, converted, originSkips, recipes);
                for (PowerToGeneConverter.ConvertedGene gene : converted) {
                    if (geneFiles.containsKey(gene.id())) {
                        if (!originGenes.contains(gene.id())) originGenes.add(gene.id());
                        continue;
                    }
                    String key = "gene." + gene.id().getNamespace() + "." + gene.id().getPath().replace('/', '.');
                    gene.json().add("display_name", translateComponent(key));
                    lang.put(key, gene.displayText());
                    geneFiles.put(gene.id(), gene.json());
                    originGenes.add(gene.id());
                }
            }

            writeOrigin(dataNs, namespace, originId, originJson, originGenes, originSkips);
            writeAncestry(dataNs, namespace, originId, originJson);

            originCount++;
            skipCount += originSkips.size();
            report.add("Origin " + originId + ": " + originGenes.size() + " genes, "
                    + originSkips.size() + " skipped");
            for (PowerToGeneConverter.Skip skip : originSkips) {
                report.add("    skip " + skip.power() + " [" + skip.type() + "] - " + skip.reason());
            }
        }

        for (Map.Entry<ResourceLocation, JsonObject> gene : geneFiles.entrySet()) {
            writeJson(geneFile(dataNs, gene.getKey()), gene.getValue());
        }
        for (Map.Entry<ResourceLocation, JsonObject> recipe : recipes.entrySet()) {
            writeJson(dataNs.resolve("recipe").resolve(recipe.getKey().getPath() + ".json"), recipe.getValue());
        }
        writeSpecies(dataNs);
        writeJson(dataNs.resolve("lang").resolve("en_us.json"), langJson(lang));
        writePackMeta(outRoot, namespace);
        writeReport(outRoot, namespace, originCount, geneFiles.size(), skipCount, report);
        return new int[]{originCount, geneFiles.size(), skipCount};
    }

    // --- source discovery / reading -------------------------------------------------

    private static Path resolveSource(Path inDir, String name) {
        Path jar = inDir.resolve(name + ".jar");
        if (Files.isRegularFile(jar)) return jar;
        Path zip = inDir.resolve(name + ".zip");
        if (Files.isRegularFile(zip)) return zip;
        Path exact = inDir.resolve(name);
        if (Files.isRegularFile(exact) || Files.isDirectory(exact)) return exact;
        return null;
    }

    private static void readSource(Path source, Map<ResourceLocation, JsonObject> powers,
                                   Map<String, Map<ResourceLocation, JsonObject>> originsByNs) throws IOException {
        if (Files.isDirectory(source)) {
            readData(source, powers, originsByNs);
            return;
        }
        try (FileSystem fs = FileSystems.newFileSystem(source)) {
            for (Path root : fs.getRootDirectories()) {
                readData(root, powers, originsByNs);
            }
        }
    }

    private static void readData(Path root, Map<ResourceLocation, JsonObject> powers,
                                 Map<String, Map<ResourceLocation, JsonObject>> originsByNs) throws IOException {
        Path data = root.resolve("data");
        if (!Files.isDirectory(data)) return;
        try (Stream<Path> nsDirs = Files.list(data)) {
            for (Path nsDir : (Iterable<Path>) nsDirs::iterator) {
                if (!Files.isDirectory(nsDir)) continue;
                String ns = nsDir.getFileName().toString().replace("/", "");
                readDir(nsDir.resolve("origins"), ns,
                        (id, json) -> originsByNs.computeIfAbsent(ns, k -> new LinkedHashMap<>()).put(id, json));
                readDir(nsDir.resolve("powers"), ns, powers::put);
            }
        }
    }

    private static void readDir(Path dir, String namespace, BiConsumer<ResourceLocation, JsonObject> sink)
            throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
                    .forEach(p -> {
                        String rel = dir.relativize(p).toString().replace('\\', '/');
                        rel = rel.substring(0, rel.length() - ".json".length());
                        ResourceLocation id = ResourceLocation.tryParse(namespace + ":" + rel);
                        JsonObject json = readJson(p);
                        if (id != null && json != null) sink.accept(id, json);
                    });
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                    // best effort; a leftover file won't break the regenerate
                }
            });
        }
    }

    private static JsonObject readJson(Path path) {
        try (Reader reader = Files.newBufferedReader(path)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }

    // --- output writers (unchanged shape, output per namespace) ---------------------

    private static void writeOrigin(Path dataNs, String namespace, ResourceLocation originId,
                                    JsonObject apoli, List<ResourceLocation> genes,
                                    List<PowerToGeneConverter.Skip> skips) throws Exception {
        JsonObject out = new JsonObject();
        out.add("display_name", component(apoli.get("name"), originId.getPath()));
        if (apoli.has("description")) out.add("backstory", component(apoli.get("description"), ""));
        out.addProperty("species", namespace + ":imported");
        out.addProperty("ancestry", namespace + ":" + originId.getPath());
        JsonArray geneArray = new JsonArray();
        for (ResourceLocation gene : genes) geneArray.add(gene.toString());
        out.add("genes", geneArray);
        if (!skips.isEmpty()) {
            JsonArray todo = new JsonArray();
            for (PowerToGeneConverter.Skip skip : skips) {
                todo.add(skip.power() + " [" + skip.type() + "] - " + skip.reason());
            }
            out.add("_todo_professions", todo);
        }
        writeJson(dataNs.resolve("origin").resolve(originId.getPath() + ".json"), out);
    }

    private static void writeAncestry(Path dataNs, String namespace, ResourceLocation originId,
                                      JsonObject apoli) throws Exception {
        JsonObject out = new JsonObject();
        out.add("display_name", component(apoli.get("name"), originId.getPath()));
        out.addProperty("species", namespace + ":imported");
        writeJson(dataNs.resolve("ancestry").resolve(originId.getPath() + ".json"), out);
    }

    private static void writeSpecies(Path dataNs) throws Exception {
        JsonObject out = new JsonObject();
        out.add("display_name", literal("Imported"));
        out.addProperty("shape", "humanoid");
        out.addProperty("admixture_chance", 0.0);
        writeJson(dataNs.resolve("species").resolve("imported.json"), out);
    }

    private static Path geneFile(Path dataNs, ResourceLocation geneId) {
        return dataNs.resolve("gene").resolve(geneId.getPath() + ".json");
    }

    private static void writeJson(Path path, JsonElement json) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(json));
    }

    private static JsonObject langJson(Map<String, String> lang) {
        JsonObject out = new JsonObject();
        lang.forEach(out::addProperty);
        return out;
    }

    private static void writePackMeta(Path outRoot, String namespace) throws Exception {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description", "Townstead port of " + namespace + " origins (refine before use)");
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        writeJson(outRoot.resolve("pack.mcmeta"), root);
    }

    private static void writeReport(Path outRoot, String namespace, int origins, int genes, int skips,
                                    List<String> lines) throws Exception {
        StringBuilder sb = new StringBuilder();
        sb.append("Townstead Origins port report for namespace: ").append(namespace).append('\n');
        sb.append(origins).append(" origins, ").append(genes).append(" unique genes, ")
                .append(skips).append(" powers skipped.\n");
        sb.append("Skipped powers are class/active content deferred to the professions system,\n");
        sb.append("or powers/conditions outside the heritable subset. Each is stubbed as\n");
        sb.append("_todo_professions in its origin file.\n\n");
        for (String line : lines) sb.append(line).append('\n');
        Files.createDirectories(outRoot);
        Files.writeString(outRoot.resolve("port_report.txt"), sb.toString());
    }

    // --- paths / helpers ------------------------------------------------------------

    private static Path townsteadDir(MinecraftServer server) {
        //? if >=1.21 {
        return server.getServerDirectory().resolve("townstead");
        //?} else {
        /*return server.getServerDirectory().toPath().resolve("townstead");
        *///?}
    }

    private static Path portIn(MinecraftServer server) {
        return townsteadDir(server).resolve("port-in");
    }

    private static Path portOut(MinecraftServer server) {
        return townsteadDir(server).resolve("port-out");
    }

    private static String stripArchiveSuffix(String name) {
        if (name.endsWith(".jar")) return name.substring(0, name.length() - ".jar".length());
        if (name.endsWith(".zip")) return name.substring(0, name.length() - ".zip".length());
        return name;
    }

    /** Pass an Apoli text component through, or wrap a bare string / fall back to a literal. */
    private static JsonElement component(JsonElement source, String fallback) {
        if (source != null && source.isJsonObject()) return source;
        if (source != null && source.isJsonPrimitive()) return literal(source.getAsString());
        return literal(fallback);
    }

    private static JsonObject literal(String text) {
        JsonObject out = new JsonObject();
        out.add("text", new JsonPrimitive(text));
        return out;
    }

    private static JsonObject translateComponent(String key) {
        JsonObject out = new JsonObject();
        out.addProperty("translate", key);
        return out;
    }
}
