package com.aetherianartificer.townstead.packcheck;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.block.haze.HazeKindsSyncPayload;
import com.aetherianartificer.townstead.client.catalog.CatalogSyncS2CPayload;
import com.aetherianartificer.townstead.culture.DestinyLabelsSyncPayload;
import com.aetherianartificer.townstead.dialogue.DialogueThemesSyncPayload;
import com.aetherianartificer.townstead.needs.ConsumableEffectsSyncPayload;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.particle.DataParticlesS2CPayload;
import com.aetherianartificer.townstead.profession.ProfessionClothingSyncPayload;
import com.aetherianartificer.townstead.rest.RestSpotsSyncS2CPayload;
import com.aetherianartificer.townstead.root.ExpressedGenesS2CPayload;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.PlayerRoot;
import com.aetherianartificer.townstead.root.RootCatalog;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.RootCatalogSyncPayload;
import com.aetherianartificer.townstead.root.attachment.AttachmentChunkS2CPayload;
import com.aetherianartificer.townstead.root.attachment.AttachmentDef;
import com.aetherianartificer.townstead.root.attachment.AttachmentManifestS2CPayload;
import com.aetherianartificer.townstead.root.attachment.AttachmentPointDef;
import com.aetherianartificer.townstead.root.attachment.AttachmentRequestC2SPayload;
import com.aetherianartificer.townstead.root.attachment.AttachmentServerData;
import com.aetherianartificer.townstead.root.gene.Allele;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Sends the login and reload sync payloads through their real wire codecs with the loaded packs, then
 * follows the client's attachment path using only decoded values: root, gene, attachment definition,
 * blobs and attachment point. Catches lost fields, codec exceptions, leftover bytes and packets over
 * the size limits, none of which show on a connection that skips encoding. The report is written to
 * {@code sync-wire-report.txt} in the run directory.
 */
@GameTestHolder("townstead_pack_check")
@PrefixGameTestTemplate(false)
public final class SyncWireGameTests {

    private static final int CLIENTBOUND_LIMIT = 1_048_576;
    private static final int SERVERBOUND_LIMIT = 32_767;
    private static final int MAX_DIFF_EXAMPLES = 3;

