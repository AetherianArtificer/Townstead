package com.aetherianartificer.townstead.client.gui.charter;

import com.aetherianartificer.townstead.client.gui.common.BookRenderer;
import com.aetherianartificer.townstead.client.gui.common.Controls;
import com.aetherianartificer.townstead.politics.charter.CharterActionC2SPayload;
import com.aetherianartificer.townstead.politics.charter.CharterSnapshotS2CPayload;
import com.aetherianartificer.townstead.politics.heraldry.EmblemItems;
import com.aetherianartificer.townstead.politics.heraldry.EmblemRecipe;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.aetherianartificer.townstead.client.gui.common.BookRenderer.*;

/**
 * The heraldry pages of the Charter. An emblem is designed here and added to the amendment; it
 * changes when the bell proclaims it. A proclaimed emblem can be copied onto a banner or shield.
 */
public final class HeraldryScreen extends BookScreen {
    private static final int PREVIEW = 48, PREVIEW_GAP = 32;
    private static final int FIELD = 0, DIVISION = 1, DIVISION_COLOR = 2, SYMBOL = 3, SYMBOL_COLOR = 4;
    private static final String[] KEYS = {"field", "division", "division_color", "symbol", "symbol_color"};

    private final Screen parent;
    private CharterSnapshotS2CPayload snapshot;
    private EmblemRecipe draft;
    private long baseRevision;
    private boolean pending, conflict, proposing, leaveArmed, stampArmed;
    private int pendingTicks;
    private String status = "";
    private List<String> patterns = List.of("");
    private int itemSlot = -1;
    private String itemKey = "";
    private final List<PageZone> valueZones = new ArrayList<>();

    public HeraldryScreen(Screen parent, CharterSnapshotS2CPayload snapshot) {
        super(tr("title"));
        this.parent = parent;
        this.snapshot = snapshot;
        for (int i = 0; i < heraldry(snapshot).size(); i++) {
            if (heraldry(snapshot).get(i).actor().startsWith("faction:")) { tab = i; break; }
        }
        resetDraft();
    }

    private static Component tr(String key, Object... args) { return Component.translatable("charter.townstead.heraldry." + key, args); }

    private static List<CharterSnapshotS2CPayload.Heraldry> heraldry(CharterSnapshotS2CPayload value) {
        return value.book() == null ? List.of() : value.book().heraldry();
    }

    private CharterSnapshotS2CPayload.Heraldry target() { return heraldry(snapshot).get(tab); }

    private void resetDraft() {
        draft = EmblemRecipe.safe(target().recipe());
        baseRevision = target().revision();
        conflict = false;
        status = "";
    }

    private boolean dirty() { return !draft.encode().equals(target().recipe()); }

    private boolean editable() { return target().editable() && !pending; }

    public boolean accept(CharterSnapshotS2CPayload incoming) {
        if (!snapshot.lectern().equals(incoming.lectern())) return false;
        String actor = target().actor();
        int next = -1;
        for (int i = 0; i < heraldry(incoming).size(); i++) if (heraldry(incoming).get(i).actor().equals(actor)) next = i;
        if (next < 0) return false;
        snapshot = incoming;
        if (parent instanceof CharterScreen charter) charter.updateSnapshot(incoming);
        tab = next;
        pending = false;
        status = incoming.message();
        if (proposing) {
            proposing = false;
            minecraft.setScreen(parent);
            return true;
        }
        if (draft.encode().equals(target().recipe())) { baseRevision = target().revision(); conflict = false; }
        else if (baseRevision != target().revision()) conflict = true;
        rebuildWidgets();
        return true;
    }

    @Override protected void init() {
        var ids = new ArrayList<String>();
        ids.add("");
        if (minecraft.level != null) {
            // Vanilla first, then each mod's, by name. A pattern without banner and shield art would draw broken, so it is left out.
            ids.addAll(EmblemItems.patterns(minecraft.level.registryAccess()).stream()
                    .filter(id -> !id.equals("minecraft:base") && hasArt(id))
                    .sorted(java.util.Comparator.comparing((String id) -> !id.startsWith("minecraft:"))
                            .thenComparing(id -> pattern(id, 0).getString()))
                    .toList());
        }
        patterns = List.copyOf(ids);
        super.init();
    }

