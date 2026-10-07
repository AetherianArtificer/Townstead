package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDateFormatter;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.emote.AiEmoteScheduler;
import com.aetherianartificer.townstead.naming.Naming;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.GovernanceDefinition;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.founding.FoundingProfileApplier;
import com.aetherianartificer.townstead.politics.founding.FoundingProfileDefinition;
import com.aetherianartificer.townstead.politics.founding.FoundingProfiles;
import com.aetherianartificer.townstead.politics.heraldry.HeraldryService;
import com.aetherianartificer.townstead.politics.legitimacy.LegitimacyService;
import com.aetherianartificer.townstead.politics.seat.SeatBuildings;
import com.aetherianartificer.townstead.politics.seat.SeatService;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.PoliticalAuthority;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SeatInstance;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
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

/** Recognition, founding, the Charter book's snapshot, drafts at the lectern, and proclamation at the bell. */
public final class CharterBellService {
    private static final long PROPOSAL_LIFETIME = 20L * 60L * 10L;
    private static final double USE_DISTANCE_SQUARED = 64.0D;
    private static final int ROSTER_LIMIT = 128;
    private static final ResourceLocation PLAYER_FACTION = id("townstead:player_faction");
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
            CharterSavedData saved = CharterSavedData.get(level.getServer());
            boolean known = saved.binding(level.dimension().location(), pos) != null
                    || saved.proposal(level.dimension().location(), pos) != null;
            if (state.getValue(LecternBlock.HAS_BOOK) && !known) return false;
            if (assembly(level, pos) == null && !known) return false;
            if (leaveAccord(player, saved.binding(level.dimension().location(), pos))) return true;
            send(player, pos, true, "");
            return true;
        }
        if (state.is(CharterBellBlocks.ELIGIBLE)) {
            // The platform interaction event proves this was a direct normal player use. Validation
            // below binds it to the exact prepared bell; automated/projectile rings never enter here.
            if (!(state.getBlock() instanceof BellBlock) || properBellHit(state, hitFace, hitHeight)) ring(player, pos);
        }
        return false;
    }

    /** An envoy holding an accord letter leaves it at the Charter it is addressed to. */
    private static boolean leaveAccord(ServerPlayer player, @Nullable CharterSavedData.Binding binding) {
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (binding == null || !(held.getItem() instanceof com.aetherianartificer.townstead.item.AccordLetterItem)) return false;
        Faction faction = PoliticalSavedData.get(player.server).faction(binding.faction());
        if (faction == null) return false;
        Component message = CharterAccords.leave(player, faction, held);
        if (!message.getString().isEmpty()) player.displayClientMessage(message, false);
        return true;
    }

    /**
     * Whether using this lectern opens the Charter. It reads only what the client also has, so the
     * client cancels its own use of the held item or block with it and the lectern opens cleanly.
     */
    public static boolean claimsUse(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.LECTERN)) return false;
        if (level.getBlockEntity(pos) instanceof CharterLecternAccess access
                && access.townstead$charterState() != CharterLecternAccess.NONE) return true;
        return !state.getValue(LecternBlock.HAS_BOOK) && assembly(level, pos) != null;
    }

    public static void handle(CharterActionC2SPayload request, ServerPlayer player) {
        if (!near(player, request.lectern()) || !player.serverLevel().isLoaded(request.lectern())) return;
        if (request.action() == CharterActionC2SPayload.REFRESH) {
            send(player, request.lectern(), false, "");
            return;
        }
        CharterSavedData saved = CharterSavedData.get(player.server);
        ResourceLocation dimension = player.serverLevel().dimension().location();
        int action = request.action();
        if (action == CharterActionC2SPayload.WEAR_LIVERY) {
            // A personal choice: any member may wear the faction's livery or put it away.
            var binding = saved.binding(dimension, request.lectern());
            Faction faction = binding == null ? null : PoliticalSavedData.get(player.server).faction(binding.faction());
            if (faction == null || FactionBonds.membership(PoliticalSavedData.get(player.server), player.getUUID(), faction.id(),
                    membershipBond(faction)) == null) return;
            boolean wear = request.operation().equals("on");
            com.aetherianartificer.townstead.livery.LiverySavedData.get(player.server).setWears(player.getUUID(), wear);
            com.aetherianartificer.townstead.livery.LiverySync.refresh();
            send(player, request.lectern(), false, Component.translatable(wear
                    ? "charter.townstead.livery.worn" : "charter.townstead.livery.put_away").getString());
            return;
        }
        if (action == CharterActionC2SPayload.MEMBERSHIP || action == CharterActionC2SPayload.CIVIC
                || action == CharterActionC2SPayload.DRAFT || action == CharterActionC2SPayload.HERALDRY) {
            var binding = saved.binding(dimension, request.lectern());
            if (binding == null || !player.serverLevel().getBlockState(request.lectern()).is(Blocks.LECTERN)
                    || !player.serverLevel().mayInteract(player, request.lectern())) return;
            Faction faction = PoliticalSavedData.get(player.server).faction(binding.faction());
            if (faction == null) return;
            var civic = CivicProviders.read(player, binding.settlement());
            Component result;
            if (action == CharterActionC2SPayload.DRAFT) {
                Assembly structure = assembly(player.serverLevel(), request.lectern());
                if (structure == null || !structure.bell().equals(binding.bell())) {
                    result = CharterDrafts.message("repair");
                } else {
                    result = CharterDrafts.handle(player, binding, faction, request);
                }
            } else if (action == CharterActionC2SPayload.HERALDRY) {
                result = HeraldryService.handle(player, binding, request);
            } else {
                if (request.revision() != CivicProviders.revision(player, civic)) {
                    send(player, request.lectern(), false, Component.translatable("charter.townstead.membership.stale").getString());
                    return;
                }
                if (action == CharterActionC2SPayload.CIVIC) {
                    boolean executed = CivicProviders.execute(player, binding.settlement(), civic, request.target(), request.operation());
                    if (!executed) player.displayClientMessage(Component.translatable("charter.townstead.membership.denied"), false);
                    if (!executed || !CivicProviders.opensScreen(civic, request.operation())) send(player, request.lectern(), false, "");
                    return;
                }
                if (civic != null && civic.controlsGovernment()) return;
                result = CharterMemberships.handle(player, faction, new CharterActionC2SPayload(request.lectern(), action,
                        request.name(), request.profile(), request.culture(), request.operation(), request.target(),
                        request.argument(), CharterMemberships.revision(player.server)));
            }
            player.displayClientMessage(result, false);
            send(player, request.lectern(), false, result.getString());
            return;
        }
        if (action == CharterActionC2SPayload.CANCEL) {
            if (saved.cancel(dimension, request.lectern(), player.getUUID())) {
                setLecternState(player.serverLevel(), request.lectern(), CharterLecternAccess.NONE);
            }
            send(player, request.lectern(), false, Component.translatable("charter.townstead.proclamation_cancelled").getString());
            return;
        }
        if (!player.mayBuild() || !player.serverLevel().mayInteract(player, request.lectern())) return;
        Assembly assembly = assembly(player.serverLevel(), request.lectern());
        if (assembly == null) {
            send(player, request.lectern(), false, Component.translatable("charter.townstead.repair").getString());
            return;
        }
        if (action == CharterActionC2SPayload.LINK_EXISTING) {
            Existing existing = existing(player.serverLevel(), assembly.bell());
            if (existing == null || !mayLink(player, existing.faction(), existing.settlement())) {
                send(player, request.lectern(), false, Component.translatable("charter.townstead.link_denied").getString());
                return;
            }
            if (saved.bindExisting(dimension, request.lectern(), assembly.bell(), existing.settlement(),
                    existing.faction().id(), player.getUUID(), player.serverLevel().getGameTime())) {
                setLecternState(player.serverLevel(), request.lectern(), CharterLecternAccess.FOUNDED);
                SeatService.designate(player.serverLevel(), saved.binding(dimension, request.lectern()), false);
            }
            send(player, request.lectern(), false, Component.translatable("charter.townstead.linked").getString());
            return;
        }
        if (action != CharterActionC2SPayload.PREPARE) return;
        if (saved.binding(dimension, request.lectern()) != null) return;
        String name = normalizeName(request.name());
        FoundingProfileDefinition profile = FoundingProfiles.get(PLAYER_FACTION);
        ResourceLocation culture = request.culture().isBlank() ? null : ResourceLocation.tryParse(request.culture());
        if (name == null || profile == null || profile.faction() == null || !FoundingProfiles.validate(profile).isEmpty()
                || (!request.culture().isBlank() && (culture == null || Cultures.get(culture) == null))) {
            send(player, request.lectern(), false, Component.translatable("charter.townstead.definition_changed").getString());
            return;
        }
        var factionName = com.aetherianartificer.townstead.culture.FactionNaming.review(culture, profile.faction().kind(),
                request.target(), request.operation(), request.argument().isBlank() ? name : request.argument());
        if (factionName == null) {
            send(player, request.lectern(), false, Component.translatable("charter.townstead.identity.invalid").getString());
            return;
        }
        long now = player.serverLevel().getGameTime();
        saved.prepare(new CharterSavedData.Proposal(UUID.randomUUID(), player.getUUID(), dimension,
                request.lectern().immutable(), assembly.bell().immutable(), name, profile.id(), culture,
                now, now + PROPOSAL_LIFETIME, factionName));
        setLecternState(player.serverLevel(), request.lectern(), CharterLecternAccess.PREPARED);
        gather(player.serverLevel(), assembly.bell());
        send(player, request.lectern(), false, Component.translatable("charter.townstead.ring_to_found").getString());
    }

    private static void ring(ServerPlayer player, BlockPos bell) {
        ServerLevel level = player.serverLevel();
        CharterSavedData saved = CharterSavedData.get(level.getServer());
        saved.expire(level.getGameTime());
        var draft = saved.draftAtBell(level.dimension().location(), bell);
        if (draft != null) {
            if (!draft.signatures().contains(player.getUUID())) return;
            var binding = saved.binding(draft.dimension(), draft.lectern());
            var structure = assembly(level, draft.lectern());
            if (binding == null || !binding.faction().equals(draft.faction()) || !binding.bell().equals(bell)
                    || structure == null || !structure.bell().equals(bell) || !near(player, bell)
                    || !player.mayBuild() || !level.mayInteract(player, bell)) return;
            PoliticalSavedData politics = PoliticalSavedData.get(level.getServer());
            Faction before = politics.faction(draft.faction());
            if (before == null) return;
            List<Component> clauses = new ArrayList<>();
            for (CharterSavedData.Clause clause : draft.clauses()) clauses.add(clauseView(player, politics, before, clause).label().component());
            CharterDrafts.Proclamation result = CharterDrafts.proclaim(player, draft);
            player.displayClientMessage(result.message(), false);
            if (result.done()) {
                Faction after = politics.faction(draft.faction());
                String name = after == null ? before.name() : after.name();
                Component summary = clauses.size() == 1 ? clauses.get(0)
                        : Component.translatable("charter.townstead.draft.proclaimed_many", clauses.size());
                celebrate(level, bell, Component.literal(name), summary, waveRadius(level, binding.settlement()));
                LegitimacyService.notifyMembers(level.getServer(), politics, after == null ? before : after,
                        Component.translatable("charter.townstead.draft.proclaimed_notice", name, summary));
            }
            gather(level, bell);
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
        if (profile == null || profile.faction() == null || !profile.id().equals(PLAYER_FACTION)
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
        SettlementRef settlement = new SettlementRef(level.dimension().location(), village.getId());
        if (CivicProviders.ownsGovernment(level.getServer(), settlement)) {
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
        Faction founded = politics.faction(result.faction());
        if (founded != null) {
            politics.putFaction(founded.withName(proposal.factionName().display()));
            politics.putFactionName(founded.id(), proposal.factionName());
        }
        if (!saved.commit(proposal, settlement, result.faction(), level.getGameTime())) return;
        SeatService.designate(level, saved.binding(proposal.dimension(), proposal.lectern()), false);
        setLecternState(level, proposal.lectern(), CharterLecternAccess.FOUNDED);
        celebrate(level, bell, Component.literal(proposal.factionName().display()),
                Component.translatable("charter.townstead.founded_title"), waveRadius(level, settlement));
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
        setLecternState(level, lectern, binding != null ? CharterLecternAccess.FOUNDED
                : proposal != null ? CharterLecternAccess.PREPARED : CharterLecternAccess.NONE);
        Assembly assembly = assembly(level, lectern);
        BlockPos bell = binding != null ? binding.bell() : proposal != null ? proposal.bell() : assembly != null ? assembly.bell() : lectern;
        boolean editable = player.mayBuild() && level.mayInteract(player, lectern);
        if (binding == null && proposal == null && assembly == null) {
            return empty(lectern, bell, message.isBlank() ? Component.translatable("charter.townstead.unavailable").getString() : message);
        }
        if (binding == null) {
            if (proposal == null) {
                Existing existing = existing(level, assembly.bell());
                if (existing != null) {
                    Component form = kindName(existing.faction());
                    var external = CivicProviders.read(player, existing.settlement());
                    if (external != null && external.controlsGovernment()) form = external.governance().component();
                    return new CharterSnapshotS2CPayload(lectern, bell, CharterSnapshotS2CPayload.EXISTING,
                            editable && mayLink(player, existing.faction(), existing.settlement()), message, 0,
                            existing.village().getName(), existing.faction().name(), text(form),
                            text(Component.translatable("charter.townstead.existing_help")),
                            text(Component.translatable("charter.townstead.no_founding_culture")), List.of(), List.of(), null);
                }
            }
            List<CharterSnapshotS2CPayload.Option> profiles = profileOptions();
            if (profiles.isEmpty()) return empty(lectern, bell, Component.translatable("charter.townstead.no_profile").getString());
            List<CharterSnapshotS2CPayload.Option> cultures = cultureOptions();
            if (proposal == null) {
                return new CharterSnapshotS2CPayload(lectern, bell, CharterSnapshotS2CPayload.UNFOUNDED, editable, message, 0,
                        "", "", text(Component.translatable("charter.townstead.not_founded")),
                        text(Component.translatable("charter.townstead.review_help")),
                        text(Component.translatable("charter.townstead.no_founding_culture")), profiles, cultures, null);
            }
            FoundingProfileDefinition profile = FoundingProfiles.get(proposal.profile());
            Component form = profile == null ? Component.literal(proposal.profile().toString()) : kindName(profile.faction() == null ? null : profile.faction().kind());
            return new CharterSnapshotS2CPayload(lectern, bell,
                    assembly == null ? CharterSnapshotS2CPayload.REPAIR : CharterSnapshotS2CPayload.PREPARED,
                    editable && proposal.initiator().equals(player.getUUID()), message, 0, proposal.name(),
                    proposal.factionName().display(), text(form), text(Component.translatable("charter.townstead.ring_to_found")),
                    text(cultureName(proposal.culture())), profiles, cultures, null);
        }

        PoliticalSavedData politics = PoliticalSavedData.get(level.getServer());
        Faction faction = politics.faction(binding.faction());
        Village village = VillageManager.get(level).getOrEmpty(binding.settlement().villageId()).orElse(null);
        if (faction == null || village == null) {
            return empty(lectern, bell, Component.translatable("charter.townstead.record_unavailable").getString());
        }
        boolean intact = assembly != null && assembly.bell().equals(binding.bell());
        SettlementFoundingRecord founding = politics.founding(binding.settlement());
        Component form = kindName(faction);
        var civic = CivicProviders.read(player, binding.settlement());
        boolean external = civic != null && civic.controlsGovernment();
        if (external) {
            form = civic.governance().component();
            if (civic.state().equals("active") || !saved.externalGovernment(binding.settlement()).isEmpty()) politics.markExternalGovernment(faction.id());
        }
        boolean mayDraft = !external && intact && editable && CharterDrafts.mayDraft(player, faction);
        CharterSnapshotS2CPayload.Book book = new CharterSnapshotS2CPayload.Book(faction.id().toString(),
                text(proclaimed(player, faction, binding)),
                text(mayDraft ? editingAs(player, politics, faction) : Component.empty()),
                external ? null : legitimacyValue(politics, faction),
                external ? text(Component.empty()) : legitimacyDetail(player, faction, village),
                seatView(level, binding, faction, mayDraft),
                censusScopes(level, village, faction),
                external ? civic.history() : List.of(),
                HeraldryService.views(player, binding, village.getName()),
                mayDraft,
                external ? civicOffices(civic) : offices(player, politics, faction, mayDraft),
                members(player, politics, faction, external),
                external ? List.of() : CharterMemberships.requests(player, politics, faction),
                draftView(player, politics, faction),
                civic,
                liveryStyles(politics, faction),
                external ? List.of() : welcomes(politics, faction),
                external ? List.of() : accords(level, politics, faction, village));
        return new CharterSnapshotS2CPayload(lectern, bell, intact ? CharterSnapshotS2CPayload.FOUNDED : CharterSnapshotS2CPayload.REPAIR,
                editable, message, CivicProviders.revision(player, civic), village.getName(), faction.name(), text(form),
                text(Component.empty()), text(cultureName(founding == null ? null : founding.culture())), List.of(), List.of(), book);
    }

    /** "Proclaimed at the bell of Merry Hollow, Autumn 3, Year 41." */
    private static Component proclaimed(ServerPlayer player, Faction faction, CharterSavedData.Binding binding) {
        String place = settlementName(player.serverLevel(), binding.settlement());
        long daysAgo = Math.max(0, (player.serverLevel().getGameTime() - faction.createdAt()) / 24000L);
        Component date = CalendarDateFormatter.format(player.server,
                Math.max(0, TownsteadCalendar.worldDay(player.server) - daysAgo), CalendarDateFormatter.Style.LONG);
        return date.getString().isBlank() ? Component.translatable("charter.townstead.proclaimed_undated", place)
                : Component.translatable("charter.townstead.proclaimed", place, date);
    }

    private static List<CharterSnapshotS2CPayload.Office> offices(ServerPlayer player, PoliticalSavedData data, Faction faction, boolean mayDraft) {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
        if (kind == null) return List.of();
        ResourceLocation head = CharterDrafts.headOffice(faction);
        boolean transferable = mayDraft && !kind.membership().admission().equals(FactionKind.RESIDENCE);
        List<CharterSnapshotS2CPayload.Office> out = new ArrayList<>();
        for (FactionKind.Office office : kind.offices()) {
            List<CharterSnapshotS2CPayload.Holder> holders = new ArrayList<>();
            for (UUID holder : FactionBonds.holders(data, faction.id(), office.bond())) {
                holders.add(new CharterSnapshotS2CPayload.Holder(holder.toString(), text(CharterPeople.name(player, holder)),
                        holder.equals(player.getUUID())));
            }
            BondKind bond = BondKinds.byId(office.bond());
            out.add(new CharterSnapshotS2CPayload.Office(office.bond().toString(), text(bond.displayName()),
                    office.minimum(), office.maximum(), holders, transferable && office.bond().equals(head)));
        }
        return out;
    }

    private static List<CharterSnapshotS2CPayload.Office> civicOffices(CharterSnapshotS2CPayload.Civic civic) {
        List<CharterSnapshotS2CPayload.Office> out = new ArrayList<>();
        for (CharterSnapshotS2CPayload.Role role : civic.offices()) {
            List<CharterSnapshotS2CPayload.Holder> holders = role.holders().stream()
                    .map(name -> new CharterSnapshotS2CPayload.Holder("", name, false)).toList();
            out.add(new CharterSnapshotS2CPayload.Office("", role.name(), 0, -1, holders, false));
        }
        return out;
    }

    /** Whom the viewer may amend the charter as: their office, or a server operator. */
    private static Component editingAs(ServerPlayer player, PoliticalSavedData data, Faction faction) {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
        if (kind != null) {
            for (ResourceLocation held : FactionBonds.kinds(data, player.getUUID(), faction.id())) {
                if (kind.office(held) != null && PoliticalAuthority.allowed(data, player.getUUID(), faction.id(), CharterDrafts.GOVERN)) {
                    return Component.translatable("charter.townstead.editing_as", BondKinds.byId(held).displayName());
                }
            }
        }
        return Component.translatable("charter.townstead.editing_as_operator");
    }

    /** One plain line on how people join: by living there, by application, or openly. */
    private static Component joinHint(Faction faction, ServerLevel level) {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
        if (kind == null) return Component.empty();
        ResourceLocation admission = kind.membership().admission();
        if (admission.equals(FactionKind.RESIDENCE)) {
            SettlementRef home = faction.seatSettlement();
            return home == null ? Component.empty() : Component.translatable("charter.townstead.join.residence", settlementName(level, home));
        }
        if (admission.equals(FactionKind.APPLICATION)) return Component.translatable("charter.townstead.join.application");
        return Component.translatable("charter.townstead.join.open");
    }

    private static ResourceLocation membershipBond(Faction faction) {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
        return kind == null ? FactionBonds.CITIZENSHIP : kind.membership().bond();
    }

    private static CharterSnapshotS2CPayload.Members members(ServerPlayer player, PoliticalSavedData data, Faction faction, boolean external) {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
        ResourceLocation membership = kind == null ? FactionBonds.CITIZENSHIP : kind.membership().bond();
        List<UUID> members = FactionBonds.holders(data, faction.id(), membership);
        UUID self = player.getUUID();
        boolean member = members.contains(self);
        BondKind bond = BondKinds.byId(membership);
        BondKind.Role citizen = bond.roleFor(BondKind.Party.PERSON);
        boolean visible = member || player.hasPermissions(2) || citizen == null || !citizen.membersOnly();
        List<CharterSnapshotS2CPayload.Person> people = new ArrayList<>();
        if (visible) {
            record Entry(UUID id, Component name, int rank, @Nullable Component office, boolean player) {}
            List<Entry> entries = new ArrayList<>();
            for (UUID person : members) {
                Entity entity = findEntity(player, person);
                boolean isPlayer = !(entity instanceof VillagerEntityMCA) && (player.server.getPlayerList().getPlayer(person) != null
                        || player.server.getProfileCache() != null && player.server.getProfileCache().get(person).isPresent());
                int rank = Integer.MAX_VALUE;
                Component office = null;
                if (kind != null) {
                    for (int i = 0; i < kind.offices().size() && office == null; i++) {
                        ResourceLocation officeBond = kind.offices().get(i).bond();
                        if (FactionBonds.holders(data, faction.id(), officeBond).contains(person)) {
                            rank = i;
                            office = BondKinds.byId(officeBond).displayName();
                        }
                    }
                }
                entries.add(new Entry(person, CharterPeople.name(player, person), rank, office, isPlayer));
            }
            // Office holders by rank, then players, then everyone else, each by name.
            entries.sort(Comparator.comparingInt(Entry::rank).thenComparing(e -> !e.player())
                    .thenComparing(e -> e.name().getString()));
            for (Entry entry : entries.subList(0, Math.min(ROSTER_LIMIT, entries.size()))) {
                people.add(new CharterSnapshotS2CPayload.Person(entry.id().toString(), text(entry.name()),
                        text(entry.office() == null ? Component.empty() : entry.office()), entry.player(), entry.id().equals(self)));
            }
        }
        int livery = !member ? -1 : com.aetherianartificer.townstead.livery.LiverySavedData.get(player.server).wears(self) ? 1 : 0;
        return new CharterSnapshotS2CPayload.Members(members.size(), visible, people, text(yourStatus(player, data, faction, member)),
                text(external ? Component.empty() : joinHint(faction, player.serverLevel())),
                external ? List.of() : CharterMemberships.actions(player, data, faction), livery);
    }

    /** Every loaded livery style, by name, for the heraldry desk's chooser. */
    private static final int ACCORD_CANDIDATES = 12;

    /** Allies first, then the nearest factions someone could answer an offer for. */
    private static List<CharterSnapshotS2CPayload.Accord> accords(ServerLevel level, PoliticalSavedData politics,
                                                                 Faction faction, Village village) {
        java.util.Set<ResourceLocation> allies = CharterAccords.allies(politics, faction);
        List<CharterSnapshotS2CPayload.Accord> out = new java.util.ArrayList<>();
        List<java.util.Map.Entry<Double, Faction>> candidates = new java.util.ArrayList<>();
        net.minecraft.core.BlockPos here = new net.minecraft.core.BlockPos(village.getCenter());
        for (Faction other : politics.factions()) {
            if (allies.contains(other.id())) {
                out.add(new CharterSnapshotS2CPayload.Accord(other.id().toString(), text(Component.literal(other.name())), true));
                continue;
            }
            if (CharterAccords.problem(politics, faction, other) != null || CharterAccords.representatives(politics, other).isEmpty()) continue;
            candidates.add(java.util.Map.entry(distance(level, other, here), other));
        }
        candidates.sort(java.util.Map.Entry.comparingByKey());
        for (int i = 0; i < Math.min(ACCORD_CANDIDATES, candidates.size()); i++) {
            Faction other = candidates.get(i).getValue();
            out.add(new CharterSnapshotS2CPayload.Accord(other.id().toString(), text(Component.literal(other.name())), false));
        }
        return out;
    }

    private static double distance(ServerLevel level, Faction faction, net.minecraft.core.BlockPos from) {
        var settlement = faction.seatSettlement();
        if (settlement == null || !settlement.dimension().equals(level.dimension().location())) return Double.MAX_VALUE;
        return VillageManager.get(level).getOrEmpty(settlement.villageId())
                .map(v -> Math.sqrt(new net.minecraft.core.BlockPos(v.getCenter()).distSqr(from))).orElse(Double.MAX_VALUE);
    }

    private static List<CharterSnapshotS2CPayload.Welcome> welcomes(PoliticalSavedData politics, Faction faction) {
        java.util.Set<String> current = politics.welcomes(faction.id());
        List<CharterSnapshotS2CPayload.Welcome> out = new java.util.ArrayList<>();
        com.aetherianartificer.townstead.root.disposition.DispositionRelations.welcomable().forEach((group, name) ->
                out.add(new CharterSnapshotS2CPayload.Welcome(group, text(name), current.contains(group))));
        return out;
    }

    /**
     * Every livery style, the faction's own culture's first. A style belongs to the culture whose pack
     * namespace it shares; styles from other cultures carry that culture's name, so a borrowed style
     * reads as borrowed.
     */
    private static List<CharterSnapshotS2CPayload.StyleOption> liveryStyles(PoliticalSavedData politics, Faction faction) {
        SettlementRef seat = faction.seatSettlement();
        var founding = seat == null ? null : politics.founding(seat);
        ResourceLocation own = founding == null ? null : Cultures.rootOf(founding.culture());
        Map<String, Culture> byNamespace = new java.util.HashMap<>();
        for (ResourceLocation root : Cultures.rootIds()) byNamespace.putIfAbsent(root.getNamespace(), Cultures.get(root));
        return com.aetherianartificer.townstead.livery.LiveryStyles.all().values().stream()
                .sorted(Comparator.comparing((com.aetherianartificer.townstead.livery.LiveryStyle style) -> {
                            Culture culture = byNamespace.get(style.id().getNamespace());
                            return own != null && culture != null && culture.id().equals(own) ? 0 : 1;
                        })
                        .thenComparing(style -> {
                            Culture culture = byNamespace.get(style.id().getNamespace());
                            return culture == null ? "" : culture.displayName().getString();
                        })
                        .thenComparing(style -> style.name().getString()))
                .map(style -> {
                    Culture culture = byNamespace.get(style.id().getNamespace());
                    Component name = culture == null || culture.id().equals(own) ? style.name()
                            : Component.translatable("charter.townstead.livery_of_culture", style.name(), culture.displayName());
                    return new CharterSnapshotS2CPayload.StyleOption(style.id().toString(), text(name),
                            com.aetherianartificer.townstead.livery.LiveryView.of(style, style.primary(), style.secondary()));
                })
                .toList();
    }

    private static Component yourStatus(ServerPlayer player, PoliticalSavedData data, Faction faction, boolean member) {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
        MutableComponent offices = Component.empty();
        if (kind != null) {
            for (ResourceLocation held : FactionBonds.kinds(data, player.getUUID(), faction.id())) {
                if (kind.office(held) == null) continue;
                if (!offices.getSiblings().isEmpty()) offices.append(", ");
                offices.append(BondKinds.byId(held).displayName());
            }
        }
        if (!offices.getSiblings().isEmpty()) return Component.translatable("charter.townstead.status.office", offices);
        boolean pending = CharterRequests.get(player.server).entries().stream()
                .anyMatch(r -> r.open() && r.person().equals(player.getUUID()) && r.faction().equals(faction.id().toString()));
        if (pending) return Component.translatable("charter.townstead.status.pending");
        return Component.translatable(member ? "charter.townstead.status.member" : "charter.townstead.status.outsider");
    }

    private static @Nullable CharterSnapshotS2CPayload.Draft draftView(ServerPlayer player, PoliticalSavedData data, Faction faction) {
        CharterSavedData.Draft draft = CharterSavedData.get(player.server).draft(faction.id());
        if (draft == null) return null;
        List<UUID> signers = CharterDrafts.signers(player, faction, draft.author());
        if (!CharterDrafts.mayDraft(player, faction) && !signers.contains(player.getUUID())) return null;
        List<CharterSnapshotS2CPayload.Clause> clauses = new ArrayList<>();
        for (CharterSavedData.Clause clause : draft.clauses()) clauses.add(clauseView(player, data, faction, clause));
        List<CharterSnapshotS2CPayload.Signer> signerViews = new ArrayList<>();
        for (UUID signer : signers) {
            boolean signed = draft.signatures().contains(signer);
            Long day = draft.signedOn().get(signer);
            Component date = signed && day != null
                    ? CalendarDateFormatter.format(player.server, day, CalendarDateFormatter.Style.MEDIUM) : Component.empty();
            signerViews.add(new CharterSnapshotS2CPayload.Signer(text(CharterPeople.name(player, signer)),
                    text(signerOffice(data, faction, signer)), text(date),
                    com.aetherianartificer.townstead.seal.PersonalSeals.of(player.server, signer), signed, signer.equals(player.getUUID())));
        }
        long remaining = draft.prepared() ? Math.max(0, draft.expiresAt() - player.serverLevel().getGameTime()) : 0;
        boolean maySign = !draft.prepared() && signers.contains(player.getUUID()) && !draft.signatures().contains(player.getUUID());
        return new CharterSnapshotS2CPayload.Draft(draft.token().toString(), clauses, signerViews, maySign,
                draft.prepared(), remaining);
    }

    /** The signer's highest office. Only an operator can draft without one, so that is the fallback. */
    private static Component signerOffice(PoliticalSavedData data, Faction faction, UUID signer) {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
        if (kind != null) {
            for (FactionKind.Office office : kind.offices()) {
                if (FactionBonds.holders(data, faction.id(), office.bond()).contains(signer)) return BondKinds.byId(office.bond()).displayName();
            }
        }
        return Component.translatable("charter.townstead.draft.operator");
    }

    private static CharterSnapshotS2CPayload.Clause clauseView(ServerPlayer player, PoliticalSavedData data, Faction faction,
                                                               CharterSavedData.Clause clause) {
        return switch (clause.type()) {
            case CharterDrafts.RENAME -> new CharterSnapshotS2CPayload.Clause(
                    text(Component.translatable("charter.townstead.clause.rename", clause.argument())),
                    text(Component.translatable("charter.townstead.clause.rename.detail", clause.expected())));
            case CharterDrafts.HERALDRY -> new CharterSnapshotS2CPayload.Clause(
                    text(Component.translatable(clause.target().startsWith("settlement:")
                            ? "charter.townstead.clause.heraldry.settlement" : "charter.townstead.clause.heraldry.faction")),
                    text(Component.translatable("charter.townstead.clause.heraldry.detail")));
            case CharterDrafts.LIVERY -> {
                var choice = CharterDrafts.LiveryChoice.decode(clause.argument());
                var style = choice == null ? null : com.aetherianartificer.townstead.livery.LiveryStyles.get(choice.style());
                yield new CharterSnapshotS2CPayload.Clause(
                        text(Component.translatable(clause.target().startsWith("settlement:")
                                ? "charter.townstead.clause.livery.settlement" : "charter.townstead.clause.livery.faction")),
                        text(style == null ? Component.translatable("charter.townstead.clause.livery.default") : style.name()));
            }
            case CharterDrafts.SEAT -> new CharterSnapshotS2CPayload.Clause(
                    text(Component.translatable("charter.townstead.clause.seat")),
                    text(Component.translatable("charter.townstead.clause.seat.detail")));
            case CharterDrafts.TRANSFER_LEADERSHIP -> {
                UUID recipient = CharterDrafts.uuid(clause.argument());
                ResourceLocation head = CharterDrafts.headOffice(faction);
                yield new CharterSnapshotS2CPayload.Clause(
                        text(Component.translatable("charter.townstead.clause.transfer",
                                head == null ? Component.empty() : BondKinds.byId(head).displayName(),
                                recipient == null ? Component.empty() : CharterPeople.name(player, recipient))),
                        text(Component.translatable("charter.townstead.clause.transfer.detail")));
            }
            case CharterDrafts.ACCORD, CharterDrafts.END_ACCORD -> {
                Faction other = data.faction(ResourceLocation.tryParse(clause.target()));
                Component name = Component.literal(other == null ? clause.target() : other.name());
                boolean offer = clause.type().equals(CharterDrafts.ACCORD);
                yield new CharterSnapshotS2CPayload.Clause(
                        text(Component.translatable(offer ? "charter.townstead.clause.accord" : "charter.townstead.clause.end_accord", name)),
                        text(Component.translatable(offer ? "charter.townstead.clause.accord.detail" : "charter.townstead.clause.end_accord.detail", name)));
            }
            case CharterDrafts.WELCOME -> {
                var name = com.aetherianartificer.townstead.root.disposition.DispositionRelations.welcomable()
                        .getOrDefault(clause.target(), Component.literal(clause.target()));
                boolean welcome = "1".equals(clause.argument());
                yield new CharterSnapshotS2CPayload.Clause(
                        text(Component.translatable(welcome ? "charter.townstead.clause.welcome" : "charter.townstead.clause.unwelcome", name)),
                        text(Component.translatable(welcome ? "charter.townstead.clause.welcome.detail" : "charter.townstead.clause.unwelcome.detail", name)));
            }
            case CharterDrafts.DISSOLVE -> new CharterSnapshotS2CPayload.Clause(
                    text(Component.translatable("charter.townstead.clause.dissolve", faction.name())),
                    text(Component.translatable("charter.townstead.clause.dissolve.detail")));
            default -> new CharterSnapshotS2CPayload.Clause(text(Component.literal(clause.type())), text(Component.empty()));
        };
    }

    private static @Nullable Entity findEntity(ServerPlayer viewer, UUID person) {
        ServerPlayer online = viewer.server.getPlayerList().getPlayer(person);
        if (online != null) return online;
        for (ServerLevel level : viewer.server.getAllLevels()) {
            Entity entity = level.getEntity(person);
            if (entity != null) return entity;
        }
        return null;
    }

    private static List<CharterSnapshotS2CPayload.Option> profileOptions() {
        var profile = FoundingProfiles.get(PLAYER_FACTION);
        if (profile == null || profile.faction() == null || !FoundingProfiles.validate(profile).isEmpty()) return List.of();
        return List.of(new CharterSnapshotS2CPayload.Option(profile.id().toString(), text(profile.displayName()),
                text(Component.translatable("charter.townstead.founding_leader_help")),
                text(Component.translatable("charter.townstead.founding_leader_status")), true,
                com.aetherianartificer.townstead.culture.FactionNaming.patterns(profile.faction().kind())));
    }

    private static List<CharterSnapshotS2CPayload.Option> cultureOptions() {
        List<CharterSnapshotS2CPayload.Option> out = new ArrayList<>();
        out.add(new CharterSnapshotS2CPayload.Option("", text(Component.translatable("charter.townstead.no_founding_culture")),
                text(Component.translatable("charter.townstead.no_culture_description")),
                text(Component.translatable("charter.townstead.consequences.no_culture")), false, List.of()));
        Cultures.rootIds().stream().sorted(Comparator.comparing(ResourceLocation::toString)).forEach(id -> {
            Culture culture = Cultures.get(id);
            if (culture != null) out.add(new CharterSnapshotS2CPayload.Option(id.toString(), text(culture.displayName()),
                    text(Component.translatable("charter.townstead.founding_culture_description")),
                    text(Component.translatable("charter.townstead.consequences.founding_culture")), false,
                    com.aetherianartificer.townstead.culture.FactionNaming.suggestions(id)));
        });
        return List.copyOf(out);
    }

    private static Component kindName(@Nullable Faction faction) {
        return kindName(faction == null ? null : faction.kind());
    }

    private static Component kindName(@Nullable ResourceLocation kindId) {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(kindId);
        return kind == null ? Component.translatable("charter.townstead.governance_unavailable") : kind.display().name();
    }

    private static List<CharterSnapshotS2CPayload.CensusScope> censusScopes(ServerLevel level, Village local, Faction faction) {
        List<CharterSnapshotS2CPayload.CensusScope> out = new ArrayList<>();
        Census localCensus = census(level, local);
        out.add(new CharterSnapshotS2CPayload.CensusScope("settlement",
                text(Component.translatable("charter.townstead.census.local", local.getName())),
                localCensus.groups(), localCensus.total(), localCensus.uncounted(), true));
        if (faction.settlements().size() < 2) return List.copyOf(out);
        Map<UUID, Entity> people = new LinkedHashMap<>();
        boolean available = true;
        for (var settlement : faction.settlements()) {
            ServerLevel source = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, settlement.dimension()));
            Village village = source == null ? null : VillageManager.get(source).getOrEmpty(settlement.villageId()).orElse(null);
            if (village == null) { available = false; continue; }
            for (UUID person : village.getResidentsUUIDs().toList()) if (person != null) {
                Entity loaded = source.getEntity(person);
                if (!people.containsKey(person) || loaded != null) people.put(person, loaded);
            }
        }
        Census census = census(people);
        out.add(new CharterSnapshotS2CPayload.CensusScope("faction",
                text(Component.translatable("charter.townstead.census.faction", faction.name())),
                census.groups(), census.total(), census.uncounted(), available));
        return List.copyOf(out);
    }

    private static Census census(ServerLevel level, Village village) {
        Map<UUID, Entity> people = new LinkedHashMap<>();
        village.getResidentsUUIDs().toList().stream().filter(java.util.Objects::nonNull)
                .forEach(person -> people.put(person, level.getEntity(person)));
        return census(people);
    }

    /** Counts loaded villagers only; a resident whose entity is not loaded cannot be read and is left uncounted. */
    private static Census census(Map<UUID, Entity> people) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        int counted = 0;
        for (Entity entity : people.values()) {
            if (!(entity instanceof VillagerEntityMCA villager)) continue;
            String culture = Cultures.rootOf(Naming.cultureOf(villager));
            if (culture.isBlank() || Cultures.get(culture) == null) culture = "";
            counts.merge(culture, 1, Integer::sum);
            counted++;
        }
        List<CharterSnapshotS2CPayload.CensusGroup> groups = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            Culture culture = entry.getKey().isBlank() ? null : Cultures.get(ResourceLocation.tryParse(entry.getKey()));
            Component name = culture == null ? Component.translatable("charter.townstead.unrecorded_culture") : culture.displayName();
            groups.add(new CharterSnapshotS2CPayload.CensusGroup(entry.getKey(), text(name), entry.getValue(), cultureColor(entry.getKey())));
        }
        groups.sort(Comparator.comparingInt(CharterSnapshotS2CPayload.CensusGroup::count).reversed());
        return new Census(List.copyOf(groups), counted, people.size() - counted);
    }

    private static void gather(ServerLevel level, BlockPos bell) {
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(bell).inflate(24.0D))) {
            if (!villager.isAlive()) continue;
            villager.getNavigation().moveTo(bell.getX() + 0.5D, bell.getY(), bell.getZ() + 0.5D, 0.75D);
        }
    }

    /** How far the proclamation's wave runs: across the settlement's buildings, within sensible bounds. */
    private static int waveRadius(ServerLevel level, SettlementRef settlement) {
        Village village = VillageManager.get(level).getOrEmpty(settlement.villageId()).orElse(null);
        if (village == null) return 24;
        var box = village.getBox();
        int span = Math.max(box.getXSpan(), box.getZSpan());
        return Math.max(16, Math.min(96, span / 2 + 8));
    }

    private static void celebrate(ServerLevel level, BlockPos bell, Component title, Component subtitle, int radius) {
        double reach = (radius + 32.0D) * (radius + 32.0D);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(bell.getX() + 0.5D, bell.getY() + 0.5D, bell.getZ() + 0.5D) > reach) continue;
            CharterCeremonyS2CPayload cue = new CharterCeremonyS2CPayload(bell, text(title), text(subtitle), radius);
            //? if neoforge {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(viewer, cue);
            //?} else {
            /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(viewer, cue);
            *///?}
        }
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(VillagerEntityMCA.class, new AABB(bell).inflate(20.0D))) {
            villager.getLookControl().setLookAt(bell.getX() + 0.5D, bell.getY() + 0.5D, bell.getZ() + 0.5D);
            AiEmoteScheduler.playEmote(villager, CLAP);
        }
    }

    private static @Nullable Assembly assembly(Level level, BlockPos lectern) {
        BlockState lecternState = level.getBlockState(lectern);
        if (!lecternState.is(Blocks.LECTERN)) return null;
        Direction facing = lecternState.getValue(LecternBlock.FACING);
        BlockPos support = lectern.relative(facing.getOpposite());
        BlockState supportState = level.getBlockState(support);
        BlockPos bell = support.above();
        if (supportState.isAir() || !supportState.isFaceSturdy(level, support, Direction.UP)
                || !level.getBlockState(bell).is(CharterBellBlocks.ELIGIBLE)) return null;
        BlockState bellState = level.getBlockState(bell);
        if (bellState.hasProperty(BellBlock.ATTACHMENT) && bellState.getValue(BellBlock.ATTACHMENT) != BellAttachType.FLOOR) return null;
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
        Faction faction = PoliticalSavedData.get(level.getServer()).faction(settlement);
        return faction == null || faction.status() == Faction.Status.DISSOLVED ? null : new Existing(village, settlement, faction);
    }

    /** "Accepted (68)", or null for a faction without governance. */
    private static @Nullable CharterSnapshotS2CPayload.Text legitimacyValue(PoliticalSavedData politics, Faction faction) {
        if (LegitimacyService.governance(faction) == null) return null;
        double value = LegitimacyService.current(politics, faction);
        Component band = Component.translatable("townstead.legitimacy.band." + LegitimacyService.band(value));
        return text(Component.translatable("charter.townstead.legitimacy.value", band, Math.round(value)));
    }

    /**
     * What legitimacy rests on, named as the systems players already know: "Needs: steady ·
     * Spirit: Homestead". Sources that are not a known system are left out.
     */
    private static CharterSnapshotS2CPayload.Text legitimacyDetail(ServerPlayer player, Faction faction, Village village) {
        GovernanceDefinition governance = LegitimacyService.governance(faction);
        if (governance == null) return text(Component.empty());
        MutableComponent line = Component.empty();
        for (var contribution : LegitimacyService.target(player.server, faction).contributions()) {
            Component part = switch (contribution.label().getPath()) {
                case "needs" -> Component.translatable("charter.townstead.legitimacy.needs",
                        Component.translatable("charter.townstead.needs." + needsBand(contribution.raw())));
                case "village_spirit" -> Component.translatable("charter.townstead.legitimacy.spirit", spiritName(player.serverLevel(), village));
                case "leader_standing" -> Component.translatable("charter.townstead.legitimacy.leader_standing", Math.round(contribution.raw()));
                default -> null;
            };
            if (part == null) continue;
            if (!line.getSiblings().isEmpty()) line.append(" \u00b7 ");
            line.append(part);
        }
        return text(line);
    }

    /** The band a village's average need reading falls in, from -1 (all in crisis) to 1 (all thriving). */
    private static String needsBand(double value) {
        if (value > 0.75) return "thriving";
        if (value > 0) return "steady";
        if (value > -0.75) return "strained";
        return "crisis";
    }

    private static Component spiritName(ServerLevel level, Village village) {
        var cached = com.aetherianartificer.townstead.spirit.VillageSpiritCache.get(level, village.getId());
        var readout = cached != null ? cached.readout() : com.aetherianartificer.townstead.spirit.VillageSpiritAggregator.readoutFor(
                com.aetherianartificer.townstead.spirit.VillageSpiritAggregator.snapshotFor(level, village).totals());
        return readout.asComponent();
    }

    private static CharterSnapshotS2CPayload.SeatRow seatView(ServerLevel level, CharterSavedData.Binding binding, Faction faction, boolean mayDraft) {
        SeatInstance seat = PoliticalSavedData.get(level.getServer()).seat(faction.id());
        boolean here = seat != null && seat.settlement().dimension().equals(binding.dimension()) && seat.lectern().equals(binding.lectern());
        Component value = seat == null ? Component.translatable("charter.townstead.seat.none_short") : seatBuildingName(level, seat);
        Component detail;
        if (seat == null) detail = Component.empty();
        else if (seat.damaged()) detail = Component.translatable("charter.townstead.seat.damaged." + seat.damage());
        else if (here) detail = Component.empty();
        else detail = Component.translatable("charter.townstead.seat.elsewhere_short", settlementName(level, seat.settlement()));
        boolean mayMove = mayDraft && !here && SeatService.host(level, binding) != null;
        return new CharterSnapshotS2CPayload.SeatRow(text(value), text(detail), seat != null && seat.damaged(), mayMove);
    }

    private static @Nullable String seatBuildingType(ServerLevel level, SeatInstance seat) {
        ServerLevel seatLevel = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, seat.settlement().dimension()));
        return seatLevel == null ? null : VillageManager.get(seatLevel).getOrEmpty(seat.settlement().villageId())
                .map(v -> com.aetherianartificer.townstead.compat.mca.McaBuildings.byId(v, seat.buildingId()))
                .map(Building::getType).orElse(null);
    }

    /** The host building's type name, or "Meeting Place" when that type has no Seat data. */
    private static Component seatBuildingName(ServerLevel level, SeatInstance seat) {
        String type = seatBuildingType(level, seat);
        if (!SeatBuildings.isSeatBuilding(type)) return Component.translatable("charter.townstead.seat.meeting_place");
        return Component.translatable("buildingType." + type);
    }

    private static String settlementName(ServerLevel level, SettlementRef settlement) {
        ServerLevel source = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, settlement.dimension()));
        if (source == null) return "";
        return VillageManager.get(source).getOrEmpty(settlement.villageId()).map(Village::getName).orElse("");
    }

    /** Linking a new Charter Bell to a faction needs its authority, unless it has no government at all. */
    private static boolean mayLink(ServerPlayer player, Faction faction, SettlementRef settlement) {
        var civic = CivicProviders.read(player, settlement);
        if (civic != null && civic.controlsGovernment()) return civic.mayManage() || player.hasPermissions(2);
        if (player.hasPermissions(2)) return true;
        FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
        if (kind == null || kind.offices().isEmpty()) return true;
        return PoliticalAuthority.allowed(PoliticalSavedData.get(player.server), player.getUUID(), faction.id(), CharterDrafts.GOVERN);
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

    private static CharterSnapshotS2CPayload empty(BlockPos lectern, BlockPos bell, String message) {
        return new CharterSnapshotS2CPayload(lectern, bell, CharterSnapshotS2CPayload.UNAVAILABLE, false, message, 0, "", "",
                text(Component.translatable("charter.townstead.unavailable")), text(Component.empty()), text(Component.empty()),
                List.of(), List.of(), null);
    }

    private static CharterSnapshotS2CPayload.Text text(Component value) {
        return CharterSnapshotS2CPayload.Text.of(value);
    }

    private static int cultureColor(String id) {
        if (id == null || id.isBlank()) return 0xFF918C82;
        int[] colors = {0xFF97623F, 0xFF557F72, 0xFFB08A3E, 0xFF697D9C, 0xFF966480, 0xFF71854B, 0xFFB36650};
        return colors[Math.floorMod(id.hashCode(), colors.length)];
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalStateException(value);
        return id;
    }

    private record Assembly(BlockPos lectern, BlockPos support, BlockPos bell) {}

    private record Existing(Village village, SettlementRef settlement, Faction faction) {}

    private record Census(List<CharterSnapshotS2CPayload.CensusGroup> groups, int total, int uncounted) {}
}