    /** Fields that differ across the wire by design. Anything else is lost data. */
    private static final Map<String, String> EXPECTED_DIFFS = Map.of(
            "haze kinds .kinds[].insideCondition", "runs on the server only",
            "haze kinds .kinds[].insideAction", "runs on the server only",
            "haze kinds .kinds[].insideInterval", "runs on the server only",
            "root catalog .entries[].animations.sources.keys", "write() spells out each state's default",
            "attachment manifest .defs[].physics[].notes.size", "authoring notes for the server's doctor command");

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void syncSurvivesTheWire(GameTestHelper h) {
        RegistryAccess registries = h.getLevel().registryAccess();
        List<String> report = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        ServerPlayer player = mockPlayer(h, report);

        Map<String, Supplier<Object>> payloads = new LinkedHashMap<>();
        payloads.put("root catalog", SyncWireGameTests::rootCatalog);
        payloads.put("attachment manifest", SyncWireGameTests::manifest);
        payloads.put("building catalog", CatalogSyncS2CPayload::snapshot);
        payloads.put("haze kinds", HazeKindsSyncPayload::snapshot);
        payloads.put("dialogue themes", DialogueThemesSyncPayload::snapshot);
        payloads.put("destiny labels", DestinyLabelsSyncPayload::snapshot);
        payloads.put("consumable effects", ConsumableEffectsSyncPayload::snapshot);
        payloads.put("rest spots", RestSpotsSyncS2CPayload::snapshot);
        payloads.put("data particles", DataParticlesS2CPayload::snapshot);
        payloads.put("profession clothing", ProfessionClothingSyncPayload::snapshot);
        if (player != null) {
            payloads.put("calendar", () -> Townstead.townstead$calendarSync(player));
        }

        report.add("== Login and reload payloads (bytes, Forge clientbound limit " + CLIENTBOUND_LIMIT + ")");
        Map<String, Object> decoded = new LinkedHashMap<>();
        for (Map.Entry<String, Supplier<Object>> entry : payloads.entrySet()) {
            String name = entry.getKey();
            try {
                Wire wire = roundTrip(entry.getValue().get(), registries);
                decoded.put(name, wire.decoded());
                report.add(String.format("%-22s %,10d", name, wire.size()));
                if (wire.size() > CLIENTBOUND_LIMIT) {
                    failures.add(name + ": " + wire.size() + " bytes is over the Forge clientbound limit");
                }
                if (wire.leftover() != 0) failures.add(name + ": read left " + wire.leftover() + " bytes unread");
                Diffs diffs = new Diffs();
                compare(entry.getValue().get(), wire.decoded(), "", diffs);
                for (String group : diffs.byPath.keySet()) {
                    String line = name + " " + group;
                    if (EXPECTED_DIFFS.containsKey(line)) report.add("expected: " + line + " (" + EXPECTED_DIFFS.get(line) + ")");
                    else failures.add(line + " lost data (" + diffs.counts.get(group) + " times), e.g. " + diffs.byPath.get(group).get(0));
                }
            } catch (Throwable ex) {
                failures.add(name + ": " + describe(ex));
            }
        }

        RootCatalogSyncPayload catalog = (RootCatalogSyncPayload) decoded.get("root catalog");
        AttachmentManifestS2CPayload manifest = (AttachmentManifestS2CPayload) decoded.get("attachment manifest");
        if (catalog != null && manifest != null) {
            Set<String> hashes = checkBlobs(manifest, registries, report, failures);
            checkRequest(hashes, registries, report, failures);
            checkRootChain(catalog, manifest, report, failures);
            checkTheCheckCanFail(catalog, manifest, failures);
            if (player != null) checkExpressed(player, catalog, manifest, registries, report, failures);
        } else {
            failures.add("attachment chain not checked: root catalog or manifest failed to decode");
        }

        report.add("");
        report.add("== Failures: " + failures.size());
        report.addAll(failures);
        try {
            Files.write(Path.of("sync-wire-report.txt"), report, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // The assertion below still reports the failures.
        }
        h.assertTrue(failures.isEmpty(), failures.size() + " problems, see sync-wire-report.txt:\n"
                + String.join("\n", failures));
        h.succeed();
    }

    private static Object rootCatalog() {
        RootCatalog.Snapshot snap = RootCatalog.build();
        return new RootCatalogSyncPayload(snap.origins(), snap.genes(), snap.traits(), snap.rigs(),
                snap.entityGroups(), snap.looks(), snap.browse());
    }

    private static Object manifest() {
        return new AttachmentManifestS2CPayload(AttachmentServerData.definitions(), AttachmentServerData.slots(),
                AttachmentServerData.namedTextures(), AttachmentServerData.namedGeo(),
                AttachmentServerData.namedAnimations());
    }

    private static ServerPlayer mockPlayer(GameTestHelper h, List<String> report) {
        try {
            return FakePlayerFactory.getMinecraft(h.getLevel());
        } catch (Throwable ex) {
            report.add("No mock player, player payloads skipped: " + describe(ex));
            return null;
        }
    }

    // ── Blobs ──

    /** Every blob the client would ask for must exist, survive chunking and match its hash. */
    private static Set<String> checkBlobs(AttachmentManifestS2CPayload manifest, RegistryAccess registries,
                                          List<String> report, List<String> failures) {
        Map<String, String> needed = neededHashes(manifest);
        long totalBytes = 0;
        int largestChunk = 0;
        for (Map.Entry<String, String> entry : needed.entrySet()) {
            String sha1 = entry.getKey();
            AttachmentServerData.Blob blob = AttachmentServerData.blob(sha1);
            if (blob == null) {
                failures.add("no server blob for " + sha1 + ", needed by " + entry.getValue());
                continue;
            }
            totalBytes += blob.bytes().length;
            try {
                largestChunk = Math.max(largestChunk, checkChunks(sha1, blob, registries, failures, entry.getValue()));
                checkContent(blob, entry.getValue(), failures);
            } catch (Throwable ex) {
                failures.add("blob " + entry.getValue() + ": " + describe(ex));
            }
        }
        report.add("");
        report.add("== Blobs");
        report.add("needed by the manifest: " + needed.size());
        report.add(String.format("total bytes: %,d", totalBytes));
        report.add(String.format("largest chunk packet: %,d", largestChunk));
        if (largestChunk > CLIENTBOUND_LIMIT) failures.add("chunk packet of " + largestChunk + " bytes is over the limit");
        return needed.keySet();
    }

    /** Mirrors {@code AttachmentClient.onManifest}, keeping who needs each hash for the report. */
    private static Map<String, String> neededHashes(AttachmentManifestS2CPayload manifest) {
        Map<String, String> needed = new LinkedHashMap<>();
        for (AttachmentDef def : manifest.defs()) {
            needed.putIfAbsent(def.geoSha1(), def.id() + " geometry");
            needed.putIfAbsent(def.textureSha1(), def.id() + " texture");
            for (AttachmentDef.StageOverride stage : def.stages().values()) {
                if (stage.geoSha1() != null) needed.putIfAbsent(stage.geoSha1(), def.id() + " stage geometry");
            }
            for (AttachmentDef.AnimationEntry animation : def.animations()) {
                needed.putIfAbsent(animation.animSha(), def.id() + " animation");
            }
            if (!def.emissiveSha1().isEmpty()) needed.putIfAbsent(def.emissiveSha1(), def.id() + " emissive");
            if (!def.tintMaskSha1().isEmpty()) needed.putIfAbsent(def.tintMaskSha1(), def.id() + " tint mask");
        }
        manifest.namedTextures().forEach((id, sha1) -> needed.putIfAbsent(sha1, id));
        manifest.namedGeo().forEach((id, sha1) -> needed.putIfAbsent(sha1, id));
        manifest.namedAnimations().forEach((id, sha1) -> needed.putIfAbsent(sha1, id));
        return needed;
    }

    /** Mirrors {@code AttachmentSync.handleRequest} and {@code AttachmentClient.onChunk}. */
    private static int checkChunks(String sha1, AttachmentServerData.Blob blob, RegistryAccess registries,
                                   List<String> failures, String owner) throws Exception {
        byte[] bytes = blob.bytes();
        int chunkSize = AttachmentChunkS2CPayload.CHUNK_SIZE;
        int total = Math.max(1, (bytes.length + chunkSize - 1) / chunkSize);
        byte[] assembled = new byte[bytes.length];
        int position = 0;
        int largest = 0;
        for (int i = 0; i < total; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, bytes.length);
            Wire wire = roundTrip(new AttachmentChunkS2CPayload(sha1, i, total, blob.kind(),
                    Arrays.copyOfRange(bytes, start, end)), registries);
            largest = Math.max(largest, wire.size());
            byte[] data = ((AttachmentChunkS2CPayload) wire.decoded()).data();
            System.arraycopy(data, 0, assembled, position, data.length);
            position += data.length;
        }
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(assembled));
        if (!actual.equals(sha1)) failures.add("blob " + owner + ": reassembled hash " + actual + " is not " + sha1);
        return largest;
    }

    private static void checkContent(AttachmentServerData.Blob blob, String owner, List<String> failures) {
        byte[] bytes = blob.bytes();
        if (blob.kind() == AttachmentServerData.KIND_TEXTURE) {
            boolean png = bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
            if (!png) failures.add("blob " + owner + ": texture is not a PNG");
        } else {
            JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void checkRequest(Set<String> hashes, RegistryAccess registries,
                                     List<String> report, List<String> failures) {
        try {
            List<String> all = new ArrayList<>(hashes);
            int unbatched = roundTrip(new AttachmentRequestC2SPayload(all), registries).size();
            int largestBatch = 0;
            for (int start = 0; start < all.size(); start += AttachmentRequestC2SPayload.MAX_HASHES) {
                int end = Math.min(start + AttachmentRequestC2SPayload.MAX_HASHES, all.size());
                largestBatch = Math.max(largestBatch,
                        roundTrip(new AttachmentRequestC2SPayload(new ArrayList<>(all.subList(start, end))), registries).size());
            }
            report.add(String.format("cold-cache request, one packet: %,d (Forge serverbound limit %,d)", unbatched, SERVERBOUND_LIMIT));
            report.add(String.format("cold-cache request, largest batch: %,d", largestBatch));
            if (largestBatch > SERVERBOUND_LIMIT) failures.add("request batch of " + largestBatch + " bytes is over the serverbound limit");
        } catch (Throwable ex) {
            failures.add("attachment request: " + describe(ex));
        }
    }

    // ── The client's attachment path ──

    /** Every attachment each root's genes can wear must resolve from decoded data alone. */
    private static void checkRootChain(RootCatalogSyncPayload catalog, AttachmentManifestS2CPayload manifest,
                                       List<String> report, List<String> failures) {
        Map<String, GeneCatalogEntry> genes = new LinkedHashMap<>();
        for (GeneCatalogEntry gene : catalog.genes()) genes.put(gene.id(), gene);
        Map<String, AttachmentDef> defs = new LinkedHashMap<>();
        for (AttachmentDef def : manifest.defs()) defs.put(def.id(), def);

        int roots = 0, wearing = 0, links = 0;
        for (RootCatalogEntry root : catalog.entries()) {
            roots++;
            int found = 0;
            for (RootCatalogEntry.Inherited inherited : root.inheritedGenes()) {
                GeneCatalogEntry gene = genes.get(inherited.geneId());
                if (gene == null) {
                    failures.add("root " + root.id() + ": gene " + inherited.geneId() + " is not in the catalog");
                    continue;
                }
                if (!gene.grantsAttachment()) continue;
                Set<String> carried = new LinkedHashSet<>();
                carried.add("");
                for (GeneCatalogEntry.Variant variant : gene.variants()) carried.add(variant.id());
                for (String variant : carried) {
                    for (String attachmentId : gene.attachmentsFor(variant)) {
                        links++;
                        found++;
                        AttachmentDef def = defs.get(attachmentId);
                        if (def == null) {
                            failures.add("root " + root.id() + ": gene " + gene.id() + " wears " + attachmentId + ", not in the manifest");
                            continue;
                        }
                        checkDef(def, root.rigBase(), manifest, failures);
                    }
                }
            }
            if (found > 0) wearing++;
        }
        report.add("");
        report.add("== Attachment path from decoded data");
        report.add("roots: " + roots + ", roots wearing attachments: " + wearing + ", gene to attachment links: " + links);
    }

    /** The attachment path must report a definition missing from the manifest. */
    private static void checkTheCheckCanFail(RootCatalogSyncPayload catalog, AttachmentManifestS2CPayload manifest,
                                             List<String> failures) {
        Set<String> linked = new LinkedHashSet<>();
        for (GeneCatalogEntry gene : catalog.genes()) {
            if (gene.grantsAttachment()) linked.addAll(gene.attachmentsFor(""));
        }
        List<AttachmentDef> fewer = new ArrayList<>(manifest.defs());
        if (!fewer.removeIf(def -> linked.contains(def.id()))) return;
        List<String> found = new ArrayList<>();
        checkRootChain(catalog, new AttachmentManifestS2CPayload(fewer, manifest.slots(), manifest.namedTextures(),
                manifest.namedGeo(), manifest.namedAnimations()), new ArrayList<>(), found);
        if (found.isEmpty()) failures.add("self-check: the attachment path did not notice missing definitions");
    }

    /** Mirrors the gates in {@code AttachmentRenderLayer}: blobs, condition and anchor points. */
    private static void checkDef(AttachmentDef def, String rig, AttachmentManifestS2CPayload manifest, List<String> failures) {
        if (AttachmentServerData.blob(def.geoSha1()) == null) failures.add(def.id() + ": no geometry blob");
        if (AttachmentServerData.blob(def.textureSha1()) == null) failures.add(def.id() + ": no texture blob");
        if (!def.whenJson().isEmpty()) {
            try {
                if (Conditions.parse(JsonParser.parseString(def.whenJson())) == null) {
                    failures.add(def.id() + ": condition does not parse: " + def.whenJson());
                }
            } catch (RuntimeException ex) {
                failures.add(def.id() + ": condition throws: " + describe(ex));
            }
        }
        if (def.targetTag() != null && !hasPoint(def.targetTag(), rig, manifest.slots())) {
            failures.add(def.id() + ": no attachment point for tag " + def.targetTag() + " on rig '" + rig + "'");
        }
        if (def.targetTag() == null && def.targetPoint() != null
                && manifest.slots().stream().noneMatch(point -> point.id().equals(def.targetPoint()))) {
            failures.add(def.id() + ": no attachment point " + def.targetPoint());
        }
    }

    private static boolean hasPoint(String tag, String rig, List<AttachmentPointDef> points) {
        for (AttachmentPointDef point : points) {
            if (point.tags().contains(tag) && (point.rig().isEmpty() || point.rig().equals(rig))) return true;
        }
        return false;
    }

    /** A player on each root must receive expressed genes that lead to the root's attachments. */
    private static void checkExpressed(ServerPlayer player, RootCatalogSyncPayload catalog,
                                       AttachmentManifestS2CPayload manifest, RegistryAccess registries,
                                       List<String> report, List<String> failures) {
        Map<String, GeneCatalogEntry> genes = new LinkedHashMap<>();
        for (GeneCatalogEntry gene : catalog.genes()) genes.put(gene.id(), gene);
        Set<String> defIds = new LinkedHashSet<>();
        for (AttachmentDef def : manifest.defs()) defIds.add(def.id());

        int checked = 0, wearing = 0, largest = 0;
        for (RootCatalogEntry root : catalog.entries()) {
            boolean rootWears = root.inheritedGenes().stream().anyMatch(inherited -> {
                GeneCatalogEntry gene = genes.get(inherited.geneId());
                return gene != null && gene.grantsAttachment();
            });
            try {
                PlayerRoot.setRootId(player, root.id());
                PlayerRoot.getOrSeedGenotype(player, player.getRandom());
                Wire wire = roundTrip(ExpressedGenesS2CPayload.forEntity(player.getId(), player), registries);
                largest = Math.max(largest, wire.size());
                checked++;
                int worn = 0;
                for (String encoded : ((ExpressedGenesS2CPayload) wire.decoded()).genes()) {
                    Allele allele = Allele.decode(encoded);
                    if (allele.isWild()) continue;
                    GeneCatalogEntry gene = genes.get(allele.geneId().toString());
                    if (gene == null) {
                        failures.add("root " + root.id() + ": expressed gene " + allele.geneId() + " is not in the catalog");
                        continue;
                    }
                    if (!gene.grantsAttachment()) continue;
                    for (String attachmentId : gene.attachmentsFor(allele.variantId() == null ? "" : allele.variantId())) {
                        if (defIds.contains(attachmentId)) worn++;
                        else failures.add("root " + root.id() + ": expressed " + attachmentId + " is not in the manifest");
                    }
                }
                if (worn > 0) wearing++;
                else if (rootWears) report.add("note: " + root.id() + " has attachment genes, none expressed on this roll");
            } catch (Throwable ex) {
                failures.add("expressed genes for " + root.id() + ": " + describe(ex));
            }
        }
        report.add("");
        report.add("== Expressed genes on a fake player");
        report.add("roots checked: " + checked + ", expressing attachments: " + wearing + ", largest packet: " + largest);
    }

    // ── Wire round trip ──

    private record Wire(Object decoded, int size, int leftover) {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Wire roundTrip(Object payload, RegistryAccess registries) throws Exception {
        Field field = payload.getClass().getField("STREAM_CODEC");
        StreamCodec codec = (StreamCodec) field.get(null);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            codec.encode(buf, payload);
            int size = buf.readableBytes();
            Object decoded = codec.decode(buf);
            return new Wire(decoded, size, buf.readableBytes());
        } finally {
            buf.release();
        }
    }

    // ── Deep comparison ──

    /** Mismatches grouped by field path, indexes removed, so one lost field is one line. */
    private static final class Diffs {
        final Map<String, List<String>> byPath = new LinkedHashMap<>();
        final Map<String, Integer> counts = new LinkedHashMap<>();

        void add(String path, Object sent, Object received) {
            String group = path.replaceAll("\\[[^\\]]*\\]", "[]");
            counts.merge(group, 1, Integer::sum);
            List<String> examples = byPath.computeIfAbsent(group, k -> new ArrayList<>());
            if (examples.size() < MAX_DIFF_EXAMPLES) {
                examples.add(path + ": sent <" + brief(sent) + "> received <" + brief(received) + ">");
            }
        }

        private static String brief(Object value) {
            String text = String.valueOf(value);
            return text.length() > 80 ? text.substring(0, 80) + "..." : text;
        }
    }

    private static void compare(Object sent, Object received, String path, Diffs diffs) throws Exception {
        if (sent == received) return;
        if (sent == null || received == null) {
            diffs.add(path, sent, received);
            return;
        }
        if (sent instanceof Float a && received instanceof Float b) {
            if (Float.compare(a, b) != 0) diffs.add(path, sent, received);
        } else if (sent.getClass().isArray() && received.getClass().isArray()) {
            int length = Array.getLength(sent);
            if (length != Array.getLength(received)) {
                diffs.add(path + ".length", length, Array.getLength(received));
                return;
            }
            for (int i = 0; i < length; i++) compare(Array.get(sent, i), Array.get(received, i), path + "[" + i + "]", diffs);
        } else if (sent instanceof List<?> a && received instanceof List<?> b) {
            if (a.size() != b.size()) {
                diffs.add(path + ".size", a.size(), b.size());
                return;
            }
            for (int i = 0; i < a.size(); i++) compare(a.get(i), b.get(i), path + "[" + i + "]", diffs);
        } else if (sent instanceof Set<?> a && received instanceof Set<?> b) {
            if (!a.equals(b)) diffs.add(path, a, b);
        } else if (sent instanceof Map<?, ?> a && received instanceof Map<?, ?> b) {
            if (!a.keySet().equals(b.keySet())) {
                diffs.add(path + ".keys", a.keySet(), b.keySet());
                return;
            }
            for (Map.Entry<?, ?> entry : a.entrySet()) {
                compare(entry.getValue(), b.get(entry.getKey()), path + "[" + entry.getKey() + "]", diffs);
            }
        } else if (sent instanceof Component a && received instanceof Component b) {
            if (!a.getString().equals(b.getString())) diffs.add(path, a.getString(), b.getString());
        } else if (sent.getClass().isRecord() && sent.getClass() == received.getClass()) {
            for (RecordComponent component : sent.getClass().getRecordComponents()) {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                compare(accessor.invoke(sent), accessor.invoke(received), path + "." + component.getName(), diffs);
            }
        } else if (isOurPlainClass(sent) && sent.getClass() == received.getClass()) {
            for (Field field : sent.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                field.setAccessible(true);
                compare(field.get(sent), field.get(received), path + "." + field.getName(), diffs);
            }
        } else if (!Objects.equals(sent, received)) {
            diffs.add(path, sent, received);
        }
    }

    /** A Townstead class that keeps Object's identity equals, which says nothing about its contents. */
    private static boolean isOurPlainClass(Object value) throws Exception {
        Class<?> type = value.getClass();
        return !type.isEnum() && type.getName().startsWith("com.aetherianartificer.townstead.")
                && type.getMethod("equals", Object.class).getDeclaringClass() == Object.class;
    }

    private static String describe(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        StackTraceElement[] stack = root.getStackTrace();
        return root + (stack.length > 0 ? " at " + stack[0] : "");
    }
}
