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
 * <p><b>For players</b>, a House is the family name: it outranks anything typed or rolled, and passes
 * to children ahead of descent. A tradition with no family names draws from Capitals' surnames for
 * that bucket, as a villager's would.</p>
 *
 * <p>Nothing here links a Capitals class. The compound and its keys are strings, and the House is
 * read by reflection, so this compiles and runs with Capitals absent.</p>
 */
public final class CapitalsNameBridge {

    private static final String IDENTITY_TAG = "McaCapitalsIdentity";
    private static final String KEY_SURNAME = "CurrentSurname";
    private static final String KEY_SURNAME_SOURCE = "SurnameSource";
    private static final String LAST_APPLIED = "townstead:AppliedSurname";
    private static final String EXTERNAL = "townstead:ExternalSurname";
    private static final String SETTLED = "LEGAL_RENAME";
    private static final String KEY_HOUSE_NAME = "HouseName";
    private static final String PLAYER_HOUSE_SERVICE = "com.majesttyx.mcacapitals.house.PlayerHouseService";
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("townstead/CapitalsNameBridge");

    private static java.lang.reflect.Method houseRecord;
    private static boolean houseRecordMissing;
    private static java.lang.reflect.Method birthRecord;
    private static boolean birthRecordMissing;

    private CapitalsNameBridge() {}

    public static void bootstrap() {
        if (!ModCompat.isLoaded(CapitalsSurnameRegisters.MOD_ID)) return;
        VillagerNames.addSource(CapitalsNameBridge::contribute);
        VillagerNames.addTarget(CapitalsNameBridge::accept);
        com.aetherianartificer.townstead.naming.PlayerNames.addSource(new com.aetherianartificer.townstead.naming.PlayerFamilySource() {
            @Override
            public String authoritative(net.minecraft.world.entity.player.Player player) {
                return playerHouse(player);
            }

            @Override
            public String fallback(net.minecraft.world.entity.player.Player player,
                                   @org.jetbrains.annotations.Nullable com.aetherianartificer.townstead.naming.NamingTradition tradition) {
                return bucketSurname(tradition);
            }
        });
    }

    /**
     * Capitals' surname, as the fallback. Registered before Townstead's own culture source, and a
     * later source overrides an earlier one, so a culture that declares a family-name rule wins and
     * a culture that declares none leaves Capitals' surname standing.
     */
    private static void contribute(VillagerEntityMCA villager, NameParts.Builder builder) {
        if (keepsFamilyBack(villager)) return;
        CompoundTag identity = villager.getPersistentData().getCompound(IDENTITY_TAG);
        if (externalOwns(identity)) builder.authoritativeFamily(identity.getString(KEY_SURNAME));
        else builder.family(surnameOf(villager));
    }

    /** Writes a Townstead-decided family name into the identity Capitals renders. */
    private static void accept(VillagerEntityMCA villager, NameParts parts) {
        if (!parts.hasFamily()) return;
        // A child of a House is Capitals' to record, with the House's details. Writing the name
        // here would mark it as a legal rename, which stops Capitals repairing the child's identity.
        if (childOfHouse(villager, parts.family())) return;

        CompoundTag persistent = villager.getPersistentData();
        if (!persistent.contains(IDENTITY_TAG)) return;
        CompoundTag identity = persistent.getCompound(IDENTITY_TAG);

        // Capitals shows its surname to every player, so a Persona's family name that is told
        // only stays out of it. One written there before is taken back, and Capitals gives them
        // a surname of its own.
        if (keepsFamilyBack(villager)) {
            if (parts.family().equals(identity.getString(LAST_APPLIED))) {
                identity.remove(KEY_SURNAME);
                identity.remove(KEY_SURNAME_SOURCE);
                identity.remove(LAST_APPLIED);
                persistent.put(IDENTITY_TAG, identity);
            }
            return;
        }
        apply(identity, parts.family());
        persistent.put(IDENTITY_TAG, identity);
    }

