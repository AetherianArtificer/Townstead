package com.aetherianartificer.townstead.root.gene;

/**
 * Splitter for the legacy persisted allele-pair string, which joined the two
 * {@link Allele#encode()} strings with {@code ";"}. That separator collides with
 * {@link AllelePayload}'s own channel separator: a multi-channel payload
 * ({@code "gene#span=1.014;tint_r=0.949;..."}) contains {@code ';'} itself, so a
 * first-semicolon split truncated allele A after its first channel and turned
 * allele B into garbage that decoded WILD — silently dropping every channel after
 * the first (heritable tint, extra size rolls) on each save/load round trip.
 *
 * <p>New saves store the pair as a two-entry list instead (see {@code Genotype});
 * this class only reads the old string form. It scans for the semicolon whose
 * remainder reads as one whole allele — wild ({@code "~"}) or {@code ns:path}
 * optionally followed by {@code "#payload"} — which is unambiguous because channel
 * fragments ({@code "tint_r=0.949"}) contain {@code '='} before any {@code ':'}.
 * On a pair written before the fix that scan lands on the separator in front of
 * allele B's gene id, recovering allele A's full payload.</p>
 */
public final class AllelePair {

    private AllelePair() {}

    /**
     * Split a legacy joined pair into its allele encodings: two entries when a
     * pair separator is found, one entry (a lone allele, stored pre-diploid or
     * with its twin lost to the old truncation) otherwise.
     */
    public static String[] splitLegacy(String raw) {
        if (raw == null) return new String[]{""};
        int from = 0;
        int sep;
        while ((sep = raw.indexOf(';', from)) >= 0) {
            if (readsAsAllele(raw.substring(sep + 1))) {
                return new String[]{raw.substring(0, sep), raw.substring(sep + 1)};
            }
            from = sep + 1;
        }
        return new String[]{raw};
    }

    /** Whether the string reads as one whole allele encoding: {@code "~"}, or {@code ns:path} with an optional {@code #payload} tail. */
    private static boolean readsAsAllele(String s) {
        if (s.equals("~")) return true;
        int hash = s.indexOf('#');
        String id = hash < 0 ? s : s.substring(0, hash);
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) return false;
        for (int i = 0; i < id.length(); i++) {
            if (i == colon) continue;
            char c = id.charAt(i);
            boolean valid = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-' || (c == '/' && i > colon);
            if (!valid) return false;
        }
        return true;
    }
}
