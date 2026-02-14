package com.aetherianartificer.townstead;

import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.hunger.HungerSyncPayload;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
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
        LOGGER.info("Townstead loaded");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(MOD_ID).versioned("1");
        registrar.playToClient(
                HungerSyncPayload.TYPE,
                HungerSyncPayload.STREAM_CODEC,
                this::handleHungerSync
        );
    }

    private void handleHungerSync(HungerSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> HungerClientStore.set(payload.entityId(), payload.hunger()));
    }

    private void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        HungerClientStore.clear();
    }
}
