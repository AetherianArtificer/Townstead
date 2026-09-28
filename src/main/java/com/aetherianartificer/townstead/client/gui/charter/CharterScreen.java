package com.aetherianartificer.townstead.client.gui.charter;

import com.aetherianartificer.townstead.client.gui.common.BookRenderer;
import com.aetherianartificer.townstead.client.gui.common.Controls;
import com.aetherianartificer.townstead.client.gui.common.InkSeal;
import com.aetherianartificer.townstead.client.seal.ClientSeal;
import com.aetherianartificer.townstead.client.gui.common.LineField;
import com.aetherianartificer.townstead.client.gui.common.Palette;
import com.aetherianartificer.townstead.client.gui.common.Signet;
import com.aetherianartificer.townstead.politics.charter.CharterActionC2SPayload;
import com.aetherianartificer.townstead.politics.charter.CharterIdentityService;
import com.aetherianartificer.townstead.politics.charter.CharterSnapshotS2CPayload;
import com.aetherianartificer.townstead.politics.charter.CharterSnapshotS2CPayload.Book;
import com.aetherianartificer.townstead.politics.heraldry.EmblemItems;
import com.aetherianartificer.townstead.politics.heraldry.EmblemRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static com.aetherianartificer.townstead.client.gui.common.BookRenderer.*;

/**
 * The Charter: a bound book on the lectern. Its pages are for reading and for writing on dotted
 * lines; choosing a row puts what can be done with it on the desk below. Nothing changes at once:
 * changes gather in an amendment, the signers press their seals on it, and the bell proclaims it.
 */
public final class CharterScreen extends BookScreen {
    private static final int CHARTER = 0, MEMBERS = 1;
    /** A signature: the field a seal lands in, its line, and the caption under the line. */
    /** A signature row: the stamp, then the name on its line and the office and date under it. */
    private static final int SIG_H = 34, SIG_TEXT = 30;
    private static final int EMBLEM = 48;
    private static final int WAX = InkSeal.SIZE;
    /** One line of the name at twice the size. */
    private static final int NAME_LINE = 18;
    private static final int LEGEND_MAX = 4;
    private static final int PAST_MAX = 5;

    private CharterSnapshotS2CPayload snapshot;
    private int cultureIndex, rosterPage, refreshTicks;
    private @Nullable String selection;
    private boolean renaming, customFaction;
    private String renameDraft = "", draftName = "", draftFaction = "", factionBase = "", factionPattern = "{name}";
    private final Set<String> selectable = new HashSet<>();
    private Signet signet;
    private @Nullable Controls.Rect sealTarget, signetRest;
    private boolean sealPending;
    private @Nullable CharterButton prepareButton;

    private CharterScreen(CharterSnapshotS2CPayload snapshot) {
        super(Component.translatable("charter.townstead.title"));
        this.snapshot = snapshot;
    }

    static Component tr(String key, Object... args) { return Component.translatable("charter.townstead." + key, args); }

