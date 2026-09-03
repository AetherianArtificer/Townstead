package com.aetherianartificer.townstead.hospitality.service;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Extension point for service sessions owned by Townstead or another mod. Implementations must be
 * idempotent and must re-check native request validity during fulfillment.
 */
public interface HospitalityServiceProvider {
    ResourceLocation id();

    List<ServiceSite> discover(ServerLevel level, BlockPos center, int radius);

    List<ServiceRequest> requests(ServerLevel level, ServiceSite site);

    /** Whether this concrete carried stack can fulfill the semantic product request. */
    default boolean accepts(ServiceRequest request, ItemStack offered) {
        return request != null && request.product().matches(offered);
    }

    ServiceFulfillment fulfill(ServerLevel level, ServiceClaim claim, ItemStack offered,
                               LivingEntity supplier, @Nullable BlockPos paymentSink);

    /** Native workstations that can make this concrete stack acceptable for the request. */
    default List<ServicePreparation> preparations(ServerLevel level, ServiceSite site,
                                                   ServiceRequest request, ItemStack offered,
                                                   LivingEntity worker) {
        return List.of();
    }

    /** Provider commits the offered stack and returns the newly prepared, exact serving. */
    default ServicePreparationResult prepare(ServerLevel level, ServicePreparation preparation,
                                             ServiceRequest request, ItemStack offered,
                                             LivingEntity worker) {
        return ServicePreparationResult.rejected(ServicePreparationResult.Status.UNSUPPORTED,
                "provider does not implement preparation");
    }

    default List<ServiceFollowup> followups(ServerLevel level, ServiceSite site) { return List.of(); }

    default ServiceFollowupCompletion completeFollowup(ServerLevel level, ServiceFollowup followup,
                                                        LivingEntity worker) {
        return ServiceFollowupCompletion.rejected(ServiceFollowupCompletion.Status.UNSUPPORTED,
                "provider does not implement follow-up work");
    }
}
