package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.gui.origin.OriginPicker;
import com.aetherianartificer.townstead.client.origin.OriginClientStore;
import com.aetherianartificer.townstead.client.skin.OriginSkinPickerTexture;
import com.aetherianartificer.townstead.client.skin.SkinTintRegistry;
import com.aetherianartificer.townstead.mixin.accessor.ColorPickerWidgetAccessor;
import com.aetherianartificer.townstead.origin.GeneRange;
import com.aetherianartificer.townstead.origin.Genome;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import com.aetherianartificer.townstead.origin.OriginGenes;
import com.aetherianartificer.townstead.origin.OriginRegistry;
import com.aetherianartificer.townstead.origin.OriginSetC2SPayload;
import net.conczin.mca.client.gui.DestinyScreen;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.client.gui.widget.ColorPickerWidget;
import net.conczin.mca.client.gui.widget.HorizontalColorPickerWidget;
import net.conczin.mca.entity.VillagerEntityMCA;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

        // On the Body page, repaint MCA's skin color-picker square to the origin's tinted skin
        // field so the picker is WYSIWYG with the rendered villager.
        if ("body".equals(page)) townstead$recolorSkinPicker();

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
        java.util.OptionalInt tint = SkinTintRegistry.resolve(villager);
        if (tint.isEmpty()) return;
        ResourceLocation tex = OriginSkinPickerTexture.forTint(tint.getAsInt());
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
