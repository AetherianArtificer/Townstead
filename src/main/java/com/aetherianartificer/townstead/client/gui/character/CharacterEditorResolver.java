package com.aetherianartificer.townstead.client.gui.character;

import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.root.CharacterEditorLayout;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves a species' authored {@link CharacterEditorLayout} into the concrete tab list the editor
 * renders. Pure, client-side (it reads the synced gene catalog); no Minecraft widgets here.
 *
 * <p>{@code null} return means the species declared no layout → keep MCA's full native Character tab
 * unchanged. Otherwise: when explicit {@code tabs} were authored they're used verbatim; else the tabs
 * are auto-derived — the kept MCA-native groups first (each its own delegated tab, canonical order),
 * then the species' visually-editable genes bucketed into tabs by their {@code category}. Only VARIANTS
 * genes with options surface (so wings/tail authored as variant genes become cyclers, while non-visual
 * genes like immunities stay out).</p>
 */
public final class CharacterEditorResolver {
    private CharacterEditorResolver() {}

    /**
     * Synthetic page id for the always-on Height/Width scale tab we render ourselves. MCA's
     * size/width sliders live on its native {@code body} subpage; a species that hides that group
     * ({@code "native": []}) would lose them, so this tab surfaces them independently.
     */
    public static final String SIZE_PAGE = "townstead_char:__size__";

    /** Canonical left-to-right order for kept native groups. */
    private static final String[] NATIVE_ORDER = {
            CharacterEditorLayout.NATIVE_BODY, CharacterEditorLayout.NATIVE_CLOTHES,
            CharacterEditorLayout.NATIVE_HAIR, CharacterEditorLayout.NATIVE_EYES
    };

    /**
     * A resolved field within a tab: a delegated MCA-native group, a plain variant cycler, a TONE
     * field (a palette {@code skin_tone} gene → hue cycler + the draggable melanin×hemoglobin swatch),
     * or a SLIDER (a sized attachment gene → its size roll over the gene's range).
     */
    public record Field(Kind kind, String nativeGroup, GeneCatalogEntry gene) {
        public enum Kind { NATIVE, CYCLER, TONE, SLIDER, SCALE }
        static Field nativeGroup(String g) { return new Field(Kind.NATIVE, g, null); }
        /** The Height/Width scale field: no gene, drives MCA's SIZE/WIDTH float genes directly. */
        static Field scale() { return new Field(Kind.SCALE, null, null); }
        static Field gene(GeneCatalogEntry g) {
            // A variant gene is a cycler even when its options carry size channels (the
            // builder appends the channel sliders under it); a channel-only gene is sliders.
            if (!g.isVariants() && !g.channels().isEmpty()) return new Field(Kind.SLIDER, null, g);
            // A palette skin-tone gene (tinted variants, no face slot) gets the draggable swatch;
            // face/other variant genes are plain cyclers.
            boolean tone = g.faceSlot().isEmpty() && hasTintedVariant(g);
            return new Field(tone ? Kind.TONE : Kind.CYCLER, null, g);
        }
    }

    private static boolean hasTintedVariant(GeneCatalogEntry g) {
        for (GeneCatalogEntry.Variant v : g.variants()) if (v.tint() >= 0) return true;
        return false;
    }

    /**
     * A resolved tab. {@code pageId} is what we pass to MCA's {@code setPage}: an MCA subpage id for a
     * native tab (so MCA renders its content), or a {@code townstead_char:*} id for a gene tab (which
     * MCA ignores, leaving us to render the cyclers).
     */
    public record Tab(String pageId, Component label, List<Field> fields) {}

    public record Resolved(List<Tab> tabs) {
        public Tab byPage(String pageId) {
            for (Tab t : tabs) if (t.pageId().equals(pageId)) return t;
            return null;
        }

        /**
         * This layout with one MCA-native group removed: a species or rig that renders no hair
         * has nothing for MCA's hair page to edit, so the Hair tab disappears rather than
         * showing trimmed-down controls. A tab left with no fields is dropped.
         */
        public Resolved withoutNative(String nativeGroup) {
            List<Tab> kept = new ArrayList<>();
            for (Tab t : tabs) {
                List<Field> fields = new ArrayList<>();
                for (Field f : t.fields()) {
                    if (f.kind() != Field.Kind.NATIVE || !nativeGroup.equals(f.nativeGroup())) fields.add(f);
                }
                if (!fields.isEmpty()) kept.add(new Tab(t.pageId(), t.label(), fields));
            }
            return new Resolved(kept);
        }
    }

