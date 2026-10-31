package com.aetherianartificer.townstead.root.attachment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves {@code physics.chains} entries into {@link AttachmentDef.PhysicsChain}s.
 * Layers apply in order: built-in defaults, the chain's {@code preset}
 * ({@code data/<ns>/physics_preset/<id>.json}), values converted from legacy keys,
 * then the chain's own keys. A bare preset id looks in the attachment's namespace
 * first, then {@code townstead}.
 *
 * <p>Legacy chains (stiffness, damping, max_angle, response, ...) still load: the
 * spring's speed converts to frequency (its damping does not carry), the clamps fold into
 * {@code limit}, and the movement channels fold into {@code inertia}. Each legacy
 * key leaves a note for the attachment doctor.</p>
 */
public final class PhysicsChains {

    public static final float FREQUENCY = 2.0f;
    public static final float DAMPING_RATIO = 0.7f;
    public static final float GRAVITY = 0.15f;
    public static final float INERTIA = 0.5f;
    public static final float DRAG = 1.0f;
    public static final float LIMIT = 35f;
    public static final List<String> HINGES = List.of("none", "pitch", "side");
    /** A chain that names no colliders rests against every collider the rig has. */
    public static final List<String> COLLIDE_ALL = List.of("*");

    private static final List<String> LEGACY_KEYS = List.of("stiffness", "damping", "max_angle", "min_pitch",
            "max_pitch", "follow", "droop_angle", "snap", "response");
    private static final List<String> KNOBS = List.of("frequency", "damping_ratio", "gravity", "inertia",
            "drag", "limit");

    private static volatile Map<String, JsonObject> presets = Map.of();

    private PhysicsChains() {}

    public static void setPresets(Map<String, JsonObject> loaded) {
        presets = Map.copyOf(loaded);
    }

    public static Map<String, JsonObject> presets() {
        return presets;
    }

    public static List<AttachmentDef.PhysicsChain> parse(JsonObject attachment, String namespace) {
        return parse(attachment, namespace, presets);
    }

    public static List<AttachmentDef.PhysicsChain> parse(JsonObject attachment, String namespace,
                                                         Map<String, JsonObject> presetMap) {
        if (!attachment.has("physics") || !attachment.get("physics").isJsonObject()) return List.of();
        JsonObject physics = attachment.getAsJsonObject("physics");
        if (!physics.has("chains") || !physics.get("chains").isJsonArray()) return List.of();
        List<AttachmentDef.PhysicsChain> out = new ArrayList<>();
        for (JsonElement element : physics.getAsJsonArray("chains")) {
            if (!element.isJsonObject()) continue;
            AttachmentDef.PhysicsChain chain = parseChain(element.getAsJsonObject(), namespace, presetMap);
            if (chain != null) out.add(chain);
        }
        return out;
    }

    static AttachmentDef.PhysicsChain parseChain(JsonObject chain, String namespace, Map<String, JsonObject> presetMap) {
        List<String> bones = strings(chain, "bones");
        if (bones.isEmpty()) return null;
        List<String> notes = new ArrayList<>();
        String preset = GsonHelper.getAsString(chain, "preset", "");
        JsonObject merged = new JsonObject();
        if (!preset.isEmpty()) {
            String resolved = resolvePreset(preset, namespace, presetMap);
            if (resolved == null) {
                notes.add("unknown physics preset '" + preset + "'");
            } else {
                preset = resolved;
                presetMap.get(resolved).entrySet().forEach(e -> merged.add(e.getKey(), e.getValue()));
            }
        }
        JsonObject legacy = convertLegacy(chain, notes);
        legacy.entrySet().forEach(e -> merged.add(e.getKey(), e.getValue()));
        for (Map.Entry<String, JsonElement> e : chain.entrySet()) {
            String key = e.getKey();
            if (key.equals("bones") || key.equals("preset") || LEGACY_KEYS.contains(key)) continue;
            // A legacy chain's gravity was a local droop; its converted value stands.
            if (legacy.has(key) && key.equals("gravity")) continue;
            merged.add(key, e.getValue());
        }
        String hinge = GsonHelper.getAsString(merged, "hinge", "none").toLowerCase(Locale.ROOT);
        return new AttachmentDef.PhysicsChain(bones, preset,
                knob(merged, "frequency", FREQUENCY),
                knob(merged, "damping_ratio", DAMPING_RATIO),
                knob(merged, "gravity", GRAVITY),
                knob(merged, "inertia", INERTIA),
                knob(merged, "drag", DRAG),
                knob(merged, "limit", LIMIT),
                HINGES.contains(hinge) ? hinge : "none",
                merged.has("collide") ? strings(merged, "collide") : COLLIDE_ALL,
                GsonHelper.getAsFloat(merged, "rest_pitch", 0f),
                GsonHelper.getAsFloat(merged, "sway", 0f),
                GsonHelper.getAsFloat(merged, "sway_speed", 1f),
                GsonHelper.getAsBoolean(merged, "scale_with_size", true),
                GsonHelper.getAsInt(chain, "segments", 0),
                GsonHelper.getAsString(chain, "axis", "auto"),
                List.copyOf(notes));
    }

