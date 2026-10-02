package com.aetherianartificer.townstead.clothing.wardrobe;

import com.aetherianartificer.townstead.clothing.ClothingChannel;
import com.aetherianartificer.townstead.clothing.ClothingLayer;
import com.aetherianartificer.townstead.clothing.ClothingQuery;
import com.aetherianartificer.townstead.clothing.policy.WardrobePolicies;
import com.aetherianartificer.townstead.clothing.policy.WardrobePolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An outfit template as the Wardrobe screen lists it: a wardrobe policy with the reserved
 * {@code villager} scope, which applies to nobody until a cell names it. The three layer lines
 * are plain data words ("required", "warm body") so the client can show them without knowing
 * the policy format.
 *
 * @param requirements per layer base, outerwear, accessory: 0 required, 1 preferred, 2 none,
 *                     -1 no rule
 * @param selectors    per layer, the selector in data words, empty when there is no rule
 */
public record WardrobeTemplate(ResourceLocation id, String nameKey, int[] requirements, String[] selectors) {

    public static final ClothingLayer[] LAYERS = {ClothingLayer.BASE, ClothingLayer.OUTERWEAR, ClothingLayer.ACCESSORY};

    public static final int REQUIRED = 0;
    public static final int PREFERRED = 1;
    public static final int NONE = 2;
    public static final int NO_RULE = -1;

    public WardrobeTemplate {
        requirements = requirements == null ? new int[] {NO_RULE, NO_RULE, NO_RULE} : requirements.clone();
        selectors = selectors == null ? new String[] {"", "", ""} : selectors.clone();
    }

    /** The lang key a template's name lives under, from the policy id path. */
    public static String nameKeyOf(ResourceLocation id) {
        return "townstead.wardrobe.template." + id.getPath().replace('/', '.') + ".name";
    }

    /** A readable fallback when the name key has no translation. */
    public String fallbackName() {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        String tail = slash >= 0 ? path.substring(slash + 1) : path;
        String[] words = tail.split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.length() == 0 ? id.toString() : out.toString();
    }

    public static WardrobeTemplate of(WardrobePolicy policy) {
        int[] requirements = new int[LAYERS.length];
        String[] selectors = new String[LAYERS.length];
        for (int i = 0; i < LAYERS.length; i++) {
            WardrobePolicy.LayerRule rule = policy.rule(LAYERS[i]);
            if (rule == null) {
                requirements[i] = NO_RULE;
                selectors[i] = "";
                continue;
            }
            switch (rule.requirement()) {
                case REQUIRED: requirements[i] = REQUIRED; break;
                case PREFERRED: requirements[i] = PREFERRED; break;
                default: requirements[i] = NONE; break;
            }
            selectors[i] = describe(rule.selector());
        }
        return new WardrobeTemplate(policy.id(), nameKeyOf(policy.id()), requirements, selectors);
    }

    /** Every loaded template, in load order. */
    public static List<WardrobeTemplate> all() {
        List<WardrobeTemplate> out = new ArrayList<>();
        for (WardrobePolicy policy : WardrobePolicies.all()) {
            if (policy.scope() == WardrobePolicy.Scope.VILLAGER) out.add(of(policy));
        }
        return out;
    }

    static String describe(@Nullable WardrobePolicy.Selector selector) {
        if (selector == null) return "";
        if (selector.cultureSets()) return "<culture>";
        if (selector.bodySets()) return "<body>";
        if (selector.set() != null) return selector.set().toString();
        if (selector.skin() != null) return selector.skin();
        ClothingQuery query = selector.query();
        if (query == null || query.isEmpty()) return "";
        List<String> words = new ArrayList<>();
        if (query.thermal() == ClothingQuery.Thermal.WARM) words.add("warm");
        if (query.thermal() == ClothingQuery.Thermal.COOL) words.add("cool");
        if (query.slot() != null && query.slot() != ClothingChannel.ALL) words.add(query.slot().name().toLowerCase(Locale.ROOT));
        if (!query.materials().isEmpty()) words.add(String.join("/", query.materials()));
        if (!query.occasions().isEmpty()) words.add(String.join("/", query.occasions()));
        if (!query.spirits().isEmpty()) words.add(String.join("/", query.spirits()));
        if (!query.sources().isEmpty()) words.add(String.join("/", query.sources()));
        return String.join(" ", words);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
        buf.writeUtf(nameKey);
        for (int i = 0; i < LAYERS.length; i++) {
            buf.writeVarInt(requirements[i]);
            buf.writeUtf(selectors[i] == null ? "" : selectors[i]);
        }
    }

    public static WardrobeTemplate read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        String nameKey = buf.readUtf();
        int[] requirements = new int[LAYERS.length];
        String[] selectors = new String[LAYERS.length];
        for (int i = 0; i < LAYERS.length; i++) {
            requirements[i] = buf.readVarInt();
            selectors[i] = buf.readUtf();
        }
        return new WardrobeTemplate(id, nameKey, requirements, selectors);
    }
}
