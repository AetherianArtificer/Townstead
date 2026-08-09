package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.gui.McaEditorCompat;
import com.aetherianartificer.townstead.client.gui.character.CharacterEditorResolver;
import com.aetherianartificer.townstead.client.gui.character.CharacterTabStrip;
import com.aetherianartificer.townstead.client.gui.root.RootPicker;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.client.root.PreviewParticles;
import net.minecraft.client.gui.GuiGraphics;
import com.aetherianartificer.townstead.client.skin.RootSkinPickerTexture;
import com.aetherianartificer.townstead.client.skin.SkinTintRegistry;
import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.client.species.RigSkinTone;
import com.aetherianartificer.townstead.mixin.accessor.ColorPickerWidgetAccessor;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.GeneRange;
import com.aetherianartificer.townstead.root.Genome;
import com.aetherianartificer.townstead.root.SetGeneVariantC2SPayload;
import net.minecraft.network.chat.Component;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.aetherianartificer.townstead.root.RootGenes;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.RootSetC2SPayload;
import net.conczin.mca.client.gui.DestinyScreen;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.client.gui.widget.ColorPickerWidget;
import net.conczin.mca.client.gui.widget.GeneSliderWidget;
import net.conczin.mca.client.gui.widget.HorizontalColorPickerWidget;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Adds an "origins" tab to MCA's Villager Editor. When editing an NPC the picker
 * targets that villager (by network id resolved from its UUID); when editing the
 * player's own model it targets the player ({@link RootSetC2SPayload#SELF}).
 *
 * <p><b>Live preview:</b> MCA's editor renders/saves a client dummy {@code villager}
 * (the sliders write to it; {@code syncVillagerData} saves it by UUID). Selecting
 * an origin rolls its genome onto that dummy (WYSIWYG), tags it so the skin-tint
 * layer paints it, and marks the preview dirty. The original genes are snapshotted
 * on entering the page and restored whenever the user leaves the tab or MCA saves,
 * <em>unless</em> Apply ran — Apply clears the dirty flag and ships the dummy's
 * exact previewed genes to the server via Townstead's own commit packet (never
 * MCA's full editor save, whose family-tree side effects corrupt the target).
 * So browsing never commits; only Apply does.</p>
 */
@Mixin(VillagerEditorScreen.class)
public abstract class VillagerEditorRootMixin extends Screen {

    @Shadow(remap = false) @Final protected VillagerEntityMCA villager;
    @Shadow(remap = false) @Final UUID villagerUUID;
    @Shadow(remap = false) @Final UUID playerUUID;
    @Shadow(remap = false) protected String page;
    @Shadow(remap = false) protected abstract void setPage(String page);
    @Shadow(remap = false) protected abstract String[] getPages();

    @Unique private boolean townstead$previewDirty;
    @Unique private float[] townstead$geneSnapshot;
    @Unique private String townstead$baseRootId = "";
    @Unique private boolean townstead$primed;
    @Unique private int townstead$target;
    // Per-gene widget refreshers: the randomize dice and the palette swatches change a
    // whole payload at once, so the gene's cycler label and sliders re-read it in place
    // (rebuilding the page would seed from the not-yet-resynced real target instead).
    @Unique private final Map<String, List<java.util.function.Consumer<
            com.aetherianartificer.townstead.root.gene.AllelePayload>>> townstead$refreshers = new LinkedHashMap<>();
    // While a gene tab is being built, its controls land in this scroll viewport instead
    // of the screen, so a control-heavy race can wheel through them. Null elsewhere
    // (body/head-page controls stay screen-level).
    @Unique private com.aetherianartificer.townstead.client.gui.character.ControlColumn townstead$column;

    /** Adds an editor control to the active scroll column, or straight to the screen. */
    @Unique
    private void townstead$addControl(net.minecraft.client.gui.components.AbstractWidget widget) {
        if (townstead$column != null) townstead$column.add(widget);
        else addRenderableWidget(widget);
    }

    private VillagerEditorRootMixin() {
        super(null);
    }

    @Inject(method = "getPages", remap = false, at = @At("RETURN"), cancellable = true)
    private void townstead$appendRootsPage(CallbackInfoReturnable<String[]> cir) {
        if ((Object) this instanceof DestinyScreen) return;
        String[] pages = RootPicker.insertRootsPage(cir.getReturnValue());
        // Old editor only (the new one hosts gene tabs under its Character hub): add a top-level
        // Character page whenever the target's species resolves custom gene tabs. getPages runs
        // inside MCA's setPage BEFORE our TAIL primes, so fall back to resolving the target here.
        if (!McaEditorCompat.isNewCharacterEditor(pages)) {
            int target = townstead$primed ? townstead$target
                    : villagerUUID.equals(playerUUID) ? RootSetC2SPayload.SELF
                    : townstead$resolveVillagerEntityId(villagerUUID);
            CharacterEditorResolver.Resolved layout = CharacterEditorResolver.resolveOrDefault(
                    com.aetherianartificer.townstead.client.root.RootCatalogClient.origin(RootClientStore.get(target)),
                    townstead$expressedOf(target));
            if (layout != null && !townstead$customTabs(layout).isEmpty()) {
                pages = townstead$insertAfter(pages, "head", TOWNSTEAD_LEGACY_PAGE);
            }
        }
        cir.setReturnValue(pages);
    }

    /**
     * Old-editor top-level page hosting the species' gene tabs. Labelled "Features", not
     * "Character", so a future MCA backport of the Character hub can land alongside it and
     * the two merge then. The page id predates the label; keep it stable for lang packs.
     */
    @Unique private static final String TOWNSTEAD_LEGACY_PAGE = "townstead_character";

    /** The layout's Townstead-rendered gene tabs, dropping delegated MCA-native groups. */
    @Unique
    private static List<CharacterEditorResolver.Tab> townstead$customTabs(CharacterEditorResolver.Resolved layout) {
        List<CharacterEditorResolver.Tab> out = new ArrayList<>();
        for (CharacterEditorResolver.Tab t : layout.tabs()) {
            // The Height/Width scale tab is a new-editor stand-in for MCA's body-page sliders; the
            // old editor keeps its top-level body page, so those sliders are already reachable there.
            if (CharacterEditorResolver.isCustomPage(t.pageId())
                    && !CharacterEditorResolver.SIZE_PAGE.equals(t.pageId())) out.add(t);
        }
        return out;
    }

    /** {@code pages} with {@code page} inserted after {@code anchor} (appended if absent); idempotent. */
    @Unique
    private static String[] townstead$insertAfter(String[] pages, String anchor, String page) {
        for (String p : pages) {
            if (page.equals(p)) return pages;
        }
        String[] out = new String[pages.length + 1];
        int i = 0;
        boolean inserted = false;
        for (String p : pages) {
            out[i++] = p;
            if (!inserted && anchor.equals(p)) {
                out[i++] = page;
                inserted = true;
            }
        }
        if (!inserted) out[i] = page;
        return out;
    }

    // Draw the previewed origin's ambient particles around the model (the real emitter is
    // server-side and never runs in this GUI). Matches MCA's entity render box:
    // x = width/2 - DATA_WIDTH(175), y = height/2, half-height 75.
    //? if neoforge {
    @Inject(method = "render", remap = false, at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_88315_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$renderPreviewParticles(GuiGraphics context, int mouseX, int mouseY, float delta,
                                                  CallbackInfo ci) {
        int x = this.width / 2 - 175;
        int y = this.height / 2;
        PreviewParticles.render(context, villager, townstead$previewSubject(), x, y - 75, x + 175, y + 75);
    }

    /**
     * The entity the preview REPRESENTS, for condition-gated preview effects: the player
     * on a self-edit, the real (synced) villager otherwise — the throwaway dummy has no
     * mood or needs to test against. Null when the target can't be resolved.
     */
    @Unique
    private net.minecraft.world.entity.LivingEntity townstead$previewSubject() {
        if (!townstead$primed) return null;
        if (townstead$target == RootSetC2SPayload.SELF) return Minecraft.getInstance().player;
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level != null
                && level.getEntity(townstead$target) instanceof net.minecraft.world.entity.LivingEntity living) {
            return living;
        }
        return null;
    }

    /** The expressed gene ids of the real character being edited (empty when unresolvable/unsynced). */
    @Unique
    private java.util.Set<String> townstead$expressedOfTarget() {
        net.minecraft.world.entity.LivingEntity real = townstead$previewSubject();
        return real == null ? java.util.Set.of() : RootClientStore.expressedGenes(real);
    }

