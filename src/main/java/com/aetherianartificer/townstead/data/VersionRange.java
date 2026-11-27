package com.aetherianartificer.townstead.data;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A version test for {@link ModGate}: comparisons separated by spaces, all of which must pass, such
 * as {@code ">=1.2.0 <2"} or {@code "1.21.1"}. A missing segment counts as zero, build metadata after
 * {@code +} and words such as {@code neoforge} are ignored, and a pre-release sorts before its release.
 */
final class VersionRange {

    private static final Pattern COMPARISON = Pattern.compile("(>=|<=|>|<|=)?(\\d.*)");
    private static final Pattern SEGMENT = Pattern.compile("\\d+|[a-zA-Z]+");
    private static final List<String> PRE_RELEASES = List.of("snapshot", "dev", "alpha", "beta", "pre", "rc");
    /** Pre-releases sit below zero, the value of a missing segment, so they sort before their release. */
    private static final long PRE_RELEASE_BASE = -PRE_RELEASES.size();

    private record Comparison(String operator, List<Long> version) {
        boolean test(List<Long> candidate) {
            int order = compare(candidate, version);
            return switch (operator) {
                case ">=" -> order >= 0;
                case "<=" -> order <= 0;
                case ">" -> order > 0;
                case "<" -> order < 0;
                default -> order == 0;
            };
        }
    }

    private final List<Comparison> comparisons;

    private VersionRange(List<Comparison> comparisons) {
        this.comparisons = comparisons;
    }

    /** The parsed range, or null when it is malformed. */
    static @Nullable VersionRange parse(String text) {
        List<Comparison> parsed = new ArrayList<>();
        for (String part : text.trim().split("\\s+")) {
            Matcher matcher = COMPARISON.matcher(part);
            if (!matcher.matches()) return null;
            String operator = matcher.group(1) == null ? "=" : matcher.group(1);
            parsed.add(new Comparison(operator, segments(matcher.group(2))));
        }
        return new VersionRange(parsed);
    }

    boolean contains(String version) {
        List<Long> candidate = segments(version);
        for (Comparison comparison : comparisons) {
            if (!comparison.test(candidate)) return false;
        }
        return true;
    }

    private static List<Long> segments(String version) {
        int metadata = version.indexOf('+');
        String core = metadata < 0 ? version : version.substring(0, metadata);
        List<Long> segments = new ArrayList<>();
        Matcher matcher = SEGMENT.matcher(core);
        while (matcher.find()) {
            String segment = matcher.group();
            if (Character.isDigit(segment.charAt(0))) {
                segments.add(number(segment));
            } else {
                int preRelease = PRE_RELEASES.indexOf(segment.toLowerCase(Locale.ROOT));
                if (preRelease >= 0) segments.add(PRE_RELEASE_BASE + preRelease);
            }
        }
        return segments;
    }

    private static long number(String digits) {
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException tooLong) {
            return Long.MAX_VALUE;
        }
    }

    private static int compare(List<Long> left, List<Long> right) {
        for (int i = 0; i < Math.max(left.size(), right.size()); i++) {
            long a = i < left.size() ? left.get(i) : 0L;
            long b = i < right.size() ? right.get(i) : 0L;
            if (a != b) return Long.compare(a, b);
        }
        return 0;
    }
}
