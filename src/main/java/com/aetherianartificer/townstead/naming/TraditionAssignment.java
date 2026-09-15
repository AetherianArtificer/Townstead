package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;

/**
 * Which naming tradition a villager is named by.
 *
 * <p>A separate question from which culture they belong to, and the two must not be collapsed.
 * "Named in the Swedish manner" is a fact about how a name is built; it says nothing about what
 * anybody values, and treating it as a culture would make a file named after a country the place
 * somebody is expected to write down what its people believe. Naming traditions are universal and
 * everyone has one; cultures are held by communities, most villagers have none, and beliefs live
 * only there.</p>
 *
 * <p>A culture that declares a tradition wins, so a Highhold family living in a Japanese region
 * stays named the Highhold way and passes that on. Everyone else is named by their region, which is
 * how a world with no culture packs keeps exactly the naming it already had.</p>
 */
public final class TraditionAssignment {

    private TraditionAssignment() {}

    /**
     * Settles this villager's naming tradition if they have none, and returns its id. Empty only
     * when MCA has no name buckets loaded at all.
     */
    public static String ensure(ServerLevel level, VillagerEntityMCA villager) {
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();

        // A culture's own tradition outranks anything geographic, and keeps outranking it, so a
        // culture assigned later moves a villager's naming with it.
        String fromCulture = ofCulture(life.culture());
        if (!fromCulture.isEmpty()) return record(villager, life, fromCulture);

        String recorded = life.namingTradition();
        if (NamingTraditions.get(parse(recorded)) != null) return recorded;

        // Already named, so their register names the tradition they have always been named by.
        String named = ofRegister(NamingRegisters.recorded(villager));
        if (!named.isEmpty()) return record(villager, life, named);

        // Never named through Townstead: ask MCA what it would call them, which settles a register
        // and hands back the tradition matching it without renaming anybody.
        return record(villager, life, ofRegister(NamingRegisters.deriveFromMca(villager)));
    }

    /** The tradition a culture declares, or empty when it declares none or does not exist. */
    public static String ofCulture(String culture) {
        Culture resolved = Cultures.get(culture);
        return resolved == null || !resolved.hasNamingTradition()
                ? ""
                : resolved.namingTradition().toString();
    }

    /**
     * The tradition a naming register implies. MCA's buckets are keyed bare and each has a
     * tradition, {@code japan} meaning {@code mca:japan}: either the one Townstead authored for that
     * bucket, or the synthesized one that draws on the bucket and declares no family names.
     */
    private static String ofRegister(String register) {
        if (register == null || register.isBlank() || register.indexOf(':') >= 0) return "";
        return NamingTraditions.IMPLICIT_NAMESPACE + ":" + register.trim();
    }

    private static String record(VillagerEntityMCA villager, TownsteadVillager.Life life, String tradition) {
        if (tradition.isEmpty() || NamingTraditions.get(parse(tradition)) == null) return "";
        if (!tradition.equals(life.namingTradition())) {
            life.setNamingTradition(tradition);
            TownsteadVillagers.flush(villager);
        }
        return tradition;
    }

    private static net.minecraft.resources.ResourceLocation parse(String id) {
        return id == null || id.isBlank() ? null : net.minecraft.resources.ResourceLocation.tryParse(id.trim());
    }
}
