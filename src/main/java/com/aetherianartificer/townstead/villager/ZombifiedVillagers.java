package com.aetherianartificer.townstead.villager;

import com.aetherianartificer.townstead.root.ExpressedGenesS2CPayload;
import com.aetherianartificer.townstead.root.RootSyncS2CPayload;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A villager who turns into a zombie is still that person. MCA rebuilds the entity on both turns and
 * copies only its own data, so everything Townstead knows about them (Root, genes, family name, life,
 * needs) rides across here, is kept on the zombie, and comes back whole on a cure. A zombie that
 * drowns keeps them too: the Drowned holds the zombie body they had, is drawn as it, and a cure turns
 * it back into that zombie villager mid-cure.
 */
public final class ZombifiedVillagers {

    private static final String STATE_KEY = "townstead:state";
    private static final String CAPITALS_IDENTITY = "McaCapitalsIdentity";
    // On a Drowned: the MCA zombie villager it was ({type, data}), which a cure brings back.
    private static final String DROWNED_BODY = "townstead:drowned_body";
    private static final String TYPE = "type";
    private static final String DATA = "data";
    // Roles a person holds only while they are themselves: a zombie follows no one and holds no post.
    private static final Set<String> LEFT_BEHIND = Set.of(
            "townstead:companion", "townstead:leaving", "townstead_downed_until", "townstead:minion",
            "townstead:visitor", "townstead:visitor_party", "townstead:seated_since", "townstead:levy_issued",
            "townstead_thermal_care", "townstead_thermal_care_until", "townstead:toyed", "townstead:toyed_with",
            "TownsteadBuildingPin", "TownsteadBuildingPinDimension", "TownsteadBuildingPinVillage");
    // Bookkeeping about the entity rather than the person. The aging stamp in particular never
    // refreshes on a zombie, so carrying it back would age a cured child by every zombie day.
    private static final Set<String> ENTITY_ONLY = Set.of("townstead.last_seen_world_day", DROWNED_BODY);
    private static final double SYNC_RANGE = 160;

    // The person each zombie still is, rebuilt on demand for names and gene expression. Never added.
    private static final Map<Mob, VillagerEntityMCA> PEOPLE = new WeakHashMap<>();
    // The zombie body each Drowned holds, rebuilt on demand to build its person from. Never added.
    private static final Map<Mob, ZombieVillagerEntityMCA> BODIES = new WeakHashMap<>();
    // The zombie each stand-in speaks for, so whatever is recorded on the stand-in lands on the zombie.
    private static final Map<VillagerEntityMCA, WeakReference<Mob>> ZOMBIES = new WeakHashMap<>();
    // Villagers back from a cure, so the finalizeSpawn the cure runs does not roll them anew.
    private static final Map<VillagerEntityMCA, Boolean> RESTORED = new WeakHashMap<>();
    // The tick a cure is under way in: the villager joins the world before MCA copies them over.
    private static long curingTick = Long.MIN_VALUE;

    private ZombifiedVillagers() {}

    /** Whether {@code zombie} (a zombie villager or a Drowned) carries a person Townstead knew. */
    public static boolean carries(Mob zombie) {
        //? if neoforge {
        if (zombie.hasData(com.aetherianartificer.townstead.Townstead.LIFE_DATA)
                && zombie.getData(com.aetherianartificer.townstead.Townstead.LIFE_DATA).contains(STATE_KEY)) return true;
        //?}
        return zombie.getPersistentData().contains(STATE_KEY);
    }

    /** After MCA turned {@code villager} into {@code zombie}: the zombie keeps who they were. */
    public static void zombified(VillagerEntityMCA villager, Mob zombie) {
        if (zombie.level().isClientSide) return;
        TownsteadVillagers.flush(villager);
        carry(villager, zombie, true);
        synchronized (PEOPLE) {
            PEOPLE.remove(zombie);
        }
        syncToTracking(zombie);
    }

