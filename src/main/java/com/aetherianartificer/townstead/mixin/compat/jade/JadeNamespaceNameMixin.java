package com.aetherianartificer.townstead.mixin.compat.jade;

import com.aetherianartificer.townstead.client.NamespaceNames;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets Jade consume Townstead's general pack name instead of exposing a raw namespace. */
@Pseudo
@Mixin(targets = "snownee.jade.util.ModIdentification", remap = false)
public final class JadeNamespaceNameMixin {

    @Inject(
            method = "getModName(Lnet/minecraft/resources/ResourceLocation;)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private static void townstead$useAuthoredPackName(ResourceLocation id,
            CallbackInfoReturnable<String> cir) {
        NamespaceNames.authored(id.getNamespace()).ifPresent(cir::setReturnValue);
    }
}
