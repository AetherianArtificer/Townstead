package com.aetherianartificer.townstead.api.v1;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Enumeration of Townstead's data registries, so a consumer can offer choices or validate ids
 * without guessing. Lookups by id live on the area facades.
 */
public interface RegistriesApi {

    List<ResourceLocation> rootIds();

    List<ResourceLocation> geneIds();

    List<String> personalityIds();

    List<ResourceLocation> calendarProfileIds();

    List<String> professionIds();

    List<ResourceLocation> skillIds();

    List<String> spiritIds();

    List<ResourceLocation> reactionIds();

    List<ResourceLocation> expressionCueIds();

    List<ResourceLocation> storageRoleIds();

    List<ResourceLocation> hangoutVenueIds();

    List<String> needIds();
}
