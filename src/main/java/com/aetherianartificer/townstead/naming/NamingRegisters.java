package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.resources.Names;
import net.conczin.mca.server.world.data.Nationality;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Which naming register a villager draws names from, and how that answer is made to stop moving.
 *
 * <p>MCA resolves a villager's register on every call, as
 * {@code REGION_NAMES.get(floorMod(regionId, REGION_NAMES.size()))}, and persists only the raw
 * region id. {@code REGION_NAMES} is rebuilt from whichever {@code mca_names} folders are loaded,
 * so any mod or datapack that adds one silently re-rolls the register of every region in the
 * world, and every mod that derives names through the same call inherits the drift. This freezes
 * the answer instead, at the villager and at the region.</p>
 *
 * <p>Resolution, first hit wins:</p>
 * <ol>
 *   <li>the list already recorded on the villager</li>
 *   <li>the culture they belong to, which decides their naming tradition</li>
 *   <li>any registered {@link RegisterEvidence}, in registration order</li>
 *   <li>a parent's list, so a household keeps one naming tradition</li>
 *   <li>the village's assigned list</li>
 *   <li>the list frozen for this map region</li>
 *   <li>nothing, and MCA's positional derivation stands and is then frozen</li>
 * </ol>
 *
 * <p>Naming is cultural, never ethnic: nothing here consults a villager's Root, species, ancestry
 * or lineage. A dwarf raised in a human household is named like that household. Registers are
 * held by people and communities, and Cultures will declare them once that system exists.</p>
 */
public final class NamingRegisters {

    private static final List<RegisterEvidence> EVIDENCE = new CopyOnWriteArrayList<>();

    private NamingRegisters() {}

    /**
     * Registers an outside opinion on which register a villager belongs to, consulted in
     * registration order. Call once at setup; a source that depends on another mod gates itself on
     * that mod being loaded rather than being gated here.
     */
    public static void addEvidence(RegisterEvidence evidence) {
        if (evidence != null) EVIDENCE.add(evidence);
    }

    /**
     * The register this villager should use, or empty to let MCA derive one. Never returns a
     * register MCA has no name pool for, since {@code pickCitizenName} would dereference it.
     */
    public static String resolve(Entity entity) {
        if (!enabled() || !(entity instanceof VillagerEntityMCA villager)) return "";
        if (!(villager.level() instanceof ServerLevel level)) return "";
        if (SpawnNaming.pending(villager)) return "";

        String own = recorded(villager);
        if (usable(own)) return own;

        // Naming is the moment a villager needs a culture, so settle one here if they have none:
        // inherited from parents, then from their village, and only rolled from the root's bias for
        // a founder who has neither.
        com.aetherianartificer.townstead.culture.CultureAssignment.ensure(level, villager);

        // A villager who belongs to a culture is named the way that culture names people, and the
        // list their tradition rolls is recorded so they keep one tradition for life.
        String fromCulture = NameLists.givenKey(Naming.nameList(villager));
        if (usable(fromCulture)) {
            record(villager, fromCulture);
            // Being named is also when a family name is settled and handed to whatever shows it.
            VillagerNames.publish(villager);
            return fromCulture;
        }

        String attested = fromEvidence(villager);
        if (usable(attested)) {
            record(villager, attested);
            return attested;
        }

        String inherited = fromParents(villager);
        if (usable(inherited)) {
            record(villager, inherited);
            return inherited;
        }

        String assigned = fromVillage(level, villager);
        if (usable(assigned)) {
            record(villager, assigned);
            return assigned;
        }

        String frozen = fromRegion(level, villager);
        if (usable(frozen)) {
            record(villager, frozen);
            return frozen;
        }

        return "";
    }

    /**
     * Freezes what MCA just derived, so the same villager and the same region keep this register
     * after the loaded name buckets change. Called on the way out of MCA's own derivation.
     *
     * <p>MCA's {@code useModernUSANamesOnly} short-circuits its derivation, so that setting is
     * frozen like any other answer. That is deliberate: a villager named under it really is of
     * that tradition, and turning the setting off later should not rename their family.</p>
     */
    public static void freeze(Entity entity, String derived) {
        if (!enabled() || !usable(derived)) return;
        if (!(entity instanceof VillagerEntityMCA villager)) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (SpawnNaming.pending(villager)) return;

        record(villager, derived);
        try {
            NamingRegisterSavedData.get(level.getServer())
                    .putRegion(Nationality.get(level).getRegionId(villager.blockPosition()), derived);
        } catch (Throwable ignored) {
            // A region we cannot key is one we simply do not freeze.
        }
    }

    /** The list recorded on a villager, or empty. */
    public static String recorded(VillagerEntityMCA villager) {
        return TownsteadVillagers.get(villager).life().nameList();
    }

    /** Records a list on a villager, leaving an existing one alone. */
    public static void record(VillagerEntityMCA villager, String reference) {
        if (!usable(reference)) return;
        TownsteadVillager state = TownsteadVillagers.get(villager);
        if (!state.life().nameList().isEmpty()) return;
        state.life().setNameList(reference);
        TownsteadVillagers.flush(villager);
    }

    /** Assigns a village's register; blank clears it. Existing villagers keep what they recorded. */
    public static void assignVillage(ServerLevel level, int villageId, String register) {
        NamingRegisterSavedData.get(level.getServer()).putVillage(villageId, register);
    }

    /** Whether MCA can actually serve names for this reference. */
    public static boolean usable(String reference) {
        return reference != null && !reference.isBlank() && Names.NAMES_MAP.containsKey(reference);
    }

    private static String fromEvidence(VillagerEntityMCA villager) {
        for (RegisterEvidence evidence : EVIDENCE) {
            try {
                String register = evidence.registerFor(villager);
                if (usable(register)) return register;
            } catch (Throwable ignored) {
                // One source's opinion is never worth failing a villager's naming over.
            }
        }
        return "";
    }

    private static String fromParents(VillagerEntityMCA villager) {
        try {
            return villager.getRelationships().getParents()
                    .filter(VillagerEntityMCA.class::isInstance)
                    .map(parent -> recorded((VillagerEntityMCA) parent))
                    .filter(NamingRegisters::usable)
                    .findFirst()
                    .orElse("");
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String fromVillage(ServerLevel level, VillagerEntityMCA villager) {
        try {
            Optional<Village> home = villager.getResidency().getHomeVillage();
            if (home.isEmpty()) return "";
            return NamingRegisterSavedData.get(level.getServer()).village(home.get().getId());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String fromRegion(ServerLevel level, VillagerEntityMCA villager) {
        try {
            int regionId = Nationality.get(level).getRegionId(villager.blockPosition());
            return NamingRegisterSavedData.get(level.getServer()).region(regionId);
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static boolean enabled() {
        try {
            return TownsteadConfig.ENABLE_STABLE_NAMING_REGISTERS.get();
        } catch (Throwable ignored) {
            // Config not loaded yet: stamping is the safe default, since the alternative is drift.
            return true;
        }
    }
}
