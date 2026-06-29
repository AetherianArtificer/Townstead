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

    /** Canonical left-to-right order for kept native groups. */
    private static final String[] NATIVE_ORDER = {
            CharacterEditorLayout.NATIVE_BODY, CharacterEditorLayout.NATIVE_CLOTHES,
            CharacterEditorLayout.NATIVE_HAIR, CharacterEditorLayout.NATIVE_EYES
    };

    /**
     * A resolved field within a tab: a delegated MCA-native group, a plain variant cycler, or a TONE
     * field (a palette {@code skin_tone} gene → hue cycler + the draggable melanin×hemoglobin swatch).
     */
    public record Field(Kind kind, String nativeGroup, GeneCatalogEntry gene) {
        public enum Kind { NATIVE, CYCLER, TONE }
        static Field nativeGroup(String g) { return new Field(Kind.NATIVE, g, null); }
        static Field gene(GeneCatalogEntry g) {
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
            Map<String, List<Field>> byCategory = new LinkedHashMap<>();
            for (RootCatalogEntry.Inherited in : entry.inheritedGenes()) {
                GeneCatalogEntry g = RootCatalogClient.gene(in.geneId());
                if (!isEditable(g)) continue;
                byCategory.computeIfAbsent(g.category(), k -> new ArrayList<>()).add(Field.gene(g));
            }
            for (Map.Entry<String, List<Field>> e : byCategory.entrySet()) {
                tabs.add(new Tab(tabPageId(e.getKey()), Component.literal(e.getKey()), e.getValue()));
            }
            for (String g : NATIVE_ORDER) {
                if (layout.nativeGroups().contains(g)) {
                    tabs.add(new Tab(mcaSubpage(g), nativeLabel(g), List.of(Field.nativeGroup(g))));
                }
            }
        }
        return new Resolved(tabs);
    }

    /** True for the editor true-name pages we render ourselves (vs delegating to MCA). */
    public static boolean isCustomPage(String page) {
        return page != null && page.startsWith("townstead_char:");
    }

    private static boolean isEditable(GeneCatalogEntry g) {
        return g != null && g.isVariants() && !g.variants().isEmpty();
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
