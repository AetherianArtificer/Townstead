package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.emote.AiEmoteScheduler;
import com.aetherianartificer.townstead.naming.Naming;
import com.aetherianartificer.townstead.politics.definition.OrganizationKindDefinition;
import com.aetherianartificer.townstead.politics.definition.OrganizationRoleDefinition;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.founding.FoundingProfileApplier;
import com.aetherianartificer.townstead.politics.founding.FoundingProfileDefinition;
import com.aetherianartificer.townstead.politics.founding.FoundingProfiles;
import com.aetherianartificer.townstead.politics.seat.SeatService;
import com.aetherianartificer.townstead.politics.state.AffiliationInstance;
import com.aetherianartificer.townstead.politics.state.MembershipInstance;
import com.aetherianartificer.townstead.politics.state.OrganizationInstance;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.PoliticalStatus;
import com.aetherianartificer.townstead.politics.state.PoliticalAuthority;
import com.aetherianartificer.townstead.politics.state.PolityInstance;
import com.aetherianartificer.townstead.politics.state.SeatInstance;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Recognition, authoritative founding, presentation snapshots and celebration for Charter Bells. */
public final class CharterBellService {
    private static final long PROPOSAL_LIFETIME = 20L * 60L * 10L;
    private static final double USE_DISTANCE_SQUARED = 64.0D;
    private static final ResourceLocation GOVERN_POLITY = id("townstead:govern_polity");
    private static final String DESIGNATE_SEAT = "designate_seat";
    //? if >=1.21 {
    private static final ResourceLocation CLAP = ResourceLocation.fromNamespaceAndPath("townstead", "clap");
    //?} else {
    /*private static final ResourceLocation CLAP = new ResourceLocation("townstead", "clap");
    *///?}

    private CharterBellService() {}

