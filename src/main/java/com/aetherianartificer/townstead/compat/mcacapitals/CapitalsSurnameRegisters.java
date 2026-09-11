package com.aetherianartificer.townstead.compat.mcacapitals;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.naming.NameList;
import com.aetherianartificer.townstead.naming.NameLists;
import com.aetherianartificer.townstead.naming.NamingRegisters;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.resources.WeightedPool;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Recovers a villager's naming register from the surname MCA Capitals already gave them.
 *
 * <p>Capitals picks a surname from {@code data/mcacapitals/surnames/cultures/<bucket>.json} using
 * the same positional lookup Townstead now stabilises, then persists it. On a world where that
 * lookup has already drifted, the persisted surname is better evidence of the villager's naming
 * tradition than a fresh derivation would be, because 89% of the surnames Capitals ships appear in
 * exactly one bucket. Reading that back gives a villager whose surname is Fischer a register that
 * will keep naming their children Fischer, instead of one that starts naming them Silverleaf.</p>
 *
 * <p>This is a fingerprint, not a history lookup. It recovers the register consistent with the
 * villager's own surname, which is what a player actually sees; it cannot recover a register that
 * drifted before Capitals assigned the surname in the first place. It is never worse than deriving
 * afresh, so it runs for any villager without a recorded register, not only pre-existing ones.</p>
 *
 * <p>Nothing here links a Capitals class. The identity compound, its keys, and the surname
 * directory are all strings, so this compiles and runs with Capitals absent.</p>
 */
public final class CapitalsSurnameRegisters extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(Townstead.MOD_ID + "/CapitalsSurnameRegisters");
    private static final Gson GSON = new Gson();

    public static final String MOD_ID = "mcacapitals";

    /** Capitals' per-villager identity compound, and the two keys read from it. */
    private static final String IDENTITY_TAG = "McaCapitalsIdentity";
    private static final String KEY_SURNAME = "CurrentSurname";
    private static final String KEY_SURNAME_SOURCE = "SurnameSource";

    /**
     * Surname sources that reflect where a villager came from. A surname taken in marriage, granted
     * with a player house, or set by decree says something about the villager's life rather than
     * their naming tradition, so it is not evidence and is skipped.
     */
    private static final Set<String> ORIGIN_SOURCES = Set.of("GENERATED", "BIRTH");

    /** Lowercased surname to the single bucket that owns it. Surnames in several buckets are dropped. */
    private static volatile Map<String, String> byUniqueSurname = Map.of();

    /** Each bucket's surnames, so a tradition can draw family names from {@code mcacapitals:<bucket>}. */
    private static volatile Map<String, NameList> byBucket = Map.of();

    public CapitalsSurnameRegisters() {
        super(GSON, "surnames/cultures");
    }

    /**
     * Offers the surname fingerprint to {@link NamingRegisters} as evidence, only when Capitals is
     * installed. The index this reads is static and maintained by the reload listener, so the
     * evidence is registered as a function rather than as this listener instance.
     */
    public static void bootstrap() {
        if (!ModCompat.isLoaded(MOD_ID)) return;
        NamingRegisters.addEvidence(CapitalsSurnameRegisters::fingerprint);
        NameLists.addProvider(MOD_ID, CapitalsSurnameRegisters::listFor);
    }

    /**
     * Capitals' surnames for one bucket, reached as {@code mcacapitals:<bucket>}. Family names only:
     * the given names Capitals ships go into MCA's own namespace, where {@code mca:<bucket>} already
     * finds them and nothing can tell them from MCA's.
     */
    private static @Nullable NameList listFor(String bucket) {
        return bucket == null ? null : byBucket.get(bucket.toLowerCase(Locale.ROOT));
    }

    /**
     * The register the villager's Capitals surname points to, or empty when Capitals is absent, the
     * villager has no qualifying surname, or the surname appears in more than one bucket.
     */
    public static String fingerprint(Entity entity) {
        if (entity == null || !ModCompat.isLoaded(MOD_ID)) return "";
        Map<String, String> index = byUniqueSurname;
        if (index.isEmpty()) return "";

        CompoundTag identity = entity.getPersistentData().getCompound(IDENTITY_TAG);
        if (identity.isEmpty()) return "";
        if (!ORIGIN_SOURCES.contains(identity.getString(KEY_SURNAME_SOURCE))) return "";

        String surname = identity.getString(KEY_SURNAME).trim();
        if (surname.isEmpty()) return "";
        String register = index.get(surname.toLowerCase(Locale.ROOT));
        return register == null ? "" : register;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        if (!ModCompat.isLoaded(MOD_ID)) {
            byUniqueSurname = Map.of();
            byBucket = Map.of();
            return;
        }

        Map<String, String> owner = new HashMap<>();
        Set<String> shared = new HashSet<>();
        Map<String, NameList> pools = new HashMap<>();

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            ResourceLocation file = entry.getKey();
            if (!MOD_ID.equals(file.getNamespace())) continue;
            String bucket = file.getPath();
            int slash = bucket.lastIndexOf('/');
            if (slash >= 0) bucket = bucket.substring(slash + 1);
            if (bucket.isEmpty()) continue;

            WeightedPool.Mutable<String> pool = new WeightedPool.Mutable<>("?");
            boolean any = false;
            for (String surname : surnamesOf(entry.getValue(), file)) {
                String key = surname.toLowerCase(Locale.ROOT);
                String previous = owner.putIfAbsent(key, bucket);
                if (previous != null && !previous.equals(bucket)) {
                    shared.add(key);
                }
                pool.add(surname, 1.0F);
                any = true;
            }
            if (any) {
                ResourceLocation id = ResourceLocation.tryParse(MOD_ID + ":" + bucket);
                if (id != null) pools.put(bucket, new NameList(id, Map.of(), pool));
            }
        }

        owner.keySet().removeAll(shared);
        byUniqueSurname = Map.copyOf(owner);
        byBucket = Map.copyOf(pools);

        LOGGER.info("Indexed {} MCA Capitals surnames unique to one naming register ({} shared across registers, ignored)",
                byUniqueSurname.size(), shared.size());
    }

    private static Iterable<String> surnamesOf(JsonElement element, ResourceLocation file) {
        Set<String> names = new HashSet<>();
        try {
            if (!element.isJsonObject()) return names;
            JsonObject object = element.getAsJsonObject();
            JsonElement list = object.get("surnames");
            if (list == null || !list.isJsonArray()) return names;
            JsonArray array = list.getAsJsonArray();
            for (JsonElement value : array) {
                if (value == null || !value.isJsonPrimitive()) continue;
                String surname = value.getAsString();
                if (surname != null && !surname.isBlank()) names.add(surname.trim());
            }
        } catch (Exception exception) {
            LOGGER.warn("Could not read MCA Capitals surname list {}", file, exception);
        }
        return names;
    }
}
