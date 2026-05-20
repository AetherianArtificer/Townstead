package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire format for {@link LeapRule}s in {@link CalendarSyncPayload}. Kept
 * separate from {@link LeapRule} itself so the rule model has no MC dependency
 * and can be exercised in plain unit tests.
 *
 * <p>The format is tagged-variant + length-prefixed lists. Versioned via a
 * single leading byte; current version is {@code 1}. Future versions can add
 * new predicate / action tags or extra fields without breaking older clients
 * (they read the tag byte they don't recognize and bail).</p>
 */
public final class LeapRuleCodec {

    private static final byte VERSION = 1;

    private static final byte PRED_EQUALS = 0;
    private static final byte PRED_IN = 1;
    private static final byte PRED_ALL = 2;
    private static final byte PRED_ANY = 3;

    private static final byte ACTION_ADJUST = 0;
    private static final byte ACTION_INSERT = 1;
    private static final byte ACTION_RENAME = 2;

    private LeapRuleCodec() {}

    public static void writeList(FriendlyByteBuf buf, List<LeapRule> rules) {
        buf.writeByte(VERSION);
        if (rules == null) {
            buf.writeVarInt(0);
            return;
        }
        buf.writeVarInt(rules.size());
        for (LeapRule r : rules) {
            writePredicate(buf, r.when());
            writeAction(buf, r.action());
        }
    }

    public static List<LeapRule> readList(FriendlyByteBuf buf) {
        byte version = buf.readByte();
        int n = buf.readVarInt();
        if (n == 0) return List.of();
        if (version != VERSION) {
            // Newer server than this client knows; skip-load gracefully.
            // We can't safely skip an unknown payload, so just bail with empty.
            // The client will render years as if no leap rules exist; not
            // perfect but better than crashing.
            return List.of();
        }
        List<LeapRule> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            LeapRule.Predicate pred = readPredicate(buf);
            LeapRule.Action act = readAction(buf);
            out.add(new LeapRule(pred, act));
        }
        return out;
    }

    private static void writePredicate(FriendlyByteBuf buf, LeapRule.Predicate p) {
        if (p instanceof LeapRule.Equals e) {
            buf.writeByte(PRED_EQUALS);
            buf.writeVarInt(e.mod());
            buf.writeVarInt(e.equalsValue());
        } else if (p instanceof LeapRule.In in) {
            buf.writeByte(PRED_IN);
            buf.writeVarInt(in.mod());
            buf.writeVarInt(in.residues().length);
            for (int v : in.residues()) buf.writeVarInt(v);
        } else if (p instanceof LeapRule.AllOf all) {
            buf.writeByte(PRED_ALL);
            buf.writeVarInt(all.parts().size());
            for (LeapRule.Predicate part : all.parts()) writePredicate(buf, part);
        } else if (p instanceof LeapRule.AnyOf any) {
            buf.writeByte(PRED_ANY);
            buf.writeVarInt(any.parts().size());
            for (LeapRule.Predicate part : any.parts()) writePredicate(buf, part);
        } else {
            throw new IllegalStateException("Unknown predicate type: " + p.getClass());
        }
    }

    private static LeapRule.Predicate readPredicate(FriendlyByteBuf buf) {
        byte tag = buf.readByte();
        switch (tag) {
            case PRED_EQUALS: {
                int mod = buf.readVarInt();
                int eq = buf.readVarInt();
                return new LeapRule.Equals(mod, eq);
            }
            case PRED_IN: {
                int mod = buf.readVarInt();
                int n = buf.readVarInt();
                int[] residues = new int[n];
                for (int i = 0; i < n; i++) residues[i] = buf.readVarInt();
                return new LeapRule.In(mod, residues);
            }
            case PRED_ALL: {
                int n = buf.readVarInt();
                List<LeapRule.Predicate> parts = new ArrayList<>(n);
                for (int i = 0; i < n; i++) parts.add(readPredicate(buf));
                return new LeapRule.AllOf(parts);
            }
            case PRED_ANY: {
                int n = buf.readVarInt();
                List<LeapRule.Predicate> parts = new ArrayList<>(n);
                for (int i = 0; i < n; i++) parts.add(readPredicate(buf));
                return new LeapRule.AnyOf(parts);
            }
            default:
                throw new IllegalStateException("Unknown predicate tag: " + tag);
        }
    }

    private static void writeAction(FriendlyByteBuf buf, LeapRule.Action a) {
        if (a instanceof LeapRule.AdjustDays adj) {
            buf.writeByte(ACTION_ADJUST);
            buf.writeVarInt(adj.monthIndex());
            buf.writeVarInt(adj.delta());
        } else if (a instanceof LeapRule.InsertMonth ins) {
            buf.writeByte(ACTION_INSERT);
            boolean hasAfter = ins.afterMonthIndex() != null;
            buf.writeBoolean(hasAfter);
            if (hasAfter) buf.writeVarInt(ins.afterMonthIndex());
            buf.writeVarInt(ins.month().days());
            String[] kf = ComponentSync.extract(ins.month().commonName());
            buf.writeUtf(kf[0]);
            buf.writeUtf(kf[1]);
        } else if (a instanceof LeapRule.RenameMonth ren) {
            buf.writeByte(ACTION_RENAME);
            buf.writeVarInt(ren.monthIndex());
            String[] kf = ComponentSync.extract(ren.newName());
            buf.writeUtf(kf[0]);
            buf.writeUtf(kf[1]);
        } else {
            throw new IllegalStateException("Unknown action type: " + a.getClass());
        }
    }

    private static LeapRule.Action readAction(FriendlyByteBuf buf) {
        byte tag = buf.readByte();
        switch (tag) {
            case ACTION_ADJUST: {
                int idx = buf.readVarInt();
                int delta = buf.readVarInt();
                return new LeapRule.AdjustDays(idx, delta);
            }
            case ACTION_INSERT: {
                boolean hasAfter = buf.readBoolean();
                Integer afterIdx = hasAfter ? buf.readVarInt() : null;
                int days = buf.readVarInt();
                String key = buf.readUtf();
                String fallback = buf.readUtf();
                Component name = ComponentSync.reconstruct(key, fallback);
                return new LeapRule.InsertMonth(afterIdx, new MonthDef(name, days));
            }
            case ACTION_RENAME: {
                int idx = buf.readVarInt();
                String key = buf.readUtf();
                String fallback = buf.readUtf();
                Component name = ComponentSync.reconstruct(key, fallback);
                return new LeapRule.RenameMonth(idx, name);
            }
            default:
                throw new IllegalStateException("Unknown action tag: " + tag);
        }
    }
}
