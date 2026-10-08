package com.aetherianartificer.townstead.contract;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;

/**
 * A text value in a contract, as in MCA: Quests: a plain string, {@code { "text": ... }}, or
 * {@code { "translate": key, "with": [...] }} resolved from the data-pack lang files for the
 * player's language. {@code {var}} puts in a template value and {@code {var_name}} its display
 * name; {@code {{} and {@code }}} are literal braces.
 */
public record ContractText(@Nullable String literal, @Nullable String key, List<String> with) {
    public static final ContractText EMPTY = new ContractText("", null, List.of());

    public static ContractText parse(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) return EMPTY;
        if (element.isJsonPrimitive()) return new ContractText(element.getAsString(), null, List.of());
        JsonObject json = element.getAsJsonObject();
        if (json.has("translate")) {
            List<String> with = new ArrayList<>();
            if (json.has("with")) json.getAsJsonArray("with").forEach(e -> with.add(e.getAsString()));
            return new ContractText(null, json.get("translate").getAsString(), List.copyOf(with));
        }
        if (json.has("text")) return new ContractText(json.get("text").getAsString(), null, List.of());
        throw new IllegalArgumentException("a text value needs \"text\" or \"translate\"");
    }

    public static ContractText key(String key, String... with) {
        return new ContractText(null, key, List.of(with));
    }

    public boolean isEmpty() {
        return key == null && (literal == null || literal.isEmpty());
    }

    /** The text for {@code locale}, with template values in. */
    public String resolve(Map<String, String> values, String locale) {
        if (key == null) return fill(literal == null ? "" : literal, values);
        String pattern = DataPackLang.find(key, locale);
        if (pattern == null) return key;
        Object[] args = with.stream().map(arg -> fill(arg, values)).toArray();
        try {
            return args.length == 0 ? pattern : String.format(pattern, args);
        } catch (IllegalFormatException e) {
            return pattern;
        }
    }

    /** {@code {var}} and {@code {var_name}} from {@code values}; {@code {{} and {@code }}} stay literal. */
    static String fill(String text, Map<String, String> values) {
        String out = text.replace("{{", "\u0001").replace("}}", "\u0002");
        for (Map.Entry<String, String> value : values.entrySet()) out = out.replace("{" + value.getKey() + "}", value.getValue());
        return out.replace('\u0001', '{').replace('\u0002', '}');
    }
}
