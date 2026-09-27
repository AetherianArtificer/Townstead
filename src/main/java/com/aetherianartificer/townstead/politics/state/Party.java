package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.social.BondKind;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** One side of a bond: a person, by UUID, or a faction, by id. */
public record Party(BondKind.Party type, String id) {
    public Party {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }

    public static Party person(UUID person) {
        return new Party(BondKind.Party.PERSON, person.toString());
    }

    public static Party faction(ResourceLocation faction) {
        return new Party(BondKind.Party.FACTION, faction.toString());
    }

    public boolean isPerson() {
        return type == BondKind.Party.PERSON;
    }

    public boolean isFaction() {
        return type == BondKind.Party.FACTION;
    }

    public @Nullable UUID person() {
        if (!isPerson()) return null;
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    public @Nullable ResourceLocation faction() {
        return isFaction() ? DataPackLang.parseId(id) : null;
    }

    /** {@code person:<uuid>} or {@code faction:<id>}. */
    public String encode() {
        return (isPerson() ? "person:" : "faction:") + id;
    }

    public static @Nullable Party decode(String value) {
        if (value == null) return null;
        if (value.startsWith("person:")) {
            try {
                return person(UUID.fromString(value.substring(7)));
            } catch (IllegalArgumentException error) {
                return null;
            }
        }
        if (value.startsWith("faction:")) {
            ResourceLocation id = DataPackLang.parseId(value.substring(8));
            return id == null ? null : faction(id);
        }
        return null;
    }
}
