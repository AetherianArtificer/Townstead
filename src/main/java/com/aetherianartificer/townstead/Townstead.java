package com.aetherianartificer.townstead;

import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.HungerSetPayload;
import com.aetherianartificer.townstead.hunger.HungerSyncPayload;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

@Mod(Townstead.MOD_ID)
public class Townstead {
    public static final String MOD_ID = "townstead";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final Supplier<AttachmentType<CompoundTag>> HUNGER_DATA = ATTACHMENTS.register(
            "hunger_data",
            () -> AttachmentType.builder(() -> new CompoundTag())
                    .serialize(net.minecraft.nbt.CompoundTag.CODEC)
                    .build()
    );

    public Townstead(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.addListener(this::onClientDisconnect);
        NeoForge.EVENT_BUS.addListener(this::onStartTracking);
        LOGGER.info("Townstead loaded");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MOD_ID).versioned("1");
        registrar.playToClient(
                HungerSyncPayload.TYPE,
                HungerSyncPayload.STREAM_CODEC,
                this::handleHungerSync
        );
        registrar.playToServer(
                HungerSetPayload.TYPE,
                HungerSetPayload.STREAM_CODEC,
                this::handleHungerSet
        );
    }

    private void handleHungerSync(HungerSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HungerClientStore.set(payload.entityId(), payload.hunger()));
    }

    private void handleHungerSet(HungerSetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            Entity entity = sp.serverLevel().getEntity(payload.entityId());
            LOGGER.debug("HungerSet packet: entityId={}, target={}, found={}", payload.entityId(), payload.hunger(), entity);
            if (entity instanceof VillagerEntityMCA villager) {
                CompoundTag hunger = villager.getData(HUNGER_DATA);
                int oldHunger = HungerData.getHunger(hunger);
                int newHunger = payload.hunger();
                HungerData.setHunger(hunger, newHunger);
                // Reset saturation when increasing (for clean testing); leave it when decreasing
                if (newHunger > oldHunger) {
                    HungerData.setSaturation(hunger, Math.min(newHunger, HungerData.MAX_SATURATION));
                }
                HungerData.setExhaustion(hunger, 0f);
                villager.setData(HUNGER_DATA, hunger);
                PacketDistributor.sendToPlayer(sp, new HungerSyncPayload(villager.getId(), newHunger));
                PacketDistributor.sendToPlayersTrackingEntity(villager, new HungerSyncPayload(villager.getId(), newHunger));
                LOGGER.debug("Hunger set: {} -> {}", oldHunger, newHunger);
            }
        });
    }

    private void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!(event.getTarget() instanceof VillagerEntityMCA villager)) return;

        CompoundTag hunger = villager.getData(HUNGER_DATA);
        int currentHunger = HungerData.getHunger(hunger);
        PacketDistributor.sendToPlayer(sp, new HungerSyncPayload(villager.getId(), currentHunger));
    }

    private void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        HungerClientStore.clear();
    }
}
