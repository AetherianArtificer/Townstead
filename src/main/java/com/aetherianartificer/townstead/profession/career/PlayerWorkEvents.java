package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.profession.def.WorkTaskTypes;
import com.aetherianartificer.townstead.work.job.WorkJobDef;
import com.aetherianartificer.townstead.work.job.WorkJobs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;
//? if neoforge {
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.brewing.PlayerBrewedPotionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
//?} else if forge {
/*import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.brewing.PlayerBrewedPotionEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
*///?}

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Player-side attribution for work a villager career also does: crafting, smelting and smoking,
 * potion brewing, shearing, and authored entity jobs such as slaughter. Each hook resolves the
 * career from the same profession definitions the villager engines read, so a data pack that
 * teaches villagers a trade teaches the player's Career record the same trade for free.
 */
//? if neoforge {
@EventBusSubscriber(modid = Townstead.MOD_ID)
//?} else if forge {
/*@Mod.EventBusSubscriber(modid = Townstead.MOD_ID)
*///?}
public final class PlayerWorkEvents {
    /** One station completion by hand. Villagers earn the recipe tier; a hand-made item counts one. */
    public static final int XP_STATION = 1;
    /** One shorn animal. Shared with the shepherd engine. */
    public static final int XP_SHEAR = 1;
    /** How long a hit on a job target stays attributable to the player who struck it. */
    private static final int HIT_MEMORY_TICKS = 100;

    public static final Set<ResourceLocation> SMELTING_TYPES = Set.of(WorkTaskTypes.SMELT, WorkTaskTypes.SMOKE);

    private record Hit(UUID player, WorkJobDef job, long gameTime) {}

    private static final Map<UUID, Hit> HITS = new HashMap<>();
    private static final Set<UUID> PENDING_SHEAR = new HashSet<>();

    private PlayerWorkEvents() {}

    private static boolean realPlayer(Object entity) {
        return entity instanceof ServerPlayer && !(entity instanceof FakePlayer);
    }

    // ── Stations ──

