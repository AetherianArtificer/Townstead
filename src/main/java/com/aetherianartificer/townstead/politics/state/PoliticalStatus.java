package com.aetherianartificer.townstead.politics.state;

import java.util.Locale;

public final class PoliticalStatus {
    private PoliticalStatus() {}

    public enum Organization {
        ACTIVE, DORMANT, DISSOLVED;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        static Organization parse(String value) {
            try { return valueOf(value.toUpperCase(Locale.ROOT)); }
            catch (RuntimeException error) { return DORMANT; }
        }
    }

    public enum Polity {
        ACTIVE, DORMANT, DISSOLVED;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        static Polity parse(String value) {
            try { return valueOf(value.toUpperCase(Locale.ROOT)); }
            catch (RuntimeException error) { return DORMANT; }
        }
    }

    public enum Affiliation {
        PENDING, ACTIVE, SUSPENDED, FORMER, REJECTED;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        static Affiliation parse(String value) {
            try { return valueOf(value.toUpperCase(Locale.ROOT)); }
            catch (RuntimeException error) { return FORMER; }
        }
    }

    public enum Visibility {
        PUBLIC, MEMBERS, PRIVATE;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        static Visibility parse(String value) {
            try { return valueOf(value.toUpperCase(Locale.ROOT)); }
            catch (RuntimeException error) { return PRIVATE; }
        }
    }
}