    private static String resolvePreset(String preset, String namespace, Map<String, JsonObject> presetMap) {
        if (preset.contains(":")) return presetMap.containsKey(preset) ? preset : null;
        if (presetMap.containsKey(namespace + ":" + preset)) return namespace + ":" + preset;
        if (presetMap.containsKey("townstead:" + preset)) return "townstead:" + preset;
        return null;
    }

    /** New-format values for whatever legacy keys the chain carries. */
    static JsonObject convertLegacy(JsonObject chain, List<String> notes) {
        JsonObject out = new JsonObject();
        boolean legacy = false;
        for (String key : LEGACY_KEYS) legacy |= chain.has(key);
        if (!legacy) return out;
        List<String> found = new ArrayList<>();
        for (String key : LEGACY_KEYS) if (chain.has(key)) found.add(key);
        if (chain.has("stiffness") || chain.has("damping")) {
            float[] spring = legacySpring(GsonHelper.getAsFloat(chain, "stiffness", 0.4f),
                    GsonHelper.getAsFloat(chain, "damping", 0.8f));
            out.addProperty("frequency", round(spring[0]));
            // The old spring's ratio (about 0.3) is what made it bouncy; keep the speed only.
            notes.add("legacy damping (ratio " + round(spring[1]) + ") replaced by damping_ratio "
                    + DAMPING_RATIO + " or the preset's");
        }
        if (chain.has("max_angle") || chain.has("min_pitch") || chain.has("max_pitch")) {
            float maxAngle = Math.abs(GsonHelper.getAsFloat(chain, "max_angle", LIMIT));
            float down = Math.abs(GsonHelper.getAsFloat(chain, "min_pitch", maxAngle));
            float up = Math.abs(GsonHelper.getAsFloat(chain, "max_pitch", maxAngle));
            out.addProperty("limit", round(Math.max(maxAngle, Math.max(down, up))));
            if (down != up) notes.add("asymmetric min_pitch/max_pitch became one symmetric limit");
        }
        if (chain.has("gravity") || chain.has("droop_angle")) {
            float droop = GsonHelper.getAsFloat(chain, "droop_angle", 40f);
            out.addProperty("gravity", round(GsonHelper.getAsFloat(chain, "gravity", GRAVITY) * droop / 40f));
        }
        if (chain.has("response") || chain.has("snap")) {
            float sum = 0f;
            if (chain.has("response") && chain.get("response").isJsonObject()) {
                JsonObject r = chain.getAsJsonObject("response");
                for (String channel : List.of("vertical", "forward", "lateral", "turn")) {
                    sum += Math.abs(GsonHelper.getAsFloat(r, channel, 1f));
                }
            } else {
                sum = 4f;
            }
            float snap = Math.min(2f, Math.max(0f, GsonHelper.getAsFloat(chain, "snap", 1f)));
            out.addProperty("inertia", round(Math.min(1f, INERTIA * (sum / 4f) * (0.5f + 0.5f * snap))));
        }
        notes.add("legacy physics keys converted (" + String.join(", ", found) + ")");
        return out;
    }

