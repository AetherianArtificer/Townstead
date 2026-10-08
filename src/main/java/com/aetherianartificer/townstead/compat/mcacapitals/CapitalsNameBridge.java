package com.aetherianartificer.townstead.compat.mcacapitals;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.naming.NameParts;
import com.aetherianartificer.townstead.naming.VillagerNames;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;

/**
 * MCA Capitals as both a name source and a name target.
 *
 * <p><b>As a source</b>, its surname is the family name for any villager whose own culture declares
 * no family-name rule. That is every implicit culture, so on a world with no culture packs Capitals
 * keeps naming people exactly as it does today and Townstead takes nothing over.</p>
 *
 * <p><b>As a target</b>, a family name Townstead's culture <em>did</em> decide is written back into
 * the identity Capitals already renders. That puts an Ashmarch patronymic on the nameplate Capitals
 * draws, in its Book of Houses and in its chronicle entries at once, instead of Townstead drawing a
 * competing nameplate over the top and the two mods disagreeing about one villager's name.</p>
 *
 * <p>The write marks the surname {@code LEGAL_RENAME}, which is Capitals' own flag for a name
 * somebody set deliberately. Its automatic paths already respect it: the birth repair returns early
 * on it and its surname assignment only fills a blank. A player using a Decree of the House still
 * overrides Townstead, which is the right order of authority.</p>
 *
 * <p>Nothing here links a Capitals class. The compound and its keys are strings, so this compiles
 * and runs with Capitals absent.</p>
 */
public final class CapitalsNameBridge {

    private static final String IDENTITY_TAG = "McaCapitalsIdentity";
    private static final String KEY_SURNAME = "CurrentSurname";
    private static final String KEY_SURNAME_SOURCE = "SurnameSource";
    private static final String LAST_APPLIED = "townstead:AppliedSurname";
    private static final String EXTERNAL = "townstead:ExternalSurname";
    private static final String SETTLED = "LEGAL_RENAME";

    private CapitalsNameBridge() {}

    public static void bootstrap() {
        if (!ModCompat.isLoaded(CapitalsSurnameRegisters.MOD_ID)) return;
        VillagerNames.addSource(CapitalsNameBridge::contribute);
        VillagerNames.addTarget(CapitalsNameBridge::accept);
    }

    /**
     * Capitals' surname, as the fallback. Registered before Townstead's own culture source, and a
     * later source overrides an earlier one, so a culture that declares a family-name rule wins and
     * a culture that declares none leaves Capitals' surname standing.
     */
    private static void contribute(VillagerEntityMCA villager, NameParts.Builder builder) {
        CompoundTag identity = villager.getPersistentData().getCompound(IDENTITY_TAG);
        if (externalOwns(identity)) builder.authoritativeFamily(identity.getString(KEY_SURNAME));
        else builder.family(surnameOf(villager));
    }

    /** Writes a Townstead-decided family name into the identity Capitals renders. */
    private static void accept(VillagerEntityMCA villager, NameParts parts) {
        if (!parts.hasFamily()) return;

        CompoundTag persistent = villager.getPersistentData();
        if (!persistent.contains(IDENTITY_TAG)) return;
        CompoundTag identity = persistent.getCompound(IDENTITY_TAG);

        apply(identity, parts.family());
        persistent.put(IDENTITY_TAG, identity);
    }

    static boolean externalOwns(CompoundTag identity) {
        if (identity.getBoolean(EXTERNAL)) return true;
        String current = identity.getString(KEY_SURNAME);
        String source = identity.getString(KEY_SURNAME_SOURCE);
        String last = identity.getString(LAST_APPLIED);
        boolean changed = !last.isEmpty() && (!last.equals(current) || !SETTLED.equals(source));
        boolean externalSource = last.isEmpty() && !source.isEmpty()
                && !"GENERATED".equals(source) && !"BIRTH".equals(source);
        if (changed || externalSource) identity.putBoolean(EXTERNAL, true);
        return changed || externalSource;
    }

    static void apply(CompoundTag identity, String family) {
        if (externalOwns(identity) || family.isEmpty()
                || family.equals(identity.getString(KEY_SURNAME))) return;
        identity.putString(KEY_SURNAME, family);
        identity.putString(KEY_SURNAME_SOURCE, SETTLED);
        identity.putString(LAST_APPLIED, family);
    }

    private static String surnameOf(VillagerEntityMCA villager) {
        CompoundTag identity = villager.getPersistentData().getCompound(IDENTITY_TAG);
        return identity.isEmpty() ? "" : identity.getString(KEY_SURNAME).trim();
    }
}
