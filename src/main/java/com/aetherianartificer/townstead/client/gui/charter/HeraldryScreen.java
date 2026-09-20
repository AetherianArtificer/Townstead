package com.aetherianartificer.townstead.client.gui.charter;

import com.aetherianartificer.townstead.client.gui.common.*;
import com.aetherianartificer.townstead.politics.charter.*;
import com.aetherianartificer.townstead.politics.heraldry.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import java.util.*;

/** Local drafts never mutate an actor until the server accepts an explicit publication. */
public final class HeraldryScreen extends Screen {
    private final Screen parent;
    private CharterSnapshotS2CPayload snapshot;
    private int scope, left, top, panelW, panelH;
    private EmblemRecipe draft;
    private long baseRevision;
    private boolean pending, conflict;
    private int pendingTicks;
    private String status = "";
    private List<String> patterns = List.of("");
    private int itemSlot = -1;
    private String itemKey = "";
    public HeraldryScreen(Screen parent, CharterSnapshotS2CPayload snapshot) {
        super(tr("title")); this.parent = parent; this.snapshot = snapshot;
        for (int i = 0; i < snapshot.heraldry().size(); i++) if (snapshot.heraldry().get(i).actor().startsWith("polity:")) { scope = i; break; }
        resetDraft();
    }
    private static Component tr(String key, Object... args) { return Component.translatable("charter.townstead.heraldry." + key, args); }
    private CharterSnapshotS2CPayload.Heraldry target() { return snapshot.heraldry().get(scope); }
    private void resetDraft() { draft = EmblemRecipe.safe(target().recipe()); baseRevision = target().revision(); conflict = false; status = ""; }
    private boolean dirty() { return !draft.encode().equals(target().recipe()); }
    public boolean accept(CharterSnapshotS2CPayload incoming) {
        if (!snapshot.lectern().equals(incoming.lectern())) return false;
        String actor = target().actor();
        int next = -1;
        for (int i = 0; i < incoming.heraldry().size(); i++) if (incoming.heraldry().get(i).actor().equals(actor)) next = i;
        if (next < 0) return false;
        snapshot = incoming; if (parent instanceof CharterScreen charter) charter.updateSnapshot(incoming); scope = next; pending = false; status = incoming.message();
        if (draft.encode().equals(target().recipe())) { baseRevision = target().revision(); conflict = false; }
        else if (baseRevision != target().revision()) conflict = true;
        rebuildWidgets(); return true;
    }
    @Override protected void init() {
        panelW = Math.min(560, width - 20); panelH = Math.min(326, height - 20);
        left = (width - panelW) / 2; top = (height - panelH) / 2;
        var ids = new ArrayList<String>(); ids.add("");
        if (minecraft.level != null) ids.addAll(EmblemItems.patterns(minecraft.level.registryAccess()).stream().filter(id -> !id.equals("minecraft:base")).toList());
        patterns = List.copyOf(ids);
        var scopeButton = button(left + 12, top + 30, panelW - 24, target().name().component().copy().append("  >"), () -> guarded(() -> {
            scope = (scope + 1) % snapshot.heraldry().size(); resetDraft(); rebuildWidgets();
        }));
        scopeButton.active = !pending && snapshot.heraldry().size() > 1;
        int controls = Math.min(260, panelW / 2 - 12);
        String[] keys = {"field", "division", "division_color", "symbol", "symbol_color"};
        for (int i = 0; i < 5; i++) {
            final int row = i; int y = top + (panelH < 300 ? 63 + i * (panelW < 400 ? 20 : 25) : 75 + i * 34);
            button(left + 12, y, 20, Component.literal("<"), () -> change(row, -1));
            button(left + 35, y, controls - 46, panelH < 300 ? Component.empty().append(tr(keys[row])).append(": ").append(value(row)) : value(row), () -> change(row, 1));
            button(left + controls - 8, y, 20, Component.literal(">"), () -> change(row, 1));
        }
        int footer = top + panelH - 31;
        int actionFooter = panelW < 400 ? footer - 23 : footer;
        var candidates = itemSlots();
        if (!candidates.contains(itemSlot)) itemSlot = candidates.isEmpty() ? -1 : candidates.get(0);
        itemKey = itemSlot < 0 ? "" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(minecraft.player.getInventory().getItem(itemSlot).getItem()).toString();
        Component itemLabel = itemSlot < 0 ? tr("no_item") : tr("item_target", minecraft.player.getInventory().getItem(itemSlot).getHoverName(), candidates.indexOf(itemSlot) + 1, candidates.size());
        var chooseItem = button(left + 12, actionFooter - 25, panelW - 24, itemLabel, () -> {
            var items = itemSlots();
            if (!items.isEmpty()) itemSlot = items.get(Math.floorMod(items.indexOf(itemSlot) + 1, items.size()));
            rebuildWidgets();
        });
        chooseItem.active = !pending && candidates.size() > 1;
        button(left + 12, footer, 64, Component.translatable("gui.back"), this::onClose);
        button(left + 82, footer, 76, tr("reset"), () -> guarded(() -> { resetDraft(); rebuildWidgets(); })).active = !pending && (dirty() || conflict);
        var publish = button(left + panelW - 226, actionFooter, 102, tr("publish"), () -> {
            minecraft.setScreen(new ConfirmScreen(yes -> { minecraft.setScreen(this); if (yes) send("publish"); }, tr("publish_question"), target().name().component()));
        });
        publish.active = target().editable() && !pending && !conflict && (dirty() || target().revision() == 0);
        var stamp = button(left + panelW - 118, actionFooter, 106, tr("stamp"), () -> {
            final int selectedSlot = itemSlot;
            final String selectedKey = itemKey;
            Component selectedName = minecraft.player.getInventory().getItem(selectedSlot).getHoverName();
            minecraft.setScreen(new ConfirmScreen(yes -> { minecraft.setScreen(this); if (yes) { itemSlot = selectedSlot; itemKey = selectedKey; send("stamp"); } }, tr("stamp_question"), tr("stamp_help", selectedName, target().name().component())));
        });
        stamp.active = itemSlot >= 0 && target().revision() > 0 && !dirty() && !pending && !conflict;
    }
    private List<Integer> itemSlots() {
        if (minecraft == null || minecraft.player == null) return List.of();
        var slots = new ArrayList<Integer>();
        for (int i = 0; i <= 40; i++) if ((i < 36 || i == 40) && EmblemItems.canDecorate(minecraft.player.getInventory().getItem(i))) slots.add(i);
        return slots;
    }
    private CharterButton button(int x, int y, int w, Component text, Runnable action) {
        return addRenderableWidget(new CharterButton(x, y, w, 18, text, b -> action.run()));
    }
    private Component value(int row) {
        return switch (row) {
            case 0 -> color(draft.field()); case 2 -> color(draft.divisionColor()); case 4 -> color(draft.symbolColor());
            case 1 -> pattern(draft.division(), draft.divisionColor()); default -> pattern(draft.symbol(), draft.symbolColor());
        };
    }
    private Component color(int id) { return Component.translatable("color.minecraft." + DyeColor.byId(id).getName()); }
    private Component pattern(String id, int dye) {
        if (id.isEmpty()) return tr("none");
        var key = net.minecraft.resources.ResourceLocation.tryParse(id);
        //? if >=1.21 {
        var pattern = minecraft.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BANNER_PATTERN).get(key);
        if (pattern != null) return Component.translatable(pattern.translationKey() + "." + DyeColor.byId(dye).getName());
        //?}
        return Component.translatable("block." + key.getNamespace() + ".banner." + key.getPath() + "." + DyeColor.byId(dye).getName());
    }
    private String next(String id, int delta) { return patterns.get(Math.floorMod(Math.max(0, patterns.indexOf(id)) + delta, patterns.size())); }
    private void change(int row, int delta) {
        if (pending) return;
        draft = new EmblemRecipe(row == 0 ? Math.floorMod(draft.field() + delta, 16) : draft.field(),
                row == 1 ? next(draft.division(), delta) : draft.division(), row == 2 ? Math.floorMod(draft.divisionColor() + delta, 16) : draft.divisionColor(),
                row == 3 ? next(draft.symbol(), delta) : draft.symbol(), row == 4 ? Math.floorMod(draft.symbolColor() + delta, 16) : draft.symbolColor());
        rebuildWidgets();
    }
    private void send(String operation) {
        pending = true; pendingTicks = 0; status = tr("sending").getString(); rebuildWidgets();
        var payload = new CharterActionC2SPayload(snapshot.lectern(), CharterActionC2SPayload.HERALDRY, "", "", "", operation, target().actor(), operation.equals("stamp") ? itemSlot + "|" + itemKey : draft.encode(), baseRevision);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }
    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        g.fill(0, 0, width, height, 0xC0100C08);
        FrameRenderer.drawInnerPanel(g, left, top, panelW, panelH); FrameRenderer.drawWoodenFrame(g, left, top, panelW, panelH, 6);
        g.drawString(font, title, left + 12, top + 12, 0xFFE8D5A6, false);
        int controls = Math.min(260, panelW / 2 - 12);
        String[] keys = {"field", "division", "division_color", "symbol", "symbol_color"};
        if (panelH >= 300) for (int i = 0; i < 5; i++) g.drawString(font, tr(keys[i]), left + 12, top + 64 + i * 34, 0xFFE8D5A6, false);
        int px = left + controls + 28, pw = panelW - controls - 40;
        g.fill(px, top + 62, px + pw, top + panelH - 100, 0xFFF3E8C8);
        if (minecraft.level != null) {
            var access = minecraft.level.registryAccess();
            preview(g, EmblemItems.banner(access, draft), px + pw / 4 - 24, top + (panelH < 300 ? 70 : 80));
            preview(g, EmblemItems.shield(access, draft), px + pw * 3 / 4 - 24, top + (panelH < 300 ? 70 : 80));
        }
        g.drawString(font, tr("banner"), px + pw / 4 - font.width(tr("banner")) / 2, top + panelH - 114, 0xFF382A1B, false);
        g.drawString(font, tr("shield"), px + pw * 3 / 4 - font.width(tr("shield")) / 2, top + panelH - 114, 0xFF382A1B, false);
        Component hint = conflict ? tr("stale") : !target().editable() ? tr("read_only") : lowContrast() ? tr("contrast") : tr("copy_hint");
        if (!status.isBlank()) hint = Component.literal(status);
        if (status.isBlank() && !conflict && target().revision() == 0) hint = tr("unpublished");
        g.enableScissor(px, top + panelH - 96, px + pw, top + panelH - (panelW < 400 ? 81 : 59));
        g.drawWordWrap(font, hint, px + 8, top + panelH - 94, pw - 16, 0xFFE8D5A6);
        g.disableScissor();
        super.render(g, mx, my, tick);
    }
    private boolean lowContrast() {
        if (draft.symbol().isEmpty()) return false;
        double symbol = luminance(draft.symbolColor());
        return contrast(symbol, luminance(draft.field())) < 3 || (!draft.division().isEmpty() && contrast(symbol, luminance(draft.divisionColor())) < 3);
    }
    private static double contrast(double a, double b) { return (Math.max(a, b) + .05) / (Math.min(a, b) + .05); }
    private static double luminance(int dye) {
        int rgb = DyeColor.byId(dye).getFireworkColor(); double[] channels = new double[3];
        for (int i = 0; i < 3; i++) { double c = ((rgb >> (16 - i * 8)) & 255) / 255.0; channels[i] = c <= .04045 ? c / 12.92 : Math.pow((c + .055) / 1.055, 2.4); }
        return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722;
    }
    private void preview(GuiGraphics g, net.minecraft.world.item.ItemStack stack, int x, int y) {
        g.pose().pushPose(); g.pose().translate(x, y, 0); float scale = panelH < 300 ? 2 : 3; g.pose().scale(scale, scale, scale); g.renderItem(stack, 0, 0); g.pose().popPose();
    }
    private void guarded(Runnable action) {
        if (!dirty()) { action.run(); return; }
        minecraft.setScreen(new ConfirmScreen(yes -> { minecraft.setScreen(this); if (yes) action.run(); }, tr("discard"), tr("discard_help")));
    }
    @Override public void onClose() { if (!pending) guarded(() -> minecraft.setScreen(parent)); }
    @Override public void tick() {
        if (pending && ++pendingTicks > 200) { pending = false; status = tr("timeout").getString(); rebuildWidgets(); }
    }
    // This screen draws its own background before its content. Do not blur that content from super.render.
    //? if >=1.21 {
    @Override public void renderBackground(GuiGraphics g, int x, int y, float partialTick) {}
    //?} else {
    /*@Override public void renderBackground(GuiGraphics g) {}
    *///?}
    @Override public boolean isPauseScreen() { return false; }
}
