package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import com.aetherianartificer.townstead.root.gene.GeneInstance;
import com.aetherianartificer.townstead.root.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A cosmetic model attachment a race wears (elf ears, tusks, horns, …). The
 * {@code attachment} id points at a client-side attachment definition (geometry
 * + texture + anchor part + tint), rendered as an extra layer anchored to the
 * villager's model so it follows animation. {@code attachments} grants a whole
 * set through one gene (horns + tail + spines inheriting together); the set
 * shares the gene's size channels, each definition's morphs reading whichever
 * channels it names. A multi-variant gene whose variants each carry a different
 * {@code attachment} is a heritable style swap (three horn shapes inheriting
 * Mendelian-style).
 *
 * <p>An optional {@code size} block makes the attachment heritable quantitatively.
 * Its channels each roll a value in {@code [min,max]} per allele, two carried
 * copies express their mean, and an optional per-channel heritage coupling
 * multiplies the expressed value by the bearer's share of one ancestry — a
 * half-elf grows visibly smaller elven ears than a full elf. The attachment
 * definition's {@code morph} block maps channel values onto bone scales and
 * rotations on the render side.</p>
 *
 * <p>An optional {@code tint} block makes the attachment's colour heritable: it
 * declares the reserved {@code tint_r}/{@code tint_g}/{@code tint_b} channels
 * (0..1 each), newborn rolls pick a colour from its {@code palette} (with a
 * little jitter), and two carried copies mean-blend per component — children
 * wear a mix of their parents' coats. A definition opts in with
 * {@code "tint": "gene"}; the character editor renders the palette as swatches
 * plus free red/green/blue sliders.</p>
 *
 * <p>JSON, single-channel shorthand (the channel is anonymous):
 * {@code "size": { "min":0.85, "max":1.15, "label":"Ear Size",
 * "heritage": { "ancestry":"townstead_classic:elf", "floor":0.35 } }}</p>
 *
 * <p>JSON, named channels:
 * {@code "size": { "channels": {
 *   "length": { "min":0.85, "max":1.15, "label":"Ear Length",
 *               "heritage": { "ancestry":"townstead_classic:elf", "floor":0.35 } },
 *   "droop":  { "min":0.0, "max":1.0, "label":"Ear Droop" } } }}</p>
 *
 * <p>JSON, heritable colour:
 * {@code "tint": { "palette": ["#B1562B", "#E8D8C0", "#3A3A3A"], "label":"Fur Color" }}</p>
 */
public final class AttachmentGeneType implements GeneType {

    @Override
    public boolean conditionControlsExpression() { return true; }

    public static final String KEY = "townstead_roots:attachment";

    /** The reserved channel names a {@code tint} block declares (one colour component each). */
    public static final String TINT_R = "tint_r", TINT_G = "tint_g", TINT_B = "tint_b";

    /** Whether a channel name is one of the reserved heritable-tint components. */
    public static boolean isTintChannel(String name) {
        return TINT_R.equals(name) || TINT_G.equals(name) || TINT_B.equals(name);
    }

    /**
     * One heritable roll: its name ({@code ""} for the single-channel shorthand),
     * range, optional ancestry coupling, and the editor slider's label
     * ({@code labelKey} = translate key, {@code labelText} = literal fallback;
     * both empty → the editor falls back to the gene's display name).
     */
    public record Channel(String name, float min, float max,
                          @Nullable ResourceLocation heritageAncestry, float heritageFloor,
                          String labelKey, String labelText) {

        /** The heritage multiplier for a bearer holding {@code fraction} of the coupled ancestry. */
        public float heritageFactor(float fraction) {
            if (heritageAncestry == null) return 1f;
            float f = Math.max(0f, Math.min(1f, fraction));
            return heritageFloor + (1f - heritageFloor) * f;
        }
    }

    /**
     * {@code attachments} is every definition this instance wears (one entry for the
     * {@code attachment} shorthand); {@code palette} is the {@code tint} block's
     * preset colours ({@code 0xRRGGBB}), empty when the colour isn't heritable —
     * when it is, {@code channels} also carries the three reserved tint channels.
     */
    public record Instance(List<String> attachments, List<Channel> channels,
                           List<Integer> palette) implements GeneInstance {
        @Override public String typeKey() { return KEY; }

        public boolean sized() { return !channels.isEmpty(); }

        /** Whether this instance rolls a heritable colour (declares the tint channels). */
        public boolean tinted() { return channel(TINT_R) != null; }

        /** The first (primary) attachment id; the joined form rides in the catalog {@code targetId}. */
        public String attachmentId() { return attachments.isEmpty() ? "" : attachments.get(0); }

        @Nullable
        public Channel channel(String name) {
            for (Channel channel : channels) {
                if (channel.name().equals(name)) return channel;
            }
            return null;
        }

        @Override public GeneDisplay display() {
            String joined = String.join(";", attachments);
            for (Channel channel : channels) {
                if (!isTintChannel(channel.name())) {
                    return GeneDisplay.sizedAttachment(joined, channel.min(), channel.max());
                }
            }
            if (!channels.isEmpty()) return GeneDisplay.sizedAttachment(joined, 0f, 1f);
            return GeneDisplay.attachment(joined);
        }
    }