    public static boolean onUse(ServerPlayer player, BlockPos pos, InteractionHand hand,
                                Direction hitFace, double hitHeight) {
        if (player == null || hand != InteractionHand.MAIN_HAND) return false;
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.LECTERN)) {
            if (state.getValue(LecternBlock.HAS_BOOK)
                    && CharterSavedData.get(level.getServer()).binding(level.dimension().location(), pos) == null
                    && CharterSavedData.get(level.getServer()).proposal(level.dimension().location(), pos) == null) {
                return false;
            }
            Assembly assembly = assembly(level, pos);
            CharterSavedData saved = CharterSavedData.get(level.getServer());
            if (assembly == null && saved.binding(level.dimension().location(), pos) == null
                    && saved.proposal(level.dimension().location(), pos) == null) return false;
            send(player, pos, true, "");
            return true;
        }
        if (state.is(CharterBellBlocks.ELIGIBLE)) {
            // The platform interaction event proves this was a direct normal player use. Validation
            // below binds it to the exact prepared bell; automated/projectile rings never enter here.
            if (!(state.getBlock() instanceof BellBlock) || properBellHit(state, hitFace, hitHeight)) {
                ring(player, pos);
            }
        }
        return false;
    }

    public static void handle(CharterActionC2SPayload request, ServerPlayer player) {
        if (!near(player, request.lectern()) || !player.serverLevel().isLoaded(request.lectern())) return;
        if (request.action() == CharterActionC2SPayload.REFRESH) {
            send(player, request.lectern(), false, "");
            return;
        }
        CharterSavedData saved = CharterSavedData.get(player.server);
        ResourceLocation dimension = player.serverLevel().dimension().location();
        if (request.action() == CharterActionC2SPayload.MEMBERSHIP || request.action() == CharterActionC2SPayload.CIVIC) {
            var binding = saved.binding(dimension, request.lectern());
            if (binding == null || !player.serverLevel().getBlockState(request.lectern()).is(Blocks.LECTERN)
                    || !player.serverLevel().mayInteract(player, request.lectern())) return;
            var polity = PoliticalSavedData.get(player.server).polity(binding.polity());
            var civic = CivicProviders.read(player, binding.settlement());
            if (request.revision() != CivicProviders.revision(player, civic)) { send(player, request.lectern(), false, "The records have changed. Please review them again."); return; }
            if (request.action() == CharterActionC2SPayload.CIVIC) {
                boolean executed = CivicProviders.execute(player, binding.settlement(), civic, request.target(), request.operation());
                if (!executed) player.displayClientMessage(Component.translatable("charter.townstead.membership.denied"), false);
                if (!executed || !CivicProviders.opensScreen(civic, request.operation())) send(player, request.lectern(), false, "");
                return;
            }
            if (request.operation().equals(DESIGNATE_SEAT)) {
                if (polity == null || !request.target().equals(polity.id().toString())
                        || !mayLink(player, polity, binding.settlement())) return;
                Component result = seatResult(SeatService.designate(player.serverLevel(), binding, true));
                player.displayClientMessage(result, false);
                send(player, request.lectern(), false, result.getString());
                return;
            }
            if (civic != null && civic.controlsGovernment() && polity != null && polity.governmentOrganization() != null
                    && request.target().equals(polity.governmentOrganization().toString())) return;
            var nativeIntent = new CharterActionC2SPayload(request.lectern(), request.action(), request.name(), request.profile(), request.culture(),
                    request.operation(), request.target(), request.argument(), CharterMemberships.revision(player.server));
            boolean amendment = request.operation().equals("transfer_leadership") || request.operation().equals("dissolve_faction") || request.operation().equals("cancel_amendment");
            Component result;
            if (amendment) {
                if (polity == null || polity.governmentOrganization() == null || !request.target().equals(polity.governmentOrganization().toString())) return;
                var structure = assembly(player.serverLevel(), request.lectern());
                if (!request.operation().equals("cancel_amendment") && (structure == null || !structure.bell().equals(binding.bell()))) return;
                result = FactionLifecycle.prepare(player, binding, request.operation(), polity.name(), request.argument());
            } else result = CharterMemberships.handle(player, polity, nativeIntent);
            player.displayClientMessage(result, false);
            send(player, request.lectern(), false, result.getString());
            return;
        }
        if (request.action() == CharterActionC2SPayload.IDENTITY) {
            var binding = saved.binding(dimension, request.lectern());
            if (binding == null || !player.serverLevel().getBlockState(request.lectern()).is(Blocks.LECTERN)
                    || !player.serverLevel().mayInteract(player, request.lectern())) return;
            var structure = assembly(player.serverLevel(), request.lectern());
            if (structure == null || !structure.bell().equals(binding.bell())) return;
            Component result = CharterIdentityService.handle(player, binding, request);
            player.displayClientMessage(result, false);
            send(player, request.lectern(), false, result.getString()); return;
        }
        if (request.action() == CharterActionC2SPayload.HERALDRY) {
            var binding = saved.binding(dimension, request.lectern());
            if (binding == null || !player.serverLevel().getBlockState(request.lectern()).is(Blocks.LECTERN)
                    || !player.serverLevel().mayInteract(player, request.lectern())) return;
            Component result = com.aetherianartificer.townstead.politics.heraldry.HeraldryService.handle(player, binding, request);
            player.displayClientMessage(result, false);
            send(player, request.lectern(), false, result.getString()); return;
        }
        if (request.action() == CharterActionC2SPayload.CANCEL) {
            if (saved.cancel(dimension, request.lectern(), player.getUUID())) {
                setLecternState(player.serverLevel(), request.lectern(), CharterLecternAccess.NONE);
            }
            send(player, request.lectern(), false, "Proclamation cancelled.");
            return;
        }
        if (!player.mayBuild() || !player.serverLevel().mayInteract(player, request.lectern())) return;
        Assembly assembly = assembly(player.serverLevel(), request.lectern());
        if (assembly == null) {
            send(player, request.lectern(), false, "Restore the lectern, support, and bell before preparing.");
            return;
        }
        if (request.action() == CharterActionC2SPayload.LINK_EXISTING) {
            Existing existing = existing(player.serverLevel(), assembly.bell());
            if (existing == null) {
                send(player, request.lectern(), false, "That settlement record is no longer available.");
                return;
            }
            if (!mayLink(player, existing.polity(), existing.settlement())) {
                send(player, request.lectern(), false, "You cannot bind a Charter Bell for this polity.");
                return;
            }
            if (saved.bindExisting(dimension, request.lectern(), assembly.bell(), existing.settlement(),
                    existing.polity().id(), player.getUUID(), player.serverLevel().getGameTime())) {
                setLecternState(player.serverLevel(), request.lectern(), CharterLecternAccess.FOUNDED);
                SeatService.designate(player.serverLevel(), saved.binding(dimension, request.lectern()), false);
            }
            send(player, request.lectern(), false, "Charter Bell linked.");
            return;
        }
        if (request.action() != CharterActionC2SPayload.PREPARE) return;
        if (saved.binding(dimension, request.lectern()) != null) {
            send(player, request.lectern(), false, "This Charter Bell is already founded.");
            return;
        }
        String name = normalizeName(request.name());
        ResourceLocation profileId = id("townstead:player_faction");
        FoundingProfileDefinition profile = FoundingProfiles.get(profileId);
        ResourceLocation culture = request.culture().isBlank() ? null : ResourceLocation.tryParse(request.culture());
        if (name == null || profile == null || profile.government() == null || !FoundingProfiles.validate(profile).isEmpty()
                || (!request.culture().isBlank() && (culture == null || Cultures.get(culture) == null))) {
            send(player, request.lectern(), false, "One of the charter choices is no longer available.");
            return;
        }
        var factionName = com.aetherianartificer.townstead.culture.FactionNaming.review(
                culture, profile.government() == null ? null : profile.government().organizationKind(),
                request.target(), request.operation(), request.argument().isBlank() ? name : request.argument());
        if (factionName == null) { send(player, request.lectern(), false, Component.translatable("charter.townstead.identity.invalid").getString()); return; }
        long now = player.serverLevel().getGameTime();
        saved.prepare(new CharterSavedData.Proposal(UUID.randomUUID(), player.getUUID(), dimension,
                request.lectern().immutable(), assembly.bell().immutable(), name, profile.id(), culture,
                now, now + PROPOSAL_LIFETIME, factionName));
        setLecternState(player.serverLevel(), request.lectern(), CharterLecternAccess.PREPARED);
        gather(player.serverLevel(), assembly.bell());
        send(player, request.lectern(), false, "The proclamation is prepared. Ring the bell.");
    }

    private static void ring(ServerPlayer player, BlockPos bell) {
        ServerLevel level = player.serverLevel();
        CharterSavedData saved = CharterSavedData.get(level.getServer());
        saved.expire(level.getGameTime());
        var amendment = saved.amendmentAtBell(level.dimension().location(), bell);
        if (amendment != null) {
            if (!amendment.initiator().equals(player.getUUID())) return;
            var binding = saved.binding(amendment.dimension(), amendment.lectern());
            var structure = assembly(level, amendment.lectern());
            if (binding == null || !binding.polity().equals(amendment.polity()) || !binding.bell().equals(bell)
                    || structure == null || !structure.bell().equals(bell) || !near(player, bell) || !near(player, amendment.lectern())
                    || !player.mayBuild() || !level.mayInteract(player, bell) || !level.mayInteract(player, amendment.lectern())) return;
            var result = FactionLifecycle.commit(player, amendment);
            player.displayClientMessage(result, false);
            send(player, amendment.lectern(), false, result.getString());
            return;
        }
        CharterSavedData.Proposal proposal = saved.proposalAtBell(level.dimension().location(), bell);
        if (proposal == null || !proposal.initiator().equals(player.getUUID())) return;
        if (!near(player, bell) || !near(player, proposal.lectern())) return;
        if (!player.mayBuild() || !level.mayInteract(player, proposal.lectern()) || !level.mayInteract(player, bell)) {
            player.displayClientMessage(Component.translatable("charter.townstead.founding_access_lost"), false);
            return;
        }
        if (existing(level, bell) != null) {
            saved.cancel(proposal.dimension(), proposal.lectern(), proposal.initiator());
            setLecternState(level, proposal.lectern(), CharterLecternAccess.NONE);
            player.displayClientMessage(Component.translatable("charter.townstead.founding_location_changed"), false);
            return;
        }
        Assembly assembly = assembly(level, proposal.lectern());
        if (assembly == null || !assembly.bell().equals(bell)) {
            if (saved.cancel(proposal.dimension(), proposal.lectern(), proposal.initiator())) {
                setLecternState(level, proposal.lectern(), CharterLecternAccess.NONE);
            }
            player.displayClientMessage(Component.translatable("charter.townstead.bell_missing"), false);
            return;
        }
        FoundingProfileDefinition profile = FoundingProfiles.get(proposal.profile());
        if (profile == null || profile.government() == null || !profile.id().equals(id("townstead:player_faction"))
                || !FoundingProfiles.validate(profile).isEmpty() || (proposal.culture() != null && Cultures.get(proposal.culture()) == null)) {
            player.displayClientMessage(Component.translatable("charter.townstead.definition_changed"), false);
            return;
        }

        VillageManager manager = VillageManager.get(level);
        Village village = manager.findNearestVillage(bell, Village.MERGE_MARGIN).orElse(null);
        if (village == null) {
            Building.validationResult recognition = manager.processBuilding(bell);
            village = manager.findNearestVillage(bell, Village.MERGE_MARGIN).orElse(null);
            if (village == null) {
                Townstead.LOGGER.warn("Charter Bell at {} could not establish an MCA settlement ({})", bell, recognition);
                player.displayClientMessage(Component.translatable("charter.townstead.village_unrecognized"), false);
                return;
            }
        }

        SettlementRef candidate = new SettlementRef(level.dimension().location(), village.getId());
        if (CivicProviders.ownsGovernment(level.getServer(), candidate)) {
            saved.cancel(proposal.dimension(), proposal.lectern(), proposal.initiator());
            setLecternState(level, proposal.lectern(), CharterLecternAccess.NONE);
            player.displayClientMessage(Component.translatable("charter.townstead.founding_location_changed"), false);
            return;
        }
        FoundingProfileApplier.Result result = FoundingProfileApplier.foundByPlayer(level, village, profile, bell,
                proposal.name(), proposal.culture(), player.getUUID());
        if (!result.applied()) {
            player.displayClientMessage(Component.translatable("charter.townstead.founding_failed", result.reason()), false);
            return;
        }
        var politics = PoliticalSavedData.get(level.getServer());
        var founded = politics.polity(result.polity());
        if (founded != null) {
            politics.putPolity(new PolityInstance(founded.id(), proposal.factionName().display(), founded.color(), founded.emblem(),
                    founded.createdAt(), founded.provenance(), founded.status(), founded.settlements(), founded.governmentOrganization()));
            politics.putFactionName(founded.id(), proposal.factionName());
        }
        SettlementRef settlement = new SettlementRef(level.dimension().location(), village.getId());
        if (!saved.commit(proposal, settlement, result.polity(), level.getGameTime())) return;
        SeatService.designate(level, saved.binding(proposal.dimension(), proposal.lectern()), false);
        setLecternState(level, proposal.lectern(), CharterLecternAccess.FOUNDED);
        celebrate(level, bell, proposal.factionName().display());
        level.getServer().getPlayerList().broadcastSystemMessage(Component.translatable(
                "charter.townstead.faction_announced", player.getDisplayName(), proposal.factionName().display()), false);
    }

    public static void send(ServerPlayer player, BlockPos lectern, boolean open, String message) {
        CharterSnapshotS2CPayload snapshot = snapshot(player, lectern, message);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, snapshot);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, snapshot);
        *///?}
    }

    private static CharterSnapshotS2CPayload snapshot(ServerPlayer player, BlockPos lectern, String message) {
        ServerLevel level = player.serverLevel();
        ResourceLocation dimension = level.dimension().location();
        CharterSavedData saved = CharterSavedData.get(level.getServer());
        saved.expire(level.getGameTime());
        CharterSavedData.Binding binding = saved.binding(dimension, lectern);
        CharterSavedData.Proposal proposal = saved.proposal(dimension, lectern);
        if (binding != null && saved.amendment(binding.polity()) != null && message.isBlank())
            message = Component.translatable("charter.townstead.lifecycle.prepared").getString();
        setLecternState(level, lectern, binding != null ? CharterLecternAccess.FOUNDED
                : proposal != null ? CharterLecternAccess.PREPARED : CharterLecternAccess.NONE);
        Assembly assembly = assembly(level, lectern);
        BlockPos bell = binding != null ? binding.bell() : proposal != null ? proposal.bell()
                : assembly != null ? assembly.bell() : lectern;
        boolean editable = player.mayBuild() && level.mayInteract(player, lectern);
        if (binding == null && proposal == null && assembly == null) {
            return empty(lectern, bell, CharterSnapshotS2CPayload.UNAVAILABLE, false,
                    message.isBlank() ? "The Charter Bell assembly is unavailable." : message);
        }
        if (binding == null) {
            if (proposal == null && assembly != null) {
                Existing existing = existing(level, assembly.bell());
                if (existing != null) {
                    OrganizationInstance government = existing.polity().governmentOrganization() == null ? null
                            : PoliticalSavedData.get(level.getServer()).organization(existing.polity().governmentOrganization());
                    Component governance = governanceName(government);
                    var external = CivicProviders.read(player, existing.settlement());
                    if (external != null && external.controlsGovernment()) governance = external.governance().component();
                    return new CharterSnapshotS2CPayload(lectern, bell, CharterSnapshotS2CPayload.EXISTING,
                            editable && mayLink(player, existing.polity(), existing.settlement()), existing.village().getName(), existing.polity().name(),
                            CharterSnapshotS2CPayload.Text.of(governance),
                            text("charter.townstead.existing_help", "Link this Charter Bell without changing the settlement."),
                            text("charter.townstead.no_founding_culture", "No founding culture"), message,
                            List.of(), List.of(), List.of(), List.of(), List.of(), 0);
                }
            }
            List<CharterSnapshotS2CPayload.Option> profiles = profileOptions();
            if (profiles.isEmpty()) return empty(lectern, bell, CharterSnapshotS2CPayload.UNAVAILABLE,
                    false, "No valid founding governance is loaded.");
            List<CharterSnapshotS2CPayload.Option> cultures = cultureOptions();
            if (proposal == null) {
                return new CharterSnapshotS2CPayload(lectern, bell, CharterSnapshotS2CPayload.UNFOUNDED,
                        editable, "", "", text("charter.townstead.not_founded", "Not founded"),
                        text("charter.townstead.review_help", "Review the charter before preparing it."),
                        text("charter.townstead.no_founding_culture", "No founding culture"), message,
                        profiles, cultures, List.of(), List.of(), List.of(), 0);
            }
            FoundingProfileDefinition profile = FoundingProfiles.get(proposal.profile());
            Component government = profile == null ? Component.literal(proposal.profile().toString()) : profile.displayName();
            Component culture = cultureName(proposal.culture());
            return new CharterSnapshotS2CPayload(lectern, bell,
                    assembly == null ? CharterSnapshotS2CPayload.REPAIR : CharterSnapshotS2CPayload.PREPARED,
                    editable && proposal.initiator().equals(player.getUUID()), proposal.name(), proposal.factionName().display(),
                    CharterSnapshotS2CPayload.Text.of(government),
                    text("charter.townstead.ring_to_found", "Ring the associated bell to found this settlement."),
                    CharterSnapshotS2CPayload.Text.of(culture), message, profiles, cultures,
                    List.of(), List.of(), List.of(), 0);
        }

        PoliticalSavedData politics = PoliticalSavedData.get(level.getServer());
        PolityInstance polity = politics.polity(binding.polity());
        Village village = VillageManager.get(level).getOrEmpty(binding.settlement().villageId()).orElse(null);
        if (polity == null || village == null) {
            return empty(lectern, bell, CharterSnapshotS2CPayload.UNAVAILABLE, false,
                    "The civic record could not be loaded. Try again shortly.");
        }
        boolean intact = assembly != null && assembly.bell().equals(binding.bell());
        SettlementFoundingRecord founding = politics.founding(binding.settlement());
        OrganizationInstance government = polity.governmentOrganization() == null ? null
                : politics.organization(polity.governmentOrganization());
        Component governance = governanceName(government);
        Component authority = authority(government, politics);
        Component tradition = cultureName(founding == null ? null : founding.culture());
        List<CharterSnapshotS2CPayload.Organization> organizations = organizations(player, politics, polity);
        var civic = CivicProviders.read(player, binding.settlement());
        if (civic != null && civic.controlsGovernment()) {
            governance = civic.governance().component();
            authority = civic.description().component();
            organizations = organizations.stream().filter(o -> !o.governing()).toList();
            if (civic.state().equals("active") || !saved.externalGovernment(binding.settlement()).isEmpty()) politics.markExternalGovernment(polity.id());
        }
        List<CharterSnapshotS2CPayload.Tie> ties = ties(player, politics, polity);
        Census census = census(level, village);
        return new CharterSnapshotS2CPayload(lectern, bell,
                intact ? CharterSnapshotS2CPayload.FOUNDED : CharterSnapshotS2CPayload.REPAIR,
                editable, village.getName(), polity.name(), CharterSnapshotS2CPayload.Text.of(governance),
                CharterSnapshotS2CPayload.Text.of(authority), CharterSnapshotS2CPayload.Text.of(tradition),
                message, List.of(), List.of(), organizations, ties, census.groups(), census.total(),
                CharterMemberships.requests(player, politics, polity), CivicProviders.revision(player, civic), censusScopes(level, village, polity, census), civic, com.aetherianartificer.townstead.politics.heraldry.HeraldryService.views(player, binding, village.getName()),
                seatView(player, level, binding, polity, editable), standingView(player, binding),
                legitimacyView(player.server, politics, polity));
    }

    private static List<CharterSnapshotS2CPayload.Option> profileOptions() {
        var profile = FoundingProfiles.get(id("townstead:player_faction"));
        if (profile == null || profile.government() == null || !FoundingProfiles.validate(profile).isEmpty()) return List.of();
        return List.of(new CharterSnapshotS2CPayload.Option(profile.id().toString(),
                CharterSnapshotS2CPayload.Text.of(profile.displayName()),
                CharterSnapshotS2CPayload.Text.of(Component.translatable("charter.townstead.founding_leader_help")),
                CharterSnapshotS2CPayload.Text.of(Component.translatable("charter.townstead.founding_leader_status")), true,
                com.aetherianartificer.townstead.culture.FactionNaming.patterns(profile.government().organizationKind())));
    }

    private static List<CharterSnapshotS2CPayload.Option> cultureOptions() {
        List<CharterSnapshotS2CPayload.Option> out = new ArrayList<>();
        out.add(new CharterSnapshotS2CPayload.Option("", text("charter.townstead.no_founding_culture", "No founding culture"),
                text("charter.townstead.no_culture_description", "Begin without naming one tradition as the settlement's origin."),
                text("charter.townstead.consequences.no_culture",
                        "Records no founding tradition · Residents keep their own cultures"), false));
        Cultures.authoredIds().stream().sorted(Comparator.comparing(ResourceLocation::toString)).forEach(id -> {
            Culture culture = Cultures.get(id);
            if (culture != null) out.add(new CharterSnapshotS2CPayload.Option(id.toString(),
                    CharterSnapshotS2CPayload.Text.of(culture.displayName()),
                    text("charter.townstead.founding_culture_description", "Records a founding tradition without changing any resident."),
                    text("charter.townstead.consequences.founding_culture",
                            "Saved as founding history · Does not assign or lock resident cultures"), false,
                    com.aetherianartificer.townstead.culture.FactionNaming.suggestions(id)));
        });
        return List.copyOf(out);
    }

    private static List<CharterSnapshotS2CPayload.Organization> organizations(
            ServerPlayer player, PoliticalSavedData data, PolityInstance polity) {
        List<CharterSnapshotS2CPayload.Organization> out = new ArrayList<>();
        for (OrganizationInstance value : data.organizations()) {
            if (data.supersededGovernment(value.id())) continue;
            if ((!value.id().equals(polity.governmentOrganization()) && !polity.settlements().contains(value.home())) || !value.status().equals(com.aetherianartificer.townstead.politics.state.PoliticalStatus.Organization.ACTIVE)) continue;
            OrganizationKindDefinition kind = PoliticalDefinitions.snapshot().organizationKind(value.kind());
            Component kindName = kind == null ? Component.literal(value.kind().toString()) : kind.display().name();
            Component description = kind == null || kind.display().description() == null
                    ? Component.empty() : kind.display().description();
            MembershipInstance membership = data.membership(player.getUUID(), value.id());
            String relationship = membership == null ? "visitor" : membership.affiliation().status().id();
            boolean member = membership != null && membership.affiliation().active();
            List<CharterSnapshotS2CPayload.Text> ownRoles = membership == null ? List.of() : membership.roles().stream()
                    .sorted(Comparator.comparing(ResourceLocation::toString)).map(roleId -> {
                        OrganizationRoleDefinition role = PoliticalDefinitions.snapshot().role(roleId);
                        return CharterSnapshotS2CPayload.Text.of(role == null ? Component.literal(roleId.toString()) : role.display().name());
                    }).toList();
            List<CharterSnapshotS2CPayload.Role> roles = new ArrayList<>();
            if (kind != null) for (var binding : kind.roles()) {
                OrganizationRoleDefinition role = PoliticalDefinitions.snapshot().role(binding.role());
                if (role == null || (role.visibility() != OrganizationRoleDefinition.Visibility.PUBLIC
                        && !(member && role.visibility() == OrganizationRoleDefinition.Visibility.MEMBERS))) continue;
                List<CharterSnapshotS2CPayload.Text> holders = new ArrayList<>();
                for (MembershipInstance entry : data.memberships(value.actor())) {
                    if (!entry.affiliation().active() || !entry.roles().contains(binding.role())) continue;
                    var affiliation = entry.affiliation();
                    boolean visible = affiliation.person().equals(player.getUUID())
                            || affiliation.visibility() == com.aetherianartificer.townstead.politics.state.PoliticalStatus.Visibility.PUBLIC
                            || (member && affiliation.visibility() == com.aetherianartificer.townstead.politics.state.PoliticalStatus.Visibility.MEMBERS);
                    if (!visible) continue;
                    holders.add(CharterSnapshotS2CPayload.Text.of(CharterPeople.name(player, affiliation.person())));
                }
                roles.add(new CharterSnapshotS2CPayload.Role(CharterSnapshotS2CPayload.Text.of(role.display().name()), holders));
            }
            var policy = PoliticalDefinitions.snapshot().membershipPolicy(value.membershipPolicy());
            var admission = policy == null ? Component.translatable("charter.townstead.admission_unknown")
                    : admissionDescription(policy);
            var departure = policy == null ? Component.translatable("charter.townstead.departure_unknown")
                    : departureDescription(policy);
            String icon = value.emblem() != null ? value.emblem().toString()
                    : kind != null && kind.display().icon() != null ? kind.display().icon().toString() : "minecraft:paper";
            out.add(new CharterSnapshotS2CPayload.Organization(value.id().toString(),
                    CharterSnapshotS2CPayload.Text.of(Component.literal(value.name())),
                    CharterSnapshotS2CPayload.Text.of(kindName), CharterSnapshotS2CPayload.Text.of(description), relationship,
                    value.id().equals(polity.governmentOrganization()), icon, value.color(),
                    CharterSnapshotS2CPayload.Text.of(admission), CharterSnapshotS2CPayload.Text.of(departure), ownRoles, roles, CharterMemberships.actions(player, data, value)));
        }
        out.sort(Comparator.comparing(value -> value.name().fallback()));
        return List.copyOf(out);
    }

    private static List<CharterSnapshotS2CPayload.Tie> ties(ServerPlayer player, PoliticalSavedData data, PolityInstance scope) {
        List<CharterSnapshotS2CPayload.Tie> out = new ArrayList<>();
        for (AffiliationInstance affiliation : data.affiliations(player.getUUID())) {
            String actor = affiliation.actor().id().toString();
            if (affiliation.actor().kind() == com.aetherianartificer.townstead.politics.state.PoliticalActorRef.Kind.ORGANIZATION) {
                if (data.supersededGovernment(affiliation.actor().id())) continue;
                OrganizationInstance organization = data.organization(affiliation.actor().id());
                if (!CharterMemberships.inScope(organization, scope)) continue;
                if (organization != null) actor = organization.name();
            } else {
                if (!affiliation.actor().id().equals(scope.id())) continue;
                PolityInstance polity = data.polity(affiliation.actor().id());
                if (polity != null) actor = polity.name();
            }
            MembershipInstance membership = data.membership(affiliation.id());
            String roles = membership == null ? "" : membership.roles().stream().map(id -> {
                OrganizationRoleDefinition role = PoliticalDefinitions.snapshot().role(id);
                return role == null ? id.getPath() : role.display().name().getString();
            }).sorted().reduce((a, b) -> a + ", " + b).orElse("");
            out.add(new CharterSnapshotS2CPayload.Tie(actor, title(affiliation.kind().getPath()), roles,
                    title(affiliation.status().id())));
        }
        return List.copyOf(out);
    }

    private static Component governanceName(@Nullable OrganizationInstance government) {
        if (government == null) return Component.translatable("charter.townstead.no_formal_government");
        var kind = PoliticalDefinitions.snapshot().organizationKind(government.kind());
        return kind == null ? Component.translatable("charter.townstead.governance_unavailable") : kind.display().name();
    }

    private static Component admissionDescription(com.aetherianartificer.townstead.politics.definition.MembershipPolicyDefinition policy) {
        Component procedure = procedureName(policy.admission().procedure());
        var decision = policy.admission().decision();
        if (decision == null) return procedure;
        if (!decision.procedure().equals(id("townstead:role_approval")))
            return Component.translatable("charter.townstead.admission_decision", procedure, procedureName(decision.procedure()));
        var role = decision.role() == null ? null : PoliticalDefinitions.snapshot().role(decision.role());
        if (role != null && role.visibility() == OrganizationRoleDefinition.Visibility.PUBLIC)
            return Component.translatable("charter.townstead.admission_role." + (decision.approvals() == 1 ? "one" : "many"),
                    procedure, role.display().name(), decision.approvals());
        return Component.translatable("charter.townstead.admission_approvals." + (decision.approvals() == 1 ? "one" : "many"), procedure, decision.approvals());
    }

    private static Component departureDescription(com.aetherianartificer.townstead.politics.definition.MembershipPolicyDefinition policy) {
        int days = policy.departure().noticeDays();
        Component procedure = procedureName(policy.departure().procedure());
        if (days <= 0) return procedure;
        if (policy.departure().procedure().equals(id("townstead:notice")))
            return Component.translatable("charter.townstead.departure_notice." + (days == 1 ? "one" : "many"), days);
        return Component.translatable("charter.townstead.departure_with_notice." + (days == 1 ? "one" : "many"), procedure, days);
    }

    private static Component procedureName(ResourceLocation id) {
        return Component.translatableWithFallback("politics." + id.getNamespace() + ".procedure." + id.getPath().replace('/', '.'), title(id.getPath()));
    }

    private static Component authority(@Nullable OrganizationInstance government, PoliticalSavedData data) {
        return government == null ? Component.translatable("charter.townstead.association_authority")
                : Component.literal(government.name());
    }

    private static Census census(ServerLevel level, Village village) {
        Map<UUID, Entity> people = new LinkedHashMap<>();
        village.getResidentsUUIDs().toList().stream().filter(java.util.Objects::nonNull)
                .forEach(person -> people.put(person, level.getEntity(person)));
        return census(people);
    }

    private static List<CharterSnapshotS2CPayload.CensusScope> censusScopes(ServerLevel level, Village local, PolityInstance polity, Census localCensus) {
        List<CharterSnapshotS2CPayload.CensusScope> out = new ArrayList<>();
        out.add(new CharterSnapshotS2CPayload.CensusScope("settlement",
                CharterSnapshotS2CPayload.Text.of(Component.translatable("charter.townstead.census.local", local.getName())),
                localCensus.groups(), localCensus.total(), true));
        Map<UUID, Entity> people = new LinkedHashMap<>();
        boolean available = true;
        for (var settlement : polity.settlements()) {
            ServerLevel source = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, settlement.dimension()));
            Village village = source == null ? null : VillageManager.get(source).getOrEmpty(settlement.villageId()).orElse(null);
            if (village == null) { available = false; continue; }
            for (UUID person : village.getResidentsUUIDs().toList()) if (person != null) {
                Entity loaded = source.getEntity(person);
                if (!people.containsKey(person) || loaded != null) people.put(person, loaded);
            }
        }
        Census census = census(people);
        out.add(new CharterSnapshotS2CPayload.CensusScope("polity",
                CharterSnapshotS2CPayload.Text.of(Component.translatable("charter.townstead.census.polity", polity.name())),
                census.groups(), census.total(), available));
        return List.copyOf(out);
    }

    private static Census census(Map<UUID, Entity> people) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Entity entity : people.values()) {
            String culture = entity instanceof VillagerEntityMCA villager ? Naming.cultureOf(villager) : "unavailable";
            if (!culture.equals("unavailable") && (culture.isBlank() || Cultures.get(culture) == null)) culture = "";
            counts.merge(culture, 1, Integer::sum);
        }
        List<CharterSnapshotS2CPayload.CensusGroup> groups = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            ResourceLocation id = entry.getKey().isBlank() || entry.getKey().equals("unavailable") ? null : ResourceLocation.tryParse(entry.getKey());
            Culture culture = Cultures.get(id);
            Component name = entry.getKey().equals("unavailable") ? Component.translatable("charter.townstead.census.unavailable_culture")
                    : culture == null ? Component.translatable("charter.townstead.unrecorded_culture") : culture.displayName();
            groups.add(new CharterSnapshotS2CPayload.CensusGroup(entry.getKey(),
                    CharterSnapshotS2CPayload.Text.of(name), entry.getValue(), cultureColor(entry.getKey())));
        }
        groups.sort(Comparator.comparingInt(CharterSnapshotS2CPayload.CensusGroup::count).reversed());
        return new Census(List.copyOf(groups), people.size());
    }

    private static void gather(ServerLevel level, BlockPos bell) {
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class,
                new AABB(bell).inflate(24.0D))) {
            if (!villager.isAlive()) continue;
            villager.getNavigation().moveTo(bell.getX() + 0.5D, bell.getY(), bell.getZ() + 0.5D, 0.75D);
        }
    }

    private static void celebrate(ServerLevel level, BlockPos bell, String name) {
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(bell.getX() + 0.5D, bell.getY() + 0.5D, bell.getZ() + 0.5D) > 4096.0D) continue;
            CharterCeremonyS2CPayload cue = new CharterCeremonyS2CPayload(bell, name);
            //? if neoforge {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, cue);
            //?} else {
            /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(viewer, cue);
            *///?}
        }
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class,
                new AABB(bell).inflate(20.0D))) {
            villager.getLookControl().setLookAt(bell.getX() + 0.5D, bell.getY() + 0.5D, bell.getZ() + 0.5D);
            AiEmoteScheduler.playEmote(villager, CLAP);
        }
    }

    private static @Nullable Assembly assembly(ServerLevel level, BlockPos lectern) {
        BlockState lecternState = level.getBlockState(lectern);
        if (!lecternState.is(Blocks.LECTERN)) return null;
        Direction facing = lecternState.getValue(LecternBlock.FACING);
        BlockPos support = lectern.relative(facing.getOpposite());
        BlockState supportState = level.getBlockState(support);
        BlockPos bell = support.above();
        if (supportState.isAir() || !supportState.isFaceSturdy(level, support, Direction.UP)
                || !level.getBlockState(bell).is(CharterBellBlocks.ELIGIBLE)) return null;
        BlockState bellState = level.getBlockState(bell);
        if (bellState.hasProperty(BellBlock.ATTACHMENT)
                && bellState.getValue(BellBlock.ATTACHMENT) != BellAttachType.FLOOR) return null;
        return new Assembly(lectern.immutable(), support.immutable(), bell.immutable());
    }

    /** Mirrors vanilla BellBlock's proper-hit gate so a failed click can never found a settlement. */
    private static boolean properBellHit(BlockState state, Direction hitFace, double hitHeight) {
        if (hitFace == null || hitFace.getAxis() == Direction.Axis.Y || hitHeight > 0.8124D) return false;
        Direction facing = state.getValue(BellBlock.FACING);
        BellAttachType attachment = state.getValue(BellBlock.ATTACHMENT);
        return switch (attachment) {
            case FLOOR -> facing.getAxis() == hitFace.getAxis();
            case SINGLE_WALL, DOUBLE_WALL -> facing.getAxis() != hitFace.getAxis();
            case CEILING -> true;
        };
    }

    private static boolean near(ServerPlayer player, BlockPos pos) {
        return player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= USE_DISTANCE_SQUARED;
    }

    private static @Nullable Existing existing(ServerLevel level, BlockPos bell) {
        Village village = VillageManager.get(level).findNearestVillage(bell, Village.MERGE_MARGIN).orElse(null);
        if (village == null) return null;
        SettlementRef settlement = new SettlementRef(level.dimension().location(), village.getId());
        PoliticalSavedData politics = PoliticalSavedData.get(level.getServer());
        PolityInstance polity = politics.polity(settlement);
        return polity == null || polity.status() == PoliticalStatus.Polity.DISSOLVED ? null : new Existing(village, settlement, polity);
    }

    /** "Accepted (68). Heading toward it: Prosperity +10, Village spirit +4." Null without a governance block. */
    private static @Nullable CharterSnapshotS2CPayload.Text legitimacyView(net.minecraft.server.MinecraftServer server,
            PoliticalSavedData politics, PolityInstance polity) {
        if (polity.governmentOrganization() == null) return null;
        OrganizationInstance government = politics.organization(polity.governmentOrganization());
        if (government == null || com.aetherianartificer.townstead.politics.legitimacy.LegitimacyService.governance(government) == null) return null;
        double value = com.aetherianartificer.townstead.politics.legitimacy.LegitimacyService.current(politics, government);
        var target = com.aetherianartificer.townstead.politics.legitimacy.LegitimacyService.target(server, polity, government);
        net.minecraft.network.chat.MutableComponent reasons = Component.empty();
        for (var contribution : target.contributions()) {
            long amount = Math.round(contribution.amount());
            if (amount == 0) continue;
            if (!reasons.getSiblings().isEmpty()) reasons.append(", ");
            reasons.append(Component.translatable("townstead.legitimacy.source." + contribution.label().getNamespace()
                    + "." + contribution.label().getPath())).append(" " + (amount > 0 ? "+" : "") + amount);
        }
        Component band = Component.translatable("townstead.legitimacy.band." + com.aetherianartificer.townstead.politics.legitimacy.LegitimacyService.band(value));
        Component text = reasons.getSiblings().isEmpty()
                ? Component.translatable("charter.townstead.legitimacy.detail", band, Math.round(value))
                : Component.translatable("charter.townstead.legitimacy.detail_reasons", band, Math.round(value), reasons);
        return CharterSnapshotS2CPayload.Text.of(text);
    }

    /** "34: hearts 20, deeds 9, reputation 5" for the viewing player in this Charter's settlement. */
    private static CharterSnapshotS2CPayload.Text standingView(ServerPlayer player, CharterSavedData.Binding binding) {
        var standing = com.aetherianartificer.townstead.politics.standing.StandingService.of(
                player.server, player.getUUID(), binding.settlement());
        Component text = com.aetherianartificer.townstead.compat.otectus.OtectusStanding.available()
                ? Component.translatable("charter.townstead.standing.detail_reputation", standing.total(),
                        standing.hearts(), standing.deeds(), standing.reputation())
                : Component.translatable("charter.townstead.standing.detail", standing.total(),
                        standing.hearts(), standing.deeds());
        return CharterSnapshotS2CPayload.Text.of(text);
    }

    private static CharterSnapshotS2CPayload.Seat seatView(ServerPlayer player, ServerLevel level,
            CharterSavedData.Binding binding, PolityInstance polity, boolean editable) {
        SeatInstance seat = PoliticalSavedData.get(level.getServer()).seat(polity.actor());
        boolean here = seat != null && seat.settlement().dimension().equals(binding.dimension())
                && seat.lectern().equals(binding.lectern());
        Component body = seat == null ? Component.translatable("charter.townstead.seat.none")
                : here ? Component.translatable("charter.townstead.seat.here", seatBuildingName(level, seat))
                : Component.translatable("charter.townstead.seat.elsewhere", seatBuildingName(level, seat), settlementName(level, seat));
        if (seat != null) body = Component.empty().append(body).append(" ").append(seatDetail(level, seat));
        List<CharterSnapshotS2CPayload.Action> actions = List.of();
        if (!here && editable && mayLink(player, polity, binding.settlement())) {
            if (SeatService.host(level, binding) == null) {
                body = Component.empty().append(body).append(" ").append(Component.translatable("charter.townstead.seat.no_building"));
            } else {
                actions = List.of(new CharterSnapshotS2CPayload.Action(DESIGNATE_SEAT,
                        CharterSnapshotS2CPayload.Text.of(Component.translatable("charter.townstead.seat.designate")),
                        CharterSnapshotS2CPayload.Text.of(Component.translatable(seat == null
                                ? "charter.townstead.seat.designate.description" : "charter.townstead.seat.move.description")),
                        false));
            }
        }
        return new CharterSnapshotS2CPayload.Seat(polity.id().toString(),
                CharterSnapshotS2CPayload.Text.of(Component.translatable("charter.townstead.seat.title")),
                CharterSnapshotS2CPayload.Text.of(body), actions);
    }

    /** "Tier 2. It provides Records, Audience, and Assembly." or what is damaged and how to repair it. */
    private static Component seatDetail(ServerLevel level, SeatInstance seat) {
        if (seat.damaged()) return Component.translatable("charter.townstead.seat.damaged." + seat.damage());
        var spec = com.aetherianartificer.townstead.politics.seat.SeatBuildings.forType(seatBuildingType(level, seat));
        net.minecraft.network.chat.MutableComponent functions = Component.empty();
        for (int i = 0; i < spec.functions().size(); i++) {
            ResourceLocation function = spec.functions().get(i);
            if (i > 0) functions.append(", ");
            functions.append(Component.translatable("townstead.seat.function." + function.getNamespace() + "." + function.getPath()));
        }
        return Component.translatable("charter.townstead.seat.detail", spec.tier(), functions);
    }

    private static @Nullable String seatBuildingType(ServerLevel level, SeatInstance seat) {
        ServerLevel seatLevel = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, seat.settlement().dimension()));
        return seatLevel == null ? null : VillageManager.get(seatLevel).getOrEmpty(seat.settlement().villageId())
                .map(v -> com.aetherianartificer.townstead.compat.mca.McaBuildings.byId(v, seat.buildingId()))
                .map(Building::getType).orElse(null);
    }

    /** The host building's type name, or "Meeting Place" when that type has no Seat data. */
    private static Component seatBuildingName(ServerLevel level, SeatInstance seat) {
        String type = seatBuildingType(level, seat);
        if (!com.aetherianartificer.townstead.politics.seat.SeatBuildings.isSeatBuilding(type)) {
            return Component.translatable("charter.townstead.seat.meeting_place");
        }
        return Component.translatable("buildingType." + type);
    }

    private static String settlementName(ServerLevel level, SeatInstance seat) {
        ServerLevel seatLevel = level.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, seat.settlement().dimension()));
        if (seatLevel == null) return "";
        return VillageManager.get(seatLevel).getOrEmpty(seat.settlement().villageId()).map(Village::getName).orElse("");
    }

    private static Component seatResult(SeatService.Result result) {
        return Component.translatable("charter.townstead.seat.result." + result.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean mayLink(ServerPlayer player, PolityInstance polity, SettlementRef settlement) {
        var civic = CivicProviders.read(player, settlement);
        if (civic != null && civic.controlsGovernment()) return civic.mayManage() || player.hasPermissions(2);
        if (player.hasPermissions(2) || polity.governmentOrganization() == null) return true;
        return PoliticalAuthority.mayAct(PoliticalSavedData.get(player.server), player.getUUID(),
                polity.actor(), GOVERN_POLITY).allowed();
    }

    static void setLecternState(ServerLevel level, BlockPos pos, int state) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof CharterLecternAccess access)) return;
        access.townstead$setCharterState(state);
        blockEntity.setChanged();
        level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
    }

    private static @Nullable String normalizeName(String raw) {
        if (raw == null) return null;
        String value = raw.trim().replaceAll("\\s+", " ");
        if (value.length() < 2 || value.length() > 48) return null;
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return null;
        return value;
    }

    private static Component cultureName(@Nullable ResourceLocation id) {
        Culture culture = Cultures.get(id);
        return culture == null ? Component.translatable("charter.townstead.no_founding_culture") : culture.displayName();
    }

    private static CharterSnapshotS2CPayload empty(BlockPos lectern, BlockPos bell, int state,
                                                    boolean editable, String message) {
        return new CharterSnapshotS2CPayload(lectern, bell, state, editable, "", "",
                text("charter.townstead.unavailable", "Unavailable"), text("", ""), text("", ""), message,
                List.of(), List.of(), List.of(), List.of(), List.of(), 0);
    }

    private static CharterSnapshotS2CPayload.Text text(String key, String fallback) {
        return new CharterSnapshotS2CPayload.Text(key, fallback);
    }

    private static String title(String value) {
        if (value == null || value.isBlank()) return "";
        String spaced = value.replace('_', ' ');
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    private static int cultureColor(String id) {
        if (id == null || id.isBlank() || id.equals("unavailable")) return 0xFF918C82;
        int[] colors = {0xFF97623F, 0xFF557F72, 0xFFB08A3E, 0xFF697D9C, 0xFF966480, 0xFF71854B, 0xFFB36650};
        return colors[Math.floorMod(id.hashCode(), colors.length)];
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalStateException(value);
        return id;
    }

    private record Assembly(BlockPos lectern, BlockPos support, BlockPos bell) {}
    private record Existing(Village village, SettlementRef settlement, PolityInstance polity) {}
    private record Census(List<CharterSnapshotS2CPayload.CensusGroup> groups, int total) {}
}
