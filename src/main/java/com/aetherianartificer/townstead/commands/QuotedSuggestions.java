package com.aetherianartificer.townstead.commands;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Suggestions for a quoted string argument. Vanilla's suggest never adds quotes, so an id with a
 * colon would be accepted from the list and then fail to parse.
 */
public final class QuotedSuggestions {
    private QuotedSuggestions() {}

    public static CompletableFuture<Suggestions> suggest(Iterable<String> values, SuggestionsBuilder builder) {
        String typed = builder.getRemaining();
        String bare = (typed.startsWith("\"") ? typed.substring(1) : typed).toLowerCase(Locale.ROOT);
        for (String value : values) {
            if (SharedSuggestionProvider.matchesSubStr(bare, value.toLowerCase(Locale.ROOT))) builder.suggest(quote(value));
        }
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> suggest(Stream<String> values, SuggestionsBuilder builder) {
        return suggest(values::iterator, builder);
    }

    /** The value as typed: in quotes when a bare word could not hold it. */
    public static String quote(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!StringReader.isAllowedInUnquotedString(value.charAt(i))) {
                return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
            }
        }
        return value;
    }
}