    private static boolean childOfHouse(VillagerEntityMCA child, String family) {
        for (net.minecraft.world.entity.Entity parent : com.aetherianartificer.townstead.naming.Naming.parents(child)) {
            String house = parent instanceof net.minecraft.world.entity.player.Player player ? playerHouse(player)
                    : parent.getPersistentData().getCompound(IDENTITY_TAG).getString(KEY_HOUSE_NAME);
            if (family.equals(house)) return true;
        }
        return false;
    }

    private static boolean keepsFamilyBack(VillagerEntityMCA villager) {
        com.aetherianartificer.townstead.persona.PersonaDefinition persona =
                com.aetherianartificer.townstead.persona.PersonaService.definition(villager);
        return persona != null && persona.familyToldOnly();
    }

    static boolean externalOwns(CompoundTag identity) {
        if (identity.getBoolean(EXTERNAL)) return true;
        String current = identity.getString(KEY_SURNAME);
        // Not sticky: once the House is gone, so is the claim.
        if (!current.isBlank() && current.equals(identity.getString(KEY_HOUSE_NAME))) return true;
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

    /**
     * Records a baby item's child in Capitals, which its own hook no longer can on current MCA. See
     * {@code TownsteadMixinPlugin}. Safe to run alongside a hook that works: the writes settle on
     * the same values.
     */
    public static void recordBirth(VillagerEntityMCA child) {
        if (!ModCompat.isLoaded(CapitalsSurnameRegisters.MOD_ID)) return;
        if (!(child.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
        java.lang.reflect.Method record = birthLookup();
        if (record == null) return;
        try {
            var family = child.getRelationships().getFamilyEntry();
            record.invoke(null, level, child, family.mother(), family.father());
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("MCA Capitals could not record a birth", e);
        }
    }

    private static java.lang.reflect.Method birthLookup() {
        if (birthRecord != null || birthRecordMissing) return birthRecord;
        try {
            birthRecord = Class.forName("com.majesttyx.mcacapitals.identity.PlayerHouseIdentityService")
                    .getMethod("applyBirthIdentityFromParentIds", net.minecraft.server.level.ServerLevel.class,
                            net.minecraft.world.entity.Entity.class, java.util.UUID.class, java.util.UUID.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            birthRecordMissing = true;
            LOGGER.warn("MCA Capitals has no birth record lookup; a child's House details will wait for its repair", e);
        }
        return birthRecord;
    }

    /** The player's House name, or empty. Capitals keeps it in a saved record per level. */
    private static String playerHouse(net.minecraft.world.entity.player.Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return "";
        java.lang.reflect.Method lookup = houseLookup();
        if (lookup == null) return "";
        try {
            Object record = lookup.invoke(null, level, player.getUUID());
            if (record == null) return "";
            Object name = record.getClass().getMethod("getHouseName").invoke(record);
            return name instanceof String house ? house.trim() : "";
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "";
        }
    }

    private static java.lang.reflect.Method houseLookup() {
        if (houseRecord != null || houseRecordMissing) return houseRecord;
        try {
            houseRecord = Class.forName(PLAYER_HOUSE_SERVICE).getMethod("get",
                    net.minecraft.server.level.ServerLevel.class, java.util.UUID.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            houseRecordMissing = true;
            LOGGER.warn("MCA Capitals has no player House lookup; player House names will not show", e);
        }
        return houseRecord;
    }

    /** A surname from Capitals for the MCA bucket a tradition is built on, or empty. */
    private static String bucketSurname(@org.jetbrains.annotations.Nullable com.aetherianartificer.townstead.naming.NamingTradition tradition) {
        if (tradition == null || !com.aetherianartificer.townstead.naming.NamingTraditions.IMPLICIT_NAMESPACE.equals(tradition.id().getNamespace())) return "";
        com.aetherianartificer.townstead.naming.FamilyNames names = CapitalsSurnameRegisters.listFor(tradition.id().getPath());
        return names == null ? "" : names.pick();
    }

    private static String surnameOf(VillagerEntityMCA villager) {
        CompoundTag identity = villager.getPersistentData().getCompound(IDENTITY_TAG);
        return identity.isEmpty() ? "" : identity.getString(KEY_SURNAME).trim();
    }
}
