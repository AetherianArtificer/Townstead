package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.naming.Naming;
import com.aetherianartificer.townstead.naming.NamingRegisterSavedData;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Where a villager's culture comes from.
 *
 * <p>Two different questions, and conflating them is the usual mistake. Almost everyone
 * <em>inherits</em> a culture: from the people who raised them, or failing that from the town they
 * grew up in. Only a founder, someone who spawned with no parents and no home, has a culture rolled
 * for them, and that roll is the one place a Root has any say at all.</p>
 *
 * <p>So a Piglin born in a Japanese village is culturally Japanese and named accordingly. The bias
 * never gets consulted, because the bias exists only for people who came from nowhere.</p>
 */
public final class CultureAssignment {

    private CultureAssignment() {}

    /**
     * Settles this villager's culture if they have none, and returns it. Empty when no culture can
     * be found at all, which is the normal answer in a world with none authored.
     */
    public static String ensure(ServerLevel level, VillagerEntityMCA villager) {
        String recorded = Naming.cultureOf(villager);
        if (Cultures.exists(recorded)) return recorded;

        Optional<String> inherited = Naming.cultureFromParents(villager);
        if (inherited.isPresent()) return record(villager, inherited.get());

        String home = ofVillage(level, villager);
        if (Cultures.exists(home)) return record(villager, home);

        String founded = founderRoll(villager);
        if (Cultures.exists(founded)) {
            // A founder settling a village is also the moment its culture starts existing.
            rememberForVillage(level, villager, founded);
            return record(villager, founded);
        }
        return "";
    }

    /** The culture a village has settled into, or empty. */
    public static String ofVillage(ServerLevel level, VillagerEntityMCA villager) {
        try {
            Optional<Village> home = villager.getResidency().getHomeVillage();
            if (home.isEmpty()) return "";
            return NamingRegisterSavedData.get(level.getServer()).villageCulture(home.get().getId());
        } catch (Throwable ignored) {
            return "";
        }
    }

    /** Assigns a village's culture outright, which is what a command or a datapack does. */
    public static void assignVillage(ServerLevel level, int villageId, String culture) {
        NamingRegisterSavedData.get(level.getServer()).putVillageCulture(villageId, culture);
    }

    /**
     * A village takes the culture of whoever settles it first, and keeps it until something says
     * otherwise. Emergent rather than authored, so an existing world grows cultures on its own.
     */
    private static void rememberForVillage(ServerLevel level, VillagerEntityMCA villager, String culture) {
        try {
            Optional<Village> home = villager.getResidency().getHomeVillage();
            if (home.isEmpty()) return;
            NamingRegisterSavedData data = NamingRegisterSavedData.get(level.getServer());
            if (data.villageCulture(home.get().getId()).isEmpty()) {
                data.putVillageCulture(home.get().getId(), culture);
            }
        } catch (Throwable ignored) {
            // A village we cannot key is one that simply has no culture yet.
        }
    }

    /**
     * The founder roll: the only place a Root has a say, and only ever as a weight.
     *
     * <p>A root that declares no bias gets no culture, deliberately. Rolling one anyway would hand
     * every founder in a world with no culture packs a culture drawn from the sixty-odd implicit
     * ones, and each villager in a village would then be named from a different tradition instead
     * of from their region. Declining leaves naming exactly where it was, so cultures are something
     * a pack opts into rather than something that reshapes a world by being installed.</p>
     */
    private static String founderRoll(VillagerEntityMCA villager) {
        List<CulturalSpawnBias.Entry> bias = biasOf(villager);
        if (bias.isEmpty()) return "";
        RandomSource random = villager.getRandom();

        float total = 0.0F;
        for (CulturalSpawnBias.Entry entry : bias) {
            if (entry.rate() > 0 && resolvable(entry.culture())) total += entry.rate();
        }
        // A bias whose every culture is missing is a pack referencing something absent, not a
        // request for any culture at all, so it declines the same way an unbiased root does.
        if (total <= 0.0F) return "";

        float roll = random.nextFloat() * total;
        for (CulturalSpawnBias.Entry entry : bias) {
            if (entry.rate() <= 0 || !resolvable(entry.culture())) continue;
            roll -= entry.rate();
            if (roll <= 0.0F) return pick(entry.culture(), random);
        }
        return "";
    }

    /** The composed bias of this villager's root: ancestry, then lineage, then the root itself. */
    private static List<CulturalSpawnBias.Entry> biasOf(VillagerEntityMCA villager) {
        String root = TownsteadVillagers.get(villager).life().rootId();
        if (root.isEmpty()) return List.of();
        ResourceLocation id = ResourceLocation.tryParse(root);
        if (id == null) return List.of();
        return com.aetherianartificer.townstead.root.RootRegistry
                .effectiveCulturalSpawnBias(id).entries();
    }

    private static boolean resolvable(String culture) {
        return Cultures.ANY.equals(culture) || Cultures.exists(culture);
    }

    private static String pick(String culture, RandomSource random) {
        return Cultures.ANY.equals(culture) ? anyCulture(random) : culture;
    }

    /**
     * Any loaded culture. Only ever reached through an explicit {@code any} entry, which is a pack
     * deliberately keeping a root's founders open rather than a default applied to everyone.
     */
    private static String anyCulture(RandomSource random) {
        List<ResourceLocation> all = new ArrayList<>(Cultures.allIds());
        if (all.isEmpty()) return "";
        return all.get(random.nextInt(all.size())).toString();
    }

    private static String record(VillagerEntityMCA villager, String culture) {
        ResourceLocation id = ResourceLocation.tryParse(culture);
        if (id != null) Naming.recordCulture(villager, id);
        return culture;
    }

    /** The culture recorded on a villager, without settling one. */
    public static @Nullable Culture recorded(VillagerEntityMCA villager) {
        return Cultures.get(TownsteadVillagers.get(villager).life().culture());
    }
}