    /** Before MCA converts {@code zombie} (a cure, or drowning): it is someone already known. */
    public static void curing(ZombieVillagerEntityMCA zombie) {
        if (zombie.level().isClientSide || !carries(zombie)) return;
        curingTick = zombie.level().getGameTime();
        // Settle what the stand-in holds onto the zombie now: the villager joining next shares the
        // UUID, and may load a blank state over it before the copy.
        VillagerEntityMCA standIn;
        synchronized (PEOPLE) {
            standIn = PEOPLE.get(zombie);
        }
        if (standIn != null) TownsteadVillagers.flush(standIn);
    }

    /** Whether a villager joining {@code level} now is one a cure is bringing back. */
    public static boolean curing(ServerLevel level) {
        return curingTick == level.getGameTime();
    }

    /** After MCA converted {@code zombie}: a cured villager comes back as themselves, a Drowned keeps them. */
    public static void cured(ZombieVillagerEntityMCA zombie, @Nullable Mob converted) {
        curingTick = Long.MIN_VALUE;
        if (converted instanceof Drowned drowned) {
            drowned(zombie, drowned);
            return;
        }
        if (!(converted instanceof VillagerEntityMCA villager) || !(villager.level() instanceof ServerLevel level)
                || !carries(zombie)) return;
        synchronized (PEOPLE) {
            PEOPLE.remove(zombie);
        }
        carry(zombie, villager, false);
        TownsteadVillagers.reload(villager);
        synchronized (RESTORED) {
            RESTORED.put(villager, Boolean.TRUE);
        }
        VillagerArrival.settle(level, villager);
        // Whoever saw them join was told about a stranger: tell them again, needs and all.
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(villager) < SYNC_RANGE * SYNC_RANGE) {
                com.aetherianartificer.townstead.Townstead.townstead$syncVillagerTo(player, villager);
            }
        }
    }

    // The zombie drowned: the Drowned keeps the person and the body they had, to be drawn and cured.
    private static void drowned(ZombieVillagerEntityMCA zombie, Drowned drowned) {
        if (drowned.level().isClientSide || !carries(zombie)) return;
        synchronized (PEOPLE) {
            PEOPLE.remove(zombie);
        }
        carry(zombie, drowned, false);
        CompoundTag data = zombie.saveWithoutId(new CompoundTag());
        // Townstead's state travels on the Drowned itself; the body only needs to be MCA's.
        for (String key : new String[]{"NeoForgeData", "neoforge:attachments", "ForgeData", "ForgeCaps", "Passengers"}) {
            data.remove(key);
        }
        CompoundTag body = new CompoundTag();
        body.putString(TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(zombie.getType()).toString());
        body.put(DATA, data);
        drowned.getPersistentData().put(DROWNED_BODY, body);
        drowned.setPersistenceRequired();
        syncToTracking(drowned);
    }

    /** Whether {@code mob} is a zombie villager or a Drowned that may carry a person. */
    public static boolean isZombified(Mob mob) {
        return mob instanceof ZombieVillagerEntityMCA || isDrownedPerson(mob);
    }

    /** Whether {@code mob} is a Drowned that was someone. */
    public static boolean isDrownedPerson(Mob mob) {
        return mob instanceof Drowned && mob.getPersistentData().contains(DROWNED_BODY);
    }

    /**
     * A golden apple given to a drowned villager under Weakness: the Drowned becomes the zombie
     * villager they were, and that zombie takes the apple, so the cure runs as it does for any zombie
     * villager. True when the cure started.
     */
    public static boolean tryCureDrowned(Mob mob, ServerPlayer player, InteractionHand hand) {
        if (mob.isRemoved() || !isDrownedPerson(mob) || !(mob.level() instanceof ServerLevel level)) return false;
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.GOLDEN_APPLE) || !mob.hasEffect(MobEffects.WEAKNESS)) return false;
        ZombieVillagerEntityMCA zombie = rebuild(mob);
        if (zombie == null) return false;
        VillagerEntityMCA standIn;
        synchronized (PEOPLE) {
            standIn = PEOPLE.remove(mob);
            BODIES.remove(mob);
        }
        if (standIn != null) TownsteadVillagers.flush(standIn);
        carry(mob, zombie, false);
        zombie.setYHeadRot(mob.getYHeadRot());
        zombie.setYBodyRot(mob.yBodyRot);
        zombie.setHealth(Math.max(1f, zombie.getMaxHealth() * mob.getHealth() / mob.getMaxHealth()));
        zombie.removeAllEffects();
        for (MobEffectInstance effect : mob.getActiveEffects()) zombie.addEffect(new MobEffectInstance(effect));
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack worn = mob.getItemBySlot(slot);
            if (!worn.isEmpty()) zombie.setItemSlot(slot, worn.copy());
        }
        if (mob.hasCustomName()) zombie.setCustomName(mob.getCustomName());
        zombie.setPersistenceRequired();
        mob.discard();
        level.addFreshEntity(zombie);
        return zombie.interact(player, hand).consumesAction();
    }

    // The zombie villager a Drowned was, from what it holds, placed where the Drowned is. Never added.
    private static @Nullable ZombieVillagerEntityMCA rebuild(Mob drowned) {
        CompoundTag body = drowned.getPersistentData().getCompound(DROWNED_BODY);
        ResourceLocation typeId = ResourceLocation.tryParse(body.getString(TYPE));
        EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (type == null || !(type.create(drowned.level()) instanceof ZombieVillagerEntityMCA zombie)) return null;
        zombie.load(body.getCompound(DATA));
        zombie.moveTo(drowned.getX(), drowned.getY(), drowned.getZ(), drowned.getYRot(), drowned.getXRot());
        return zombie;
    }

    // The zombie body a zombie villager or drowned villager has, or null for anything else.
    private static @Nullable ZombieVillagerEntityMCA body(Mob mob) {
        if (mob instanceof ZombieVillagerEntityMCA zombie) return zombie;
        if (!isDrownedPerson(mob)) return null;
        synchronized (PEOPLE) {
            ZombieVillagerEntityMCA known = BODIES.get(mob);
            if (known != null) return known;
        }
        ZombieVillagerEntityMCA rebuilt = rebuild(mob);
        if (rebuilt == null) return null;
        synchronized (PEOPLE) {
            BODIES.put(mob, rebuilt);
        }
        return rebuilt;
    }

    /** Whether {@code villager} is back from a cure; true once, for the finalizeSpawn the cure runs. */
    public static boolean takeRestored(VillagerEntityMCA villager) {
        synchronized (RESTORED) {
            return RESTORED.remove(villager) != null;
        }
    }

    /**
     * The person {@code zombie} (a zombie villager or a Drowned) still is, for reading their name and
     * genes, or null for one Townstead never knew. Carries the person's UUID and the zombie's network id.
     */
    public static @Nullable VillagerEntityMCA person(Mob zombie) {
        if (!carries(zombie)) return null;
        ZombieVillagerEntityMCA mca = body(zombie);
        if (mca == null) return null;
        synchronized (PEOPLE) {
            VillagerEntityMCA cached = PEOPLE.get(zombie);
            if (cached != null) {
                cached.moveTo(zombie.getX(), zombie.getY(), zombie.getZ(), zombie.getYRot(), zombie.getXRot());
                return cached;
            }
        }
        VillagerEntityMCA person = mca.getGenetics().getGender().getVillagerType().create(zombie.level());
        if (person == null) return null;
        person.setUUID(mca.getUUID());
        person.setId(zombie.getId());
        person.moveTo(zombie.getX(), zombie.getY(), zombie.getZ(), zombie.getYRot(), zombie.getXRot());
        person.copyVillagerAttributesFrom(mca);
        Component name = zombie.getCustomName();
        if (name != null) person.setCustomName(name);
        carry(zombie, person, false);
        synchronized (PEOPLE) {
            PEOPLE.put(zombie, person);
            ZOMBIES.put(person, new WeakReference<>(zombie));
        }
        return person;
    }

    /** The zombie {@code villager} stands in for, or null when it is a real villager. */
    public static @Nullable Mob zombieOf(VillagerEntityMCA villager) {
        synchronized (PEOPLE) {
            WeakReference<Mob> zombie = ZOMBIES.get(villager);
            return zombie == null ? null : zombie.get();
        }
    }

    /** Tells {@code player} who {@code zombie} was: their body if drowned, name, Root, genes and life stage. */
    public static void syncTo(ServerPlayer player, Mob zombie) {
        VillagerEntityMCA person = person(zombie);
        if (person == null) return;
        if (isDrownedPerson(zombie)) {
            CompoundTag body = zombie.getPersistentData().getCompound(DROWNED_BODY);
            DrownedPersonS2CPayload look = new DrownedPersonS2CPayload(zombie.getId(), body.getString(TYPE),
                    body.getCompound(DATA).copy());
            //? if neoforge {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, look);
            //?} else {
            /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, look);
            *///?}
        }
        com.aetherianartificer.townstead.naming.NameSyncTarget.syncToPlayer(player, person);
        RootSyncS2CPayload root = new RootSyncS2CPayload(zombie.getId(), TownsteadVillagers.get(person).life().rootId());
        ExpressedGenesS2CPayload genes = ExpressedGenesS2CPayload.forEntity(zombie.getId(), person);
        // Their life stage, so a body that changes with age (an egg, a child's) draws the right one.
        com.aetherianartificer.townstead.calendar.VillagerLifeSyncPayload life =
                com.aetherianartificer.townstead.Townstead.townstead$lifeSync(person, player);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, root);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, genes);
        if (life != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, life);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, root);
        com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, genes);
        if (life != null) com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, life);
        *///?}
    }

    // The zombie joined the world before it carried anyone, so whoever already sees it is told now.
    private static void syncToTracking(Mob zombie) {
        if (!(zombie.level() instanceof ServerLevel level)) return;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(zombie) < SYNC_RANGE * SYNC_RANGE) syncTo(player, zombie);
        }
    }

    private static void carry(Mob from, Mob to, boolean leaveRoles) {
        //? if neoforge {
        copyAttachment(from, to, com.aetherianartificer.townstead.Townstead.LIFE_DATA);
        copyAttachment(from, to, com.aetherianartificer.townstead.Townstead.HUNGER_DATA);
        copyAttachment(from, to, com.aetherianartificer.townstead.Townstead.THIRST_DATA);
        copyAttachment(from, to, com.aetherianartificer.townstead.Townstead.FATIGUE_DATA);
        copyAttachment(from, to, com.aetherianartificer.townstead.Townstead.SHIFT_DATA);
        //?}
        CompoundTag source = from.getPersistentData();
        CompoundTag target = to.getPersistentData();
        for (String key : source.getAllKeys()) {
            if (!identity(key) || (leaveRoles && LEFT_BEHIND.contains(key))) continue;
            net.minecraft.nbt.Tag value = source.get(key);
            if (value != null) target.put(key, value.copy());
        }
    }

    //? if neoforge {
    private static void copyAttachment(Mob from, Mob to,
                                       java.util.function.Supplier<net.neoforged.neoforge.attachment.AttachmentType<CompoundTag>> type) {
        if (from.hasData(type)) to.setData(type, from.getData(type).copy());
    }
    //?}

    private static boolean identity(String key) {
        String lower = key.toLowerCase(Locale.ROOT);
        if (ENTITY_ONLY.contains(key)) return false;
        return lower.startsWith("townstead") || lower.startsWith("pheno") || key.equals(CAPITALS_IDENTITY);
    }
}
