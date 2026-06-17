package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.gui.origin.OriginPicker;
import com.aetherianartificer.townstead.client.origin.OriginClientStore;
import com.aetherianartificer.townstead.client.origin.PreviewParticles;
import net.minecraft.client.gui.GuiGraphics;
import com.aetherianartificer.townstead.client.skin.OriginSkinPickerTexture;
import com.aetherianartificer.townstead.client.skin.SkinTintRegistry;
import com.aetherianartificer.townstead.client.species.RigModels;
import com.aetherianartificer.townstead.client.species.RigSkinTone;
import com.aetherianartificer.townstead.mixin.accessor.ColorPickerWidgetAccessor;
import com.aetherianartificer.townstead.origin.GeneCatalogEntry;
import com.aetherianartificer.townstead.origin.GeneRange;
import com.aetherianartificer.townstead.origin.Genome;
import com.aetherianartificer.townstead.origin.SetGeneVariantC2SPayload;
import net.minecraft.network.chat.Component;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import com.aetherianartificer.townstead.origin.OriginGenes;
import com.aetherianartificer.townstead.origin.OriginRegistry;
import com.aetherianartificer.townstead.origin.OriginSetC2SPayload;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Adds an "origins" tab to MCA's Villager Editor. When editing an NPC the picker
 * targets that villager (by network id resolved from its UUID); when editing the
 * player's own model it targets the player ({@link OriginSetC2SPayload#SELF}).
 *
 * <p><b>Live preview:</b> MCA's editor renders/saves a client dummy {@code villager}
 * (the sliders write to it; {@code syncVillagerData} saves it by UUID). Selecting
 * an origin rolls its genome onto that dummy (WYSIWYG), tags it so the skin-tint
 * layer paints it, and marks the preview dirty. The original genes are snapshotted
 * on entering the page and restored whenever the user leaves the tab or MCA saves,
 * <em>unless</em> Apply ran — Apply clears the dirty flag and calls
 * {@code syncVillagerData}, committing the exact previewed genes to the real
 * villager. So browsing never commits; only Apply does.</p>
 */
@Mixin(VillagerEditorScreen.class)
public abstract class VillagerEditorOriginMixin extends Screen {

    @Shadow(remap = false) @Final protected VillagerEntityMCA villager;
    @Shadow(remap = false) @Final UUID villagerUUID;
    @Shadow(remap = false) @Final UUID playerUUID;
    @Shadow(remap = false) public abstract void syncVillagerData();

    @Unique private boolean townstead$previewDirty;
    @Unique private float[] townstead$geneSnapshot;
    @Unique private String townstead$baseOriginId = "";
    @Unique private boolean townstead$primed;
    @Unique private int townstead$target;

    private VillagerEditorOriginMixin() {
        super(null);
    }

    @Inject(method = "getPages", remap = false, at = @At("RETURN"), cancellable = true)
    private void townstead$appendOriginsPage(CallbackInfoReturnable<String[]> cir) {
        if ((Object) this instanceof DestinyScreen) return;
        cir.setReturnValue(OriginPicker.insertOriginsPage(cir.getReturnValue()));
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
        PreviewParticles.render(context, villager, x, y - 75, x + 175, y + 75);
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
        // up the moment the editor opens, not only when the Origins tab is built — otherwise the
        // initial render shows MCA-native (human) skin. The editor renders a throwaway dummy
        // (EntityType.create) whose id differs from the real villager, so we resolve the REAL
        // villager by UUID, read its synced origin, and mirror it onto the dummy's id.
        if (!townstead$primed) {
            townstead$target = villagerUUID.equals(playerUUID)
                    ? OriginSetC2SPayload.SELF
                    : townstead$resolveVillagerEntityId(villagerUUID);
            townstead$sendOriginSet(townstead$target, "");      // also makes the picker row highlight
            townstead$baseOriginId = OriginClientStore.get(townstead$target);
            townstead$geneSnapshot = OriginGenes.snapshot(villager);   // baseline for revert
            townstead$primed = true;
        }
        // Keep the dummy tinted to the real origin on every page. A page change reverts any
        // un-applied preview at HEAD first, so this never clobbers a live preview.
        OriginClientStore.set(villager.getId(), townstead$baseOriginId);
        townstead$previewDirty = false;
        // The editor dummy is a separate entity with no synced face alleles, so without this it
        // renders a different random face (eyes/mouth/colour) than the real villager. Seed the dummy's
        // carried face variants from the real target so the preview matches the game, on every page.
        townstead$seedFaceVariants();

        // On the Body page, repaint MCA's skin color-picker square to the origin's tinted skin
        // field so the picker is WYSIWYG with the rendered villager, and add the tone-variant
        // cycler above it for palette species (a conditional/optional field).
        if ("body".equals(page)) {
            townstead$recolorSkinPicker();
            townstead$addTonePicker();
            townstead$trimInertBodySliders();
        }
        if ("head".equals(page)) {
            townstead$trimInertHair();
            townstead$addFaceCyclers();
        }
        if ("personality".equals(page)) {
            townstead$replacePersonalityButtons();
        }

        if (!"origins".equals(page)) return;

        OriginPicker.Widgets ws = OriginPicker.build(
                Minecraft.getInstance(),
                this.width / 2, this.height / 2 - 80, 175, 185, townstead$target,
                originId -> townstead$applyOrigin(townstead$target, originId),
                entry -> townstead$previewOrigin(townstead$target, entry));
        addRenderableWidget(ws.tabOrigin());
        addRenderableWidget(ws.tabGenes());
        addRenderableWidget(ws.search());
        addRenderableWidget(ws.list());
        addRenderableWidget(ws.description());
        addRenderableWidget(ws.master());
        addRenderableWidget(ws.apply());
    }

