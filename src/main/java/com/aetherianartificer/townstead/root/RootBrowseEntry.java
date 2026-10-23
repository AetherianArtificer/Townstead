package com.aetherianartificer.townstead.root;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * What the root browser needs about one root beyond its {@link RootCatalogEntry}: where it sits
 * (species, ancestry and pack ids), its art, whether it is featured, the at-a-glance facts
 * (diet, activity, lifespan and whatever else sets it apart), the trait keys the browser filters
 * on, and its powers in order. Everything here is derived server-side from the root's genes, so a
 * pack author writes nothing extra for it.
 */
public record RootBrowseEntry(
        String rootId,
        String icon,
        String speciesId,
        String ancestryId,
        String lineageId,
        String pack,
        Text packName,
        Text packQualifier,
        boolean featured,
        List<Fact> facts,
        List<String> traits,
        List<Power> powers
) {
    public RootBrowseEntry {
        icon = icon == null ? "" : icon;
        facts = facts == null ? List.of() : List.copyOf(facts);
        traits = traits == null ? List.of() : List.copyOf(traits);
        powers = powers == null ? List.of() : List.copyOf(powers);
    }

    /** A translatable string: the key (empty for literal text), its arguments, and the English fallback. */
    public record Text(String key, List<String> args, String fallback) {
        public Text {
            key = key == null ? "" : key;
            args = args == null ? List.of() : List.copyOf(args);
            fallback = fallback == null ? "" : fallback;
        }

        public static Text literal(String text) {
            return new Text("", List.of(), text);
        }

        public static Text of(String key, String fallback, String... args) {
            return new Text(key, List.of(args), fallback);
        }
    }

    /** One fact line: its kind ({@code "diet"}, {@code "lifespan"}...), which names its label, and its value. */
    public record Fact(String kind, Text value) {}

    /** One power: the gene, how it reads (active, strength or drawback), and an active's cooldown. */
    public record Power(String geneId, Kind kind, int cooldownTicks) {
        public enum Kind { ACTIVE, STRENGTH, DRAWBACK }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(rootId);
        buf.writeUtf(icon);
        buf.writeUtf(speciesId);
        buf.writeUtf(ancestryId);
        buf.writeUtf(lineageId);
        buf.writeUtf(pack);
        writeText(buf, packName);
        writeText(buf, packQualifier);
        buf.writeBoolean(featured);
        buf.writeVarInt(facts.size());
        for (Fact fact : facts) {
            buf.writeUtf(fact.kind());
            writeText(buf, fact.value());
        }
        buf.writeVarInt(traits.size());
        for (String trait : traits) buf.writeUtf(trait);
        buf.writeVarInt(powers.size());
        for (Power power : powers) {
            buf.writeUtf(power.geneId());
            buf.writeVarInt(power.kind().ordinal());
            buf.writeVarInt(power.cooldownTicks());
        }
    }

    public static RootBrowseEntry read(FriendlyByteBuf buf) {
        String rootId = buf.readUtf();
        String icon = buf.readUtf();
        String speciesId = buf.readUtf();
        String ancestryId = buf.readUtf();
        String lineageId = buf.readUtf();
        String pack = buf.readUtf();
        Text packName = readText(buf);
        Text packQualifier = readText(buf);
        boolean featured = buf.readBoolean();
        int factCount = buf.readVarInt();
        List<Fact> facts = new ArrayList<>(factCount);
        for (int i = 0; i < factCount; i++) facts.add(new Fact(buf.readUtf(), readText(buf)));
        int traitCount = buf.readVarInt();
        List<String> traits = new ArrayList<>(traitCount);
        for (int i = 0; i < traitCount; i++) traits.add(buf.readUtf());
        int powerCount = buf.readVarInt();
        List<Power> powers = new ArrayList<>(powerCount);
        Power.Kind[] kinds = Power.Kind.values();
        for (int i = 0; i < powerCount; i++) {
            String geneId = buf.readUtf();
            int kind = buf.readVarInt();
            int cooldown = buf.readVarInt();
            powers.add(new Power(geneId, kinds[Math.max(0, Math.min(kinds.length - 1, kind))], cooldown));
        }
        return new RootBrowseEntry(rootId, icon, speciesId, ancestryId, lineageId, pack, packName, packQualifier,
                featured, facts, traits, powers);
    }

    private static void writeText(FriendlyByteBuf buf, Text text) {
        buf.writeUtf(text.key());
        buf.writeVarInt(text.args().size());
        for (String arg : text.args()) buf.writeUtf(arg);
        buf.writeUtf(text.fallback());
    }

    private static Text readText(FriendlyByteBuf buf) {
        String key = buf.readUtf();
        int n = buf.readVarInt();
        List<String> args = new ArrayList<>(n);
        for (int i = 0; i < n; i++) args.add(buf.readUtf());
        return new Text(key, args, buf.readUtf());
    }
}