    /** Crafting at a table or in the inventory grid is the craft task's surface. */
    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!realPlayer(event.getEntity())) return;
        creditStation(event.getEntity(), Set.of(WorkTaskTypes.CRAFT),
                BuiltInRegistries.BLOCK.getKey(Blocks.CRAFTING_TABLE), event.getCrafting(), XP_STATION);
    }

    /** A potion taken from a brewing stand. */
    @SubscribeEvent
    public static void onPotionBrewed(PlayerBrewedPotionEvent event) {
        if (!realPlayer(event.getEntity())) return;
        creditStation(event.getEntity(), Set.of(WorkTaskTypes.BREW_POTION),
                BuiltInRegistries.BLOCK.getKey(Blocks.BREWING_STAND), event.getStack(), XP_STATION);
    }

    /** The furnace family is told apart by its menu, since the smelted-item event names no block. */
    public static @Nullable ResourceLocation furnaceStation(@Nullable AbstractContainerMenu menu) {
        if (menu instanceof SmokerMenu) return BuiltInRegistries.BLOCK.getKey(Blocks.SMOKER);
        if (menu instanceof BlastFurnaceMenu) return BuiltInRegistries.BLOCK.getKey(Blocks.BLAST_FURNACE);
        if (menu instanceof FurnaceMenu) return BuiltInRegistries.BLOCK.getKey(Blocks.FURNACE);
        return null;
    }

    /**
     * Credits the first career whose task of one of the given types admits both the station and
     * the output. One station completion belongs to one career, in deterministic data order,
     * the same rule the block-interaction bridge uses.
     */
    public static void creditStation(Player player, Set<ResourceLocation> types, ResourceLocation station,
                                     ItemStack output, int xp) {
        if (!realPlayer(player) || output == null || output.isEmpty()) return;
        ServerPlayer sp = (ServerPlayer) player;
        ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(output.getItem());
        for (ProfessionDef def : ProfessionDefs.all().values()) {
            for (WorkTaskDef task : def.workTasks()) {
                if (!types.contains(task.type())) continue;
                if (!task.allowsBlock(station) || !task.allowsRecipe(null, outputId)) continue;
                var activities = WorkTaskTypes.activities(task.type());
                String activity = activities.isEmpty() ? task.type().toString() : activities.get(0);
                CareerProgression.completeWork(sp, def.id(), xp, sp.serverLevel().getGameTime(),
                        activity, outputId, "item", output.getCount());
                return;
            }
        }
    }

    // ── Shearing ──

    /**
     * The interaction fires before vanilla shears the animal, so the award is confirmed one tick
     * later, once the wool is actually off. Both hands fire the event; one award per animal.
     */
    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !realPlayer(event.getEntity())) return;
        if (!(event.getTarget() instanceof Animal animal) || !(animal instanceof Shearable shearable)) return;
        if (!shearable.readyForShearing()) return;
        UUID id = animal.getUUID();
        if (!PENDING_SHEAR.add(id)) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        level.getServer().tell(new TickTask(level.getServer().getTickCount() + 1, () -> {
            PENDING_SHEAR.remove(id);
            if (!player.isAlive() || player.serverLevel() != level) return;
            if (!animal.isAlive() || shearable.readyForShearing()) return;
            CareerProgression.completeWork(player, Careers.SHEPHERD, XP_SHEAR, level.getGameTime(),
                    "townstead:tended", null, null, XP_SHEAR);
        }));
    }

    // ── Entity jobs ──

    /**
     * An authored entity job (slaughter, for one) names the tool, the target, and its policy. The
     * policy is judged while the animal is still alive, on the hit; the kill then pays the job.
     */
    //? if neoforge {
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Pre event) {
        rememberHit(event.getEntity(), event.getSource());
    }
    //?} else if forge {
    /*@SubscribeEvent
    public static void onDamage(LivingDamageEvent event) {
        rememberHit(event.getEntity(), event.getSource());
    }
    *///?}

    private static void rememberHit(LivingEntity target, DamageSource source) {
        if (!realPlayer(source.getEntity())) return;
        if (!(target.level() instanceof ServerLevel level)) return;
        ServerPlayer player = (ServerPlayer) source.getEntity();
        ItemStack held = player.getMainHandItem();
        for (WorkJobDef job : WorkJobs.forType(WorkJobDef.ENTITY_DELIVERY)) {
            WorkJobDef.EntitySource entitySource = job.source();
            if (entitySource == null) continue;
            if (entitySource.item() != null && !entitySource.matches(held)) continue;
            if (!entitySource.matches(target)) continue;
            prune(level.getGameTime());
            HITS.put(target.getUUID(), new Hit(player.getUUID(), job, level.getGameTime()));
            return;
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Hit hit = HITS.remove(event.getEntity().getUUID());
        if (hit == null) return;
        if (!(event.getEntity().level() instanceof ServerLevel level)) return;
        if (level.getGameTime() - hit.gameTime() > HIT_MEMORY_TICKS) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !player.getUUID().equals(hit.player())) return;
        WorkJobDef job = hit.job();
        int xp = Math.max(1, job.source().xp());
        for (ProfessionDef def : ProfessionDefs.all().values()) {
            boolean ownsJob = def.workTasks().stream().anyMatch(task -> task.type().equals(job.task()));
            if (!ownsJob) continue;
            CareerProgression.completeWork(player, def.id(), xp, level.getGameTime(),
                    job.activityKey(), null, null, xp, Map.of("job", job.id().toString()));
            return;
        }
    }

    private static void prune(long gameTime) {
        if (HITS.size() < 64) return;
        Iterator<Hit> it = HITS.values().iterator();
        while (it.hasNext()) {
            if (gameTime - it.next().gameTime() > HIT_MEMORY_TICKS) it.remove();
        }
    }
}
