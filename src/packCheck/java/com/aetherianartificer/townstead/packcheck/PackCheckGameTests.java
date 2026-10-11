package com.aetherianartificer.townstead.packcheck;

import com.aetherianartificer.townstead.data.DataDocuments;
import com.aetherianartificer.townstead.data.DataNamespaces;
import com.aetherianartificer.townstead.pheno.lang.PhenoDiagnostics;
import com.aetherianartificer.townstead.pheno.lang.compile.Diagnostic;
import com.aetherianartificer.townstead.pheno.particle.DataParticles;
import com.aetherianartificer.townstead.root.AncestryRegistry;
import com.aetherianartificer.townstead.root.LineageRegistry;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.attachment.AttachmentServerData;
import com.aetherianartificer.townstead.root.gene.GeneRegistry;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Loads external data packs as this test mod's data (see the {@code packCheck} Gradle property) and
 * fails when any of their documents did not make it into Townstead: a gene, root, lineage, ancestry,
 * attachment or particle whose gate passed but which no registry holds, or a Pheno diagnostic error.
 * A full report, including warnings and every resolved root name, is written to
 * {@code pack-check-report.txt} in the run directory.
 */
@Mod("townstead_pack_check")
@GameTestHolder("townstead_pack_check")
@PrefixGameTestTemplate(false)
public final class PackCheckGameTests {

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void everyDocumentLoads(GameTestHelper h) {
        ResourceManager manager = h.getLevel().getServer().getResourceManager();
        Set<String> namespaces = new HashSet<>(List.of(
                System.getProperty("townstead.packCheck.namespaces", "").split(",")));
        namespaces.remove("");
        List<String> report = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        report.add("Namespaces checked: " + namespaces);
        report.add("Data namespaces present: " + DataNamespaces.all().size());

        check(manager, namespaces, "gene", id -> GeneRegistry.byId(id) != null, report, failures);
        check(manager, namespaces, "root", id -> RootRegistry.byId(id) != null, report, failures);
        check(manager, namespaces, "lineage", id -> LineageRegistry.byId(id) != null, report, failures);
        check(manager, namespaces, "ancestry", id -> AncestryRegistry.byId(id) != null, report, failures);
        check(manager, namespaces, "particle", DataParticles::has, report, failures);
        Set<String> attachments = new HashSet<>();
        AttachmentServerData.definitions().forEach(def -> attachments.add(def.id()));
        check(manager, namespaces, "attachment", id -> attachments.contains(id.toString()), report, failures);

        for (Diagnostic d : PhenoDiagnostics.all()) {
            if (!namespaces.contains(d.resource().getNamespace())) continue;
            String line = d.severity() + " " + d.render();
            report.add(line);
            if (d.severity().name().equals("ERROR")) failures.add(line);
        }
        for (var root : RootRegistry.all()) {
            if (namespaces.contains(root.id().getNamespace())) {
                report.add("root " + root.id() + " = " + root.displayName().getString()
                        + " (ancestry " + root.ancestry() + ", species " + RootRegistry.effectiveSpecies(root.id()) + ")");
            }
        }
        // The browser's view of each root, built exactly as the login sync builds it.
        try {
            for (var entry : com.aetherianartificer.townstead.root.RootCatalog.build().browse()) {
                if (!namespaces.contains(entry.pack())) continue;
                StringBuilder line = new StringBuilder("browse " + entry.rootId() + " pack=" + entry.packQualifier().fallback()
                        + " icon=" + entry.icon() + " traits=" + entry.traits() + " facts=");
                for (var fact : entry.facts()) line.append(fact.kind()).append(':').append(fact.value().fallback()).append(' ');
                line.append("powers=");
                for (var power : entry.powers()) line.append(power.kind()).append(' ');
                report.add(line.toString().trim());
            }
        } catch (RuntimeException ex) {
            failures.add("root catalog failed to build: " + ex);
        }
        try {
            Files.write(Path.of("pack-check-report.txt"), report, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // The assertion below still reports the failures.
        }
        h.assertTrue(failures.isEmpty(), failures.size() + " problems:\n" + String.join("\n", failures));
        h.succeed();
    }

    /** Every gate-passing JSON document of {@code kind} in {@code namespaces} must be registered. */
    private static void check(ResourceManager manager, Set<String> namespaces, String kind,
                              Predicate<ResourceLocation> registered, List<String> report, List<String> failures) {
        int expected = 0, loaded = 0;
        Map<ResourceLocation, Resource> files = manager.listResources(kind, loc -> loc.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> file : files.entrySet()) {
            ResourceLocation location = file.getKey();
            if (!namespaces.contains(location.getNamespace())) continue;
            String path = location.getPath().substring(kind.length() + 1, location.getPath().length() - ".json".length());
            // Attachment geometry and animations sit in sub-folders and are not definitions.
            if (kind.equals("attachment") && path.contains("/")) continue;
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(location.getNamespace(), path);
            JsonObject json;
            try (Reader reader = file.getValue().openAsReader()) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception ex) {
                failures.add(kind + " " + id + ": unreadable (" + ex.getMessage() + ")");
                continue;
            }
            if (DataDocuments.applyOverrides(json, id.toString()) == null) continue;
            expected++;
            if (registered.test(id)) loaded++;
            else failures.add(kind + " " + id + ": did not load");
        }
        report.add(kind + ": " + loaded + "/" + expected + " loaded");
    }
}
