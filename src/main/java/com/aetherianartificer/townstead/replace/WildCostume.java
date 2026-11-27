package com.aetherianartificer.townstead.replace;

import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.aetherianartificer.townstead.root.ExpressedGenesS2CPayload;
import com.aetherianartificer.townstead.villager.NonResidents;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.aetherianartificer.townstead.compat.mca.McaResidency;
import io.netty.buffer.Unpooled;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A replaced mob keeps its own body and mind and wears a person's look: the villager it would have
 * been, saved on the mob. The client draws that villager in the mob's place. Looks and names are
 * read from the save. A real villager exists on the server only while a player deals with the
 * person, and joins the world only when a cure turns the mob into them for good.
 */
public final class WildCostume {
    private static final String COSTUME = "townstead:costume";
    private static final String TYPE = "type";
    private static final String DATA = "data";
    private static final String NAME = "name";
    private static final String ROOT = "root";
    private static final String GENES = "genes";
    // The person a costumed mob shows, rebuilt on demand for talk, gifts and cures.
    private static final Map<Entity, VillagerEntityMCA> PEOPLE = new WeakHashMap<>();
    // The mob each shown person stands in for, so talk and stance resolve to the mob. Held weakly,
    // or the two maps would keep each other's keys alive and no costumed mob could ever be freed.
    private static final Map<VillagerEntityMCA, WeakReference<Mob>> WEARERS = new WeakHashMap<>();

    private WildCostume() {}

    public static boolean has(Entity entity) {
        return !(entity instanceof VillagerEntityMCA) && entity.getPersistentData().contains(COSTUME);
    }

    /**
     * The name {@code mob} carries of its own, from a name tag or a mod that names its mobs, or null
     * when it goes by its type's name.
     */
    public static @Nullable String ownName(Entity mob) {
        String name = mob.getName().getString();
        return name.equals(mob.getType().getDescription().getString()) ? null : name;
    }

    /** Saves {@code person} (never added to the world) as the look {@code mob} wears. */
    public static void dress(Mob mob, VillagerEntityMCA person) {
        TownsteadVillagers.flush(person);
        CompoundTag costume = new CompoundTag();
        costume.putString(TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(person.getType()).toString());
        costume.put(DATA, kept(person.saveWithoutId(new CompoundTag())));
        costume.putString(NAME, person.getDisplayName().getString());
        String rootId = TownsteadVillagers.get(person).life().rootId();
        costume.putString(ROOT, rootId == null ? "" : rootId);
        costume.putByteArray(GENES, encoded(ExpressedGenesS2CPayload.forEntity(mob.getId(), person)));
        mob.getPersistentData().put(COSTUME, costume);
        // Re-saving the person already shown keeps it, so a conversation with them carries on.
        synchronized (PEOPLE) {
            if (PEOPLE.get(mob) != person) PEOPLE.remove(mob);
        }
    }

    /** What is worth keeping of a person who is not in the world: not a mind, nor other mods' data. */
    private static CompoundTag kept(CompoundTag data) {
        data.remove("Brain");
        data.remove("Gossips");
        data.remove("Offers");
        //? if neoforge {
        CompoundTag attachments = data.getCompound(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY);
        for (String key : java.util.List.copyOf(attachments.getAllKeys())) {
            if (!key.startsWith(com.aetherianartificer.townstead.Townstead.MOD_ID)) attachments.remove(key);
        }
        //?} else {
        /*data.remove("ForgeCaps");
        *///?}
        return data;
    }