    /**
     * Frequency (Hz) and damping ratio of the old per-tick spring, from the roots of
     * its characteristic equation {@code z^2 - (1 + d - k) z + d = 0}.
     */
    static float[] legacySpring(float stiffness, float damping) {
        double k = Math.max(0.02f, stiffness) * 0.3;
        double d = Math.min(0.99, Math.max(0.01, damping));
        double a = 1 + d - k;
        double disc = a * a - 4 * d;
        double omega;
        double zeta;
        if (disc < 0) {
            double r = Math.sqrt(d);
            double theta = Math.acos(Math.max(-1, Math.min(1, a / (2 * r))));
            double sigma = -Math.log(r);
            omega = Math.sqrt(theta * theta + sigma * sigma);
            zeta = sigma / omega;
        } else {
            double z1 = (a + Math.sqrt(disc)) / 2;
            double z2 = (a - Math.sqrt(disc)) / 2;
            if (z1 <= 0 || z1 >= 1 || z2 <= 0 || z2 >= 1) return new float[]{FREQUENCY, DAMPING_RATIO};
            double s1 = -Math.log(z1);
            double s2 = -Math.log(z2);
            omega = Math.sqrt(s1 * s2);
            zeta = (s1 + s2) / (2 * omega);
        }
        float hz = (float) (omega * 20.0 / (2 * Math.PI));
        return new float[]{Math.max(0.2f, Math.min(8f, hz)), (float) Math.max(0.05, Math.min(2.0, zeta))};
    }

    /**
     * The chain as file-ready JSON: its preset plus only the values that differ from
     * what the preset (or the defaults) would give.
     */
    public static JsonObject toJson(AttachmentDef.PhysicsChain chain) {
        JsonObject out = new JsonObject();
        JsonArray bones = new JsonArray();
        chain.bones().forEach(bones::add);
        out.add("bones", bones);
        JsonObject baselineSource = new JsonObject();
        baselineSource.add("bones", bones);
        if (!chain.preset().isEmpty()) {
            out.addProperty("preset", chain.preset());
            baselineSource.addProperty("preset", chain.preset());
        }
        AttachmentDef.PhysicsChain base = parseChain(baselineSource, "townstead", presets);
        putKnob(out, "frequency", chain.frequency(), base.frequency());
        putKnob(out, "damping_ratio", chain.dampingRatio(), base.dampingRatio());
        putKnob(out, "gravity", chain.gravity(), base.gravity());
        putKnob(out, "inertia", chain.inertia(), base.inertia());
        putKnob(out, "drag", chain.drag(), base.drag());
        putKnob(out, "limit", chain.limit(), base.limit());
        if (!chain.hinge().equals(base.hinge())) out.addProperty("hinge", chain.hinge());
        if (!chain.collide().equals(base.collide())) {
            JsonArray collide = new JsonArray();
            chain.collide().forEach(collide::add);
            out.add("collide", collide);
        }
        if (chain.restPitch() != base.restPitch()) out.addProperty("rest_pitch", chain.restPitch());
        if (chain.sway() != base.sway()) out.addProperty("sway", chain.sway());
        if (chain.swaySpeed() != base.swaySpeed()) out.addProperty("sway_speed", chain.swaySpeed());
        if (chain.scaleWithSize() != base.scaleWithSize()) out.addProperty("scale_with_size", chain.scaleWithSize());
        if (chain.segments() != 0) out.addProperty("segments", chain.segments());
        if (!chain.axis().equals("auto")) out.addProperty("axis", chain.axis());
        return out;
    }

    public static boolean isKnob(String name) {
        return KNOBS.contains(name);
    }

    private static void putKnob(JsonObject out, String key, AttachmentDef.Knob value, AttachmentDef.Knob base) {
        if (value.equals(base)) return;
        out.add(key, knobJson(value));
    }

    static JsonElement knobJson(AttachmentDef.Knob knob) {
        if (knob.uniform()) return new JsonPrimitive(round(knob.root()));
        JsonObject ramp = new JsonObject();
        ramp.addProperty("root", round(knob.root()));
        ramp.addProperty("tip", round(knob.tip()));
        return ramp;
    }

    /** A number, or a {@code {root, tip}} ramp where a missing end takes the other. */
    static AttachmentDef.Knob knob(JsonObject json, String key, float fallback) {
        if (!json.has(key)) return AttachmentDef.Knob.of(fallback);
        JsonElement element = json.get(key);
        if (element.isJsonObject()) {
            JsonObject ramp = element.getAsJsonObject();
            float root = GsonHelper.getAsFloat(ramp, "root", GsonHelper.getAsFloat(ramp, "tip", fallback));
            float tip = GsonHelper.getAsFloat(ramp, "tip", root);
            return new AttachmentDef.Knob(root, tip);
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return AttachmentDef.Knob.of(element.getAsFloat());
        }
        return AttachmentDef.Knob.of(fallback);
    }

    private static List<String> strings(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray(key)) {
            if (e.isJsonPrimitive()) out.add(e.getAsString());
        }
        return List.copyOf(out);
    }

    private static float round(float value) {
        return Math.round(value * 100f) / 100f;
    }
}
