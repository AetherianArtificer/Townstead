package com.aetherianartificer.townstead.work.site;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * What a worksite is called, and what it is called before anybody has named it.
 *
 * <p>Kept apart from the record so the rules are testable without a world: a default derived from a
 * building type is the difference between "The Kitchen" appearing in a message and
 * {@code kitchen_l3} appearing in one.</p>
 */
public final class WorksiteNames {

    /** Long enough for "The Second Kitchen By The Well", short enough to fit a masthead. */
    public static final int MAX_LENGTH = 48;

    private WorksiteNames() {}

    /**
     * A readable name for an MCA building type. Types carry a tier suffix that means nothing to a
     * player ({@code kitchen_l3}), and underscores where words belong.
     */
    public static String fromBuildingType(@Nullable String buildingType) {
        if (buildingType == null) return "";
        String raw = buildingType;
        int colon = raw.indexOf(':');
        if (colon >= 0) raw = raw.substring(colon + 1);
        // A type may be filed under a path — "compat/farmersdelight/kitchen" — and only the last
        // segment names the place. Without this a kitchen introduces itself as
        // "Compat/farmersdelight/kitchen", which is a file location, not a room.
        int slash = raw.lastIndexOf('/');
        if (slash >= 0) raw = raw.substring(slash + 1);
        raw = raw.replace('_', ' ').trim();
        // Trailing tier markers: "kitchen l3", "cafe l1".
        raw = raw.replaceAll("\\s+l\\d+$", "");
        if (raw.isEmpty()) return "";

        StringBuilder out = new StringBuilder(raw.length());
        boolean startOfWord = true;
        for (char c : raw.toCharArray()) {
            if (c == ' ') {
                startOfWord = true;
                out.append(c);
                continue;
            }
            out.append(startOfWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
            startOfWord = false;
        }
        return out.toString();
    }

    /**
     * What to show for a site, falling back to its binding when it has never been named. Never
     * returns blank: a nameless place in a message is worse than an ugly one.
     */
    public static String display(@Nullable Worksite site) {
        if (site == null) return "Unknown worksite";
        String name = site.name();
        if (name != null && !name.isBlank()) return name;
        return "Worksite " + site.id();
    }

    /**
     * Cleans a player-supplied name, or returns null when it is not usable. Trimmed, length-capped,
     * and stripped of the control characters that would let a name break a chat line.
     */
    @Nullable
    public static String sanitise(@Nullable String raw) {
        if (raw == null) return null;
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            // A formatting code is the section sign AND the letter after it. Dropping only the
            // sign leaves the letter stranded in the middle of the name.
            if (c == '§') {
                i++;
                continue;
            }
            if (Character.isISOControl(c)) continue;
            out.append(c);
        }
        String cleaned = out.toString().trim().replaceAll("\\s{2,}", " ");
        if (cleaned.isEmpty()) return null;
        if (cleaned.length() > MAX_LENGTH) cleaned = cleaned.substring(0, MAX_LENGTH).trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