    /**
     * Resolve the authored layout, or — when the species authored none but the character carries
     * editable Townstead genes (a sized attachment like elf ears, variant genes) — synthesize the
     * default: MCA's four native groups in canonical order, then the genes bucketed by category as
     * extra tabs. {@code null} only when there is nothing of ours to edit, keeping plain MCA
     * villagers on the untouched native Character tab.
     */
    public static Resolved resolveOrDefault(RootCatalogEntry entry) {
        return resolveOrDefault(entry, java.util.Set.of());
    }

    /**
     * As {@link #resolveOrDefault(RootCatalogEntry)}, but individual-first: a non-empty
     * {@code expressedGeneIds} makes the synthesized default enumerate the genes this character
     * actually expresses — a bred hybrid edits its real mix (the elf ears it inherited, not the
     * root-archetype visage it didn't) and off-root genes from the other parent's side get
     * controls too. The root's grant list only orders the tabs (stable across opens) and remains
     * the source when nothing is synced for the entity.
     */
    public static Resolved resolveOrDefault(RootCatalogEntry entry, java.util.Set<String> expressedGeneIds) {
        Resolved authored = resolve(entry);
        if (authored != null) return authored;
        if (entry == null && (expressedGeneIds == null || expressedGeneIds.isEmpty())) return null;
        Map<String, List<Field>> byCategory = expressedGeneIds == null || expressedGeneIds.isEmpty()
                ? editableByCategory(entry)
                : editableByCategory(entry, expressedGeneIds);
        if (byCategory.isEmpty()) return null;
        List<Tab> tabs = new ArrayList<>();
        for (String g : NATIVE_ORDER) {
            tabs.add(new Tab(mcaSubpage(g), nativeLabel(g), List.of(Field.nativeGroup(g))));
        }
        for (Map.Entry<String, List<Field>> e : byCategory.entrySet()) {
            tabs.add(new Tab(tabPageId(e.getKey()), Component.literal(e.getKey()), e.getValue()));
        }
        return finish(tabs);
    }

    /**
     * Append the always-on Height/Width scale tab unless a native {@code body} tab is already
     * present (MCA renders the size/width sliders there itself). This restores the pre-1.21 parity
     * where those universal scale controls were always reachable — the old editor kept {@code body}
     * as a top-level page — for custom rigs whose species hide the human body group. No-op on an
     * empty tab list, so a plain MCA villager (nothing of ours took over) is left untouched.
     */
    private static Resolved finish(List<Tab> tabs) {
        if (!tabs.isEmpty() && tabs.stream().noneMatch(t -> t.pageId().equals(mcaSubpage(CharacterEditorLayout.NATIVE_BODY)))) {
            tabs.add(new Tab(SIZE_PAGE, Component.translatable("townstead.editor.size"), List.of(Field.scale())));
        }
        return new Resolved(tabs);
    }

    private static Map<String, List<Field>> editableByCategory(RootCatalogEntry entry) {
        Map<String, List<Field>> byCategory = new LinkedHashMap<>();
        for (RootCatalogEntry.Inherited in : entry.inheritedGenes()) {
            GeneCatalogEntry g = RootCatalogClient.gene(in.geneId());
            if (!isEditable(g)) continue;
            byCategory.computeIfAbsent(g.category(), k -> new ArrayList<>()).add(Field.gene(g));
        }
        return byCategory;
    }

    /** The expressed genes bucketed by category: root-grant order first, off-root extras id-sorted. */
    private static Map<String, List<Field>> editableByCategory(RootCatalogEntry entry,
                                                               java.util.Set<String> expressedGeneIds) {
        java.util.LinkedHashSet<String> ordered = new java.util.LinkedHashSet<>();
        if (entry != null) {
            for (RootCatalogEntry.Inherited in : entry.inheritedGenes()) {
                if (expressedGeneIds.contains(in.geneId())) ordered.add(in.geneId());
            }
        }
        List<String> extras = new ArrayList<>();
        for (String id : expressedGeneIds) {
            if (!ordered.contains(id)) extras.add(id);
        }
        java.util.Collections.sort(extras);
        ordered.addAll(extras);
        Map<String, List<Field>> byCategory = new LinkedHashMap<>();
        for (String id : ordered) {
            GeneCatalogEntry g = RootCatalogClient.gene(id);
            if (!isEditable(g)) continue;
            byCategory.computeIfAbsent(g.category(), k -> new ArrayList<>()).add(Field.gene(g));
        }
        return byCategory;
    }