    private static byte[] encoded(ExpressedGenesS2CPayload genes) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        genes.write(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    /** Stops holding a person built only to be saved; the save is all that remains of them. */
    static void release(VillagerEntityMCA person) {
        EntityStates.forget(person);
        TownsteadVillagers.release(person);
    }

    /** The saved costume, brought up to date when it was worn before names and genes were saved. */
    private static @Nullable CompoundTag costume(Mob mob) {
        if (!has(mob)) return null;
        CompoundTag costume = mob.getPersistentData().getCompound(COSTUME);
        if (costume.contains(NAME)) return costume;
        VillagerEntityMCA person = person(mob);
        if (person == null) return null;
        dress(mob, person);
        synchronized (PEOPLE) {
            PEOPLE.remove(mob);
            WEARERS.remove(person);
        }
        release(person);
        return mob.getPersistentData().getCompound(COSTUME);
    }

    /** The name of the person {@code mob} shows, or null when it wears none. */
    public static @Nullable String name(Mob mob) {
        CompoundTag costume = costume(mob);
        return costume == null ? null : costume.getString(NAME);
    }

    /** The person {@code mob} shows, or null when it wears none or the save no longer loads. */
    public static @Nullable VillagerEntityMCA person(Mob mob) {
        if (!has(mob) || !(mob.level() instanceof ServerLevel level)) return null;
        synchronized (PEOPLE) {
            VillagerEntityMCA cached = PEOPLE.get(mob);
            if (cached != null) {
                cached.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
                return cached;
            }
        }
        VillagerEntityMCA person = CostumeFamily.outside(() -> load(level, mob.getPersistentData().getCompound(COSTUME)));
        if (person == null) return null;
        person.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        // Never added to the world: carrying the mob's id keeps story lines naming the right speaker.
        person.setId(mob.getId());
        synchronized (PEOPLE) {
            PEOPLE.put(mob, person);
            WEARERS.put(person, new WeakReference<>(mob));
        }
        return person;
    }

    /** The mob a shown person stands in for, or null when {@code person} is a real villager. */
    public static @Nullable Mob wearer(net.minecraft.world.entity.LivingEntity person) {
        if (!(person instanceof VillagerEntityMCA villager)) return null;
        synchronized (PEOPLE) {
            WeakReference<Mob> wearer = WEARERS.get(villager);
            return wearer == null ? null : wearer.get();
        }
    }

    /** Whether {@code entity} is a person a mob is showing, rather than a villager in the world. */
    public static boolean isShownPerson(Entity entity) {
        if (!(entity instanceof VillagerEntityMCA villager)) return false;
        synchronized (PEOPLE) {
            return WEARERS.containsKey(villager);
        }
    }

    private static @Nullable VillagerEntityMCA load(ServerLevel level, CompoundTag costume) {
        ResourceLocation typeId = ResourceLocation.tryParse(costume.getString(TYPE));
        EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (type == null || !(type.create(level) instanceof VillagerEntityMCA person)) return null;
        person.load(costume.getCompound(DATA));
        return person;
    }

    /** Tells a player who starts seeing {@code mob} which person to draw, with that person's Root look. */
    public static void syncTo(ServerPlayer player, Mob mob) {
        CompoundTag costume = costume(mob);
        if (costume == null) return;
        // The client only draws the person, from what a save reads into the villager itself.
        CompoundTag data = costume.getCompound(DATA).copy();
        data.remove("ForgeData");
        data.remove("NeoForgeData");
        //? if neoforge {
        data.remove(net.neoforged.neoforge.attachment.AttachmentHolder.ATTACHMENTS_NBT_KEY);
        //?}
        WildCostumeS2CPayload look = new WildCostumeS2CPayload(mob.getId(), costume.getString(TYPE), data);
        var root = new com.aetherianartificer.townstead.root.RootSyncS2CPayload(mob.getId(), costume.getString(ROOT));
        var genes = genes(mob, costume);
        var name = WildPlates.plate(player, mob, costume.getString(NAME));
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, look);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, root);
        if (genes != null) net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, genes);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, name);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, look);
        com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, root);
        if (genes != null) com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, genes);
        com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, name);
        *///?}
    }

    private static @Nullable ExpressedGenesS2CPayload genes(Mob mob, CompoundTag costume) {
        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(costume.getByteArray(GENES)));
            return ExpressedGenesS2CPayload.read(buf).withEntityId(mob.getId());
        } catch (RuntimeException unreadable) {
            // Saved by a version that wrote them differently; the person still knows their own.
            VillagerEntityMCA person = person(mob);
            return person == null ? null : ExpressedGenesS2CPayload.forEntity(mob.getId(), person);
        }
    }

    /**
     * Some mods add a mob to the world before finishing its spawn (Vampirism's totems, tents and
     * raids), so players already see it bare. Sends the look to everyone in tracking range.
     */
    public static void syncIfTracked(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level) || level.getEntity(mob.getId()) != mob) return;
        double range = mob.getType().clientTrackingRange() * 16.0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(mob) <= range * range) syncTo(player, mob);
        }
    }

    /**
     * Replaces {@code mob} with the person it shows: same look, health share, effects and place.
     * The person is no longer wild. A settled one seeks a home like anyone else; otherwise they
     * come back as a newcomer who belongs nowhere yet. Null when there is none.
     */
    public static @Nullable VillagerEntityMCA convert(Mob mob, boolean settled) {
        if (!has(mob) || !(mob.level() instanceof ServerLevel level)) return null;
        VillagerEntityMCA person = load(level, mob.getPersistentData().getCompound(COSTUME));
        if (person == null) return null;
        person.getPersistentData().remove(MobReplacer.WILD);
        person.getPersistentData().remove(MobReplacer.REPLACED);
        person.getPersistentData().remove(MobReplacer.NAME_HIDDEN);
        person.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        person.setYHeadRot(mob.getYHeadRot());
        person.setYBodyRot(mob.yBodyRot);
        person.setHealth(Math.max(1f, person.getMaxHealth() * mob.getHealth() / mob.getMaxHealth()));
        for (MobEffectInstance effect : mob.getActiveEffects()) person.addEffect(new MobEffectInstance(effect));
        // A name the mob took on while costumed (a name tag) outlives the costume.
        String own = ownName(mob);
        if (own != null && !own.equals(com.aetherianartificer.townstead.naming.VillagerNames.nameFor(person, null))) {
            com.aetherianartificer.townstead.naming.Naming.adopt(person, own);
        }
        mob.discard();
        synchronized (PEOPLE) {
            VillagerEntityMCA shown = PEOPLE.remove(mob);
            if (shown != null) WEARERS.remove(shown);
        }
        level.addFreshEntityWithPassengers(person);
        CostumeFamily.found(person);
        if (!settled) EntityStates.set(person, NonResidents.NEWCOMER, 1, 0, null);
        person.refreshBrain(level);
        if (settled) McaResidency.joinVillage(person);
        return person;
    }

    /**
     * Turns a wild villager from before mobs kept their own minds back into the mob it replaced,
     * wearing its look. False when the mob type is gone (its mod was removed).
     */
    public static boolean revert(VillagerEntityMCA villager, EntityType<?> replaced) {
        if (!(villager.level() instanceof ServerLevel level) || !(replaced.create(level) instanceof Mob mob)) return false;
        mob.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
        //? if >=1.21 {
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(villager.blockPosition()), MobSpawnType.CONVERSION, null);
        //?} else {
        /*mob.finalizeSpawn(level, level.getCurrentDifficultyAt(villager.blockPosition()), MobSpawnType.CONVERSION, null, null);
        *///?}
        mob.setHealth(Math.max(1f, mob.getMaxHealth() * villager.getHealth() / villager.getMaxHealth()));
        dress(mob, villager);
        com.aetherianartificer.townstead.compat.mca.McaFamily.forget(villager);
        villager.discard();
        release(villager);
        level.addFreshEntityWithPassengers(mob);
        return true;
    }
}