    public static void accept(CharterSnapshotS2CPayload snapshot) {
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.POLITICS)) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        if (minecraft.screen instanceof HeraldryScreen editor && editor.accept(snapshot)) return;
        if (minecraft.screen instanceof CharterScreen screen && screen.snapshot.lectern().equals(snapshot.lectern())) {
            screen.updateSnapshot(snapshot);
            screen.rebuildWidgets();
        } else {
            minecraft.setScreen(new CharterScreen(snapshot));
        }
    }

    void updateSnapshot(CharterSnapshotS2CPayload value) {
        if (!value.settlement().isBlank() && value.state() != CharterSnapshotS2CPayload.UNFOUNDED) draftName = value.settlement();
        if (value.book() == null || !value.book().mayDraft()) renaming = false;
        // The server's word replaces the local impression either way: signed, or refused with a reason.
        sealPending = false;
        snapshot = value;
    }

    private @Nullable Book charter() { return snapshot == null ? null : snapshot.book(); }

    @Override protected void init() {
        if (signet == null) signet = new Signet(font);
        super.init();
    }

    @Override protected void compose() {
        selectable.clear();
        prepareButton = null;
        sealTarget = null;
        signetRest = null;
        if (snapshot == null) return;
        switch (snapshot.state()) {
            case CharterSnapshotS2CPayload.UNFOUNDED -> composeFounding();
            case CharterSnapshotS2CPayload.PREPARED -> composePrepared();
            case CharterSnapshotS2CPayload.EXISTING -> composeExisting();
            case CharterSnapshotS2CPayload.FOUNDED, CharterSnapshotS2CPayload.REPAIR -> {
                if (charter() != null) composeBook(charter());
                else composePrepared();
            }
            default -> composeUnavailable();
        }
    }

    @Override protected String status() { return snapshot == null ? "" : snapshot.message(); }

    private void select(String key) {
        selection = key.equals(selection) ? null : key;
        menu = null;
        rebuildWidgets();
    }

    // ── The founded book ──

    private void composeBook(Book b) {
        long open = b.requests().stream().filter(r -> isOpen(r.state())).count();
        composeTabs(List.of(tr("tab.charter"), open > 0 ? tr("tab.members_count", open) : tr("tab.members")), index -> {
            tab = index;
            selection = null;
            menu = null;
            renaming = false;
            rebuildWidgets();
        }, true);
        if (tab == MEMBERS) {
            composeStanding(b);
            composeResidents(b);
        } else {
            composeArticles(b);
            composeGovernment(b);
        }
        if (selection != null && !selectable.contains(selection)) selection = null;
        composeBookBar(b);
    }

    /** The Charter's left page: emblem, name, when it was proclaimed, and its articles. */
    private void composeArticles(Book b) {
        Controls.Rect p = leftPage;
        int x = x(p), w = w(p), y = y(p);
        runningHead(p, tr("running_head", snapshot.settlement()), false);
        var civicRows = b.civic();
        int below = SECTION + HEADING + ROW;
        Component welcomed = welcomedNames(b);
        if (welcomed != null) below += ROW;
        if (civicRows != null && civicRows.controlsGovernment()) below += ROW;
        else {
            below += ROW + detailHeight(b.seat().detail().component(), w);
            if (b.legitimacy() != null) below += ROW + detailHeight(b.legitimacyDetail().component(), w);
        }
        Title title = fit(bottom(p) - y, nameHeight(snapshot.faction(), w), BookRenderer.lines(font, b.proclaimed().component(), w, 2), below);
        if (title.emblem() > 0) {
            Controls.Rect emblem = new Controls.Rect(x + (w - title.emblem()) / 2, y, title.emblem(), title.emblem());
            PageZone emblemZone = b.heraldry().isEmpty() ? null : zone(emblem, tr("change.emblem"), this::openHeraldry);
            op(g -> drawEmblem(g, b, emblem, emblemZone != null && emblemZone.hot()));
            y += title.emblem() + HEAD_GAP;
        }
        y = composeName(snapshot.faction(), x, y, w, b.mayDraft());
        y = centredDetail(b.proclaimed().component(), x, y, w, title.proclamation());
        y += SECTION;
        y = heading(tr("articles"), x, y, w);
        y = row(tr("row.form"), snapshot.form().component(), x, y, w, INK, null);
        var civic = b.civic();
        if (civic != null && civic.controlsGovernment()) {
            row(tr("row.standing"), civic.standing().component(), x, y, w, INK, null);
            return;
        }
        Consumer<Controls.Rect> move = b.mayDraft() && b.seat().mayMoveHere() ? rect -> draft("seat", "", "") : null;
        y = row(tr("row.seat"), b.seat().value().component(), x, y, w, b.seat().damaged() ? RUBRIC : INK, move);
        y = detail(b.seat().detail().component(), x, y, w, b.seat().damaged() ? RUBRIC : FADED);
        if (b.legitimacy() != null) {
            y = row(tr("row.legitimacy"), b.legitimacy().component(), x, y, w, INK, null);
            y = detail(b.legitimacyDetail().component(), x, y, w, FADED);
        }
        if (welcomed != null) row(tr("row.welcomes"), welcomed, x, y, w, INK, null);
    }

    /** The groups the faction welcomes, as one line; null when it welcomes none. */
    private @Nullable Component welcomedNames(Book b) {
        MutableComponent out = null;
        for (var welcome : b.welcomes()) {
            if (!welcome.welcomed()) continue;
            if (out == null) out = welcome.name().component().copy();
            else out.append(", ").append(welcome.name().component());
        }
        return out;
    }

    /** What the title block may take: the emblem's size (0 for none) and the proclamation's lines. */
    private record Title(int emblem, int proclamation) {}

    /**
     * Fits the title block to a small page. With room short, the emblem shrinks from three times to
     * twice its size, the proclamation drops to one line, and then the emblem goes. The name and
     * whatever {@code below} reserves always stay. Scaling the page instead would blur its type.
     */
    private Title fit(int available, int nameH, int proclamation, int below) {
        int room = available - below - nameH;
        int[][] steps = {{EMBLEM, proclamation}, {32, proclamation}, {32, Math.min(1, proclamation)}, {32, 0}, {0, 0}};
        for (int[] step : steps) {
            int emblemH = step[0] == 0 ? 0 : step[0] + HEAD_GAP;
            if (emblemH + step[1] * DETAIL <= room) return new Title(step[0], step[1]);
        }
        return new Title(0, 0);
    }

    private int plainEmblem(Title title, int x, int y, int w) {
        if (title.emblem() == 0) return y;
        Controls.Rect emblem = new Controls.Rect(x + (w - title.emblem()) / 2, y, title.emblem(), title.emblem());
        op(g -> drawEmblem(g, null, emblem, false));
        return y + title.emblem() + HEAD_GAP;
    }

    private int nameHeight(String name, int w) {
        return renaming ? LineField.height(2) + HEAD_GAP : nameLines(name, w).size() * NAME_LINE + 2 + HEAD_GAP;
    }

    private int detailHeight(Component text, int w) {
        int lines = BookRenderer.lines(font, text, w, 2);
        return lines == 0 ? 0 : DETAIL_GAP + lines * DETAIL;
    }

    /** The lowest line a page can hold. */
    private static int bottom(Controls.Rect page) {
        return BookRenderer.foot(page) + 9;
    }

    /**
     * The name, twice the size of everything else and centred. It wraps to a second line before it
     * is cut. An amender writes a new one straight onto its line.
     */
    private int composeName(String name, int x, int y, int w, boolean editable) {
        if (renaming) {
            LineField field = addRenderableWidget(new LineField(font, x, y, w, tr("faction_name"), 2, true));
            field.setMaxLength(48);
            field.setValue(renameDraft);
            field.setResponder(value -> renameDraft = value);
            setInitialFocus(field);
            setFocused(field);
            field.setCursorPosition(renameDraft.length());
            field.setHighlightPos(0);
            return y + LineField.height(2) + HEAD_GAP;
        }
        List<String> lines = nameLines(name, w);
        int h = lines.size() * NAME_LINE;
        int widest = lines.stream().mapToInt(line -> font.width(line) * 2).max().orElse(0);
        PageZone zone = editable ? zone(new Controls.Rect(x, y, w, h + 2), tr("change.rename"), this::startRename) : null;
        op(g -> {
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                g.pose().pushPose();
                g.pose().translate(x + (w - font.width(line) * 2) / 2f, y + i * NAME_LINE, 0);
                g.pose().scale(2, 2, 1);
                g.drawString(font, line, 0, 0, Palette.INK_HEADER, false);
                g.pose().popPose();
            }
            if (zone != null) BookRenderer.line(g, x + (w - widest) / 2, y + h + 1, widest, zone.hot(), false);
        });
        return y + h + 2 + HEAD_GAP;
    }

    private List<String> nameLines(String name, int w) {
        int room = w / 2;
        if (font.width(name) <= room) return List.of(name);
        List<String> out = new ArrayList<>();
        for (var line : font.getSplitter().splitLines(name, room, net.minecraft.network.chat.Style.EMPTY)) {
            out.add(line.getString().strip());
        }
        if (out.size() > 2) out = List.of(out.get(0), BookRenderer.fit(font, out.get(1) + " " + out.get(2), room));
        return out;
    }

    /** The Charter's right page: who governs, and the amendment when there is one. */
    private void composeGovernment(Book b) {
        Controls.Rect p = rightPage;
        int x = x(p), w = w(p), y = y(p);
        runningHead(p, Component.literal(snapshot.faction()), true);
        var draft = b.draft();
        boolean urgent = snapshot.state() == CharterSnapshotS2CPayload.REPAIR || draft != null && draft.prepared();
        int footTop = foot(p, footText(b), urgent ? RUBRIC : FADED);
        int limit = footTop - SECTION;
        var civic = b.civic();
        if (civic != null && civic.controlsGovernment()) {
            y = heading(civic.governance().component(), x, y, w);
            for (var office : b.offices()) {
                if (y + ROW > limit) break;
                y = row(office.name().component(), holders(office), x, y, w, INK, null);
            }
            return;
        }
        y = heading(tr("government"), x, y, w);
        List<SeatLine> lines = seatLines(b);
        if (lines.isEmpty()) {
            y = plain(tr("offices.none"), x, y, w, FADED);
        } else {
            int fit = Math.max(1, (limit - y - (draft == null ? 0 : draftHeight(draft))) / ROW);
            int shown = lines.size() <= fit ? lines.size() : Math.max(1, fit - 1);
            for (int i = 0; i < shown; i++) y = seatRow(b, lines.get(i), "seat:" + i, x, y, w);
            if (shown < lines.size()) y = plain(tr("offices.more", lines.size() - shown), x, y, w, FADED);
        }
        if (draft != null) composeDraft(b, draft, x, y + SECTION, w);
    }

    private static Component holders(CharterSnapshotS2CPayload.Office office) {
        if (office.holders().isEmpty()) return tr("offices.vacant");
        var out = Component.empty();
        for (var holder : office.holders()) {
            if (!out.getSiblings().isEmpty()) out.append(", ");
            out.append(holder.name().component());
        }
        return out;
    }

    private record SeatLine(CharterSnapshotS2CPayload.Office office, @Nullable CharterSnapshotS2CPayload.Holder holder) {}

    /** One line per seat. A person who holds a higher office is shown there only. */
    private static List<SeatLine> seatLines(Book b) {
        List<SeatLine> out = new ArrayList<>();
        Set<String> shown = new HashSet<>();
        for (var office : b.offices()) {
            for (var holder : office.holders()) {
                if (!holder.id().isEmpty() && !shown.add(holder.id())) continue;
                out.add(new SeatLine(office, holder));
            }
            int vacancies = Math.max(0, office.minimum() - office.holders().size());
            if (office.holders().isEmpty() && vacancies == 0) vacancies = 1;
            for (int i = 0; i < vacancies; i++) out.add(new SeatLine(office, null));
        }
        return out;
    }

    private int seatRow(Book b, SeatLine line, String key, int x, int y, int w) {
        boolean choosable = b.mayDraft() && line.office().editable();
        if (choosable) selectable.add(key);
        PageZone zone = choosable ? zone(new Controls.Rect(x - 4, y, w + 8, ROW), line.office().name().component(), () -> select(key)) : null;
        String label = line.office().name().component().getString();
        String value = line.holder() == null ? tr("offices.vacant").getString() : line.holder().name().component().getString();
        int color = line.holder() == null ? FADED : INK;
        op(g -> {
            BookRenderer.rowGround(g, x, y, w, ROW, key.equals(selection), zone != null && zone.hot());
            BookRenderer.leader(g, font, label, value, x, y, w, INK, color);
        });
        return y + ROW;
    }

    private int clauseHeight(CharterSnapshotS2CPayload.Clause clause) {
        return ROW + (clause.detail().component().getString().isBlank() ? 0 : DETAIL_GAP + DETAIL);
    }

    private int draftHeight(CharterSnapshotS2CPayload.Draft draft) {
        int h = SECTION + HEADING;
        for (var clause : draft.clauses()) h += clauseHeight(clause);
        return h + SECTION + HEADING + draft.signers().size() * SIG_H;
    }

    /** The amendment: its clauses, numbered, then a signature line for each signer. */
    private void composeDraft(Book b, CharterSnapshotS2CPayload.Draft draft, int x, int y, int w) {
        y = heading(tr("amendment"), x, y, w);
        for (int i = 0; i < draft.clauses().size(); i++) {
            var clause = draft.clauses().get(i);
            String key = "clause:" + i;
            int h = clauseHeight(clause);
            if (b.mayDraft()) selectable.add(key);
            PageZone zone = b.mayDraft() ? zone(new Controls.Rect(x - 4, y, w + 8, h), clause.label().component(), () -> select(key)) : null;
            String number = (i + 1) + ".";
            String label = clause.label().component().getString();
            String detail = clause.detail().component().getString();
            int cy = y;
            op(g -> {
                BookRenderer.rowGround(g, x, cy, w, h, key.equals(selection), zone != null && zone.hot());
                g.drawString(font, number, x, cy + TEXT_Y, RUBRIC, false);
                g.drawString(font, BookRenderer.fit(font, label, w - 12), x + 12, cy + TEXT_Y, INK, false);
                if (!detail.isBlank()) g.drawString(font, BookRenderer.fit(font, detail, w - 12), x + 12, cy + ROW + DETAIL_GAP, FADED, false);
            });
            y += h;
        }
        y += SECTION;
        y = heading(tr("signatures"), x, y, w);
        for (int i = 0; i < draft.signers().size(); i++) {
            var signer = draft.signers().get(i);
            Controls.Rect block = new Controls.Rect(x, y + i * SIG_H, w, SIG_H - 6);
            if (signer.you() && draft.maySign()) sealTarget = block;
            op(g -> drawSignature(g, draft, signer, block));
        }
    }

    /**
     * A signature row: the stamp on the left, the name written on the line beside it, and the office
     * and date under the line. Unsigned, the name stands faded over a dotted line.
     */
    private void drawSignature(GuiGraphics g, CharterSnapshotS2CPayload.Draft draft, CharterSnapshotS2CPayload.Signer signer, Controls.Rect block) {
        String name = signer.name().component().getString();
        String office = signer.office().component().getString();
        boolean pending = signer.you() && sealPending;
        boolean signed = signer.signed() || pending;
        Controls.Rect stamp = sealRect(block);
        int tx = block.x() + SIG_TEXT, tw = block.w() - SIG_TEXT, lineY = block.y() + 14;
        boolean yours = !signed && signer.you() && draft.maySign();
        boolean aiming = yours && signet.held() && block.contains(mouseX, mouseY);
        if (signed) InkSeal.draw(g, font, pending ? ClientSeal.get() : signer.seal(), name, stamp.x(), stamp.y());
        else if (yours) ring(g, stamp, aiming ? LINE_HOT : LEADER);
        g.drawString(font, BookRenderer.fit(font, name, tw), tx, block.y() + 4, signed ? INK : FADED, false);
        if (signed) g.fill(tx, lineY, tx + tw, lineY + 1, FADED);
        else BookRenderer.line(g, tx, lineY, tw, yours, aiming);
        String date = signed && !pending ? signer.date().component().getString() : "";
        String under = date.isBlank() ? office : office + " · " + date;
        g.drawString(font, BookRenderer.fit(font, under, tw), tx, lineY + 3, FADED, false);
    }

    /** Where the stamp lands: the left of the signature row, level with its text. */
    private static Controls.Rect sealRect(Controls.Rect block) {
        return new Controls.Rect(block.x(), block.y() + (block.h() - WAX) / 2, WAX, WAX);
    }

    /** A dotted circle where your seal will go. */
    private static void ring(GuiGraphics g, Controls.Rect wax, int color) {
        double cx = wax.x() + WAX / 2.0 - 0.5, cy = wax.y() + WAX / 2.0 - 0.5, r = WAX / 2.0 - 2;
        for (int i = 0; i < 24; i += 2) {
            double a = Math.PI * 2 * i / 24;
            int px = (int) Math.round(cx + Math.cos(a) * r), py = (int) Math.round(cy + Math.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private Component footText(Book b) {
        if (snapshot.state() == CharterSnapshotS2CPayload.REPAIR) return tr("repair");
        var draft = b.draft();
        if (draft == null) return Component.empty();
        if (draft.prepared()) return tr("draft.sealed_foot", lapse(draft.expiresIn()));
        if (draft.maySign()) return tr("draft.foot_yours");
        return tr("draft.foot_others");
    }

    // Members

    /** The Members' left page: where the viewer stands, how one joins, and the roster. */
    private void composeStanding(Book b) {
        Controls.Rect p = leftPage;
        int x = x(p), w = w(p), y = y(p), bottom = BookRenderer.foot(p) + 9;
        runningHead(p, tr("running_head", snapshot.settlement()), false);
        var members = b.members();
        y = heading(tr("standing_heading"), x, y, w);
        y = plain(members.status().component(), x, y, w, INK);
        y = detail(members.joinHint().component(), x, y, w, FADED);
        if (members.livery() >= 0) {
            // Wearing the faction's livery is the member's own choice; it restyles their armour's look only.
            boolean worn = members.livery() == 1;
            y = row(tr("livery_row"), tr(worn ? "livery.on" : "livery.off"), x, y, w, INK,
                    rect -> send(CharterActionC2SPayload.WEAR_LIVERY, worn ? "off" : "on", "", "", 0));
        }
        y += SECTION;
        y = composeSeal(x, y, w);
        y += SECTION;
        y = heading(tr("members.heading", members.count()), x, y, w);
        if (!members.visible()) {
            detail(tr("members.private"), x, y, w, FADED);
            return;
        }
        var people = members.people();
        int more = members.count() - people.size();
        int rows = Math.max(1, (bottom - y) / ROW - (more > 0 ? 1 : 0));
        int per = people.size() <= rows ? rows : Math.max(1, rows - 1);
        int pages = Math.max(1, (people.size() + per - 1) / per);
        rosterPage = Math.max(0, Math.min(rosterPage, pages - 1));
        for (int i = rosterPage * per; i < Math.min(people.size(), (rosterPage + 1) * per); i++) {
            var person = people.get(i);
            String name = person.name().component().getString();
            String office = person.detail().component().getString();
            int ry = y;
            op(g -> {
                if (office.isBlank()) g.drawString(font, BookRenderer.fit(font, name, w), x, ry + TEXT_Y, INK, false);
                else BookRenderer.leader(g, font, name, office, x, ry, w, INK, FADED);
            });
            y += ROW;
        }
        if (pages > 1) y = pager(x, y, w, pages);
        if (more > 0) plain(tr("members.more", more), x, y, w, FADED);
    }

    /**
     * The viewer's own seal: the impression beside two rows, its device and its ink. It is theirs
     * everywhere they sign, not this faction's.
     */
    private int composeSeal(int x, int y, int w) {
        y = heading(tr("seal_heading"), x, y, w);
        int sy = y;
        String owner = minecraft != null && minecraft.player != null ? minecraft.player.getName().getString() : "";
        op(g -> InkSeal.draw(g, font, ClientSeal.get(), owner, x, sy + 2));
        int rx = x + InkSeal.SIZE + 8, rw = w - InkSeal.SIZE - 8;
        var seal = ClientSeal.get();
        y = row(tr("seal_device"), InkSeal.name(seal.device()), rx, y, rw, INK, this::openDeviceMenu);
        return row(tr("seal_ink"), InkSeal.colourName(seal.dye()), rx, y, rw, INK, this::openInkMenu);
    }

    private void openDeviceMenu(Controls.Rect anchor) {
        var seal = ClientSeal.get();
        String owner = minecraft != null && minecraft.player != null ? minecraft.player.getName().getString() : "";
        int color = InkSeal.ink(seal.dye());
        List<DropMenu.Entry> entries = new ArrayList<>();
        for (var device : InkSeal.devices()) {
            entries.add(new DropMenu.Entry(device.name(), () -> { menu = null; ClientSeal.choose(device.id(), seal.dye()); rebuildWidgets(); },
                    false, device.id().equals(seal.device()), 0,
                    (g, ix, iy) -> InkSeal.drawDevice(g, font, device.id(), owner, ix, iy, color)));
        }
        menu = menuBelow(tr("seal_device"), entries, anchor);
    }

    private void openInkMenu(Controls.Rect anchor) {
        var seal = ClientSeal.get();
        List<DropMenu.Entry> entries = new ArrayList<>();
        for (int dye = 0; dye < 16; dye++) {
            int value = dye;
            entries.add(new DropMenu.Entry(InkSeal.colourName(dye), () -> { menu = null; ClientSeal.choose(seal.device(), value); rebuildWidgets(); },
                    false, dye == seal.dye(), InkSeal.ink(dye)));
        }
        menu = menuBelow(tr("seal_ink"), entries, anchor);
    }

    /** "‹  2 / 5  ›", centred on its row. */
    private int pager(int x, int y, int w, int pages) {
        String count = (rosterPage + 1) + " / " + pages;
        int cx = x + w / 2;
        int half = font.width(count) / 2;
        Controls.Rect prev = new Controls.Rect(cx - half - 18, y, 12, ROW);
        Controls.Rect next = new Controls.Rect(cx + half + 6, y, 12, ROW);
        PageZone back = rosterPage > 0 ? zone(prev, tr("page.previous"), () -> { rosterPage--; rebuildWidgets(); }) : null;
        PageZone forward = rosterPage < pages - 1 ? zone(next, tr("page.next"), () -> { rosterPage++; rebuildWidgets(); }) : null;
        op(g -> {
            g.drawString(font, count, cx - half, y + TEXT_Y, FADED, false);
            g.drawString(font, "‹", prev.x() + 4, y + TEXT_Y, back == null ? LEADER : back.hot() ? LINE_HOT : INK, false);
            g.drawString(font, "›", next.x() + 4, y + TEXT_Y, forward == null ? LEADER : forward.hot() ? LINE_HOT : INK, false);
        });
        return y + ROW;
    }

    /** The Members' right page: who lives here, and requests to join or leave. */
    private void composeResidents(Book b) {
        Controls.Rect p = rightPage;
        int x = x(p), w = w(p), y = y(p), bottom = BookRenderer.foot(p) + 9;
        runningHead(p, Component.literal(snapshot.faction()), true);
        var scope = b.census().isEmpty() ? null : b.census().get(0);
        if (scope != null && scope.available()) {
            y = heading(tr("residents_heading", scope.population()), x, y, w);
            y = census(scope, x, y, w);
            y += SECTION;
        }
        y = heading(tr("requests_heading"), x, y, w);
        List<CharterSnapshotS2CPayload.Request> open = b.requests().stream().filter(r -> isOpen(r.state())).toList();
        if (open.isEmpty()) y = plain(tr("requests_open_empty"), x, y, w, FADED);
        for (var request : open) {
            if (y + ROW + DETAIL_GAP + DETAIL > bottom) break;
            String key = "request:" + request.id();
            boolean choosable = !request.actions().isEmpty();
            if (choosable) selectable.add(key);
            PageZone zone = choosable ? zone(new Controls.Rect(x - 4, y, w + 8, ROW + DETAIL_GAP + DETAIL), request.person().component(), () -> select(key)) : null;
            String person = request.person().component().getString();
            String state = requestState(request).getString();
            String kind = tr("request.kind." + request.kind()).getString();
            int ry = y;
            op(g -> {
                BookRenderer.rowGround(g, x, ry, w, ROW + DETAIL_GAP + DETAIL, key.equals(selection), zone != null && zone.hot());
                BookRenderer.leader(g, font, person, state, x, ry, w, INK, FADED);
                g.drawString(font, BookRenderer.fit(font, kind, w), x, ry + ROW + DETAIL_GAP, FADED, false);
            });
            y += ROW + DETAIL_GAP + DETAIL;
        }
        List<CharterSnapshotS2CPayload.Request> past = b.requests().stream().filter(r -> !isOpen(r.state())).limit(PAST_MAX).toList();
        if (past.isEmpty() || y + SECTION + HEADING + ROW > bottom) return;
        y += SECTION;
        y = heading(tr("requests_past"), x, y, w);
        for (var request : past) {
            if (y + ROW > bottom) break;
            String person = request.person().component().getString();
            String state = requestState(request).getString();
            int ry = y;
            op(g -> BookRenderer.leader(g, font, person, state, x, ry, w, FADED, FADED));
            y += ROW;
        }
    }

    /** One bar split by culture, a legend beneath it, and how many residents could not be read. */
    private int census(CharterSnapshotS2CPayload.CensusScope scope, int x, int y, int w) {
        int total = scope.population();
        var groups = scope.groups();
        int by = y;
        op(g -> {
            if (total <= 0) { g.fill(x, by, x + w, by + 6, LEADER); return; }
            int sx = x, sum = 0;
            for (int i = 0; i < groups.size(); i++) {
                sum += groups.get(i).count();
                int ex = i == groups.size() - 1 ? x + w : x + Math.round((float) w * sum / total);
                g.fill(sx, by, ex, by + 6, groups.get(i).color());
                sx = ex;
            }
        });
        y += 6 + HEAD_GAP;
        java.text.NumberFormat percent = java.text.NumberFormat.getPercentInstance();
        percent.setMaximumFractionDigits(0);
        int others = 0;
        for (int i = 0; i < groups.size(); i++) {
            var group = groups.get(i);
            if (i >= LEGEND_MAX && groups.size() > LEGEND_MAX + 1) { others += group.count(); continue; }
            y = legendRow(group.name().component().getString(), percent.format((double) group.count() / Math.max(1, total)), group.color(), x, y, w);
        }
        if (others > 0) y = legendRow(tr("census.others").getString(), percent.format((double) others / Math.max(1, total)), LEADER, x, y, w);
        if (scope.uncounted() > 0) y = detail(tr("census.uncounted", scope.uncounted()), x, y, w, FADED);
        return y;
    }

    private int legendRow(String name, String value, int color, int x, int y, int w) {
        op(g -> {
            g.fill(x, y + TEXT_Y + 1, x + 6, y + TEXT_Y + 7, color);
            BookRenderer.leader(g, font, name, value, x + 10, y, w - 10, INK, FADED);
        });
        return y + ROW;
    }

    private static Component requestState(CharterSnapshotS2CPayload.Request request) {
        boolean waiting = request.state().equals("pending") || request.state().equals("offered");
        return request.required() > 0 && waiting
                ? tr("request.state_approvals", tr("request.state." + request.state()), request.approvals(), request.required())
                : tr("request.state." + request.state());
    }

    private static boolean isOpen(String state) {
        return state.equals("pending") || state.equals("offered") || state.equals("notice");
    }

    // The bar

    private void composeBookBar(Book b) {
        Bar bar = new Bar();
        var draft = b.draft();
        if (tab == CHARTER) {
            if (b.mayDraft()) bar.left(tr("change.add"), this::openChangeMenu);
            if (selection != null && selection.startsWith("seat:")) {
                SeatLine line = seatLines(b).get(Integer.parseInt(selection.substring(5)));
                bar.left(tr("change.successor"), anchor -> openSuccessorMenu(anchor, b, line.office()));
            } else if (selection != null && selection.startsWith("clause:") && draft != null) {
                String index = selection.substring(7);
                bar.left(tr("draft.strike"), () -> { selection = null; draft("remove", draft.token(), index); });
                bar.left(tr("draft.discard_button"), () -> { selection = null; draft("discard", draft.token(), ""); });
            } else if (b.civic() != null) {
                var civic = b.civic();
                for (var action : civic.actions()) {
                    bar.left(action.label().component(), () -> send(CharterActionC2SPayload.CIVIC, action.id(), civic.actor(), "", snapshot.revision()));
                }
            }
        } else {
            var request = selection == null || !selection.startsWith("request:") ? null
                    : b.requests().stream().filter(r -> selection.equals("request:" + r.id())).findFirst().orElse(null);
            var actions = request != null ? request.actions() : b.members().actions();
            String target = request != null ? request.id() : b.id();
            for (var action : actions) bar.left(action.label().component(), anchor -> act(anchor, b, action, target));
        }
        bar.right(Component.translatable("gui.done"), this::onClose);
        if (tab == CHARTER && draft != null && draft.signers().stream().anyMatch(CharterSnapshotS2CPayload.Signer::you)) {
            signetRest = bar.reserveRight(Signet.REST_W, Signet.REST_H);
            if (draft.maySign()) zone(signetRest, tr("draft.seal"), this::seal);
        }
    }

    private void openChangeMenu(Controls.Rect anchor) {
        Book b = charter();
        if (b == null) return;
        List<DropMenu.Entry> entries = new ArrayList<>();
        entries.add(new DropMenu.Entry(tr("change.rename"), () -> { menu = null; startRename(); }));
        if (!b.heraldry().isEmpty()) entries.add(new DropMenu.Entry(tr("change.emblem"), () -> { menu = null; openHeraldry(); }));
        if (b.seat().mayMoveHere()) entries.add(new DropMenu.Entry(tr("change.seat"), () -> { menu = null; draft("seat", "", ""); }));
        b.offices().stream().filter(CharterSnapshotS2CPayload.Office::editable).findFirst()
                .ifPresent(office -> entries.add(new DropMenu.Entry(tr("change.successor"), () -> openSuccessorMenu(anchor, b, office))));
        if (!b.welcomes().isEmpty()) entries.add(new DropMenu.Entry(tr("change.welcome"), () -> openWelcomeMenu(anchor, b)));
        entries.add(new DropMenu.Entry(tr("change.dissolve"), () -> { menu = null; draft("dissolve", "", ""); }, true, false, 0));
        Component amending = b.editingAs().component();
        menu = menuAbove(amending.getString().isBlank() ? null : amending, entries, anchor);
    }

    private void openWelcomeMenu(Controls.Rect anchor, Book b) {
        List<DropMenu.Entry> entries = new ArrayList<>();
        for (var welcome : b.welcomes()) {
            entries.add(new DropMenu.Entry(welcome.name().component(), () -> {
                menu = null;
                draft("welcome", welcome.id(), welcome.welcomed() ? "0" : "1");
            }, false, welcome.welcomed(), 0));
        }
        menu = menuAbove(tr("picker.welcome"), entries, anchor);
    }

    private void openSuccessorMenu(Controls.Rect anchor, Book b, CharterSnapshotS2CPayload.Office office) {
        List<DropMenu.Entry> entries = new ArrayList<>();
        for (var person : b.members().people()) {
            if (!person.player() || person.you() || office.holders().stream().anyMatch(h -> h.id().equals(person.id()))) continue;
            entries.add(new DropMenu.Entry(person.name().component(), () -> {
                menu = null;
                selection = null;
                draft("transfer_leadership", "", person.id());
            }));
        }
        if (entries.isEmpty()) entries.add(new DropMenu.Entry(tr("picker.empty"), () -> menu = null));
        menu = menuAbove(tr("picker.heading", office.name().component()), entries, anchor);
    }

    /** Membership actions go straight to the server; an invitation first asks whom, from who is online. */
    private void act(Controls.Rect anchor, Book b, CharterSnapshotS2CPayload.Action action, String target) {
        if (!action.personInput()) {
            selection = null;
            send(CharterActionC2SPayload.MEMBERSHIP, action.id(), target, "", snapshot.revision());
            return;
        }
        Set<String> members = new HashSet<>();
        b.members().people().forEach(person -> members.add(person.id()));
        List<DropMenu.Entry> entries = new ArrayList<>();
        if (minecraft != null && minecraft.getConnection() != null) {
            for (var info : minecraft.getConnection().getOnlinePlayers()) {
                var profile = info.getProfile();
                if (members.contains(profile.getId().toString())
                        || minecraft.player != null && profile.getId().equals(minecraft.player.getUUID())) continue;
                String name = profile.getName();
                entries.add(new DropMenu.Entry(Component.literal(name), () -> {
                    menu = null;
                    send(CharterActionC2SPayload.MEMBERSHIP, action.id(), target, name, snapshot.revision());
                }));
            }
        }
        entries.sort(Comparator.comparing(e -> e.label().getString()));
        if (entries.isEmpty()) entries.add(new DropMenu.Entry(tr("invite.empty"), () -> menu = null));
        menu = menuAbove(action.label().component(), entries, anchor);
    }

    private void startRename() {
        renaming = true;
        renameDraft = snapshot.faction();
        selection = null;
        rebuildWidgets();
    }

    private void finishRename() {
        renaming = false;
        String value = renameDraft == null ? "" : renameDraft.strip();
        if (CharterIdentityService.normalize(value) != null && !value.equals(snapshot.faction())) draft("rename", "", value);
        rebuildWidgets();
    }

    private void openHeraldry() {
        if (charter() != null && !charter().heraldry().isEmpty()) minecraft.setScreen(new HeraldryScreen(this, snapshot));
    }

    // Signing

    private boolean maySign() {
        Book b = charter();
        return b != null && b.draft() != null && b.draft().maySign() && sealTarget != null && !sealPending;
    }

    /** The press always lands on the seal's place, wherever on the line the signet was brought down. */
    private void seal() {
        Book b = charter();
        if (!maySign() || b == null) return;
        Controls.Rect wax = sealRect(sealTarget);
        signet.moveTo(wax.x() + WAX / 2.0, wax.y() + WAX / 2.0);
        signet.press();
        signet.putDown();
        sealPending = true;
        draft("sign", b.draft().token(), "");
    }

    // Founding and the other states

    /** A blank charter: the faction's name, the settlement's name and its culture are written in. */
    private void composeFounding() {
        Controls.Rect p = leftPage;
        int x = x(p), w = w(p), y = y(p);
        runningHead(p, tr("unfounded"), false);
        Title title = fit(bottom(p) - y, LineField.height(2) + HEAD_GAP, 2, SECTION + HEADING + 3 * ROW);
        y = plainEmblem(title, x, y, w);
        if (draftName.isBlank()) draftName = tr("default_settlement").getString();
        if (draftFaction.isBlank()) suggestFaction();
        LineField name = addRenderableWidget(new LineField(font, x, y, w, tr("faction_name"), 2, true));
        name.setMaxLength(48);
        name.setValue(draftFaction);
        name.setResponder(value -> { draftFaction = value; customFaction = true; updatePrepare(); });
        name.setEditable(snapshot.editable());
        y += LineField.height(2) + HEAD_GAP;
        int py = y, proclamationLines = title.proclamation();
        if (proclamationLines > 0) op(g -> BookRenderer.wrap(g, font, tr("to_be_proclaimed", draftName), x, py, w, FADED, proclamationLines, true));
        y += proclamationLines * DETAIL + SECTION;
        y = heading(tr("articles"), x, y, w);
        Component settlementLabel = tr("row.settlement");
        int fieldX = x + font.width(settlementLabel) + 8;
        int ly = y;
        op(g -> g.drawString(font, settlementLabel, x, ly + TEXT_Y, INK, false));
        LineField settlement = addRenderableWidget(new LineField(font, fieldX, y + TEXT_Y, x + w - fieldX, tr("settlement_name"), 1, false));
        settlement.setMaxLength(48);
        settlement.setValue(draftName);
        settlement.setResponder(value -> { draftName = value; updatePrepare(); });
        settlement.setEditable(snapshot.editable());
        y += ROW;
        if (!snapshot.cultures().isEmpty()) {
            Component culture = selected(snapshot.cultures(), cultureIndex).name().component();
            y = row(tr("row.culture"), culture, x, y, w, INK, snapshot.editable() ? this::openCultureMenu : null);
        }
        if (!snapshot.profiles().isEmpty()) row(tr("row.form"), selected(snapshot.profiles(), 0).name().component(), x, y, w, INK, null);
        composeFounder(tr("unfounded_subtitle"), FADED);

        Bar bar = new Bar();
        if (snapshot.editable() && !snapshot.cultures().isEmpty() && !snapshot.profiles().isEmpty()) {
            bar.left(tr("suggest_name"), () -> { suggestFaction(); rebuildWidgets(); });
        }
        bar.right(Component.translatable("gui.done"), this::onClose);
        prepareButton = bar.right(tr("prepare"), () -> send(CharterActionC2SPayload.PREPARE, factionPattern, factionBase, draftFaction, 0));
        updatePrepare();
    }

    /** The right page of a charter not yet proclaimed: who will lead, and what happens next. */
    private void composeFounder(Component footText, int footColor) {
        Controls.Rect p = rightPage;
        int x = x(p), w = w(p), y = y(p);
        if (snapshot.editable() && minecraft != null && minecraft.player != null) {
            y = heading(tr("government"), x, y, w);
            row(tr("row.leader"), minecraft.player.getName(), x, y, w, INK, null);
        }
        foot(p, footText, footColor);
    }

    private void openCultureMenu(Controls.Rect anchor) {
        List<DropMenu.Entry> entries = new ArrayList<>();
        for (int i = 0; i < snapshot.cultures().size(); i++) {
            int index = i;
            entries.add(new DropMenu.Entry(snapshot.cultures().get(i).name().component(), () -> {
                menu = null;
                cultureIndex = index;
                if (!customFaction) suggestFaction();
                rebuildWidgets();
            }, false, i == cultureIndex, 0));
        }
        menu = menuBelow(tr("row.culture"), entries, anchor);
    }

    private void updatePrepare() {
        if (prepareButton != null) prepareButton.active = snapshot.editable() && draftName.trim().length() >= 2
                && CharterIdentityService.normalize(draftFaction) != null;
    }

    private void suggestFaction() {
        if (snapshot.cultures().isEmpty() || snapshot.profiles().isEmpty()) return;
        customFaction = false;
        var names = selected(snapshot.cultures(), cultureIndex).naming();
        var patterns = selected(snapshot.profiles(), 0).naming();
        var random = java.util.concurrent.ThreadLocalRandom.current();
        factionBase = names.isEmpty() ? draftName : names.get(random.nextInt(names.size()));
        factionPattern = patterns.isEmpty() ? "{name}" : patterns.get(random.nextInt(patterns.size()));
        draftFaction = factionPattern.replace("{name}", factionBase);
        if (CharterIdentityService.normalize(draftFaction) == null) { factionPattern = "{name}"; draftFaction = factionBase; }
    }

    /** The prepared charter: the same page, written out and sealed, waiting for the bell. */
    private void composePrepared() {
        Controls.Rect p = leftPage;
        int x = x(p), w = w(p), y = y(p);
        runningHead(p, tr("prepared_head"), false);
        Component proclaimed = tr("to_be_proclaimed", snapshot.settlement());
        Title title = fit(bottom(p) - y, nameHeight(snapshot.faction(), w), BookRenderer.lines(font, proclaimed, w, 2),
                SECTION + HEADING + 3 * ROW);
        y = plainEmblem(title, x, y, w);
        y = composeName(snapshot.faction(), x, y, w, false);
        y = centredDetail(proclaimed, x, y, w, title.proclamation());
        y += SECTION;
        y = heading(tr("articles"), x, y, w);
        y = row(tr("row.settlement"), Component.literal(snapshot.settlement()), x, y, w, INK, null);
        y = row(tr("row.culture"), snapshot.tradition().component(), x, y, w, INK, null);
        row(tr("row.form"), snapshot.form().component(), x, y, w, INK, null);
        boolean broken = snapshot.state() == CharterSnapshotS2CPayload.REPAIR;
        composeFounder(broken ? tr("repair") : tr("prepared_foot"), RUBRIC);
        Bar bar = new Bar();
        if (snapshot.editable()) bar.left(tr("cancel"), () -> send(CharterActionC2SPayload.CANCEL, "", "", "", 0));
        bar.right(Component.translatable("gui.done"), this::onClose);
    }

    /** A lectern beside a settlement that already has a faction: link it rather than found one. */
    private void composeExisting() {
        Controls.Rect p = leftPage;
        int x = x(p), w = w(p), y = y(p);
        runningHead(p, tr("existing_settlement"), false);
        Title title = fit(bottom(p) - y, nameHeight(snapshot.faction(), w), 0, SECTION + HEADING + 2 * ROW);
        y = plainEmblem(title, x, y, w);
        y = composeName(snapshot.faction(), x, y, w, false);
        y += SECTION;
        y = heading(tr("articles"), x, y, w);
        y = row(tr("row.settlement"), Component.literal(snapshot.settlement()), x, y, w, INK, null);
        row(tr("row.form"), snapshot.form().component(), x, y, w, INK, null);
        foot(rightPage, snapshot.note().component(), FADED);
        Bar bar = new Bar();
        bar.left(tr("link_existing"), () -> send(CharterActionC2SPayload.LINK_EXISTING, "", "", "", 0)).active = snapshot.editable();
        bar.right(Component.translatable("gui.done"), this::onClose);
    }

    private void composeUnavailable() {
        Controls.Rect p = leftPage;
        runningHead(p, tr("title"), false);
        detail(snapshot.message().isBlank() ? tr("unavailable") : Component.literal(snapshot.message()), x(p), y(p), w(p), FADED);
        Bar bar = new Bar();
        bar.left(tr("retry"), () -> send(CharterActionC2SPayload.REFRESH, "", "", "", 0));
        bar.right(Component.translatable("gui.done"), this::onClose);
    }

    /** The faction's banner, or a pale unpainted one until an emblem is proclaimed. */
    private void drawEmblem(GuiGraphics g, @Nullable Book b, Controls.Rect rect, boolean hot) {
        var emblem = b == null ? null : b.heraldry().stream().filter(h -> h.actor().startsWith("faction:")).findFirst().orElse(null);
        boolean painted = emblem != null && emblem.revision() > 0 && minecraft.level != null;
        ItemStack stack = painted ? EmblemItems.banner(minecraft.level.registryAccess(), EmblemRecipe.safe(emblem.recipe()))
                : new ItemStack(Items.WHITE_BANNER);
        float scale = rect.w() / 16f;
        g.pose().pushPose();
        g.pose().translate(rect.x(), rect.y(), 0);
        g.pose().scale(scale, scale, scale);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
        g.flush();
        if (!painted) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 500);
            g.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), 0x99F4E6C4);
            g.pose().popPose();
        }
        if (hot) BookRenderer.line(g, rect.x() + rect.w() / 6, rect.bottom() + 1, rect.w() * 2 / 3, true, false);
    }

    private void draft(String operation, String target, String argument) {
        send(CharterActionC2SPayload.DRAFT, operation, target, argument, 0);
    }

    void send(int action, String operation, String target, String argument, long revision) {
        CharterSnapshotS2CPayload.Option profile = snapshot.profiles().isEmpty() ? null : selected(snapshot.profiles(), 0);
        CharterSnapshotS2CPayload.Option culture = snapshot.cultures().isEmpty() ? null : selected(snapshot.cultures(), cultureIndex);
        CharterActionC2SPayload payload = new CharterActionC2SPayload(snapshot.lectern(), action, draftName,
                profile == null ? "" : profile.id(), culture == null ? "" : culture.id(), operation, target, argument, revision);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }

    private static Component lapse(long ticks) {
        long hours = (ticks + 999) / 1000;
        return hours <= 1 ? tr("draft.lapse_hour") : tr("draft.lapse_hours", hours);
    }

    private static <T> T selected(List<T> values, int index) {
        return values.get(Math.max(0, Math.min(index, values.size() - 1)));
    }

    // ── Desk, overlay and input ──

    @Override protected void drawDesk(GuiGraphics g) {
        if (signetRest == null) return;
        int foot = signetRest.bottom();
        g.fill(signetRest.x() - 1, foot, signetRest.right() + 1, foot + 1, Palette.DESK_EDGE);
        signet.drawResting(g, signetRest.x(), foot, maySign() ? 1f : 0.35f);
    }

    @Override protected void drawOverlay(GuiGraphics g, int mx, int my) {
        signet.drawPressAnimation(g);
        if (signet.held()) signet.drawHeld(g, sealTarget != null && sealTarget.contains(mx, my));
    }

    @Override protected boolean clicked(double mx, double my, int button) {
        if (signet.held()) {
            if (button == 0 && sealTarget != null && sealTarget.contains(mx, my)) seal();
            else signet.putDown();
            return true;
        }
        if (button == 0 && signetRest != null && maySign() && Signet.overResting(mx, my, signetRest.x(), signetRest.bottom())) {
            signet.pickUp(mx, my);
            return true;
        }
        if (renaming && getFocused() instanceof LineField field && !field.isMouseOver(mx, my)) finishRename();
        return false;
    }

    @Override public void mouseMoved(double mx, double my) {
        if (signet.held()) signet.moveTo(mx, my);
        super.mouseMoved(mx, my);
    }

    @Override protected boolean escape() {
        if (signet.held()) { signet.putDown(); return true; }
        if (renaming) { renaming = false; rebuildWidgets(); return true; }
        if (selection != null) { selection = null; rebuildWidgets(); return true; }
        return false;
    }

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (renaming && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) { finishRename(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override public void tick() {
        super.tick();
        if (charter() != null && !renaming && menu == null && !signet.held() && ++refreshTicks >= 100) {
            refreshTicks = 0;
            send(CharterActionC2SPayload.REFRESH, "", "", "", 0);
        }
    }

    @Override public Component getNarrationMessage() {
        if (snapshot == null) return title;
        return Component.empty().append(title).append(". ").append(snapshot.faction());
    }
}