    /** Resolve, or {@code null} when the species has no custom layout (MCA native untouched). */
    public static Resolved resolve(RootCatalogEntry entry) {
        if (entry == null) return null;
        CharacterEditorLayout layout = entry.characterEditor();
        if (layout == null) return null;

        List<Tab> tabs = new ArrayList<>();
        if (!layout.tabs().isEmpty()) {
            for (CharacterEditorLayout.Tab t : layout.tabs()) {
                List<Field> fields = new ArrayList<>();
                for (String ref : t.fields()) {
                    // Listing a native group directly in a tab opts it in (no need to also be in `native`).
                    if (CharacterEditorLayout.isNativeGroup(ref)) {
                        fields.add(Field.nativeGroup(ref));
                    } else {
                        GeneCatalogEntry g = RootCatalogClient.gene(ref);
                        if (isEditable(g)) fields.add(Field.gene(g));
                    }
                }
                if (fields.isEmpty()) continue;
                // A tab that is one native group delegates to MCA's subpage (so MCA renders its content);
                // any tab with genes is our own page (gene fields rendered; a stray native ref is ignored).
                boolean pureNative = fields.size() == 1 && fields.get(0).kind() == Field.Kind.NATIVE;
                String pageId = pureNative ? mcaSubpage(fields.get(0).nativeGroup()) : tabPageId(t.id());
                tabs.add(new Tab(pageId, tabLabel(t), fields));
            }
        } else {
            // The species' own appearance genes come first (its identity), bucketed by category,
            // then any kept MCA-native groups in canonical order.
            Map<String, List<Field>> byCategory = editableByCategory(entry);
            for (Map.Entry<String, List<Field>> e : byCategory.entrySet()) {
                tabs.add(new Tab(tabPageId(e.getKey()), Component.literal(e.getKey()), e.getValue()));
            }
            for (String g : NATIVE_ORDER) {
                if (layout.nativeGroups().contains(g)) {
                    tabs.add(new Tab(mcaSubpage(g), nativeLabel(g), List.of(Field.nativeGroup(g))));
                }
            }
        }
        return finish(tabs);
    }

    /** True for the editor true-name pages we render ourselves (vs delegating to MCA). */
    public static boolean isCustomPage(String page) {
        return page != null && page.startsWith("townstead_char:");
    }

    private static boolean isEditable(GeneCatalogEntry g) {
        if (g == null) return false;
        return (g.isVariants() && !g.variants().isEmpty()) || !g.channels().isEmpty();
    }

    private static String tabPageId(String id) {
        return "townstead_char:" + id;
    }

    private static String mcaSubpage(String nativeGroup) {
        return switch (nativeGroup) {
            case CharacterEditorLayout.NATIVE_CLOTHES -> "clothing_style";
            case CharacterEditorLayout.NATIVE_HAIR -> "hair_style";
            case CharacterEditorLayout.NATIVE_EYES -> "eyes";
            default -> "body";
        };
    }

    private static Component nativeLabel(String nativeGroup) {
        return Component.translatable("gui.villager_editor.subpage." + switch (nativeGroup) {
            case CharacterEditorLayout.NATIVE_CLOTHES -> "clothing_style";
            case CharacterEditorLayout.NATIVE_HAIR -> "hair_style";
            case CharacterEditorLayout.NATIVE_EYES -> "eyes";
            default -> "body";
        });
    }

    private static Component tabLabel(CharacterEditorLayout.Tab t) {
        if (!t.labelKey().isEmpty()) return Component.translatableWithFallback(t.labelKey(),
                t.label().isEmpty() ? t.id() : t.label());
        return Component.literal(t.label().isEmpty() ? t.id() : t.label());
    }
}
