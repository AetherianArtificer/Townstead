package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.chronicle.ChronicleSocialKnowledge;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.social.SocialKnowledge;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import java.util.List;
import java.util.UUID;

/** Reusable numbers for ordinary pheno:value gates, arithmetic and author-defined evaluations. */
public final class ChronicleValueType implements ValueType {
    public enum Kind { MEMORY, SENTIMENT, CHRONICLE_COUNT }
    private final Kind kind;
    public ChronicleValueType(Kind kind) { this.kind = kind; }
    @Override public String key() { return "pheno:" + kind.name().toLowerCase(java.util.Locale.ROOT); }
    @Override public Value parse(JsonObject json) {
        String key = GsonHelper.getAsString(json, "key", "");
        if (kind == Kind.CHRONICLE_COUNT) {
            if (key.isBlank()) return null;
            return Value.subjectAware(ctx -> ctx.subject() != null ? ctx.subject().counter(key)
                    : ctx.self() != null && ctx.level() instanceof ServerLevel level
                    ? Chronicles.count(level.getServer(), ctx.self().getUUID(), key) : Double.NaN);
        }
        boolean memory = kind == Kind.MEMORY;
        SocialTarget target = SocialTarget.parse(GsonHelper.getAsString(json, memory ? "about" : "toward", memory ? "any" : "other"), memory);
        if (target == null) return null;
        String metric = GsonHelper.getAsString(json, "metric", "count");
        if (memory && !List.of("count", "strength", "valence", "age_days").contains(metric)) return null;
        return Value.subjectAware(ctx -> {
            SocialKnowledge knowledge = ChronicleSocialKnowledge.of(ctx);
            UUID other = target.resolve(ctx);
            if (knowledge == null || !target.any() && other == null) return Double.NaN;
            if (!memory) return knowledge.sentiment(other);
            List<SocialKnowledge.Memory> matches = knowledge.memories().stream()
                    .filter(m -> (key.isEmpty() || key.equals(m.key())) && (target.any() || other.equals(m.other())))
                    .filter(m -> Double.isFinite(m.strength()) && m.strength() > 0).toList();
            return switch (metric) {
                case "strength" -> matches.stream().mapToDouble(SocialKnowledge.Memory::strength).sum();
                case "age_days" -> matches.isEmpty() ? Double.NaN
                        : Math.max(0, knowledge.today() - matches.stream().mapToLong(SocialKnowledge.Memory::lastDay).max().orElseThrow());
                case "valence" -> matches.isEmpty() ? Double.NaN
                        : matches.stream().mapToDouble(m -> m.valence() * m.strength()).sum()
                            / matches.stream().mapToDouble(SocialKnowledge.Memory::strength).sum();
                default -> matches.stream().mapToDouble(m -> Math.max(0, m.count())).sum();
            };
        });
    }
}