    /** As {@link #townstead$expressedOfTarget}, for a raw target id (getPages runs before priming). */
    @Unique
    private java.util.Set<String> townstead$expressedOf(int target) {
        if (target == RootSetC2SPayload.SELF) {
            net.minecraft.world.entity.player.Player self = Minecraft.getInstance().player;
            return self == null ? java.util.Set.of() : RootClientStore.expressedGenes(self);
        }
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(target) instanceof net.minecraft.world.entity.LivingEntity living) {
            return RootClientStore.expressedGenes(living);
        }
        return java.util.Set.of();
    }

    // Leaving any page (or rebuilding one) drops an un-applied preview first, so a
    // browsed origin can't leak into another tab or get saved by MCA's Done.
    @Inject(method = "setPage", remap = false, at = @At("HEAD"))
    private void townstead$revertOnPageChange(String page, CallbackInfo ci) {
        townstead$revertPreview();
    }

    // MCA's save path: revert an un-applied preview so Done won't commit it. Apply
    // clears the dirty flag before calling this, so the previewed genes survive.
    @Inject(method = "syncVillagerData", remap = false, at = @At("HEAD"))
    private void townstead$revertOnSync(CallbackInfo ci) {
        townstead$revertPreview();
    }

    // Runs for the Destiny screen too: it routes its real pages through super.setPage,
    // so this fires there and its self-edit (SELF) path tints/previews the player's dummy.
    @Inject(method = "setPage", remap = false, at = @At("TAIL"))
    private void townstead$onSetPage(String page, CallbackInfo ci) {
        // Prime once, on the first page shown. MCA draws the preview villager on EVERY page (it's
        // the screen's left-side model, not page-specific), so the dummy's origin tint must be set
        // up the moment the editor opens, not only when the Roots tab is built — otherwise the
        // initial render shows MCA-native (human) skin. The editor renders a throwaway dummy
        // (EntityType.create) whose id differs from the real villager, so we resolve the REAL
        // villager by UUID, read its synced origin, and mirror it onto the dummy's id.
        if (!townstead$primed) {
            townstead$target = villagerUUID.equals(playerUUID)
                    ? RootSetC2SPayload.SELF
                    : townstead$resolveVillagerEntityId(villagerUUID);
            townstead$sendRootSet(townstead$target, "");      // also makes the picker row highlight
            townstead$baseRootId = RootClientStore.get(townstead$target);
            townstead$geneSnapshot = RootGenes.snapshot(villager);   // baseline for revert
            townstead$primed = true;
        }
        // Keep the dummy tinted to the real origin on every page. A page change reverts any
        // un-applied preview at HEAD first, so this never clobbers a live preview.
        RootClientStore.set(villager.getId(), townstead$baseRootId);
        townstead$previewDirty = false;
        townstead$refreshers.clear();   // the page's widgets are rebuilt below
        // The editor dummy is a separate entity with no synced alleles, so without this it
        // renders a different random face than the real villager and falls back to each
        // attachment gene's default style (a Braided or Clean-shaven dwarf previews Full).
        // Seed every carried variant from the real target so the preview matches the game.
        townstead$seedCarriedVariants();
        townstead$mirrorRealState();

        // Data-pack-driven Character layout: when the species declares one (or the character
        // carries editable Townstead genes, which synthesizes the default layout — native groups +
        // gene tabs), replace MCA's fixed subpage bar with our scrollable strip and render gene
        // fields ourselves. null = nothing of ours to edit, so MCA's native Character tab stays
        // untouched. Individual-first: the synthesized tabs enumerate the genes this character
        // actually expresses, so a bred hybrid edits its real mix, not the root archetype's.
        CharacterEditorResolver.Resolved charLayout = CharacterEditorResolver.resolveOrDefault(
                com.aetherianartificer.townstead.client.root.RootCatalogClient.origin(RootClientStore.get(townstead$target)),
                townstead$expressedOfTarget());
        // On the new 1.21.1 editor the strip replaces MCA's subpage bar under its Character hub. On the
        // old editor (separate Body/Head top pages) the gene tabs get their own top-level Character page
        // instead (inserted by the getPages hook): jamming them into Head/Body overflowed a control-heavy
        // species (wings + tint + face cyclers) into MCA's hair controls.
        boolean useCharacterEditor = charLayout != null && McaEditorCompat.isNewCharacterEditor(getPages());
        List<CharacterEditorResolver.Tab> legacyTabs = charLayout != null && !useCharacterEditor
                ? townstead$customTabs(charLayout) : java.util.Collections.emptyList();
        boolean useLegacyCharacterTab = !legacyTabs.isEmpty();
        if (useLegacyCharacterTab) {
            // The hub page itself has no content; land on the first gene tab.
            if (TOWNSTEAD_LEGACY_PAGE.equals(page)) {
                setPage(legacyTabs.get(0).pageId());
                return;
            }
            if (CharacterEditorResolver.isCustomPage(page)) {
                List<CharacterTabStrip.Entry> entries = new ArrayList<>();
                for (CharacterEditorResolver.Tab t : legacyTabs) {
                    entries.add(new CharacterTabStrip.Entry(t.pageId(), t.label()));
                }
                addRenderableWidget(new CharacterTabStrip(this.width / 2, this.height / 2 + TOWNSTEAD_STRIP_Y,
                        175, 20, entries, page, this::setPage));
                townstead$buildGeneTab(charLayout.byPage(page));
                townstead$pressLegacyCharacterTab();
            }
        }
        if (useCharacterEditor && townstead$isCharacterPage(page)) {
            // Entering Character lands on MCA's "body" subpage; if the species dropped that group,
            // jump to the first resolved tab instead.
            if ("body".equals(page) && charLayout.byPage("body") == null && !charLayout.tabs().isEmpty()) {
                setPage(charLayout.tabs().get(0).pageId());
                return;
            }
            townstead$removeSubpageTabs();
            townstead$buildCharacterStrip(charLayout, page);
            if (CharacterEditorResolver.isCustomPage(page)) {
                townstead$buildGeneTab(charLayout.byPage(page));
            }
        }

        // On the Body page, repaint MCA's skin color-picker square to the origin's tinted skin
        // field so the picker is WYSIWYG with the rendered villager, and add the tone-variant
        // cycler above it for palette species. When either Character surface is active its gene
        // tabs own the variant cyclers, so the ad-hoc tone/face cyclers are suppressed; they only
        // render per-page for a species with nothing that resolves into gene tabs.
        boolean geneTabsOwnControls = useCharacterEditor || useLegacyCharacterTab;
        if (McaEditorCompat.isBodyPage(page)) {
            townstead$recolorSkinPicker();
            if (!geneTabsOwnControls) townstead$addTonePicker();
            townstead$trimInertBodySliders();
        }
        if (McaEditorCompat.isHairPage(page)) {
            townstead$trimInertHair();
        }
        if (McaEditorCompat.isFacePage(page) && !geneTabsOwnControls) {
            townstead$addFaceCyclers();
        }
        if ("personality".equals(page)) {
            townstead$replacePersonalityButtons();
        }

        if (!"origins".equals(page)) return;

        RootPicker.Widgets ws = RootPicker.build(
                Minecraft.getInstance(),
                this.width / 2, this.height / 2 - 80, 175, 185, townstead$target,
                rootId -> townstead$applyRoot(townstead$target, rootId),
                entry -> townstead$previewRoot(townstead$target, entry));
        addRenderableWidget(ws.tabRoot());
        addRenderableWidget(ws.tabGenes());
        addRenderableWidget(ws.search());
        addRenderableWidget(ws.list());
        addRenderableWidget(ws.description());
        addRenderableWidget(ws.master());
        addRenderableWidget(ws.apply());
    }

    // Keep MCA's "Character" top tab highlighted while we're on one of our own gene subpages
    // (townstead_char:*), which MCA's private check doesn't know about. require=0: the method is new
    // to the 1.21.1 MCA, so on the older forge MCA (no Character hub) this simply doesn't apply.
    @Inject(method = "isMainPageSelected", remap = false, at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$customPageUnderCharacter(String mainPage, CallbackInfoReturnable<Boolean> cir) {
        if ("body".equals(mainPage) && CharacterEditorResolver.isCustomPage(this.page)) {
            cir.setReturnValue(true);
        }
    }

    /** Any page that lives under the Character hub: MCA's subpages plus our own gene pages. */
    @Unique
    private boolean townstead$isCharacterPage(String page) {
        return McaEditorCompat.isBodyPage(page) || McaEditorCompat.isHairPage(page)
                || McaEditorCompat.isFacePage(page) || "clothing_style".equals(page)
                || CharacterEditorResolver.isCustomPage(page);
    }

    /**
     * Press the old editor's top-level Character tab while one of our gene subpages is open.
     * MCA marks a tab pressed only when the page id equals the tab's own id
     * ({@code active = !p.equals(page)}), which a {@code townstead_char:*} page never does.
     */
    @Unique
    private void townstead$pressLegacyCharacterTab() {
        for (GuiEventListener child : children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget w
                    && ("gui.villager_editor.page." + TOWNSTEAD_LEGACY_PAGE).equals(townstead$widgetKey(w))) {
                w.active = false;
            }
        }
    }

    /** Strip MCA's fixed 4-button subpage bar; our scrollable tab strip replaces it. */
    @Unique
    private void townstead$removeSubpageTabs() {
        for (GuiEventListener child : new ArrayList<>(children())) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                String k = townstead$widgetKey(w);
                if (k != null && k.startsWith("gui.villager_editor.subpage.")) removeWidget(w);
            }
        }
    }

    // MCA's subpage tab bar sits at height/2 - 80 (the Presets/Quick-Export row); its content begins
    // 24px below. The strip takes the bar's row so native content lines up under it, not over it.
    @Unique private static final int TOWNSTEAD_STRIP_Y = -80;

    /** Build the scrollable Character tab strip where MCA's subpage bar sat, current tab pressed. */
    @Unique
    private void townstead$buildCharacterStrip(CharacterEditorResolver.Resolved layout, String page) {
        addRenderableWidget(new CharacterTabStrip(this.width / 2, this.height / 2 + TOWNSTEAD_STRIP_Y, 175, 20,
                CharacterTabStrip.entriesOf(layout), page, this::setPage));
    }

    /** Render a gene tab's fields down the right-hand data column (below the strip). */
    @Unique
    private void townstead$buildGeneTab(CharacterEditorResolver.Tab tab) {
        if (tab == null) return;
        int x = this.width / 2;
        int w = 175;
        int rowH = 20;
        int yStart = this.height / 2 + TOWNSTEAD_STRIP_Y + 24;
        // Fit the column above the Done button: shrink any tone swatch(es) when a tab is field-heavy.
        int tones = 0, rows = 0;
        for (CharacterEditorResolver.Field f : tab.fields()) {
            if (f.kind() == CharacterEditorResolver.Field.Kind.SCALE) { rows += 2; continue; }   // Height + Width
            if (f.gene() == null || townstead$geneGatedOff(f.gene()) || townstead$geneNotCarried(f.gene())) continue;
            if (f.kind() == CharacterEditorResolver.Field.Kind.TONE) tones++;
            else rows += townstead$fieldRows(f);   // a cycler row plus one row per size channel
        }
        int swatch = 64;
        if (tones > 0) {
            int free = (this.height / 2 + 92) - yStart - rows * (rowH + 2) - tones * (rowH + 4);
            swatch = Math.max(36, Math.min(64, free / tones));
        }
        // All field controls live in a scroll viewport spanning strip-bottom to the Done
        // row, so a tab with more controls than fit (style + channels + colour) wheels.
        com.aetherianartificer.townstead.client.gui.character.ControlColumn column =
                new com.aetherianartificer.townstead.client.gui.character.ControlColumn(
                        x, yStart, w + 6, (this.height / 2 + 92) - yStart);
        addRenderableWidget(column);
        townstead$column = column;
        int y = yStart;
        for (CharacterEditorResolver.Field f : tab.fields()) {
            if (f.kind() == CharacterEditorResolver.Field.Kind.SCALE) {
                y += townstead$buildScaleFields(x, y, w, rowH);
                continue;
            }
            if (f.gene() == null || townstead$geneGatedOff(f.gene()) || townstead$geneNotCarried(f.gene())) continue;
            if (f.kind() == CharacterEditorResolver.Field.Kind.TONE) {
                y += townstead$buildToneField(f.gene(), x, y, w, swatch);
            } else if (f.kind() == CharacterEditorResolver.Field.Kind.CYCLER) {
                // Each gene group opens with a header row (name + randomize dice), so the
                // cycler shows just the style and the sliders can use short channel labels.
                y += townstead$geneHeader(f.gene(), x, y, w);
                townstead$variantCycler(f.gene(), x, y, w, 20, rowH, false);
                y += rowH + 2;
                y += townstead$channelSliders(f.gene(), x, y, w, rowH, 2);
            } else if (f.kind() == CharacterEditorResolver.Field.Kind.SLIDER) {
                y += townstead$geneHeader(f.gene(), x, y, w);
                y += townstead$channelSliders(f.gene(), x, y, w, rowH, 2);
            }
        }
        townstead$column = null;
    }

    /**
     * The always-on Height/Width section: two sliders driving MCA's SIZE (vertical) and WIDTH
     * (horizontal) scale genes on the editor dummy. These are the universal scale controls that
     * normally live on MCA's body page, surfaced here so a custom rig whose species hides the human
     * body group can still be sized (a wide spider). The whole entity — including a rig layer —
     * renders inside MCA's {@code scale(width, height, width)}, so both apply to any rig.
     */
    @Unique
    private int townstead$buildScaleFields(int x, int y, int w, int rowH) {
        int used = 0;
        used += townstead$scaleSlider(Genetics.SIZE,
                Component.translatable("townstead.editor.height"), x, y + used, w, rowH);
        used += townstead$scaleSlider(Genetics.WIDTH,
                Component.translatable("townstead.editor.width"), x, y + used, w, rowH);
        return used;
    }

    /**
     * One MCA float-gene scale slider: seeds from the dummy's current gene, previews live (setGene +
     * refreshDimensions, exactly like MCA's own size slider), and commits the whole gene snapshot to
     * the real target. Returns the vertical space consumed.
     */
    @Unique
    private int townstead$scaleSlider(Genetics.GeneType gene, Component label, int x, int y, int w, int rowH) {
        Genetics genetics = villager.getGenetics();
        townstead$addControl(new GeneSliderWidget(x, y, w, rowH, label, genetics.getGene(gene), v -> {
            genetics.setGene(gene, v.floatValue());
            villager.refreshDimensions();
            townstead$commitScale();
        }));
        return rowH + 2;
    }

    /**
     * Persist the dummy's current MCA float genes (carrying the just-dragged SIZE/WIDTH) to the real
     * target via Townstead's gene-commit packet — never MCA's editor save, whose family-tree side
     * effects corrupt the target. The dummy loaded the real villager's genes on open, so this ships
     * the intended scale without disturbing the rest. Rebase the revert baseline so a later
     * origin-browse revert keeps the new size instead of snapping back to the open-time scale.
     */
    @Unique
    private void townstead$commitScale() {
        float[] snap = RootGenes.snapshot(villager);
        townstead$sendCommitGenes(townstead$target, snap);
        townstead$geneSnapshot = snap;
    }

    /** A gene group's section header: its display name, with the randomize dice at the right. */
    @Unique
    private int townstead$geneHeader(GeneCatalogEntry gene, int x, int y, int w) {
        int h = 14;
        var header = new net.minecraft.client.gui.components.StringWidget(x + 2, y, w - h - 4, h,
                Component.literal(gene.name()), Minecraft.getInstance().font);
        townstead$addControl(header.alignLeft());
        if (gene.grantsAttachment()) townstead$diceButton(gene, x + w - h, y, h);
        return h + 2;
    }

    /** Rows a non-tone field occupies: the header, the cycler (if variants), one per channel/tint row. */
    @Unique
    private int townstead$fieldRows(CharacterEditorResolver.Field f) {
        GeneCatalogEntry gene = f.gene();
        String carried = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(
                townstead$carriedRaw(gene.id())).variant();
        int channels = townstead$channelRows(gene, carried);
        return 1 + (f.kind() == CharacterEditorResolver.Field.Kind.CYCLER ? 1 + channels : Math.max(1, channels));
    }

    /** Rows a gene's channels render as: one per size channel, plus the tint swatches + R/G/B rows. */
    @Unique
    private int townstead$channelRows(GeneCatalogEntry gene, String carried) {
        int rows = 0;
        boolean tint = false;
        for (GeneCatalogEntry.Channel channel : gene.channelsFor(carried)) {
            if (com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.isTintChannel(channel.name())) {
                tint = true;
            } else {
                rows++;
            }
        }
        if (tint) rows += 3 + (gene.paletteFor(carried).isEmpty() ? 0 : 1);
        return rows;
    }

    /**
     * A palette {@code skin_tone} field: a hue cycler over the draggable melanin×hemoglobin swatch (a
     * recolored MCA {@link net.conczin.mca.client.gui.widget.ColorPickerWidget}), exactly the body-page
     * tone control but rendered standalone here. Cycling re-tints the swatch; dragging shades within the
     * hue. Returns the vertical space consumed. neoforge-only: this is a 1.21 MCA feature.
     */
    @Unique
    private int townstead$buildToneField(GeneCatalogEntry gene, int x, int y, int w, int size) {
        //? if neoforge {
        java.util.List<GeneCatalogEntry.Variant> opts = new ArrayList<>();
        for (GeneCatalogEntry.Variant v : gene.variants()) if (v.tint() >= 0) opts.add(v);
        if (opts.isEmpty()) { townstead$variantCycler(gene, x, y, w, 20, 20); return 22; }
        String geneId = gene.id();
        String current = townstead$carriedRaw(geneId);
        int start = 0;
        for (int i = 0; i < opts.size(); i++) if (opts.get(i).id().equals(current)) { start = i; break; }
        RootClientStore.setCarriedVariant(villager.getId(), geneId, opts.get(start).id());

        int arrowW = 20, rowH = 20;
        int midW = Math.max(20, w - arrowW * 2);
        int swatchX = x + (w - size) / 2;
        int swatchY = y + rowH + 2;

        Genetics genetics = villager.getGenetics();
        // The swatch texture comes from the same pipeline that paints this entity's skin
        // (townstead$skinFieldTexture), so the field is 1:1 with the rendered tone. The
        // carried variant was seeded above, so the resolve sees the current option.
        ResourceLocation startTex = townstead$skinFieldTexture();
        ColorPickerWidget swatch = new ColorPickerWidget(swatchX, swatchY, size, size,
                genetics.getGene(Genetics.HEMOGLOBIN), genetics.getGene(Genetics.MELANIN),
                startTex != null ? startTex : RootSkinPickerTexture.forPaletteHue(opts.get(start).tint()),
                (vx, vy) -> {
                    // Palette tone comes from melanin/hemoglobin via RigSkinTone, not skinDye, so we
                    // just drive those two genes (and skip clearSkinDye, which isn't in the 7.7.7 jar).
                    genetics.setGene(Genetics.HEMOGLOBIN, vx.floatValue());
                    genetics.setGene(Genetics.MELANIN, vy.floatValue());
                });

        int[] idx = { start };
        String name = gene.name();
        ButtonWidget[] mid = new ButtonWidget[1];
        java.util.function.IntConsumer cycle = delta -> {
            idx[0] = Math.floorMod(idx[0] + delta, opts.size());
            GeneCatalogEntry.Variant v = opts.get(idx[0]);
            mid[0].setMessage(Component.literal(name + ": " + v.label()));
            RootClientStore.setCarriedVariant(villager.getId(), geneId, v.id());
            ResourceLocation cycleTex = townstead$skinFieldTexture();
            ((ColorPickerWidgetAccessor) swatch).townstead$setTexture(
                    cycleTex != null ? cycleTex : RootSkinPickerTexture.forPaletteHue(v.tint()));
            townstead$sendSetVariant(townstead$target, geneId, v.id());
        };
        townstead$addControl(new ButtonWidget(x, y, arrowW, rowH, Component.literal("<"), b -> cycle.accept(-1)));
        mid[0] = new ButtonWidget(x + arrowW, y, midW, rowH,
                Component.literal(name + ": " + opts.get(start).label()), b -> { });
        townstead$addControl(mid[0]);
        townstead$addControl(new ButtonWidget(x + arrowW + midW, y, arrowW, rowH, Component.literal(">"), b -> cycle.accept(1)));
        townstead$addControl(swatch);
        return rowH + 2 + size + 2;
        //?} else {
        /*townstead$variantCycler(gene, x, y, w, 20, 20);
        return 22;
        *///?}
    }

    /**
     * Roll the selected origin's genome onto the editor dummy for a live, WYSIWYG preview.
     * Re-selecting the origin the target already is (or the default Overworlder when it has
     * none) must not re-roll: that would randomize the existing genes (e.g. skin tone) for
     * no reason, so instead we drop any in-progress preview back to the dummy's real genes.
     */
    @Unique
    private void townstead$previewRoot(int target, RootCatalogEntry entry) {
        String current = RootClientStore.get(target);
        if (current.isEmpty()) current = RootRegistry.DEFAULT_ID.toString();
        if (entry.id().equals(current)) {
            townstead$revertPreview();
            return;
        }
        Map<String, GeneRange> ranges = new LinkedHashMap<>();
        for (RootCatalogEntry.GeneRangeView r : entry.geneRanges()) {
            ranges.put(r.key(), new GeneRange(r.min(), r.max()));
        }
        // Match spawn: roll a fresh random villager first (MCA's own genome roll covers the
        // floats this origin doesn't constrain — base skin melanin/hemoglobin, hair, face,
        // voice), then constrain the genes the origin defines to its ranges. So the preview is
        // a representative member, not the opening skin re-tinted in place. A race can still pin
        // any of these by declaring a body-metric gene for it (apply runs after, so it wins).
        villager.getGenetics().randomize();
        RootGenes.apply(villager, ranges, villager.getRandom());
        RootClientStore.set(villager.getId(), entry.id());   // so the skin-tint layer paints the preview
        // Browsing previews the root's typical kit: drop the mirrored real expressed set so
        // the attachment layer falls back to this origin's grant list (restored on revert).
        RootClientStore.clearExpressed(villager.getId());
        townstead$previewDirty = true;
    }

    /** Commit the previewed genes (exactly) + the origin id to the real villager. */
    @Unique
    private void townstead$applyRoot(int target, String rootId) {
        townstead$previewDirty = false;            // keep the preview; the revert hooks must not undo it
        townstead$baseRootId = rootId;         // the applied origin is the new baseline, so page changes
                                                   // (which reset the dummy to baseline) and the Body picker
                                                   // both reflect it, not the origin the editor opened with
        townstead$sendRootSet(target, rootId); // sets Life.rootId on the real villager
        // Commit the dummy's previewed MCA floats directly (WYSIWYG), NOT via MCA's
        // syncVillagerData: the editor's full save also rewrites the target's family-tree
        // entry (gender, typed-in parents) and the player's whole MCA snapshot from stale
        // editor-buffer keys, which erased parent names and broke gendered family dialogue.
        townstead$sendCommitGenes(target, RootGenes.snapshot(villager));
    }

    @Unique
    private void townstead$revertPreview() {
        if (!townstead$previewDirty) return;
        RootGenes.restore(villager, townstead$geneSnapshot);
        RootClientStore.set(villager.getId(), townstead$baseRootId);   // back to the real origin's tint
        townstead$mirrorRealState();   // back to the real individual's expressed genes
        townstead$previewDirty = false;
    }

    /**
     * The WYSIWYG texture for the skin-picker square: MCA's colormap run through the SAME
     * pipeline that paints this entity's skin — the skin-layer blend (packed tint) for a
     * default MCA body, the palette-hue luma shading only for a custom-rig species whose
     * texture RigSkinTone shades. Picking a spot on the square then yields exactly that
     * colour on the model. Null when the entity has no Townstead skin treatment at all.
     */
    @Unique
    private ResourceLocation townstead$skinFieldTexture() {
        int hue = RigSkinTone.paletteHue(villager);
        if (hue >= 0 && RigModels.isAlternate(RigModels.rigBaseFor(villager))) {
            return RootSkinPickerTexture.forPaletteHue(hue);
        }
        java.util.OptionalInt tint = SkinTintRegistry.resolve(villager);
        if (tint.isPresent()) return RootSkinPickerTexture.forTint(tint.getAsInt());
        return hue >= 0 ? RootSkinPickerTexture.forPaletteHue(hue) : null;
    }

    /** Repaint MCA's Body skin-picker square (the one using villager_skin.png) to this origin's tinted skin. */
    @Unique
    private void townstead$recolorSkinPicker() {
        ResourceLocation tex = townstead$skinFieldTexture();
        if (tex == null) return;
        for (GuiEventListener child : children()) {
            // The Body page has two 2-D pickers (skin + hair) and 1-D HSV sliders (a subclass);
            // the skin one is the non-subclass ColorPickerWidget still bound to villager_skin.png.
            if (child instanceof ColorPickerWidget picker
                    && !(child instanceof HorizontalColorPickerWidget)
                    && RootSkinPickerTexture.isSkinPickerTexture(((ColorPickerWidgetAccessor) picker).townstead$getTexture())) {
                ((ColorPickerWidgetAccessor) picker).townstead$setTexture(tex);
            }
        }
    }

    /**
     * Add a {@code "< Tone >"} cycler above the skin picker, but only for a palette species
     * (an origin whose {@code skin_tone} gene carries tinted variants). Cycling previews the chosen
     * tone live on the dummy (and its swatch) and commits the carried variant to the real target.
     * This conditional field is the template for the rest of the appearance editor.
     */
    @Unique
    private void townstead$addTonePicker() {
        GeneCatalogEntry palette = RigSkinTone.paletteGene(villager);
        if (palette == null) return;
        List<GeneCatalogEntry.Variant> opts = new ArrayList<>();
        for (GeneCatalogEntry.Variant v : palette.variants()) {
            if (v.tint() >= 0) opts.add(v);
        }
        if (opts.isEmpty()) return;
        ColorPickerWidget skin = townstead$findSkinPicker();
        if (skin == null) return;
        // Drop the skin swatch to free a row directly above it for the cycler (it sits in the gap
        // between Previous/Next and the swatch). The page rebuilds on every setPage, so the swatch
        // starts at its original Y each time and this shift doesn't accumulate.
        int rowHeight = 20;
        int pickerY = skin.getY();
        skin.setY(pickerY + rowHeight + 2);
        String geneId = palette.id();
        // Start from the REAL target's carried variant (synced by start-tracking); the editor dummy
        // has no synced allele, so seed it to match the picker, the model, and the swatch on open.
        String current = townstead$carriedRaw(geneId);
        int start = 0;
        for (int i = 0; i < opts.size(); i++) {
            if (opts.get(i).id().equals(current)) { start = i; break; }
        }
        RootClientStore.setCarriedVariant(villager.getId(), geneId, opts.get(start).id());
        townstead$recolorSkinPicker();
        int[] idx = { start };
        // Three buttons [<] [Tone] [>], matching the editor's date pagination. The middle button is a
        // static label (no-op); the arrows page through the tone variants.
        int arrowW = 20;
        int midW = Math.max(20, skin.getWidth() - arrowW * 2);
        ButtonWidget[] mid = new ButtonWidget[1];
        IntConsumer cycle = delta -> {
            idx[0] = Math.floorMod(idx[0] + delta, opts.size());
            String variantId = opts.get(idx[0]).id();
            mid[0].setMessage(Component.literal(opts.get(idx[0]).label()));
            RootClientStore.setCarriedVariant(villager.getId(), geneId, variantId);   // live preview
            townstead$recolorSkinPicker();                                              // swatch follows
            townstead$sendSetVariant(townstead$target, geneId, variantId);              // commit
        };
        addRenderableWidget(new ButtonWidget(skin.getX(), pickerY, arrowW, rowHeight,
                Component.literal("<"), b -> cycle.accept(-1)));
        mid[0] = new ButtonWidget(skin.getX() + arrowW, pickerY, midW, rowHeight,
                Component.literal(opts.get(start).label()), b -> { });
        addRenderableWidget(mid[0]);
        addRenderableWidget(new ButtonWidget(skin.getX() + arrowW + midW, pickerY, arrowW, rowHeight,
                Component.literal(">"), b -> cycle.accept(1)));
    }

    /**
     * Drop MCA Body-page gene sliders that do nothing for this species:
     * <ul>
     *   <li><b>Breast</b> when the species opts out of breasts ({@code "breasts": false}).</li>
     *   <li><b>Skin</b> for an alternate rig: that slider only selects one of MCA's villager-skin
     *       textures, which an own-textured rig (e.g. skeletownie's {@code skeleton.png}) never shows.
     *       (The melanin/hemoglobin colour square is untouched; it still shades the rig's tone.)</li>
     * </ul>
     * MCA lays Breast + Skin out as a pair on one row, so if only one is removed the survivor is
     * widened to fill the freed half; if both go, the row is left empty.
     */
    /** The origin's face variant genes (eyes/mouth/eye_color) that have options to cycle. */
    @Unique
    private java.util.List<GeneCatalogEntry> townstead$faceGenes() {
        java.util.List<GeneCatalogEntry> out = new ArrayList<>();
        com.aetherianartificer.townstead.root.RootCatalogEntry origin =
                com.aetherianartificer.townstead.client.root.RootCatalogClient.origin(RootClientStore.get(townstead$target));
        if (origin == null) return out;
        for (com.aetherianartificer.townstead.root.RootCatalogEntry.Inherited inh : origin.inheritedGenes()) {
            GeneCatalogEntry g = com.aetherianartificer.townstead.client.root.RootCatalogClient.gene(inh.geneId());
            if (g != null && g.isFace() && !g.variants().isEmpty()) out.add(g);
        }
        return out;
    }

    /**
     * Seed the editor dummy's carried variants from the REAL target, so the preview
     * (face choices, attachment styles, channel rolls, colours) matches what the
     * villager actually shows in-game rather than a fresh per-dummy roll or each
     * gene's fallback style. Resolution goes through the real ENTITY, exactly like
     * the world renderer: the live sync when present, the entity's persisted snapshot
     * otherwise — the raw synced map alone is often empty for a long-loaded villager.
     * Runs on every page since the model renders on all of them.
     */
    @Unique
    private void townstead$seedCarriedVariants() {
        // Synced layer first (covers players and freshly-changed villagers).
        RootClientStore.carriedVariants(townstead$target).forEach((geneId, payload) -> {
            if (payload != null && !payload.isEmpty()) {
                RootClientStore.setCarriedVariant(villager.getId(), geneId, payload);
            }
        });
        // Entity-resolved layer: every gene the target could carry, through the same
        // path the world render uses (synced by the REAL entity id, snapshot fallback).
        // For a self-edit the target is the SELF sentinel, not an entity id, so resolve
        // to the player — otherwise the dummy stays unseeded and every editor open
        // falls back to a fresh random pick per gene (the grey-skin-square roulette).
        net.minecraft.world.entity.LivingEntity real = townstead$previewSubject();
        if (real == null) return;
        RootClientStore.carriedVariants(real.getId()).forEach((geneId, payload) -> {
            if (payload != null && !payload.isEmpty()) {
                RootClientStore.setCarriedVariant(villager.getId(), geneId, payload);
            }
        });
        java.util.Set<String> geneIds = new java.util.LinkedHashSet<>(RootClientStore.expressedGenes(real));
        com.aetherianartificer.townstead.root.RootCatalogEntry origin =
                com.aetherianartificer.townstead.client.root.RootCatalogClient.origin(RootClientStore.resolve(real));
        if (origin != null) {
            for (com.aetherianartificer.townstead.root.RootCatalogEntry.Inherited inherited : origin.inheritedGenes()) {
                geneIds.add(inherited.geneId());
            }
        }
        for (String geneId : geneIds) {
            String payload = RootClientStore.resolveCarriedVariant(real, geneId);
            if (!payload.isEmpty()) {
                RootClientStore.setCarriedVariant(villager.getId(), geneId, payload);
            }
        }
    }

    /**
     * WYSIWYG for the individual, not the archetype: mirror the real target's expressed
     * gene set onto the dummy's id (so the preview wears the genes this villager actually
     * inherited, not the origin-typical kit the empty-set fallback renders) and its life
     * stage (so a baby previews without the attachments its stages hide — an orc's tusks
     * grow in at child stage). Browsing a different root clears the mirror so the picker
     * still previews that root's typical kit; the revert path restores it. Senior stage
     * keys off the life sync by real entity id and stays un-mirrored: a senior previews
     * as a plain adult, the closest stage the editor can show.
     */
    @Unique
    private void townstead$mirrorRealState() {
        net.minecraft.world.entity.LivingEntity real = townstead$previewSubject();
        if (real == null) return;
        RootClientStore.mirrorExpressed(real, villager.getId());
        if (real instanceof net.conczin.mca.entity.VillagerEntityMCA realMca) {
            villager.setAgeState(realMca.getAgeState());
        }
    }

    /**
     * Drop the Head page's hair controls (selector, randomize, prev/next, HSV toggle) when this rig
     * declares no MCA hair ({@code "hair": false}, the default for a custom rig — a skeleton has none).
     * Base MCA villagers have no rig definition, so {@code rig == null} keeps their hair; a custom rig
     * can opt back in with {@code "hair": true}. Also frees the room the face cyclers reuse.
     */
    @Unique
    private void townstead$trimInertHair() {
        com.aetherianartificer.townstead.root.rig.RigDefinition rig =
                RigModels.definition(RigModels.rigBaseFor(villager));
        if (rig == null || rig.hair()) return;
        java.util.Set<String> hairKeys = java.util.Set.of(
                "gui.villager_editor.hair_hsv", "gui.villager_editor.hair_genetic",
                "gui.villager_editor.randHair", "gui.villager_editor.selectHair",
                "gui.villager_editor.prev", "gui.villager_editor.next");
        for (GuiEventListener child : new ArrayList<>(children())) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget aw) {
                String k = townstead$widgetKey(aw);   // null for non-translatable labels; Set.of throws on contains(null)
                if (k != null && hairKeys.contains(k)) removeWidget(aw);
            }
        }
    }

    @Unique
    private void townstead$addFaceCyclers() {
        java.util.List<GeneCatalogEntry> genes = townstead$faceGenes();
        java.util.List<GeneCatalogEntry> sized = townstead$sizedAttachmentGenes();
        if (genes.isEmpty() && sized.isEmpty()) return;
        int rowH = 18;
        int rows = genes.size();
        for (GeneCatalogEntry gene : sized) {
            String carried = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(
                    townstead$carriedRaw(gene.id())).variant();
            rows += 1 + townstead$channelRows(gene, carried);   // header row + controls
        }

        GeneSliderWidget faceSlider = null;
        String faceKey = Genetics.FACE.getTranslationKey();
        for (GuiEventListener child : children()) {
            if (child instanceof GeneSliderWidget s && faceKey.equals(townstead$widgetKey(s))) {
                faceSlider = s;
                break;
            }
        }
        int x, y, w;
        if (faceSlider != null) {
            x = faceSlider.getX();
            y = faceSlider.getY();
            w = faceSlider.getWidth();
            int delta = rows * (rowH + 1) - faceSlider.getHeight();
            if (delta > 0) {
                String voice = Genetics.VOICE.getTranslationKey();
                String tone = Genetics.VOICE_TONE.getTranslationKey();
                for (GuiEventListener child : children()) {
                    if (child instanceof GeneSliderWidget s && s.getY() > y) {
                        String k = townstead$widgetKey(s);
                        if (voice.equals(k) || tone.equals(k)) s.setY(s.getY() + delta);
                    }
                }
            }
            removeWidget(faceSlider);
        } else {
            x = 6;
            y = this.height / 2 - rows * (rowH + 1) / 2;
            w = 110;
        }
        for (GeneCatalogEntry gene : genes) {
            townstead$variantCycler(gene, x, y, w, 16, rowH);
            y += rowH + 1;
        }
        for (GeneCatalogEntry gene : sized) {
            y += townstead$geneHeader(gene, x, y, w);
            y += townstead$channelSliders(gene, x, y, w, rowH, 1);
        }
    }

    /** A widget's translation key, or null if its label isn't translatable. */
    @Unique
    private String townstead$widgetKey(net.minecraft.client.gui.components.AbstractWidget w) {
        return w.getMessage().getContents() instanceof TranslatableContents tc ? tc.getKey() : null;
    }

    /**
     * One slider per size channel of a sized attachment gene (ear length + ear droop, ...).
     * Each channel maps its own catalog range onto the slider, seeds from the real target's
     * synced roll (midpoint when unknown), previews live on the dummy, and commits the FULL
     * re-encoded allele payload (variant + every channel) through the same set-variant
     * payload the cyclers use — the server clamps each channel to its declared range.
     * Returns the vertical space consumed ({@code channels × (rowH + spacing)}).
     */
    @Unique
    private int townstead$channelSliders(GeneCatalogEntry gene, int x, int y, int w, int rowH, int spacing) {
        String geneId = gene.id();
        com.aetherianartificer.townstead.root.gene.AllelePayload carried =
                com.aetherianartificer.townstead.root.gene.AllelePayload.parse(
                        townstead$carriedRaw(geneId));
        java.util.List<GeneCatalogEntry.Channel> channels = gene.channelsFor(carried.variant());
        int used = 0;
        boolean tint = false;
        for (GeneCatalogEntry.Channel channel : channels) {
            if (com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.isTintChannel(channel.name())) {
                tint = true;   // the three components render as one colour control below
                continue;
            }
            townstead$channelSlider(gene, channel, x, y + used, w, rowH);
            used += rowH + spacing;
        }
        if (tint) used += townstead$tintControls(gene, carried.variant(), x, y + used, w, rowH, spacing);
        return used;
    }

    @Unique
    private void townstead$channelSlider(GeneCatalogEntry gene, GeneCatalogEntry.Channel channel,
                                         int x, int y, int w, int rowH) {
        String geneId = gene.id();
        float min = channel.min();
        float max = channel.max();
        float span = Math.max(1e-4f, max - min);
        com.aetherianartificer.townstead.root.gene.AllelePayload carried =
                com.aetherianartificer.townstead.root.gene.AllelePayload.parse(
                        townstead$carriedRaw(geneId));
        float value = Math.max(min, Math.min(max, carried.channel(channel.name(), (min + max) / 2f)));
        // Seed the dummy preview with the full payload so the render layer sees this channel.
        RootClientStore.setCarriedVariant(villager.getId(), geneId, townstead$withChannel(
                RootClientStore.carriedVariants(villager.getId()).get(geneId) != null
                        ? RootClientStore.carriedVariants(villager.getId()).get(geneId)
                        : townstead$carriedRaw(geneId),
                channel.name(), value));
        String name = !channel.label().isEmpty() ? channel.label()
                : (gene.sizeLabel().isEmpty() ? gene.name() : gene.sizeLabel());
        com.aetherianartificer.townstead.client.gui.life.LifeAgeSlider slider =
                new com.aetherianartificer.townstead.client.gui.life.LifeAgeSlider(
                x, y, w, rowH, (value - min) / span,
                v -> Component.literal(name + ": " + Math.round((min + v * span) * 100.0) + "%"),
                v -> {
                    // Re-encode against the dummy's latest payload so the variant picked in the
                    // cycler and the other channels' sliders survive this channel's change.
                    String encoded = townstead$withChannel(
                            RootClientStore.carriedVariants(villager.getId()).get(geneId),
                            channel.name(), min + (float) v * span);
                    RootClientStore.setCarriedVariant(villager.getId(), geneId, encoded);   // live preview
                    townstead$sendSetVariant(townstead$target, geneId, encoded);            // commit
                    townstead$focusGene(gene);                                              // zoom the preview in
                });
        townstead$addControl(slider);
        townstead$onRefresh(geneId, payload -> slider.setNormalizedValue(
                (Math.max(min, Math.min(max, payload.channel(channel.name(), (min + max) / 2f))) - min) / span));
    }

    /**
     * One colour control for a heritable-tint gene: a row of palette swatches (each sets
     * the whole colour) above free red/green/blue sliders, all reading and writing the
     * reserved {@code tint_r}/{@code tint_g}/{@code tint_b} channels of the same payload
     * the size sliders share. Returns the vertical space consumed.
     */
    @Unique
    private int townstead$tintControls(GeneCatalogEntry gene, String variantId,
                                       int x, int y, int w, int rowH, int spacing) {
        String geneId = gene.id();
        java.util.List<Integer> palette = gene.paletteFor(variantId);
        int used = 0;
        if (!palette.isEmpty()) {
            int bw = Math.max(14, w / palette.size());
            for (int i = 0; i < palette.size() && x + i * bw < x + w; i++) {
                final int color = palette.get(i);
                int bx = x + i * bw;
                townstead$addControl(new com.aetherianartificer.townstead.client.gui.character.SwatchButton(
                        bx, y, Math.min(bw, x + w - bx), rowH, color,
                        () -> townstead$setTint(gene, color)));
            }
            used += rowH + spacing;
        }
        // Free colour choice: MCA's HSV gradient bars where its editor ships them
        // (hue rainbow + live saturation/brightness gradients), plain R/G/B rows on
        // the old editor.
        //? if neoforge {
        used += townstead$hsvRows(gene, x, y + used, w, rowH, spacing);
        //?} else {
        /*String[] channels = {
                com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_R,
                com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_G,
                com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_B};
        String[] labels = {
                Component.translatable("townstead.editor.tint_red").getString(),
                Component.translatable("townstead.editor.tint_green").getString(),
                Component.translatable("townstead.editor.tint_blue").getString()};
        for (int i = 0; i < 3; i++) {
            townstead$tintSlider(gene, channels[i], labels[i], x, y + used, w, rowH);
            used += rowH + spacing;
        }
        *///?}
        return used;
    }

    /**
     * The three HSV bars, reusing MCA's own colour-picker widgets: a hue bar over its
     * shipped rainbow texture, and saturation/brightness bars whose gradients re-derive
     * from the current hue every frame. The shared {@code hsv} array is the source of
     * truth while dragging (an RGB round-trip would collapse the hue on greys); swatch
     * clicks and the dice re-seed it through the gene's refreshers.
     */
    //? if neoforge {
    @Unique
    private int townstead$hsvRows(GeneCatalogEntry gene, int x, int y, int w, int rowH, int spacing) {
        String geneId = gene.id();
        var carried = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(
                townstead$carriedRaw(geneId));
        double[] seed = net.conczin.mca.client.resources.ClientUtils.RGB2HSV(
                townstead$clamp01(carried.channel(com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_R, 1f)),
                townstead$clamp01(carried.channel(com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_G, 1f)),
                townstead$clamp01(carried.channel(com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_B, 1f)));
        double[] hsv = {seed[0], seed[1], seed[2]};
        Runnable commit = () -> {
            double[] rgb = net.conczin.mca.client.resources.ClientUtils.HSV2RGB(hsv[0], hsv[1], hsv[2]);
            townstead$writeTint(gene, (float) rgb[0], (float) rgb[1], (float) rgb[2], false);
        };
        // The MCA picker's 16px arrow handle centers on the value, overhanging the bar
        // by 8px at either extreme — inset the bars so the handle never leaves the
        // scroll viewport's scissor (MCA's own editor insets its bars the same way).
        int barX = x + 10;
        int barW = w - 20;
        HorizontalColorPickerWidget hue = new HorizontalColorPickerWidget(barX, y, barW, rowH, hsv[0] / 360.0,
                ResourceLocation.fromNamespaceAndPath("mca", "textures/colormap/hue.png"),
                (vx, vy) -> { hsv[0] = vx * 360.0; commit.run(); });
        net.conczin.mca.client.gui.widget.HorizontalGradientWidget saturation =
                new net.conczin.mca.client.gui.widget.HorizontalGradientWidget(
                        barX, y + rowH + spacing, barW, rowH, hsv[1],
                        () -> townstead$rgba(hsv[0], 0.0, 1.0),
                        () -> townstead$rgba(hsv[0], 1.0, 1.0),
                        (vx, vy) -> { hsv[1] = vx; commit.run(); });
        net.conczin.mca.client.gui.widget.HorizontalGradientWidget brightness =
                new net.conczin.mca.client.gui.widget.HorizontalGradientWidget(
                        barX, y + 2 * (rowH + spacing), barW, rowH, hsv[2],
                        () -> townstead$rgba(hsv[0], hsv[1], 0.0),
                        () -> townstead$rgba(hsv[0], hsv[1], 1.0),
                        (vx, vy) -> { hsv[2] = vx; commit.run(); });
        townstead$addControl(hue);
        townstead$addControl(saturation);
        townstead$addControl(brightness);
        townstead$onRefresh(geneId, payload -> {
            double[] fresh = net.conczin.mca.client.resources.ClientUtils.RGB2HSV(
                    townstead$clamp01(payload.channel(com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_R, 1f)),
                    townstead$clamp01(payload.channel(com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_G, 1f)),
                    townstead$clamp01(payload.channel(com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_B, 1f)));
            hsv[0] = fresh[0];
            hsv[1] = fresh[1];
            hsv[2] = fresh[2];
            hue.setValueX(hsv[0] / 360.0);
            saturation.setValueX(hsv[1]);
            brightness.setValueX(hsv[2]);
        });
        return 3 * (rowH + spacing);
    }

    @Unique
    private static float[] townstead$rgba(double h, double s, double v) {
        double[] rgb = net.conczin.mca.client.resources.ClientUtils.HSV2RGB(h, s, v);
        return new float[]{(float) rgb[0], (float) rgb[1], (float) rgb[2], 1f};
    }
    //?}

    @Unique
    private static float townstead$clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    @Unique
    private void townstead$tintSlider(GeneCatalogEntry gene, String channelName, String label,
                                      int x, int y, int w, int rowH) {
        String geneId = gene.id();
        com.aetherianartificer.townstead.root.gene.AllelePayload carried =
                com.aetherianartificer.townstead.root.gene.AllelePayload.parse(
                        townstead$carriedRaw(geneId));
        float value = Math.max(0f, Math.min(1f, carried.channel(channelName, 1f)));
        RootClientStore.setCarriedVariant(villager.getId(), geneId, townstead$withChannel(
                RootClientStore.carriedVariants(villager.getId()).get(geneId) != null
                        ? RootClientStore.carriedVariants(villager.getId()).get(geneId)
                        : townstead$carriedRaw(geneId),
                channelName, value));
        com.aetherianartificer.townstead.client.gui.life.LifeAgeSlider slider =
                new com.aetherianartificer.townstead.client.gui.life.LifeAgeSlider(
                x, y, w, rowH, value,
                v -> Component.literal(label + ": " + Math.round(v * 255.0)),
                v -> {
                    String encoded = townstead$withChannel(
                            RootClientStore.carriedVariants(villager.getId()).get(geneId),
                            channelName, (float) v);
                    RootClientStore.setCarriedVariant(villager.getId(), geneId, encoded);
                    townstead$sendSetVariant(townstead$target, geneId, encoded);
                    townstead$focusGene(gene);
                });
        townstead$addControl(slider);
        townstead$onRefresh(geneId, payload -> slider.setNormalizedValue(
                Math.max(0f, Math.min(1f, payload.channel(channelName, 1f)))));
    }

    /** Swatch click: write all three tint components of the gene's payload at once. */
    @Unique
    private void townstead$setTint(GeneCatalogEntry gene, int color) {
        townstead$writeTint(gene, ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, true);
    }

    /**
     * Writes the three tint components of the gene's payload: preview + commit, and
     * (for swatch/dice paths) refresh the gene's widgets. The HSV bars pass
     * {@code refresh = false} — they own the colour state while dragging.
     */
    @Unique
    private void townstead$writeTint(GeneCatalogEntry gene, float r, float g, float b, boolean refresh) {
        String geneId = gene.id();
        String raw = RootClientStore.carriedVariants(villager.getId()).get(geneId);
        if (raw == null) raw = townstead$carriedRaw(geneId);
        var payload = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(raw);
        java.util.Map<String, Float> channels = new LinkedHashMap<>(payload.channels());
        channels.put(com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_R,
                townstead$clamp01(r));
        channels.put(com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_G,
                townstead$clamp01(g));
        channels.put(com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_B,
                townstead$clamp01(b));
        String encoded = com.aetherianartificer.townstead.root.gene.AllelePayload.encode(
                payload.variant(), channels);
        RootClientStore.setCarriedVariant(villager.getId(), geneId, encoded);
        townstead$sendSetVariant(townstead$target, geneId, encoded);
        if (refresh) townstead$runRefresh(geneId, encoded);
        townstead$focusGene(gene);
    }

    /** A small dice button that re-rolls the gene: variant by weight, channels in range, colour from the palette. */
    @Unique
    private void townstead$diceButton(GeneCatalogEntry gene, int x, int y, int size) {
        townstead$addControl(new ButtonWidget(x, y, size, size, Component.literal("↻"),
                b -> townstead$randomize(gene)));
    }

    @Unique
    private void townstead$randomize(GeneCatalogEntry gene) {
        var random = villager.getRandom();
        String geneId = gene.id();
        String variantId = "";
        if (gene.isVariants() && !gene.variants().isEmpty()) {
            int total = 0;
            for (GeneCatalogEntry.Variant v : gene.variants()) total += Math.max(0, v.weight());
            int roll = total <= 0 ? 0 : random.nextInt(total);
            variantId = gene.variants().get(0).id();
            for (GeneCatalogEntry.Variant v : gene.variants()) {
                roll -= Math.max(0, v.weight());
                if (roll < 0) { variantId = v.id(); break; }
            }
        }
        java.util.List<Integer> palette = gene.paletteFor(variantId);
        java.util.Map<String, Float> values = new LinkedHashMap<>();
        int tint = -1;
        for (GeneCatalogEntry.Channel channel : gene.channelsFor(variantId)) {
            if (com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.isTintChannel(channel.name())) {
                if (tint < 0) tint = palette.isEmpty() ? 0xFFFFFF : palette.get(random.nextInt(palette.size()));
                int shift = com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_R
                        .equals(channel.name()) ? 16
                        : com.aetherianartificer.townstead.root.gene.types.AttachmentGeneType.TINT_G
                        .equals(channel.name()) ? 8 : 0;
                values.put(channel.name(), ((tint >> shift) & 0xFF) / 255f);
            } else {
                values.put(channel.name(),
                        channel.min() + random.nextFloat() * (channel.max() - channel.min()));
            }
        }
        String encoded = com.aetherianartificer.townstead.root.gene.AllelePayload.encode(variantId, values);
        RootClientStore.setCarriedVariant(villager.getId(), geneId, encoded);
        townstead$sendSetVariant(townstead$target, geneId, encoded);
        townstead$runRefresh(geneId, encoded);
        townstead$focusGene(gene);
    }

    @Unique
    private void townstead$onRefresh(String geneId,
                                     java.util.function.Consumer<com.aetherianartificer.townstead.root.gene.AllelePayload> consumer) {
        townstead$refreshers.computeIfAbsent(geneId, k -> new ArrayList<>()).add(consumer);
    }

    @Unique
    private void townstead$runRefresh(String geneId, String encoded) {
        List<java.util.function.Consumer<com.aetherianartificer.townstead.root.gene.AllelePayload>> list =
                townstead$refreshers.get(geneId);
        if (list == null) return;
        var payload = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(encoded);
        for (var consumer : list) consumer.accept(payload);
    }

    /**
     * Aim the preview camera at the body region the gene's attachment sits on, so the
     * user sees what they're dragging. Head-area tags zoom high, back/tail tags zoom
     * low-center; anything unresolved gets a gentle whole-figure zoom. The render-side
     * consumer is 1.21.1-only ({@code townstead$zoomPreview}); elsewhere this is inert.
     */
    @Unique
    private void townstead$focusGene(GeneCatalogEntry gene) {
        String carried = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(
                RootClientStore.carriedVariants(villager.getId()).get(gene.id())).variant();
        java.util.List<String> ids = gene.attachmentsFor(carried);
        com.aetherianartificer.townstead.root.attachment.AttachmentDef def = ids.isEmpty() ? null
                : com.aetherianartificer.townstead.client.attachment.AttachmentClient.def(ids.get(0));
        float height = 0.5f;
        float zoom = 1.5f;
        if (def != null) {
            String tag = def.targetTag() == null ? "" : def.targetTag();
            String point = def.targetPoint() == null ? "" : def.targetPoint();
            String region = !tag.isEmpty() ? tag : (!point.isEmpty() ? point : def.bone());
            if (region.contains("ear") || region.contains("horn") || region.contains("brow")
                    || region.contains("snout") || region.contains("crown") || region.contains("head")) {
                height = 0.78f;
                zoom = 2.0f;
            } else if (region.contains("tail") || region.contains("back") || region.contains("wing")
                    || region.contains("body")) {
                height = 0.45f;
                zoom = 1.7f;
            }
        }
        com.aetherianartificer.townstead.client.gui.character.EditorPreviewFocus.focus(height, zoom);
    }

    /** {@code raw} with one channel value set (a LONE legacy anonymous roll upgrades to the named channel). */
    @Unique
    private static String townstead$withChannel(String raw, String channelName, float value) {
        var payload = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(raw);
        java.util.Map<String, Float> channels = new java.util.LinkedHashMap<>(payload.channels());
        if (!channelName.isEmpty() && channels.size() == 1
                && channels.containsKey(com.aetherianartificer.townstead.root.gene.AllelePayload.LEGACY_CHANNEL)) {
            channels.remove(com.aetherianartificer.townstead.root.gene.AllelePayload.LEGACY_CHANNEL);
        }
        channels.put(channelName, value);
        return com.aetherianartificer.townstead.root.gene.AllelePayload.encode(payload.variant(), channels);
    }

    /** The origin's sized attachment genes (ear length, ...), whose size channels get editor sliders. */
    @Unique
    private java.util.List<GeneCatalogEntry> townstead$sizedAttachmentGenes() {
        java.util.List<GeneCatalogEntry> out = new ArrayList<>();
        com.aetherianartificer.townstead.root.RootCatalogEntry origin =
                com.aetherianartificer.townstead.client.root.RootCatalogClient.origin(RootClientStore.get(townstead$target));
        if (origin == null) return out;
        for (com.aetherianartificer.townstead.root.RootCatalogEntry.Inherited inh : origin.inheritedGenes()) {
            GeneCatalogEntry g = com.aetherianartificer.townstead.client.root.RootCatalogClient.gene(inh.geneId());
            if (g != null && !g.isVariants() && !g.channels().isEmpty()
                    && !townstead$geneGatedOff(g) && !townstead$geneNotCarried(g)) out.add(g);
        }
        return out;
    }

    /**
     * True when the target individual does not actually carry this gene — it appears in
     * the layout only because the root's archetype grants it, but inheritance left this
     * character without it (a hybrid whose orc parent passed the wild visage copy). The
     * control would present the gene as worn and edit something the character does not
     * have, so the group hides — matching the render, which shows the individual's real
     * genes. An empty expressed sync (legacy villager, vanilla-model player, not yet
     * synced) hides nothing, mirroring the render layer's origin-kit fallback. Pinning a
     * gene onto such a villager stays possible via {@code /townstead gene grant} or a
     * root re-apply.
     */
    @Unique
    private boolean townstead$geneNotCarried(GeneCatalogEntry gene) {
        java.util.Set<String> expressed = townstead$expressedOfTarget();
        return !expressed.isEmpty() && !expressed.contains(gene.id());
    }

    /**
     * True when every attachment this gene could wear is {@code when}-gated off for the
     * preview dummy (a beard on a woman, braids on a man): the control would edit
     * something invisible on this character, so the editor hides the whole group. The
     * dummy carries the editor's pending gender, so toggling gender re-decides this on
     * the next page build. Missing defs or any ungated/active def keep the control.
     */
    @Unique
    private boolean townstead$geneGatedOff(GeneCatalogEntry gene) {
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        if (gene.isAttachment()) ids.addAll(gene.attachmentsFor(""));
        for (GeneCatalogEntry.Variant variant : gene.variants()) {
            for (String id : variant.attachment().split(";")) {
                if (!id.isEmpty()) ids.add(id);
            }
        }
        if (ids.isEmpty()) return false;
        for (String id : ids) {
            com.aetherianartificer.townstead.root.attachment.AttachmentDef def =
                    com.aetherianartificer.townstead.client.attachment.AttachmentClient.def(id);
            if (def == null || def.whenJson().isEmpty()) return false;
            // A toggle-dependent gate means "not right now", not "never for this
            // character" (the dummy carries no toggle state), so keep the control.
            if (def.whenJson().contains("pheno:toggled")) return false;
            if (com.aetherianartificer.townstead.client.attachment.AttachmentPoses.defActive(villager, def)) {
                return false;
            }
        }
        return true;
    }

    @Unique
    private void townstead$variantCycler(GeneCatalogEntry gene, int x, int y, int w, int arrowW, int rowH) {
        townstead$variantCycler(gene, x, y, w, arrowW, rowH, true);
    }

    /**
     * The best-known carried payload for a gene when a control seeds: the dummy first
     * ({@code townstead$seedCarriedVariants} resolved it through the real entity, and live
     * edits land there), the raw target sync next, the entity-resolved snapshot path last.
     * The raw target map alone is often empty (SELF sentinel, long-loaded villagers), which
     * used to reset every cycler and slider to its first option on each tab build.
     */
    @Unique
    private String townstead$carriedRaw(String geneId) {
        String raw = RootClientStore.carriedVariants(villager.getId()).get(geneId);
        if (raw == null || raw.isEmpty()) raw = RootClientStore.carriedVariants(townstead$target).get(geneId);
        if (raw == null || raw.isEmpty()) {
            net.minecraft.world.entity.LivingEntity real = townstead$previewSubject();
            if (real != null) raw = RootClientStore.resolveCarriedVariant(real, geneId);
        }
        return raw == null || raw.isEmpty() ? null : raw;
    }

    /**
     * Build a {@code [<] Gene: variant [>]} cycler for one variant gene, seeded from the real
     * target ({@code showGeneName} = false drops the name prefix when a section header already
     * carries it). A variant-swapped attachment gene keeps its channel rolls across cycling (the
     * payload re-encodes variant + channels; the server clamps them to the new option's ranges).
     */
    @Unique
    private void townstead$variantCycler(GeneCatalogEntry gene, int x, int y, int w, int arrowW, int rowH,
                                         boolean showGeneName) {
        List<GeneCatalogEntry.Variant> opts = gene.variants();
        String geneId = gene.id();
        var carried = com.aetherianartificer.townstead.root.gene.AllelePayload.parse(
                townstead$carriedRaw(geneId));
        int start = 0;
        for (int i = 0; i < opts.size(); i++) {
            if (opts.get(i).id().equals(carried.variant())) { start = i; break; }
        }
        RootClientStore.setCarriedVariant(villager.getId(), geneId,
                com.aetherianartificer.townstead.root.gene.AllelePayload.encode(
                        opts.get(start).id(), carried.channels()));
        int[] idx = { start };
        int midW = Math.max(20, w - arrowW * 2);
        String prefix = showGeneName ? gene.name() + ": " : "";
        ButtonWidget[] mid = new ButtonWidget[1];
        IntConsumer cycle = delta -> {
            idx[0] = Math.floorMod(idx[0] + delta, opts.size());
            GeneCatalogEntry.Variant v = opts.get(idx[0]);
            mid[0].setMessage(Component.literal(prefix + v.label()));
            String encoded = com.aetherianartificer.townstead.root.gene.AllelePayload.encode(v.id(),
                    com.aetherianartificer.townstead.root.gene.AllelePayload.parse(
                            RootClientStore.carriedVariants(villager.getId()).get(geneId)).channels());
            RootClientStore.setCarriedVariant(villager.getId(), geneId, encoded);   // live preview
            townstead$sendSetVariant(townstead$target, geneId, encoded);              // commit
            if (gene.grantsAttachment()) townstead$focusGene(gene);
        };
        townstead$addControl(new ButtonWidget(x, y, arrowW, rowH, Component.literal("<"), b -> cycle.accept(-1)));
        mid[0] = new ButtonWidget(x + arrowW, y, midW, rowH,
                Component.literal(prefix + opts.get(start).label()), b -> { });
        townstead$addControl(mid[0]);
        townstead$addControl(new ButtonWidget(x + arrowW + midW, y, arrowW, rowH,
                Component.literal(">"), b -> cycle.accept(1)));
        townstead$onRefresh(geneId, payload -> {
            for (int i = 0; i < opts.size(); i++) {
                if (opts.get(i).id().equals(payload.variant())) { idx[0] = i; break; }
            }
            mid[0].setMessage(Component.literal(prefix + opts.get(idx[0]).label()));
        });
    }

    @Unique
    private void townstead$trimInertBodySliders() {
        boolean hideBreast = !RigModels.breasts(villager);
        boolean hideSkin = RigModels.isAlternate(RigModels.rigBaseFor(villager));
        if (!hideBreast && !hideSkin) return;
        String breastKey = Genetics.BREAST.getTranslationKey();
        String skinKey = Genetics.SKIN.getTranslationKey();
        GeneSliderWidget breast = null;
        GeneSliderWidget skin = null;
        for (GuiEventListener child : children()) {
            if (child instanceof GeneSliderWidget slider
                    && slider.getMessage().getContents() instanceof TranslatableContents tc) {
                if (breastKey.equals(tc.getKey())) breast = slider;
                else if (skinKey.equals(tc.getKey())) skin = slider;
            }
        }
        // If only one of the row-pair is removed, widen the survivor across the whole row.
        if (hideBreast && !hideSkin && breast != null && skin != null && skin.getX() > breast.getX()) {
            skin.setWidth(skin.getX() + skin.getWidth() - breast.getX());
            skin.setX(breast.getX());
        } else if (hideSkin && !hideBreast && skin != null && breast != null && breast.getX() < skin.getX()) {
            breast.setWidth(skin.getX() + skin.getWidth() - breast.getX());
        }
        if (hideBreast && breast != null) removeWidget(breast);
        if (hideSkin && skin != null) removeWidget(skin);
    }

    @Unique
    private ColorPickerWidget townstead$findSkinPicker() {
        for (GuiEventListener child : children()) {
            if (child instanceof ColorPickerWidget picker
                    && !(child instanceof HorizontalColorPickerWidget)
                    && RootSkinPickerTexture.isSkinPickerTexture(((ColorPickerWidgetAccessor) picker).townstead$getTexture())) {
                return picker;
            }
        }
        return null;
    }

    /**
     * Replace MCA's full base-enum personality grid with this villager's origin-allowed pool (custom
     * personalities by display name), synced on the life payload. No pool (origin defines none) leaves
     * MCA's default picker alone. Clicking commits the chosen ref to the real target via C2S.
     */
    @Unique
    private void townstead$replacePersonalityButtons() {
        // MCA edits a throwaway preview villager whose entity id is not the real target id. Newer
        // MCA consistently gives that dummy its own id, so looking up the life snapshot by
        // villager.getId() loses the origin's personality pool and leaves MCA's native grid intact.
        // townstead$target was resolved from villagerUUID when the editor was primed.
        com.aetherianartificer.townstead.calendar.LifeClientStore.Snapshot life =
                com.aetherianartificer.townstead.calendar.LifeClientStore.get(townstead$target);
        // Defensive fallback for editor implementations that operate on the tracked entity itself.
        if (life == null) {
            life = com.aetherianartificer.townstead.calendar.LifeClientStore.get(villager.getId());
        }
        if (life == null) return;
        String[] pool = life.personalityPool();
        if (pool.length == 0) return;

        java.util.Set<String> enumNames = new java.util.HashSet<>();
        for (net.conczin.mca.entity.ai.relationship.Personality p
                : com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat.all()) {
            if (p != net.conczin.mca.entity.ai.relationship.Personality.UNASSIGNED) enumNames.add(p.getName().getString());
        }
        int col0X = Integer.MAX_VALUE;
        int startY = Integer.MAX_VALUE;
        int btnW = 0;
        List<net.conczin.mca.util.compat.ButtonWidget> remove = new ArrayList<>();
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (child instanceof net.conczin.mca.util.compat.ButtonWidget btn
                    && enumNames.contains(btn.getMessage().getString())) {
                remove.add(btn);
                col0X = Math.min(col0X, btn.getX());
                startY = Math.min(startY, btn.getY());
                btnW = btn.getWidth();
            }
        }
        if (remove.isEmpty()) return;
        for (net.conczin.mca.util.compat.ButtonWidget b : remove) removeWidget(b);

        int col1X = col0X + btnW;
        String currentName = life.personalityName();
        List<net.conczin.mca.util.compat.ButtonWidget> buttons = new ArrayList<>();
        for (int i = 0; i < pool.length; i++) {
            final String ref = pool[i];
            String resolved = life.personalityPoolName(i);
            if (resolved.isEmpty()) {
                try {
                    resolved = com.aetherianartificer.townstead.compat.mca.McaPersonalityCompat.resolve(ref)
                            .map(p -> p.getName().getString())
                            .orElse(ref);
                } catch (IllegalArgumentException e) {
                    resolved = ref;
                }
            }
            final String label = resolved;
            int x = (i % 2 == 0) ? col0X : col1X;
            int y = startY + (i / 2) * 19;
            net.conczin.mca.util.compat.ButtonWidget btn = new net.conczin.mca.util.compat.ButtonWidget(
                    x, y, btnW, 20, Component.literal(label), b -> {
                townstead$sendSetPersonality(townstead$target, ref);
                buttons.forEach(v -> v.active = true);
                b.active = false;
            });
            btn.active = !(label.equals(currentName) && !label.isEmpty());
            buttons.add(addRenderableWidget(btn));
        }
    }

    @Unique
    private void townstead$sendSetPersonality(int target, String ref) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.aetherianartificer.townstead.root.SetPersonalityC2SPayload(target, ref));
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(
                new com.aetherianartificer.townstead.root.SetPersonalityC2SPayload(target, ref));
        *///?}
    }

    @Unique
    private void townstead$sendSetVariant(int target, String geneId, String variantId) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new SetGeneVariantC2SPayload(target, geneId, variantId));
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(
                new SetGeneVariantC2SPayload(target, geneId, variantId));
        *///?}
    }

    @Unique
    private int townstead$resolveVillagerEntityId(UUID uuid) {
        net.minecraft.client.multiplayer.ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            for (net.minecraft.world.entity.Entity entity : level.entitiesForRendering()) {
                if (uuid.equals(entity.getUUID())) return entity.getId();
            }
        }
        // The dummy's id is a client-assigned id that, sent to the server, could resolve
        // to an unrelated entity. Return an unresolvable sentinel so the server no-ops
        // instead of re-rolling the wrong villager.
        return RootSetC2SPayload.NONE;
    }

    // Drop this editor's throwaway dummy from the tint cache when the screen goes away,
    // so its origin tint can't bleed onto a real entity sharing its client-side id.
    @Override
    public void removed() {
        super.removed();
        RootClientStore.remove(villager.getId());
        PreviewParticles.clear();
        com.aetherianartificer.townstead.client.gui.character.EditorPreviewFocus.clear();
    }

    // The editor's preview dummies are created and never ticked, so they keep spawn-default
    // air state (onGround false). Since MCA models became EMF-interceptable, animation packs
    // (Fresh Animations) read that state during preview renders and play their falling/flail
    // animation, which reads as violent twitching. Ground the dummy before every preview
    // render so packs see a calm standing entity. Old MCA has no renderPreviewEntity and no
    // EMF interception, so require = 0 skips it there.
    //? if neoforge {
    @Inject(method = "renderPreviewEntity", remap = false, require = 0, at = @At("HEAD"))
    private void townstead$calmPreviewEntity(
            GuiGraphics context, int x0, int y0, int x1, int y1, int size,
            float mouseX, float mouseY, net.minecraft.world.entity.LivingEntity entity,
            float rotationOffset, CallbackInfo ci
    ) {
        entity.setOnGround(true);
        entity.fallDistance = 0f;
        entity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        // The dummy is loaded to the real villager's coordinates but never ticked, so its
        // position interpolation history keeps the spawn position. EMF lerps pos_y from
        // prev to current with the preview's wall-clock partial tick, so a stale prev makes
        // pos_y sawtooth 0->y every frame; FA's landing detector reads each reset as a fall
        // and replays the landing squash in a loop. MCA's renderPreviewEntity syncs the
        // rotation history (yBodyRotO/yRotO/xRotO) but not position, so sync it here.
        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
        entity.xOld = entity.getX();
        entity.yOld = entity.getY();
        entity.zOld = entity.getZ();
        com.aetherianartificer.townstead.client.animation.EmfPreviewSeed.seedLandingSettled(entity);
    }
    //?}

    // Camera auto-zoom: scale + re-center MCA's preview render toward the body region
    // being edited (EditorPreviewFocus eases in while dragging, back out after). New MCA
    // routes the preview through PreviewEntityAnimation (same 8-arg shape as the old
    // InventoryScreen call); the old-editor MCA (1.20.1) has no renderPreviewEntity, so
    // require = 0 skips it there.
    //? if neoforge {
    @ModifyArgs(method = "renderPreviewEntity", remap = false, require = 0,
            at = @At(value = "INVOKE",
                    target = "Lnet/conczin/mca/client/gui/PreviewEntityAnimation;renderEntityInInventory"))
    private void townstead$zoomPreview(Args args) {
        float zoom = com.aetherianartificer.townstead.client.gui.character.EditorPreviewFocus.zoomNow();
        float height = com.aetherianartificer.townstead.client.gui.character.EditorPreviewFocus.heightNow();
        if (Math.abs(zoom - 1f) < 0.01f && Math.abs(height - 0.5f) < 0.01f) return;
        if (args.size() != 8) return;
        if (!(args.get(7) instanceof net.minecraft.world.entity.LivingEntity entity)) return;
        if (!(args.get(3) instanceof Float scale)) return;
        if (!(args.get(4) instanceof org.joml.Vector3f)) return;
        args.set(3, scale * zoom);
        args.set(4, new org.joml.Vector3f(0f, entity.getBbHeight() * height, 0f));
    }
    //?}

    @Unique
    private void townstead$sendRootSet(int target, String rootId) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new RootSetC2SPayload(target, rootId));
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(
                new RootSetC2SPayload(target, rootId));
        *///?}
    }

    @Unique
    private void townstead$sendCommitGenes(int target, float[] genes) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new com.aetherianartificer.townstead.root.CommitRootGenesC2SPayload(target, genes));
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(
                new com.aetherianartificer.townstead.root.CommitRootGenesC2SPayload(target, genes));
        *///?}
    }
}
