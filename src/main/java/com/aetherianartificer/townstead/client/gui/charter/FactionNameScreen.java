package com.aetherianartificer.townstead.client.gui.charter;

import com.aetherianartificer.townstead.client.gui.common.*;
import com.aetherianartificer.townstead.politics.charter.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class FactionNameScreen extends Screen {
    private final CharterScreen parent;
    private CharterSnapshotS2CPayload snapshot;
    private String draft, expected, status = "";
    private boolean pending, conflict;
    private int pendingTicks, x, y, w;
    private CharterButton save;
    public FactionNameScreen(CharterScreen parent, CharterSnapshotS2CPayload snapshot) {
        super(tr("rename")); this.parent = parent; this.snapshot = snapshot; draft = expected = snapshot.polity();
    }
    private static Component tr(String key, Object... args) { return Component.translatable("charter.townstead.identity." + key, args); }
    private CharterSnapshotS2CPayload.Heraldry actor() { return snapshot.heraldry().stream().filter(v -> v.actor().startsWith("polity:")).findFirst().orElse(null); }
    public boolean accept(CharterSnapshotS2CPayload value) {
        if (!snapshot.lectern().equals(value.lectern()) || value.heraldry().isEmpty()) return false;
        boolean submitted = pending; snapshot = value; parent.updateSnapshot(value); pending = false; status = value.message();
        if (submitted && value.polity().equals(CharterIdentityService.normalize(draft))) { minecraft.setScreen(parent); return true; }
        conflict = !expected.equals(value.polity()); rebuildWidgets(); return true;
    }
    @Override protected void init() {
        w = Math.min(280, width - 24); x = (width - w) / 2; y = (height - 126) / 2;
        var field = addRenderableWidget(new PaperField(font, x + 16, y + 47, w - 32, 10, tr("faction_name")));
        field.setMaxLength(48); field.setValue(draft); field.setResponder(value -> { draft = value; updateSave(); });
        field.setEditable(!pending); setInitialFocus(field);
        addRenderableWidget(new CharterButton(x + 12, y + 97, 66, 20, Component.translatable("gui.cancel"), b -> onClose()));
        if (conflict) addRenderableWidget(new CharterButton(x + 82, y + 97, 82, 20, tr("reload"), b -> {
            draft = expected = snapshot.polity(); conflict = false; status = ""; rebuildWidgets();
        })).active = !pending;
        save = addRenderableWidget(new CharterButton(x + w - 104, y + 97, 92, 20, tr("save"), b -> submit()));
        updateSave();
    }
    private void updateSave() {
        if (save != null) save.active = !pending && !conflict && actor() != null && actor().editable()
                && CharterIdentityService.normalize(draft) != null && !draft.equals(snapshot.polity());
    }
    private void submit() {
        if (!save.active) return;
        pending = true; pendingTicks = 0; status = tr("saving").getString(); updateSave();
        var intent = new CharterActionC2SPayload(snapshot.lectern(), CharterActionC2SPayload.IDENTITY,
                draft, "", "", "rename_polity", actor().actor(), expected, 0);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(intent);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(intent);
        *///?}
    }
    @Override public void render(GuiGraphics g, int mx, int my, float tick) {
        g.fill(0, 0, width, height, 0xC0100C08);
        FrameRenderer.drawInnerPanel(g, x, y, w, 126); FrameRenderer.drawWoodenFrame(g, x, y, w, 126, 6);
        g.fill(x + 10, y + 10, x + w - 10, y + 89, 0xFFF3E8C8);
        g.drawString(font, title, x + 16, y + 17, 0xFF382A1B, false);
        g.drawString(font, tr("faction_name"), x + 16, y + 32, 0xFF6E5635, false);
        Component note = conflict ? tr("stale") : !status.isBlank() ? Component.literal(status) : tr("scope", snapshot.settlement());
        g.enableScissor(x + 13, y + 63, x + w - 13, y + 87);
        g.drawWordWrap(font, note, x + 16, y + 65, w - 32, conflict || !status.isBlank() ? 0xFF8A391D : 0xFF6E5635);
        g.disableScissor();
        super.render(g, mx, my, tick);
    }
    @Override public void tick() { if (pending && ++pendingTicks > 200) { pending = false; status = tr("timeout").getString(); updateSave(); } }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
    //? if >=1.21 {
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float tick) {}
    //?} else {
    /*@Override public void renderBackground(GuiGraphics g) {}
    *///?}
}