    /**
     * Roll the selected origin's genome onto the editor dummy for a live, WYSIWYG preview.
     * Re-selecting the origin the target already is (or the default Overworlder when it has
     * none) must not re-roll: that would randomize the existing genes (e.g. skin tone) for
     * no reason, so instead we drop any in-progress preview back to the dummy's real genes.
     */
    @Unique
    private void townstead$previewOrigin(int target, OriginCatalogEntry entry) {
        String current = OriginClientStore.get(target);
        if (current.isEmpty()) current = OriginRegistry.DEFAULT_ID.toString();
        if (entry.id().equals(current)) {
            townstead$revertPreview();
            return;
        }
        Map<String, GeneRange> ranges = new LinkedHashMap<>();
        for (OriginCatalogEntry.GeneRangeView r : entry.geneRanges()) {
            ranges.put(r.key(), new GeneRange(r.min(), r.max()));
        }
        // Match spawn: roll a fresh random villager first (MCA's own genome roll covers the
        // floats this origin doesn't constrain — base skin melanin/hemoglobin, hair, face,
        // voice), then constrain the genes the origin defines to its ranges. So the preview is
        // a representative member, not the opening skin re-tinted in place. A race can still pin
        // any of these by declaring a body-metric gene for it (apply runs after, so it wins).
        villager.getGenetics().randomize();
        OriginGenes.apply(villager, ranges, villager.getRandom());
        OriginClientStore.set(villager.getId(), entry.id());   // so the skin-tint layer paints the preview
        townstead$previewDirty = true;
    }

    /** Commit the previewed genes (exactly) + the origin id to the real villager. */
    @Unique
    private void townstead$applyOrigin(int target, String originId) {
        townstead$previewDirty = false;            // keep the preview; the revert hooks must not undo it
        townstead$baseOriginId = originId;         // the applied origin is the new baseline, so page changes
                                                   // (which reset the dummy to baseline) and the Body picker
                                                   // both reflect it, not the origin the editor opened with
        townstead$sendOriginSet(target, originId); // sets Life.originId on the real villager
        syncVillagerData();                        // commits the dummy's previewed genes by UUID (WYSIWYG)
    }

    @Unique
    private void townstead$revertPreview() {
        if (!townstead$previewDirty) return;
        OriginGenes.restore(villager, townstead$geneSnapshot);
        OriginClientStore.set(villager.getId(), townstead$baseOriginId);   // back to the real origin's tint
        townstead$previewDirty = false;
    }

