package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;

import java.util.List;

/**
 * One gene's display data for the catalog sync / picker, flattened to primitives
 * so the client renders it without the server-only {@code GeneRegistry} or Java
 * gene types.
 *
 * <p>{@code displayKind} is {@link GeneDisplay.Kind#ordinal()}
 * (0 = RANGE, 1 = BOOLEAN, 2 = INFLUENCE, 3 = COLOR). {@code min}/{@code max} apply to
 * RANGE; {@code targetId} to INFLUENCE (target gene) and COLOR (two hex endpoints
 * {@code "rrggbb-rrggbb"}, read via {@link #colorFrom()}/{@link #colorTo()});
 * {@code amount} to INFLUENCE. {@code dominanceOrdinal} is
 * {@link com.aetherianartificer.townstead.origin.gene.Dominance#ordinal()}
 * (0 = DOMINANT, 1 = RECESSIVE); {@code locus} is empty when none.</p>
 */
public record GeneCatalogEntry(
        String id,
        String name,
        String description,
        String category,
        int displayKind,
        float min,
        float max,
        String targetId,
        float amount,
        int dominanceOrdinal,
        String locus,
        int weight,
        List<Variant> variants,
        // Translate keys for name/description, resolved into the strings above by a
        // localized client at sync-read time (empty when the source was literal).
        String nameKey,
        String descriptionKey,
        // Face-overlay slot for a custom-face gene: "eyes" / "mouth" / "eye_color", else "". Lets the
        // client face layer identify the eyes/mouth/colour genes among an origin's inherited genes.
        String faceSlot
) {
    public GeneCatalogEntry {
        variants = variants == null ? List.of() : List.copyOf(variants);
        faceSlot = faceSlot == null ? "" : faceSlot;
    }

    /**
     * One option of a VARIANTS gene: its id, resolved label, roll weight, the label's translate key,
     * a colour tint ({@code 0xRRGGBB}, or {@code -1} when none), and — for a face eyes/mouth variant —
     * its sprite-strip {@code texture} ({@code ""} when none) and {@code glow} (emissive eyes) flag.
     */
    public record Variant(String id, String label, int weight, String labelKey, int tint,
                          String texture, boolean glow) {
        public Variant {
            texture = texture == null ? "" : texture;
        }
    }

    /** This gene's face slot is eyes / mouth / eye_color (a custom-face overlay gene). */
    public boolean isEyes() { return "eyes".equals(faceSlot); }
    public boolean isMouth() { return "mouth".equals(faceSlot); }
    public boolean isEyeColor() { return "eye_color".equals(faceSlot); }
    public boolean isFace() { return !faceSlot.isEmpty(); }

    public boolean isVariants() {
        return displayKind == GeneDisplay.Kind.VARIANTS.ordinal();
    }

    public boolean isRange() {
        return displayKind == GeneDisplay.Kind.RANGE.ordinal();
    }

    public boolean isInfluence() {
        return displayKind == GeneDisplay.Kind.INFLUENCE.ordinal();
    }

    public boolean isColor() {
        return displayKind == GeneDisplay.Kind.COLOR.ordinal();
    }

    public boolean isAttachment() {
        return displayKind == GeneDisplay.Kind.ATTACHMENT.ordinal();
    }

    /** True when this gene carries per-part render multipliers (a stocky-build "Proportions" gene). */
    public boolean isProportions() {
        return displayKind == GeneDisplay.Kind.PROPORTIONS.ordinal();
    }

    /**
     * Render multiplier for a model-part group ({@code "head"}/{@code "arms"}/{@code "legs"}/{@code "body"})
     * from a PROPORTIONS gene, parsed from {@code targetId} {@code "head=1.0;arms=1.0;..."}.
     * Returns {@link Float#NaN} when the part isn't listed (that part keeps MCA's normal squash).
     */
    public float proportionScale(String part) {
        if (targetId == null || targetId.isEmpty()) return Float.NaN;
        for (String entry : targetId.split(";")) {
            int eq = entry.indexOf('=');
            if (eq <= 0) continue;
            if (entry.substring(0, eq).equals(part)) {
                try {
                    return Float.parseFloat(entry.substring(eq + 1).trim());
                } catch (NumberFormatException e) {
                    return Float.NaN;
                }
            }
        }
        return Float.NaN;
    }

    /** For ATTACHMENT genes, the attachment id (rides in {@code targetId}). */
    public String attachmentId() { return targetId; }

    public boolean isHideFeature() {
        return displayKind == GeneDisplay.Kind.HIDE_FEATURE.ordinal();
    }

    /** True when this gene grants an innate ability (ability key rides in {@code targetId}). */
    public boolean isAbility() {
        return displayKind == GeneDisplay.Kind.ABILITY.ordinal();
    }

    /** For ABILITY genes, the granted ability key (e.g. {@code night_vision}); empty otherwise. */
    public String abilityKey() {
        return isAbility() && targetId != null ? targetId : "";
    }

    /** For ABILITY genes, whether it is toggle-mode (rides in {@code amount}); false = always-on passive. */
    public boolean abilityToggle() {
        return isAbility() && Math.round(amount) == 1;
    }

    /** True when this gene draws a full-screen HUD overlay (texture rides in {@code targetId}). */
    public boolean isOverlay() {
        return displayKind == GeneDisplay.Kind.OVERLAY.ordinal();
    }

    /** For OVERLAY genes, the texture id to blit full-screen; empty otherwise. */
    public String overlayTexture() {
        return isOverlay() && targetId != null ? targetId : "";
    }

    /** For OVERLAY genes, the draw alpha 0–1 (rides in {@code min}). */
    public float overlayAlpha() {
        return isOverlay() ? Math.max(0f, Math.min(1f, min)) : 1f;
    }

    /** Whether a HIDE_FEATURE gene hides the given part group ({@code head}/{@code body}/{@code arms}/{@code legs}). */
    public boolean hidesPart(String group) {
        if (targetId == null || targetId.isEmpty()) return false;
        for (String entry : targetId.split(";")) {
            if (entry.equals(group)) return true;
        }
        return false;
    }

    /**
     * COLOR tint (RGB), parsed from {@code targetId} {@code "rrggbb-rrggbb"} (both parts equal —
     * the gene carries one tint colour, multiplied over MCA's exact skin; white = unchanged).
     */
    public int colorFrom() { return colorPart(0, 0xFFFFFF); }
    public int colorTo()   { return colorPart(1, colorFrom()); }

    /** COLOR blend mode (rides in {@code amount}): 0 multiply/darken, 1 screen/lighten, 2 overlay/both. */
    public int blendMode() { return Math.round(amount); }

    /** COLOR blend strength 0–1 (rides in {@code min}): how strongly the tint is applied. */
    public float blendStrength() { return Math.max(0f, Math.min(1f, min)); }

    private int colorPart(int idx, int fallback) {
        if (targetId == null) return fallback;
        String[] parts = targetId.split("-");
        if (idx >= parts.length) return fallback;
        try {
            return Integer.parseInt(parts[idx].trim(), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** True when this gene emits ambient particles (emitter params ride in {@code targetId}). */
    public boolean isParticle() {
        return displayKind == GeneDisplay.Kind.PARTICLE.ordinal();
    }

    /** PARTICLE emitter params, parsed from {@code targetId} {@code "particleId;count;spread;speed;yOffset"}. */
    public String particleId() { return particlePart(0, ""); }
    public int particleCount() { return (int) particleFloat(1, 1f); }
    public float particleSpread() { return particleFloat(2, 0.4f); }
    public float particleSpeed() { return particleFloat(3, 0f); }
    public float particleYOffset() { return particleFloat(4, 0.6f); }

    private String particlePart(int idx, String fallback) {
        if (targetId == null || targetId.isEmpty()) return fallback;
        String[] parts = targetId.split(";");
        return idx < parts.length ? parts[idx] : fallback;
    }

    private float particleFloat(int idx, float fallback) {
        try {
            return Float.parseFloat(particlePart(idx, Float.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** True when this gene switches off one or more needs (suppressed needs ride in {@code targetId}). */
    public boolean isSuppressNeed() {
        return displayKind == GeneDisplay.Kind.SUPPRESS_NEED.ordinal();
    }

    /** Whether this gene suppresses the given need ({@code hunger}/{@code thirst}/{@code sleep}). */
    public boolean suppressesNeed(String need) {
        if (!isSuppressNeed() || targetId == null || targetId.isEmpty()) return false;
        for (String entry : targetId.split(";")) {
            if (entry.equals(need)) return true;
        }
        return false;
    }

    /** True when this gene moves freely through "stuck" blocks (cobweb, ...); the block ids ride in {@code targetId}. */
    public boolean isStuckImmunity() {
        return displayKind == com.aetherianartificer.townstead.origin.gene.GeneDisplay.Kind.STUCK_IMMUNITY.ordinal();
    }

    /** The block ids a STUCK_IMMUNITY gene passes through, unpacked from {@code targetId}; empty otherwise. */
    public java.util.List<net.minecraft.resources.ResourceLocation> stuckBlocks() {
        if (!isStuckImmunity() || targetId == null || targetId.isEmpty()) return java.util.List.of();
        java.util.List<net.minecraft.resources.ResourceLocation> out = new java.util.ArrayList<>();
        for (String s : targetId.split(";")) {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(s.trim());
            if (id != null) out.add(id);
        }
        return out;
    }

    public boolean isRecessive() {
        return dominanceOrdinal == com.aetherianartificer.townstead.origin.gene.Dominance.RECESSIVE.ordinal();
    }
}
