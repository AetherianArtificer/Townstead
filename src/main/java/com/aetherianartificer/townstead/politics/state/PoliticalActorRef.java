package com.aetherianartificer.townstead.politics.state;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Objects;

/** A reference to either membership-bearing organization state or public polity state. */
public record PoliticalActorRef(Kind kind, ResourceLocation id) {
    public PoliticalActorRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    public enum Kind {
        ORGANIZATION,
        POLITY;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Kind parse(String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (RuntimeException error) {
                return null;
            }
        }
    }
}