    @Override
    public String key() { return KEY; }

    @Override
    public GeneInstance parse(JsonObject json) {
        // An explicit `"none": true` variant grants nothing — the beardless option of a
        // beard-style gene. It still inherits and cycles like any other variant.
        if (GsonHelper.getAsBoolean(json, "none", false)) {
            return new Instance(List.of(), List.of(), List.of());
        }
        List<String> attachments = new ArrayList<>();
        String single = GsonHelper.getAsString(json, "attachment", "");
        if (!single.isBlank()) attachments.add(single);
        if (json.has("attachments") && json.get("attachments").isJsonArray()) {
            for (var element : json.getAsJsonArray("attachments")) {
                String id = element.getAsString();
                if (!id.isBlank() && !attachments.contains(id)) attachments.add(id);
            }
        }
        if (attachments.isEmpty()) return null;
        List<Channel> channels = new ArrayList<>();
        if (json.has("size") && json.get("size").isJsonObject()) {
            JsonObject sizeJson = json.getAsJsonObject("size");
            if (sizeJson.has("channels") && sizeJson.get("channels").isJsonObject()) {
                for (var entry : sizeJson.getAsJsonObject("channels").entrySet()) {
                    if (!entry.getValue().isJsonObject()) continue;
                    channels.add(parseChannel(entry.getKey(), entry.getValue().getAsJsonObject()));
                }
            } else {
                channels.add(parseChannel("", sizeJson));
            }
        }
        // The tint channels are appended AFTER the size channels: the save self-heal
        // renames a legacy anonymous roll to the first declared channel, which must
        // stay the authored size roll, never a colour component.
        List<Integer> palette = List.of();
        if (json.has("tint") && json.get("tint").isJsonObject()) {
            JsonObject tintJson = json.getAsJsonObject("tint");
            palette = parsePalette(tintJson);
            String labelKey = "";
            String labelText = "";
            if (tintJson.has("label")) {
                var label = tintJson.get("label");
                if (label.isJsonObject()) {
                    labelKey = GsonHelper.getAsString(label.getAsJsonObject(), "translate", "");
                    labelText = GsonHelper.getAsString(label.getAsJsonObject(), "text", "");
                } else if (label.isJsonPrimitive()) {
                    labelText = label.getAsString();
                }
            }
            channels.add(new Channel(TINT_R, 0f, 1f, null, 0f, labelKey, labelText));
            channels.add(new Channel(TINT_G, 0f, 1f, null, 0f, labelKey, labelText));
            channels.add(new Channel(TINT_B, 0f, 1f, null, 0f, labelKey, labelText));
        }
        return new Instance(List.copyOf(attachments), List.copyOf(channels), palette);
    }

    private static List<Integer> parsePalette(JsonObject tintJson) {
        if (!tintJson.has("palette") || !tintJson.get("palette").isJsonArray()) return List.of();
        List<Integer> out = new ArrayList<>();
        for (var element : tintJson.getAsJsonArray("palette")) {
            String raw = element.getAsString();
            String hex = raw.startsWith("#") ? raw.substring(1) : raw;
            try {
                out.add(Integer.parseInt(hex, 16) & 0xFFFFFF);
            } catch (NumberFormatException ignored) {
            }
        }
        return List.copyOf(out);
    }

    private static Channel parseChannel(String name, JsonObject json) {
        ResourceLocation ancestry = null;
        float floor = 0f;
        if (json.has("heritage") && json.get("heritage").isJsonObject()) {
            JsonObject heritage = json.getAsJsonObject("heritage");
            ancestry = DataPackLang.parseId(GsonHelper.getAsString(heritage, "ancestry", ""));
            floor = Math.max(0f, Math.min(1f, GsonHelper.getAsFloat(heritage, "floor", 0f)));
        }
        String labelKey = "";
        String labelText = "";
        if (json.has("label")) {
            var label = json.get("label");
            if (label.isJsonObject()) {
                labelKey = GsonHelper.getAsString(label.getAsJsonObject(), "translate", "");
                labelText = GsonHelper.getAsString(label.getAsJsonObject(), "text", "");
            } else if (label.isJsonPrimitive()) {
                labelText = label.getAsString();
            }
        }
        return new Channel(name, GsonHelper.getAsFloat(json, "min", 1f),
                GsonHelper.getAsFloat(json, "max", 1f), ancestry, floor, labelKey, labelText);
    }
}