    /** Repaint MCA's Body skin-picker square (the one using villager_skin.png) to this origin's tinted skin. */
    @Unique
    private void townstead$recolorSkinPicker() {
        ResourceLocation tex;
        // A palette species (skin_tone with tinted variants) previews its tone hue shaded across the
        // gradient; everything else uses the single-tint origin shift. Same path as the fantasy races.
        int hue = RigSkinTone.paletteHue(villager);
        if (hue >= 0) {
            tex = OriginSkinPickerTexture.forPaletteHue(hue);
        } else {
            java.util.OptionalInt tint = SkinTintRegistry.resolve(villager);
            if (tint.isEmpty()) return;
            tex = OriginSkinPickerTexture.forTint(tint.getAsInt());
        }
        for (GuiEventListener child : children()) {
            // The Body page has two 2-D pickers (skin + hair) and 1-D HSV sliders (a subclass);
            // the skin one is the non-subclass ColorPickerWidget still bound to villager_skin.png.
            if (child instanceof ColorPickerWidget picker
                    && !(child instanceof HorizontalColorPickerWidget)
                    && OriginSkinPickerTexture.isSkinPickerTexture(((ColorPickerWidgetAccessor) picker).townstead$getTexture())) {
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
        String current = OriginClientStore.carriedVariants(townstead$target).get(geneId);
        int start = 0;
        for (int i = 0; i < opts.size(); i++) {
            if (opts.get(i).id().equals(current)) { start = i; break; }
        }
        OriginClientStore.setCarriedVariant(villager.getId(), geneId, opts.get(start).id());
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
            OriginClientStore.setCarriedVariant(villager.getId(), geneId, variantId);   // live preview
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
        com.aetherianartificer.townstead.origin.OriginCatalogEntry origin =
                com.aetherianartificer.townstead.client.origin.OriginCatalogClient.origin(OriginClientStore.get(townstead$target));
        if (origin == null) return out;
        for (com.aetherianartificer.townstead.origin.OriginCatalogEntry.Inherited inh : origin.inheritedGenes()) {
            GeneCatalogEntry g = com.aetherianartificer.townstead.client.origin.OriginCatalogClient.gene(inh.geneId());
            if (g != null && g.isFace() && !g.variants().isEmpty()) out.add(g);
        }
        return out;
    }

    /**
     * Seed the editor dummy's carried face variants from the REAL target, so the previewed face
     * (eyes/mouth/colour) matches what the villager actually shows in-game rather than a fresh per-dummy
     * roll. Runs on every page since the face renders on all of them.
     */
    @Unique
    private void townstead$seedFaceVariants() {
        java.util.Map<String, String> real = OriginClientStore.carriedVariants(townstead$target);
        for (GeneCatalogEntry g : townstead$faceGenes()) {
            String v = real.get(g.id());
            if (v != null && !v.isEmpty()) OriginClientStore.setCarriedVariant(villager.getId(), g.id(), v);
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
        com.aetherianartificer.townstead.origin.rig.RigDefinition rig =
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
        if (genes.isEmpty()) return;
        int rowH = 18;

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
            int delta = genes.size() * (rowH + 1) - faceSlider.getHeight();
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
            y = this.height / 2 - genes.size() * (rowH + 1) / 2;
            w = 110;
        }
        for (GeneCatalogEntry gene : genes) {
            townstead$variantCycler(gene, x, y, w, 16, rowH);
            y += rowH + 1;
        }
    }

    /** A widget's translation key, or null if its label isn't translatable. */
    @Unique
    private String townstead$widgetKey(net.minecraft.client.gui.components.AbstractWidget w) {
        return w.getMessage().getContents() instanceof TranslatableContents tc ? tc.getKey() : null;
    }

    /** Build a {@code [<] Gene: variant [>]} cycler for one variant gene, seeded from the real target. */
    @Unique
    private void townstead$variantCycler(GeneCatalogEntry gene, int x, int y, int w, int arrowW, int rowH) {
        List<GeneCatalogEntry.Variant> opts = gene.variants();
        String geneId = gene.id();
        String current = OriginClientStore.carriedVariants(townstead$target).get(geneId);
        int start = 0;
        for (int i = 0; i < opts.size(); i++) {
            if (opts.get(i).id().equals(current)) { start = i; break; }
        }
        OriginClientStore.setCarriedVariant(villager.getId(), geneId, opts.get(start).id());
        int[] idx = { start };
        int midW = Math.max(20, w - arrowW * 2);
        String name = gene.name();
        ButtonWidget[] mid = new ButtonWidget[1];
        IntConsumer cycle = delta -> {
            idx[0] = Math.floorMod(idx[0] + delta, opts.size());
            GeneCatalogEntry.Variant v = opts.get(idx[0]);
            mid[0].setMessage(Component.literal(name + ": " + v.label()));
            OriginClientStore.setCarriedVariant(villager.getId(), geneId, v.id());   // live preview
            townstead$sendSetVariant(townstead$target, geneId, v.id());              // commit
        };
        addRenderableWidget(new ButtonWidget(x, y, arrowW, rowH, Component.literal("<"), b -> cycle.accept(-1)));
        mid[0] = new ButtonWidget(x + arrowW, y, midW, rowH,
                Component.literal(name + ": " + opts.get(start).label()), b -> { });
        addRenderableWidget(mid[0]);
        addRenderableWidget(new ButtonWidget(x + arrowW + midW, y, arrowW, rowH,
                Component.literal(">"), b -> cycle.accept(1)));
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
                    && OriginSkinPickerTexture.isSkinPickerTexture(((ColorPickerWidgetAccessor) picker).townstead$getTexture())) {
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
        com.aetherianartificer.townstead.calendar.LifeClientStore.Snapshot life =
                com.aetherianartificer.townstead.calendar.LifeClientStore.get(villager.getId());
        if (life == null) return;
        String[] pool = life.personalityPool();
        if (pool.length == 0) return;

        java.util.Set<String> enumNames = new java.util.HashSet<>();
        for (net.conczin.mca.entity.ai.relationship.Personality p
                : net.conczin.mca.entity.ai.relationship.Personality.values()) {
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
                    resolved = net.conczin.mca.entity.ai.relationship.Personality
                            .valueOf(ref.toUpperCase(java.util.Locale.ROOT)).getName().getString();
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
                new com.aetherianartificer.townstead.origin.SetPersonalityC2SPayload(target, ref));
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(
                new com.aetherianartificer.townstead.origin.SetPersonalityC2SPayload(target, ref));
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
        return OriginSetC2SPayload.NONE;
    }

    // Drop this editor's throwaway dummy from the tint cache when the screen goes away,
    // so its origin tint can't bleed onto a real entity sharing its client-side id.
    @Override
    public void removed() {
        super.removed();
        OriginClientStore.remove(villager.getId());
        PreviewParticles.clear();
    }

    @Unique
    private void townstead$sendOriginSet(int target, String originId) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new OriginSetC2SPayload(target, originId));
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(
                new OriginSetC2SPayload(target, originId));
        *///?}
    }
}
