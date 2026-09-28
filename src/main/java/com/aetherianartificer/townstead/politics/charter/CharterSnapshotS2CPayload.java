package com.aetherianartificer.townstead.politics.charter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
//?}

import java.util.ArrayList;
import java.util.List;

/**
 * Server-filtered presentation model for the Charter book. Every string is already written and
 * every permission already decided; the client only lays it out.
 */
//? if neoforge {
public record CharterSnapshotS2CPayload(BlockPos lectern, BlockPos bell, int state, boolean editable, String message,
                                        long revision, String settlement, String faction, Text form, Text note,
                                        Text tradition, List<Option> profiles, List<Option> cultures,
                                        @Nullable Book book) implements CustomPacketPayload {
    public static final Type<CharterSnapshotS2CPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("townstead", "charter_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, CharterSnapshotS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> value.write(buf), CharterSnapshotS2CPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
//?} else {
/*public record CharterSnapshotS2CPayload(BlockPos lectern, BlockPos bell, int state, boolean editable, String message,
                                        long revision, String settlement, String faction, Text form, Text note,
                                        Text tradition, List<Option> profiles, List<Option> cultures,
                                        @Nullable Book book) {
*///?}
    public static final int UNAVAILABLE = 0, UNFOUNDED = 1, PREPARED = 2, FOUNDED = 3, REPAIR = 4, EXISTING = 5;

    public CharterSnapshotS2CPayload {
        profiles = List.copyOf(profiles);
        cultures = List.copyOf(cultures);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(lectern); buf.writeBlockPos(bell); buf.writeVarInt(state); buf.writeBoolean(editable);
        buf.writeUtf(message, 512); buf.writeLong(revision); buf.writeUtf(settlement, 128); buf.writeUtf(faction, 128);
        form.write(buf); note.write(buf); tradition.write(buf);
        writeList(buf, profiles, Option::write); writeList(buf, cultures, Option::write);
        buf.writeBoolean(book != null); if (book != null) book.write(buf);
    }

    public static CharterSnapshotS2CPayload read(FriendlyByteBuf buf) {
        return new CharterSnapshotS2CPayload(buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt(), buf.readBoolean(),
                buf.readUtf(512), buf.readLong(), buf.readUtf(128), buf.readUtf(128), Text.read(buf), Text.read(buf),
                Text.read(buf), readList(buf, Option::read), readList(buf, Option::read), buf.readBoolean() ? Book.read(buf) : null);
    }

    private static <T> void writeList(FriendlyByteBuf buf, List<T> values, Writer<T> writer) {
        buf.writeVarInt(values.size());
        values.forEach(value -> writer.write(value, buf));
    }

    private static <T> List<T> readList(FriendlyByteBuf buf, Reader<T> reader) {
        int size = buf.readVarInt();
        if (size < 0 || size > 4096) throw new IllegalArgumentException("Invalid Charter list size: " + size);
        List<T> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) values.add(reader.read(buf));
        return List.copyOf(values);
    }

    private static void writeOptional(FriendlyByteBuf buf, @Nullable Text value) {
        buf.writeBoolean(value != null);
        if (value != null) value.write(buf);
    }

    private static @Nullable Text readOptional(FriendlyByteBuf buf) {
        return buf.readBoolean() ? Text.read(buf) : null;
    }

    public record Text(String key, String fallback, List<Text> arguments, List<Text> siblings) {
        public Text { arguments = List.copyOf(arguments); siblings = List.copyOf(siblings); }
        public Text(String key, String fallback, List<Text> arguments) { this(key, fallback, arguments, List.of()); }
        public Text(String key, String fallback) { this(key, fallback, List.of()); }
        public static Text of(Component value) { return of(value, 0); }
        private static Text of(Component value, int depth) {
            if (depth > 16) throw new IllegalArgumentException("Charter text nesting exceeds 16");
            List<Text> siblings = value.getSiblings().stream().map(c -> of(c, depth + 1)).toList();
            if (value.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents) {
                List<Text> args = new ArrayList<>();
                for (Object argument : contents.getArgs()) args.add(argument instanceof Component c
                        ? of(c, depth + 1) : new Text("", String.valueOf(argument)));
                return new Text(contents.getKey(), contents.getFallback() == null ? "" : contents.getFallback(), args, siblings);
            }
            return new Text("", value.plainCopy().getString(), List.of(), siblings);
        }
        public Component component() {
            Object[] args = arguments.stream().map(Text::component).toArray();
            var result = key.isEmpty() ? Component.literal(fallback) : fallback.isEmpty()
                    ? Component.translatable(key, args) : Component.translatableWithFallback(key, fallback, args);
            siblings.forEach(sibling -> result.append(sibling.component()));
            return result;
        }
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(key, 512); buf.writeUtf(fallback, 2048); writeList(buf, arguments, Text::write); writeList(buf, siblings, Text::write);
        }
        static Text read(FriendlyByteBuf buf) { return read(buf, 0); }
        private static Text read(FriendlyByteBuf buf, int depth) {
            if (depth > 16) throw new IllegalArgumentException("Charter text nesting exceeds 16");
            String key = buf.readUtf(512), fallback = buf.readUtf(2048);
            return new Text(key, fallback, readList(buf, value -> read(value, depth + 1)),
                    readList(buf, value -> read(value, depth + 1)));
        }
    }

    public record Option(String id, Text name, Text description, Text consequences, boolean government, List<String> naming) {
        public Option { naming = List.copyOf(naming); }
        void write(FriendlyByteBuf buf) { buf.writeUtf(id, 256); name.write(buf); description.write(buf); consequences.write(buf); buf.writeBoolean(government); writeList(buf, naming, (v, b) -> b.writeUtf(v, 128)); }
        static Option read(FriendlyByteBuf buf) { return new Option(buf.readUtf(256), Text.read(buf), Text.read(buf), Text.read(buf), buf.readBoolean(), readList(buf, b -> b.readUtf(128))); }
    }

    /**
     * Everything a founded Charter shows across its pages. {@code editingAs} is empty unless the
     * viewer may amend the charter; {@code joinHint} says plainly how someone becomes a member.
     */
    public record Book(String id, Text proclaimed, Text editingAs, @Nullable Text legitimacy, Text legitimacyDetail,
                       SeatRow seat, List<CensusScope> census, List<Text> history, List<Heraldry> heraldry, boolean mayDraft,
                       List<Office> offices, Members members, List<Request> requests,
                       @Nullable Draft draft, @Nullable Civic civic, List<StyleOption> liveryStyles,
                       List<Welcome> welcomes, List<Accord> accords) {
        public Book {
            census = List.copyOf(census); history = List.copyOf(history); heraldry = List.copyOf(heraldry);
            offices = List.copyOf(offices); requests = List.copyOf(requests); liveryStyles = List.copyOf(liveryStyles);
            welcomes = List.copyOf(welcomes); accords = List.copyOf(accords);
        }
        void write(FriendlyByteBuf b) {
            b.writeUtf(id, 256); proclaimed.write(b); editingAs.write(b); writeOptional(b, legitimacy); legitimacyDetail.write(b); seat.write(b);
            writeList(b, census, CensusScope::write); writeList(b, history, Text::write); writeList(b, heraldry, Heraldry::write);
            b.writeBoolean(mayDraft); writeList(b, offices, Office::write); members.write(b);
            writeList(b, requests, Request::write);
            b.writeBoolean(draft != null); if (draft != null) draft.write(b);
            b.writeBoolean(civic != null); if (civic != null) civic.write(b);
            writeList(b, liveryStyles, StyleOption::write);
            writeList(b, welcomes, Welcome::write);
            writeList(b, accords, Accord::write);
        }
        static Book read(FriendlyByteBuf b) {
            return new Book(b.readUtf(256), Text.read(b), Text.read(b), readOptional(b), Text.read(b), SeatRow.read(b),
                    readList(b, CensusScope::read), readList(b, Text::read), readList(b, Heraldry::read), b.readBoolean(),
                    readList(b, Office::read), Members.read(b), readList(b, Request::read),
                    b.readBoolean() ? Draft.read(b) : null, b.readBoolean() ? Civic.read(b) : null, readList(b, StyleOption::read),
                    readList(b, Welcome::read), readList(b, Accord::read));
        }
    }

    /** Another faction: an ally, or one this faction could offer an accord to. */
    public record Accord(String id, Text name, boolean allied) {
        void write(FriendlyByteBuf b) { b.writeUtf(id, 256); name.write(b); b.writeBoolean(allied); }
        static Accord read(FriendlyByteBuf b) { return new Accord(b.readUtf(256), Text.read(b), b.readBoolean()); }
    }

    /** A group the faction may declare welcome, and whether it does now. */
    public record Welcome(String id, Text name, boolean welcomed) {
        void write(FriendlyByteBuf b) { b.writeUtf(id, 256); name.write(b); b.writeBoolean(welcomed); }
        static Welcome read(FriendlyByteBuf b) { return new Welcome(b.readUtf(256), Text.read(b), b.readBoolean()); }
    }

    /** The Seat of Power row: the building, a line of detail, and whether this lectern can propose moving it here. */
    public record SeatRow(Text value, Text detail, boolean damaged, boolean mayMoveHere) {
        void write(FriendlyByteBuf b) { value.write(b); detail.write(b); b.writeBoolean(damaged); b.writeBoolean(mayMoveHere); }
        static SeatRow read(FriendlyByteBuf b) { return new SeatRow(Text.read(b), Text.read(b), b.readBoolean(), b.readBoolean()); }
    }

    /** One office and its holders; {@code editable} means the viewer may draft a new holder. */
    public record Office(String id, Text name, int minimum, int maximum, List<Holder> holders, boolean editable) {
        public Office { holders = List.copyOf(holders); }
        void write(FriendlyByteBuf b) { b.writeUtf(id, 256); name.write(b); b.writeVarInt(minimum); b.writeVarInt(maximum + 1); writeList(b, holders, Holder::write); b.writeBoolean(editable); }
        static Office read(FriendlyByteBuf b) { return new Office(b.readUtf(256), Text.read(b), b.readVarInt(), b.readVarInt() - 1, readList(b, Holder::read), b.readBoolean()); }
    }

    public record Holder(String id, Text name, boolean you) {
        void write(FriendlyByteBuf b) { b.writeUtf(id, 64); name.write(b); b.writeBoolean(you); }
        static Holder read(FriendlyByteBuf b) { return new Holder(b.readUtf(64), Text.read(b), b.readBoolean()); }
    }

    /**
     * The roster. {@code visible} is false for a viewer who may only see the count. {@code status}
     * says where the viewer stands, {@code joinHint} how one joins, {@code actions} what they may do.
     */
    /** {@code livery} is whether the viewer wears the faction's livery: -1 when they cannot, 0 off, 1 on. */
    public record Members(int count, boolean visible, List<Person> people, Text status, Text joinHint, List<Action> actions, int livery) {
        public Members { people = List.copyOf(people); actions = List.copyOf(actions); }
        void write(FriendlyByteBuf b) { b.writeVarInt(count); b.writeBoolean(visible); writeList(b, people, Person::write); status.write(b); joinHint.write(b); writeList(b, actions, Action::write); b.writeVarInt(livery + 1); }
        static Members read(FriendlyByteBuf b) { return new Members(b.readVarInt(), b.readBoolean(), readList(b, Person::read), Text.read(b), Text.read(b), readList(b, Action::read), b.readVarInt() - 1); }
    }

    public record Person(String id, Text name, Text detail, boolean player, boolean you) {
        void write(FriendlyByteBuf b) { b.writeUtf(id, 64); name.write(b); detail.write(b); b.writeBoolean(player); b.writeBoolean(you); }
        static Person read(FriendlyByteBuf b) { return new Person(b.readUtf(64), Text.read(b), Text.read(b), b.readBoolean(), b.readBoolean()); }
    }

    /** The faction's amendment in the making, as this viewer sees it. {@code expiresIn} is ticks until a prepared draft lapses. */
    public record Draft(String token, List<Clause> clauses, List<Signer> signers, boolean maySign,
                        boolean prepared, long expiresIn) {
        public Draft { clauses = List.copyOf(clauses); signers = List.copyOf(signers); }
        void write(FriendlyByteBuf b) { b.writeUtf(token, 64); writeList(b, clauses, Clause::write); writeList(b, signers, Signer::write); b.writeBoolean(maySign); b.writeBoolean(prepared); b.writeVarLong(expiresIn); }
        static Draft read(FriendlyByteBuf b) { return new Draft(b.readUtf(64), readList(b, Clause::read), readList(b, Signer::read), b.readBoolean(), b.readBoolean(), b.readVarLong()); }
    }

    public record Clause(Text label, Text detail) {
        void write(FriendlyByteBuf b) { label.write(b); detail.write(b); }
        static Clause read(FriendlyByteBuf b) { return new Clause(Text.read(b), Text.read(b)); }
    }

    /** One signature line: who, in what office, their seal, and the day they pressed it (empty until signed). */
    public record Signer(Text name, Text office, Text date, com.aetherianartificer.townstead.seal.PersonalSeal seal, boolean signed, boolean you) {
        void write(FriendlyByteBuf b) { name.write(b); office.write(b); date.write(b); seal.write(b); b.writeBoolean(signed); b.writeBoolean(you); }
        static Signer read(FriendlyByteBuf b) {
            return new Signer(Text.read(b), Text.read(b), Text.read(b), com.aetherianartificer.townstead.seal.PersonalSeal.read(b), b.readBoolean(), b.readBoolean());
        }
    }

    public record Role(Text name, List<Text> holders) {
        public Role { holders = List.copyOf(holders); }
        void write(FriendlyByteBuf buf) { name.write(buf); writeList(buf, holders, Text::write); }
        static Role read(FriendlyByteBuf buf) { return new Role(Text.read(buf), readList(buf, Text::read)); }
    }

    public record Action(String id, Text label, Text description, boolean personInput) {
        void write(FriendlyByteBuf buf) { buf.writeUtf(id, 256); label.write(buf); description.write(buf); buf.writeBoolean(personInput); }
        static Action read(FriendlyByteBuf buf) { return new Action(buf.readUtf(256), Text.read(buf), Text.read(buf), buf.readBoolean()); }
    }

    public record Request(String id, Text faction, Text person, String kind, String state, int approvals,
                          int required, List<Action> actions) {
        public Request { actions = List.copyOf(actions); }
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 256); faction.write(buf); person.write(buf); buf.writeUtf(kind, 64); buf.writeUtf(state, 64);
            buf.writeVarInt(approvals); buf.writeVarInt(required); writeList(buf, actions, Action::write);
        }
        static Request read(FriendlyByteBuf buf) { return new Request(buf.readUtf(256), Text.read(buf), Text.read(buf),
                buf.readUtf(64), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), readList(buf, Action::read)); }
    }

    public record Heraldry(String actor, Text name, String recipe, long revision, boolean editable, Livery livery) {
        void write(FriendlyByteBuf b) { b.writeUtf(actor, 512); name.write(b); b.writeUtf(recipe, 1024); b.writeLong(revision); b.writeBoolean(editable); livery.write(b); }
        static Heraldry read(FriendlyByteBuf b) { return new Heraldry(b.readUtf(512), Text.read(b), b.readUtf(1024), b.readLong(), b.readBoolean(), Livery.read(b)); }
    }

    /**
     * A body's proclaimed livery. {@code style} is empty when it wears what it would by default, which
     * {@code inherited} names (the faction above's, or the culture's), so the desk can say so.
     */
    public record Livery(String style, int primary, int secondary, long revision, Text inherited,
                         @Nullable com.aetherianartificer.townstead.livery.LiveryView inheritedView) {
        void write(FriendlyByteBuf b) {
            b.writeUtf(style, 256); b.writeInt(primary); b.writeInt(secondary); b.writeLong(revision); inherited.write(b);
            b.writeBoolean(inheritedView != null); if (inheritedView != null) inheritedView.write(b);
        }
        static Livery read(FriendlyByteBuf b) {
            return new Livery(b.readUtf(256), b.readInt(), b.readInt(), b.readLong(), Text.read(b),
                    b.readBoolean() ? com.aetherianartificer.townstead.livery.LiveryView.read(b) : null);
        }
    }

    /** A livery style the desk offers: its id, name, and how it looks at its own colors, for the preview. */
    public record StyleOption(String id, Text name, com.aetherianartificer.townstead.livery.LiveryView view) {
        void write(FriendlyByteBuf b) { b.writeUtf(id, 256); name.write(b); view.write(b); }
        static StyleOption read(FriendlyByteBuf b) { return new StyleOption(b.readUtf(256), Text.read(b), com.aetherianartificer.townstead.livery.LiveryView.read(b)); }
    }

    public record Civic(String provider, String actor, String state, boolean controlsGovernment, boolean mayManage,
                        Text governance, Text standing, Text description, List<Role> offices, List<Text> history, List<Action> actions) {
        public Civic { offices = List.copyOf(offices); history = List.copyOf(history); actions = List.copyOf(actions); }
        void write(FriendlyByteBuf b) {
            b.writeUtf(provider, 256); b.writeUtf(actor, 256); b.writeUtf(state, 64); b.writeBoolean(controlsGovernment); b.writeBoolean(mayManage);
            governance.write(b); standing.write(b); description.write(b);
            writeList(b, offices, Role::write); writeList(b, history, Text::write); writeList(b, actions, Action::write);
        }
        static Civic read(FriendlyByteBuf b) { return new Civic(b.readUtf(256), b.readUtf(256), b.readUtf(64), b.readBoolean(), b.readBoolean(),
                Text.read(b), Text.read(b), Text.read(b), readList(b, Role::read), readList(b, Text::read), readList(b, Action::read)); }
    }

    /** Residents by culture. Only loaded residents can be read, so {@code uncounted} says how many were left out. */
    public record CensusScope(String id, Text label, List<CensusGroup> groups, int population, int uncounted, boolean available) {
        public CensusScope { groups = List.copyOf(groups); }
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 256); label.write(buf); writeList(buf, groups, CensusGroup::write); buf.writeVarInt(population);
            buf.writeVarInt(uncounted); buf.writeBoolean(available);
        }
        static CensusScope read(FriendlyByteBuf buf) {
            return new CensusScope(buf.readUtf(256), Text.read(buf), readList(buf, CensusGroup::read), buf.readVarInt(),
                    buf.readVarInt(), buf.readBoolean());
        }
    }

    public record CensusGroup(String id, Text name, int count, int color) {
        void write(FriendlyByteBuf buf) { buf.writeUtf(id, 256); name.write(buf); buf.writeVarInt(count); buf.writeInt(color); }
        static CensusGroup read(FriendlyByteBuf buf) { return new CensusGroup(buf.readUtf(256), Text.read(buf), buf.readVarInt(), buf.readInt()); }
    }

    @FunctionalInterface private interface Writer<T> { void write(T value, FriendlyByteBuf buf); }
    @FunctionalInterface private interface Reader<T> { T read(FriendlyByteBuf buf); }
}
