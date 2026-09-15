package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.RegistriesApi;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarProfileRegistry;
import com.aetherianartificer.townstead.expression.ExpressionCues;
import com.aetherianartificer.townstead.hangout.HangoutData;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.reaction.Reaction;
import com.aetherianartificer.townstead.reaction.ReactionRegistry;
import com.aetherianartificer.townstead.root.Root;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.gene.Gene;
import com.aetherianartificer.townstead.root.gene.GeneRegistry;
import com.aetherianartificer.townstead.root.personality.PersonalityDef;
import com.aetherianartificer.townstead.root.personality.PersonalityRegistry;
import com.aetherianartificer.townstead.spirit.SpiritRegistry;
import com.aetherianartificer.townstead.storage.StorageRoleDef;
import com.aetherianartificer.townstead.storage.StorageRoles;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

final class RegistriesImpl implements RegistriesApi {

    @Override
    public List<ResourceLocation> rootIds() {
        return map(RootRegistry.all(), Root::id, "registries.rootIds");
    }

    @Override
    public List<ResourceLocation> geneIds() {
        return map(GeneRegistry.all(), Gene::id, "registries.geneIds");
    }

    @Override
    public List<String> personalityIds() {
        return map(PersonalityRegistry.all(), d -> d.id().toString(), "registries.personalityIds");
    }

    @Override
    public List<ResourceLocation> calendarProfileIds() {
        return map(CalendarProfileRegistry.all(), CalendarProfile::id, "registries.calendarProfileIds");
    }

    @Override
    public List<String> professionIds() {
        return map(ProfessionDefs.all().keySet(), ResourceLocation::toString, "registries.professionIds");
    }

    @Override
    public List<ResourceLocation> skillIds() {
        return new ArrayList<>(SkillDefs.all().keySet());
    }

    @Override
    public List<String> spiritIds() {
        return map(SpiritRegistry.ordered(), SpiritRegistry.Spirit::id, "registries.spiritIds");
    }

    @Override
    public List<ResourceLocation> reactionIds() {
        return map(ReactionRegistry.all(), Reaction::id, "registries.reactionIds");
    }

    @Override
    public List<ResourceLocation> expressionCueIds() {
        return new ArrayList<>(ExpressionCues.all().keySet());
    }

    @Override
    public List<ResourceLocation> storageRoleIds() {
        return map(StorageRoles.all(), StorageRoleDef::id, "registries.storageRoleIds");
    }

    @Override
    public List<ResourceLocation> hangoutVenueIds() {
        return new ArrayList<>(HangoutData.venues().keySet());
    }

    @Override
    public List<String> needIds() {
        return List.copyOf(NeedScales.IDS);
    }

    private static <S, T> List<T> map(Iterable<S> source, Function<S, T> convert, String where) {
        List<T> out = new ArrayList<>();
        try {
            for (S item : source) out.add(convert.apply(item));
        } catch (Throwable t) {
            ApiSupport.swallow(where, t);
        }
        return out;
    }
}
