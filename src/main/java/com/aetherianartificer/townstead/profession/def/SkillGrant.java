package com.aetherianartificer.townstead.profession.def;

import com.aetherianartificer.townstead.pheno.capability.CapabilityContribution;
import com.aetherianartificer.townstead.pheno.capability.CapabilityKey;
import com.aetherianartificer.townstead.pheno.capability.Op;
import com.aetherianartificer.townstead.pheno.capability.Provenance;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A capability a skill contributes once learned, declared in skill JSON and folded through the
 * shared capability resolver at runtime (with the skill as provenance). A grant is either a
 * flag ({@code "flag": true}) or a numeric op ({@code "op": "multiply", "value": 1.2}).
 *
 * <pre>
 * { "capability": "townstead:mining_speed", "op": "multiply", "value": 1.2 }
 * { "capability": "townstead:can_smith_netherite", "flag": true }
 * </pre>
 */
public record SkillGrant(
        CapabilityKey key,
        Op op,
        double value,
        int priority,
        @Nullable String stackingGroup,
        @Nullable String exclusivityGroup) {

    public CapabilityContribution toContribution(Provenance provenance, boolean active) {
        return new CapabilityContribution(key, op, value, priority, stackingGroup, exclusivityGroup, provenance, active);
    }

    @Nullable
    public static SkillGrant parse(JsonObject json) {
        ResourceLocation id = ResourceLocation.tryParse(GsonHelper.getAsString(json, "capability", ""));
        if (id == null) return null;
        int priority = GsonHelper.getAsInt(json, "priority", 0);
        String stacking = json.has("stacking_group") ? GsonHelper.getAsString(json, "stacking_group", null) : null;
        String exclusivity = json.has("exclusivity_group")
                ? GsonHelper.getAsString(json, "exclusivity_group", null) : null;
        if (GsonHelper.getAsBoolean(json, "flag", false)) {
            return new SkillGrant(CapabilityKey.flag(id), Op.OR, 1d, priority, stacking, exclusivity);
        }
        Op op = parseOp(GsonHelper.getAsString(json, "op", "add"));
        double value = GsonHelper.getAsDouble(json, "value", op == Op.MULTIPLY ? 1d : 0d);
        CapabilityKey key = op == Op.MULTIPLY ? CapabilityKey.scalar(id) : CapabilityKey.additive(id);
        return new SkillGrant(key, op, value, priority, stacking, exclusivity);
    }

    private static Op parseOp(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "multiply" -> Op.MULTIPLY;
            case "min" -> Op.MIN;
            case "max" -> Op.MAX;
            case "replace", "set" -> Op.REPLACE;
            case "deny" -> Op.DENY;
            default -> Op.ADD;
        };
    }
}