    @Override protected void compose() {
        valueZones.clear();
        List<Component> labels = new ArrayList<>();
        for (var target : heraldry(snapshot)) labels.add(target.name().component());
        composeTabs(labels, index -> {
            tab = index;
            resetDraft();
            leaveArmed = stampArmed = false;
            rebuildWidgets();
        }, !dirty() && !pending);
        composeDesign();
        composePreview();
        composeBar();
    }

    @Override protected String status() { return status; }

    /** The left page: the five parts of the emblem, then the item it can be copied onto. */
    private void composeDesign() {
        Controls.Rect p = leftPage;
        int x = x(p), w = w(p), y = y(p);
        runningHead(p, CharterScreen.tr("running_head", snapshot.settlement()), false);
        y = heading(tr("design"), x, y, w);
        for (int part = 0; part < KEYS.length; part++) {
            int index = part;
            PageZone zone = rowZone(tr(KEYS[part]), value(part), x, y, w, INK, editable() ? anchor -> openPartMenu(index, anchor) : null);
            valueZones.add(zone);
            y += ROW;
        }
        y += SECTION;
        y = heading(tr("apply_heading"), x, y, w);
        var slots = itemSlots();
        if (!slots.contains(itemSlot)) itemSlot = slots.isEmpty() ? -1 : slots.get(0);
        itemKey = itemSlot < 0 ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack(itemSlot).getItem()).toString();
        if (itemSlot < 0) {
            plain(tr("no_item"), x, y, w, FADED);
            return;
        }
        row(tr("item"), stack(itemSlot).getHoverName(), x, y, w, INK, slots.size() > 1 && !pending ? this::openItemMenu : null);
    }

    /** The right page: the design as a banner and as a shield, and what stands in its way. */
    private void composePreview() {
        Controls.Rect p = rightPage;
        int x = x(p), w = w(p), y = y(p);
        runningHead(p, target().name().component(), true);
        int bannerX = x + (w - 2 * PREVIEW - PREVIEW_GAP) / 2;
        int shieldX = bannerX + PREVIEW + PREVIEW_GAP;
        int captionY = y + PREVIEW + HEAD_GAP;
        String banner = tr("banner").getString(), shield = tr("shield").getString();
        op(g -> {
            if (minecraft.level == null) return;
            var access = minecraft.level.registryAccess();
            preview(g, EmblemItems.banner(access, draft), bannerX, y);
            preview(g, EmblemItems.shield(access, draft), shieldX, y);
            g.drawString(font, banner, bannerX + (PREVIEW - font.width(banner)) / 2, captionY, FADED, false);
            g.drawString(font, shield, shieldX + (PREVIEW - font.width(shield)) / 2, captionY, FADED, false);
        });
        Component hint;
        int color = FADED;
        if (conflict) { hint = tr("stale"); color = RUBRIC; }
        else if (!target().editable()) hint = tr("read_only");
        else if (lowContrast()) { hint = tr("contrast"); color = RUBRIC; }
        else if (target().revision() == 0) hint = tr("unpublished");
        else hint = Component.empty();
        foot(p, hint, color);
    }

    private void composeBar() {
        Bar bar = new Bar();
        bar.left(leaveArmed ? tr("discard") : Component.translatable("gui.back"), this::leave).active = !pending;
        if (dirty() || conflict) {
            bar.left(tr("reset"), () -> { resetDraft(); leaveArmed = stampArmed = false; rebuildWidgets(); }).active = !pending;
        }
        bar.right(tr("add_to_draft"), () -> { proposing = true; send("heraldry"); }).active =
                target().editable() && !pending && !conflict && (dirty() || target().revision() == 0);
        Component stampLabel = stampArmed && itemSlot >= 0 ? tr("stamp_confirm", stack(itemSlot).getHoverName()) : tr("stamp");
        bar.right(stampLabel, this::stamp).active = itemSlot >= 0 && target().revision() > 0 && !dirty() && !pending && !conflict;
    }

    /** Back asks once before throwing away an unproposed design. */
    private void leave() {
        if (pending) return;
        if (dirty() && !leaveArmed) {
            leaveArmed = true;
            rebuildWidgets();
            return;
        }
        minecraft.setScreen(parent);
    }

    /** Copying onto an item replaces its patterns, so the button asks once, naming the item. */
    private void stamp() {
        if (!stampArmed) {
            stampArmed = true;
            rebuildWidgets();
            return;
        }
        stampArmed = false;
        send("stamp");
    }

    private void openPartMenu(int part, Controls.Rect anchor) {
        List<DropMenu.Entry> entries = new ArrayList<>();
        boolean colour = part == FIELD || part == DIVISION_COLOR || part == SYMBOL_COLOR;
        if (colour) {
            int current = part == FIELD ? draft.field() : part == DIVISION_COLOR ? draft.divisionColor() : draft.symbolColor();
            for (int dye = 0; dye < 16; dye++) {
                int value = dye;
                entries.add(new DropMenu.Entry(color(dye), () -> { menu = null; set(part, value, ""); }, false, dye == current,
                        0xFF000000 | DyeColor.byId(dye).getFireworkColor()));
            }
        } else {
            String current = part == DIVISION ? draft.division() : draft.symbol();
            int dye = part == DIVISION ? draft.divisionColor() : draft.symbolColor();
            for (String id : patterns) {
                entries.add(new DropMenu.Entry(pattern(id, dye), () -> { menu = null; set(part, 0, id); }, false, id.equals(current), 0));
            }
        }
        menu = menuBelow(tr(KEYS[part]), entries, anchor);
    }

    private void openItemMenu(Controls.Rect anchor) {
        List<DropMenu.Entry> entries = new ArrayList<>();
        for (int slot : itemSlots()) {
            entries.add(new DropMenu.Entry(stack(slot).getHoverName(), () -> {
                menu = null;
                itemSlot = slot;
                stampArmed = false;
                rebuildWidgets();
            }, false, slot == itemSlot, 0));
        }
        menu = menuBelow(tr("item"), entries, anchor);
    }

    private void set(int part, int dye, String pattern) {
        draft = new EmblemRecipe(part == FIELD ? dye : draft.field(),
                part == DIVISION ? pattern : draft.division(), part == DIVISION_COLOR ? dye : draft.divisionColor(),
                part == SYMBOL ? pattern : draft.symbol(), part == SYMBOL_COLOR ? dye : draft.symbolColor());
        leaveArmed = stampArmed = false;
        rebuildWidgets();
    }

    /** The wheel over a value steps through its choices. */
    @Override protected boolean scrolled(double mx, double my, double delta) {
        if (!editable()) return false;
        for (int part = 0; part < valueZones.size(); part++) {
            PageZone zone = valueZones.get(part);
            if (zone == null || !zone.isMouseOver(mx, my)) continue;
            int step = delta > 0 ? -1 : 1;
            switch (part) {
                case FIELD -> set(part, Math.floorMod(draft.field() + step, 16), "");
                case DIVISION_COLOR -> set(part, Math.floorMod(draft.divisionColor() + step, 16), "");
                case SYMBOL_COLOR -> set(part, Math.floorMod(draft.symbolColor() + step, 16), "");
                case DIVISION -> set(part, 0, next(draft.division(), step));
                default -> set(part, 0, next(draft.symbol(), step));
            }
            return true;
        }
        return false;
    }

    private String next(String id, int delta) {
        return patterns.get(Math.floorMod(Math.max(0, patterns.indexOf(id)) + delta, patterns.size()));
    }

    private List<Integer> itemSlots() {
        if (minecraft == null || minecraft.player == null) return List.of();
        var slots = new ArrayList<Integer>();
        for (int i = 0; i <= 40; i++) if ((i < 36 || i == 40) && EmblemItems.canDecorate(stack(i))) slots.add(i);
        return slots;
    }

    private ItemStack stack(int slot) { return minecraft.player.getInventory().getItem(slot); }

    private Component value(int part) {
        return switch (part) {
            case FIELD -> color(draft.field());
            case DIVISION_COLOR -> color(draft.divisionColor());
            case SYMBOL_COLOR -> color(draft.symbolColor());
            case DIVISION -> pattern(draft.division(), draft.divisionColor());
            default -> pattern(draft.symbol(), draft.symbolColor());
        };
    }

    private static Component color(int id) { return Component.translatable("color.minecraft." + DyeColor.byId(id).getName()); }

    private Component pattern(String id, int dye) {
        if (id.isEmpty()) return tr("none");
        var key = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (key == null) return Component.literal(id);
        String translation = "block." + key.getNamespace() + ".banner." + key.getPath();
        //? if >=1.21 {
        var pattern = minecraft.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN).get(key);
        if (pattern != null) translation = pattern.translationKey();
        //?}
        var language = net.minecraft.locale.Language.getInstance();
        String coloured = translation + "." + DyeColor.byId(dye).getName();
        if (language.has(coloured)) {
            // Colour has its own row, so the pattern is named without it: "Yellow Pale" reads "Pale".
            String full = language.getOrDefault(coloured);
            String colour = language.getOrDefault("color.minecraft." + DyeColor.byId(dye).getName());
            int at = full.toLowerCase(java.util.Locale.ROOT).indexOf(colour.toLowerCase(java.util.Locale.ROOT));
            if (at < 0 || colour.isBlank()) return Component.literal(full);
            String bare = (full.substring(0, at) + full.substring(at + colour.length())).trim().replaceAll("\\s+", " ");
            return Component.literal(bare.isEmpty() ? full : Character.toUpperCase(bare.charAt(0)) + bare.substring(1));
        }
        // Mods often name a pattern only on its item. Fall back to its id, read as words.
        String path = key.getPath().substring(Math.max(key.getPath().lastIndexOf('/'), key.getPath().lastIndexOf('.')) + 1);
        StringBuilder words = new StringBuilder();
        for (String part : path.split("_")) {
            if (part.isEmpty()) continue;
            if (!words.isEmpty()) words.append(' ');
            words.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return Component.literal(words.toString());
    }

    private boolean hasArt(String id) {
        var key = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (key == null) return false;
        var asset = key;
        //? if >=1.21 {
        var pattern = minecraft.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN).get(key);
        if (pattern != null) asset = pattern.assetId();
        //?}
        return present(net.minecraft.client.renderer.Sheets.SHIELD_SHEET, asset.withPrefix("entity/shield/"))
                && present(net.minecraft.client.renderer.Sheets.BANNER_SHEET, asset.withPrefix("entity/banner/"));
    }

    private boolean present(net.minecraft.resources.ResourceLocation atlas, net.minecraft.resources.ResourceLocation sprite) {
        var found = minecraft.getTextureAtlas(atlas).apply(sprite);
        return !found.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation());
    }

    private boolean lowContrast() {
        if (draft.symbol().isEmpty()) return false;
        double symbol = luminance(draft.symbolColor());
        return contrast(symbol, luminance(draft.field())) < 3
                || (!draft.division().isEmpty() && contrast(symbol, luminance(draft.divisionColor())) < 3);
    }

    private static double contrast(double a, double b) { return (Math.max(a, b) + .05) / (Math.min(a, b) + .05); }

    private static double luminance(int dye) {
        int rgb = DyeColor.byId(dye).getFireworkColor();
        double[] channels = new double[3];
        for (int i = 0; i < 3; i++) {
            double c = ((rgb >> (16 - i * 8)) & 255) / 255.0;
            channels[i] = c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4);
        }
        return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722;
    }

    private void preview(GuiGraphics g, ItemStack stack, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(3, 3, 3);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private void send(String operation) {
        pending = true;
        pendingTicks = 0;
        status = tr("sending").getString();
        rebuildWidgets();
        int action = operation.equals("stamp") ? CharterActionC2SPayload.HERALDRY : CharterActionC2SPayload.DRAFT;
        var payload = new CharterActionC2SPayload(snapshot.lectern(), action, "", "", "", operation, target().actor(),
                operation.equals("stamp") ? itemSlot + "|" + itemKey : draft.encode(), baseRevision);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    @Override protected boolean escape() {
        if (stampArmed) { stampArmed = false; rebuildWidgets(); return true; }
        leave();
        return true;
    }

    @Override public void onClose() { leave(); }

    @Override public void tick() {
        if (pending && ++pendingTicks > 200) {
            pending = false;
            status = tr("timeout").getString();
            rebuildWidgets();
        }
    }

    @Override public Component getNarrationMessage() {
        return Component.empty().append(title).append(". ").append(target().name().component());
    }
}
