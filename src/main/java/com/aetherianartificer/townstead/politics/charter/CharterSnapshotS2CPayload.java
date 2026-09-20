package com.aetherianartificer.townstead.politics.charter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
//?}

import java.util.ArrayList;
import java.util.List;

/** Server-filtered presentation model for the native Charter screen. */
//? if neoforge {
public record CharterSnapshotS2CPayload(BlockPos lectern, BlockPos bell, int state, boolean editable,
                                        String settlement, String polity, Text governance,
                                        Text authority, Text foundingCulture, String message,
                                        List<Option> profiles, List<Option> cultures,
                                        List<Organization> organizations, List<Tie> ties,
                                        List<CensusGroup> census, int population, List<Request> requests, long revision, List<CensusScope> censusScopes, Civic civic, List<Heraldry> heraldry) implements CustomPacketPayload {
    public static final Type<CharterSnapshotS2CPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("townstead", "charter_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, CharterSnapshotS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> value.write(buf), CharterSnapshotS2CPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
//?} else {
/*public record CharterSnapshotS2CPayload(BlockPos lectern, BlockPos bell, int state, boolean editable,
                                        String settlement, String polity, Text governance,
                                        Text authority, Text foundingCulture, String message,
                                        List<Option> profiles, List<Option> cultures,
                                        List<Organization> organizations, List<Tie> ties,
                                        List<CensusGroup> census, int population, List<Request> requests, long revision, List<CensusScope> censusScopes, Civic civic, List<Heraldry> heraldry) {
*///?}
    public static final int UNAVAILABLE = 0, UNFOUNDED = 1, PREPARED = 2, FOUNDED = 3, REPAIR = 4,
            EXISTING = 5;

    public CharterSnapshotS2CPayload(BlockPos lectern, BlockPos bell, int state, boolean editable,
            String settlement, String polity, Text governance, Text authority, Text foundingCulture, String message,
            List<Option> profiles, List<Option> cultures, List<Organization> organizations, List<Tie> ties,
            List<CensusGroup> census, int population) {
        this(lectern, bell, state, editable, settlement, polity, governance, authority, foundingCulture, message,
                profiles, cultures, organizations, ties, census, population, List.of(), 0);
    }

    public CharterSnapshotS2CPayload(BlockPos lectern, BlockPos bell, int state, boolean editable,
            String settlement, String polity, Text governance, Text authority, Text foundingCulture, String message,
            List<Option> profiles, List<Option> cultures, List<Organization> organizations, List<Tie> ties,
            List<CensusGroup> census, int population, List<Request> requests, long revision) {
        this(lectern, bell, state, editable, settlement, polity, governance, authority, foundingCulture, message,
                profiles, cultures, organizations, ties, census, population, requests, revision, List.of());
    }

    public CharterSnapshotS2CPayload(BlockPos lectern, BlockPos bell, int state, boolean editable,
            String settlement, String polity, Text governance, Text authority, Text foundingCulture, String message,
            List<Option> profiles, List<Option> cultures, List<Organization> organizations, List<Tie> ties,
            List<CensusGroup> census, int population, List<Request> requests, long revision, List<CensusScope> censusScopes) {
        this(lectern, bell, state, editable, settlement, polity, governance, authority, foundingCulture, message,
                profiles, cultures, organizations, ties, census, population, requests, revision, censusScopes, null);
    }

    public CharterSnapshotS2CPayload(BlockPos lectern, BlockPos bell, int state, boolean editable,
            String settlement, String polity, Text governance, Text authority, Text foundingCulture, String message,
            List<Option> profiles, List<Option> cultures, List<Organization> organizations, List<Tie> ties,
            List<CensusGroup> census, int population, List<Request> requests, long revision, List<CensusScope> censusScopes, Civic civic) {
        this(lectern, bell, state, editable, settlement, polity, governance, authority, foundingCulture, message,
                profiles, cultures, organizations, ties, census, population, requests, revision, censusScopes, civic, List.of());
    }

    public CharterSnapshotS2CPayload {
        heraldry = List.copyOf(heraldry);
        requests = List.copyOf(requests);
        censusScopes = List.copyOf(censusScopes);
        profiles = List.copyOf(profiles);
        cultures = List.copyOf(cultures);
        organizations = List.copyOf(organizations);
        ties = List.copyOf(ties);
        census = List.copyOf(census);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(lectern); buf.writeBlockPos(bell); buf.writeVarInt(state); buf.writeBoolean(editable);
        buf.writeUtf(settlement, 128); buf.writeUtf(polity, 128);
        governance.write(buf); authority.write(buf); foundingCulture.write(buf); buf.writeUtf(message, 512);
        writeList(buf, profiles, Option::write); writeList(buf, cultures, Option::write);
        writeList(buf, organizations, Organization::write); writeList(buf, ties, Tie::write);
        writeList(buf, census, CensusGroup::write); buf.writeVarInt(population); writeList(buf, requests, Request::write); buf.writeLong(revision); writeList(buf, censusScopes, CensusScope::write); buf.writeBoolean(civic != null); if (civic != null) civic.write(buf); writeList(buf, heraldry, Heraldry::write);
    }

    public static CharterSnapshotS2CPayload read(FriendlyByteBuf buf) {
        return new CharterSnapshotS2CPayload(buf.readBlockPos(), buf.readBlockPos(), buf.readVarInt(), buf.readBoolean(),
                buf.readUtf(128), buf.readUtf(128), Text.read(buf), Text.read(buf), Text.read(buf), buf.readUtf(512),
                readList(buf, Option::read), readList(buf, Option::read), readList(buf, Organization::read),
                readList(buf, Tie::read), readList(buf, CensusGroup::read), buf.readVarInt(), readList(buf, Request::read), buf.readLong(), readList(buf, CensusScope::read), buf.readBoolean() ? Civic.read(buf) : null, readList(buf, Heraldry::read));
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
        public Option(String id, Text name, Text description, Text consequences, boolean government) {
            this(id, name, description, consequences, government, List.of());
        }
        public Option { naming = List.copyOf(naming); }
        void write(FriendlyByteBuf buf) { buf.writeUtf(id, 256); name.write(buf); description.write(buf); consequences.write(buf); buf.writeBoolean(government); writeList(buf, naming, (v, b) -> b.writeUtf(v, 128)); }
        static Option read(FriendlyByteBuf buf) { return new Option(buf.readUtf(256), Text.read(buf), Text.read(buf), Text.read(buf), buf.readBoolean(), readList(buf, b -> b.readUtf(128))); }
    }

    public record Role(Text name, List<Text> holders) {
        public Role { holders = List.copyOf(holders); }
        void write(FriendlyByteBuf buf) { name.write(buf); writeList(buf, holders, Text::write); }
        static Role read(FriendlyByteBuf buf) { return new Role(Text.read(buf), readList(buf, Text::read)); }
    }

    public record Organization(String id, Text name, Text kind, Text description, String relationship,
                               boolean governing, String icon, int color, Text admission, Text departure,
                               List<Text> yourRoles, List<Role> roles, List<Action> actions) {
        public Organization(String id, Text name, Text kind, Text description, String relationship, boolean governing,
                String icon, int color, Text admission, Text departure, List<Text> yourRoles, List<Role> roles) {
            this(id, name, kind, description, relationship, governing, icon, color, admission, departure, yourRoles, roles, List.of());
        }
        public Organization { actions = List.copyOf(actions); yourRoles = List.copyOf(yourRoles); roles = List.copyOf(roles); }
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 256); name.write(buf); kind.write(buf); description.write(buf); buf.writeUtf(relationship, 256);
            buf.writeBoolean(governing); buf.writeUtf(icon, 512); buf.writeInt(color); admission.write(buf); departure.write(buf);
            writeList(buf, yourRoles, Text::write); writeList(buf, roles, Role::write); writeList(buf, actions, Action::write);
        }
        static Organization read(FriendlyByteBuf buf) {
            return new Organization(buf.readUtf(256), Text.read(buf), Text.read(buf), Text.read(buf), buf.readUtf(256),
                    buf.readBoolean(), buf.readUtf(512), buf.readInt(), Text.read(buf), Text.read(buf),
                    readList(buf, Text::read), readList(buf, Role::read), readList(buf, Action::read));
        }
    }

    public record Action(String id, Text label, Text description, boolean personInput) {
        void write(FriendlyByteBuf buf) { buf.writeUtf(id, 256); label.write(buf); description.write(buf); buf.writeBoolean(personInput); }
        static Action read(FriendlyByteBuf buf) { return new Action(buf.readUtf(256), Text.read(buf), Text.read(buf), buf.readBoolean()); }
    }
    public record Request(String id, Text organization, Text person, String kind, String state, int approvals,
                          int required, List<Action> actions) {
        public Request { actions = List.copyOf(actions); }
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 256); organization.write(buf); person.write(buf); buf.writeUtf(kind, 64); buf.writeUtf(state, 64);
            buf.writeVarInt(approvals); buf.writeVarInt(required); writeList(buf, actions, Action::write);
        }
        static Request read(FriendlyByteBuf buf) { return new Request(buf.readUtf(256), Text.read(buf), Text.read(buf),
                buf.readUtf(64), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), readList(buf, Action::read)); }
    }

    public record Tie(String actor, String kind, String roles, String status) {
        void write(FriendlyByteBuf buf) { buf.writeUtf(actor, 256); buf.writeUtf(kind, 256); buf.writeUtf(roles, 512); buf.writeUtf(status, 128); }
        static Tie read(FriendlyByteBuf buf) { return new Tie(buf.readUtf(256), buf.readUtf(256), buf.readUtf(512), buf.readUtf(128)); }
    }

    public record Heraldry(String actor, Text name, String recipe, long revision, boolean editable) {
        void write(FriendlyByteBuf b) { b.writeUtf(actor, 512); name.write(b); b.writeUtf(recipe, 1024); b.writeLong(revision); b.writeBoolean(editable); }
        static Heraldry read(FriendlyByteBuf b) { return new Heraldry(b.readUtf(512), Text.read(b), b.readUtf(1024), b.readLong(), b.readBoolean()); }
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

    public record CensusScope(String id, Text label, List<CensusGroup> groups, int population, boolean available) {
        public CensusScope { groups = List.copyOf(groups); }
        void write(FriendlyByteBuf buf) {
            buf.writeUtf(id, 256); label.write(buf); writeList(buf, groups, CensusGroup::write); buf.writeVarInt(population); buf.writeBoolean(available);
        }
        static CensusScope read(FriendlyByteBuf buf) {
            return new CensusScope(buf.readUtf(256), Text.read(buf), readList(buf, CensusGroup::read), buf.readVarInt(), buf.readBoolean());
        }
    }

    public record CensusGroup(String id, Text name, int count, int color) {
        void write(FriendlyByteBuf buf) { buf.writeUtf(id, 256); name.write(buf); buf.writeVarInt(count); buf.writeInt(color); }
        static CensusGroup read(FriendlyByteBuf buf) { return new CensusGroup(buf.readUtf(256), Text.read(buf), buf.readVarInt(), buf.readInt()); }
    }

    @FunctionalInterface private interface Writer<T> { void write(T value, FriendlyByteBuf buf); }
    @FunctionalInterface private interface Reader<T> { T read(FriendlyByteBuf buf); }
}
