package com.aetherianartificer.townstead.client.gui.root;

import com.aetherianartificer.townstead.client.root.RootFoundingClient;
import com.aetherianartificer.townstead.root.FoundingLook;
import com.aetherianartificer.townstead.root.RootFoundingC2SPayload;
import com.aetherianartificer.townstead.root.RootFoundingS2CPayload;
import com.aetherianartificer.townstead.root.RootMix;
import org.jetbrains.annotations.Nullable;

/**
 * One editor's Root state, shared by the Root tab and the root browser. It knows what the target is
 * (asked from the server when the editor opens), and the founding on show, if any: a roll the server
 * made and keeps, shown on the editor's model exactly as Apply will give it. Until something is
 * changed nothing is shown but the target itself, and nothing is ever rolled twice: going back to a
 * founding shows the same one.
 */
public final class RootEditSession {

    /** After this long a roll on its way is shown as rolling; quicker ones just land. */
    private static final long ROLLING_SHOWN_AFTER_MS = 150;

    /** One root ({@code mix} null) or a mix, whose {@code rootId} is its largest share. */
    public record Choice(String rootId, @Nullable RootMix mix) {
        public static Choice root(String rootId) {
            return new Choice(rootId, null);
        }

        public static Choice of(RootMix mix) {
            return new Choice(mix.largest(), mix);
        }

        public boolean same(@Nullable Choice other) {
            if (other == null) return false;
            return mix != null ? mix.equals(other.mix) : other.mix == null && rootId.equals(other.rootId);
        }
    }

    /** What the target is: its choice, what it is called, the heritage's backstory for a mix. */
    public record Identity(Choice choice, String name, String backstory) {}

    /** A founding the server rolled and keeps until it is applied. */
    public record Founding(int id, Choice choice, String name, String backstory, FoundingLook look) {}

    /** The editor that draws the model. */
    public interface Host {
        /** Shows {@code founding} on the model, or the target as it is when null. */
        void show(@Nullable Founding founding);

        /** {@code founding} was given to the target; it is what the model now shows as the target. */
        void applied(Founding founding);

        /** The server said what the target is. */
        void identityChanged();
    }

    private final int target;
    private final Host host;
    private @Nullable Identity identity;
    private @Nullable Founding shown;
    private @Nullable Choice rolling;
    private int stateRequest;
    private int rollRequest;
    private int commitRequest;
    private long rollSince;
    private @Nullable Runnable onChange;

    public RootEditSession(int target, Host host) {
        this.target = target;
        this.host = host;
        RootFoundingClient.listen(this::accept);
        stateRequest = RootFoundingClient.askState(target);
    }

    public int target() {
        return target;
    }

    /** What the target is, or null until the server has said. */
    public @Nullable Identity identity() {
        return identity;
    }

    /** The founding on show, or null when the model shows the target as it is. */
    public @Nullable Founding shown() {
        return shown;
    }

    /** What the model shows: the founding's choice, else the target's own (null until known). */
    public @Nullable Choice shownChoice() {
        return shown != null ? shown.choice() : identity != null ? identity.choice() : null;
    }

    public boolean rolling() {
        return rollRequest != 0;
    }

    /** A roll has been on its way long enough to say so. */
    public boolean rollingLong() {
        return rolling() && System.currentTimeMillis() - rollSince > ROLLING_SHOWN_AFTER_MS;
    }

    public boolean canApply() {
        return shown != null && !rolling() && commitRequest == 0;
    }

    /** Runs whenever what is shown or known changes; one listener, the open Root tab's. */
    public void onChange(@Nullable Runnable listener) {
        onChange = listener;
    }

    /**
     * Show {@code choice}: the target itself when it is the target's own, the founding already on
     * show (or on its way) when it is that one's, else a new roll.
     */
    public void want(Choice choice) {
        if (identity != null && choice.same(identity.choice())) {
            showCurrent();
            return;
        }
        if (rolling != null ? choice.same(rolling) : shown != null && choice.same(shown.choice())) return;
        roll(choice);
    }

    /** A new roll of {@code choice}, even when one is on show or it is the target's own. */
    public void roll(Choice choice) {
        rolling = choice;
        rollSince = System.currentTimeMillis();
        rollRequest = RootFoundingClient.roll(target, choice.rootId(), choice.mix());
        changed();
    }

    /** Show the target as it is. */
    public void showCurrent() {
        cancelRoll();
        if (shown != null) {
            shown = null;
            host.show(null);
        }
        changed();
    }

    /** Show {@code founding} again (or the target when null), as the browser's Back does. */
    public void restore(@Nullable Founding founding) {
        cancelRoll();
        shown = founding;
        host.show(founding);
        changed();
    }

    /** Puts the founding on show back on a model that was reset. */
    public void redisplay() {
        if (shown != null) host.show(shown);
    }

    /** Forgets the founding on show, the model already reset (leaving the tab, MCA's save). */
    public void drop() {
        cancelRoll();
        shown = null;
    }

    /** Gives the target the founding on show. */
    public void apply() {
        if (!canApply() || shown == null) return;
        Founding founding = shown;
        commitRequest = RootFoundingClient.commit(target, founding.id());
        identity = new Identity(founding.choice(), founding.name(), founding.backstory());
        shown = null;
        host.applied(founding);
        changed();
    }

    private void cancelRoll() {
        rollRequest = 0;
        rolling = null;
    }

    private void accept(RootFoundingS2CPayload answer) {
        int id = answer.requestId();
        if (id == 0) return;
        if (id == stateRequest || id == commitRequest) {
            if (id == commitRequest) commitRequest = 0;
            if (!answer.rootId().isEmpty()) {
                identity = new Identity(choiceOf(answer), answer.name(), answer.backstory());
                host.identityChanged();
            } else if (identity == null) {
                // The server can't place the target (not loaded there): go by the root it was last synced as.
                String rootId = RootPicker.currentRootId(target);
                var entry = com.aetherianartificer.townstead.client.root.RootCatalogClient.origin(rootId);
                identity = new Identity(Choice.root(rootId), entry != null ? entry.name() : rootId, "");
            }
            changed();
            return;
        }
        if (id == rollRequest && answer.op() == RootFoundingC2SPayload.ROLL) {
            cancelRoll();
            if (!answer.rootId().isEmpty() && answer.look() != null) {
                shown = new Founding(answer.foundingId(), choiceOf(answer), answer.name(), answer.backstory(), answer.look());
                host.show(shown);
            }
            changed();
        }
    }

    private static Choice choiceOf(RootFoundingS2CPayload answer) {
        return answer.mix().members().isEmpty() ? Choice.root(answer.rootId()) : new Choice(answer.rootId(), answer.mix());
    }

    private void changed() {
        Runnable listener = onChange;
        if (listener != null) listener.run();
    }
}
