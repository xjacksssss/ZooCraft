package com.zoocraft.dashboard;

import com.mojang.authlib.GameProfile;
import com.zoocraft.ZooCraft;
import com.zoocraft.animal.AnimalCensus;
import com.zoocraft.animal.Exhibit;
import com.zoocraft.animal.ZooCensus;
import com.zoocraft.config.ZooConfig;
import com.zoocraft.feedback.CommentType;
import com.zoocraft.network.DashboardPayloads;
import com.zoocraft.network.DashboardSnapshot;
import com.zoocraft.visitor.VisitorTracker;
import com.zoocraft.zoo.ActionResult;
import com.zoocraft.zoo.Gate;
import com.zoocraft.zoo.GateStatus;
import com.zoocraft.zoo.PlayerNames;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooAlerts;
import com.zoocraft.zoo.ZooManager;
import com.zoocraft.zoo.ZooPermission;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Serves the dashboard (spec section 9.3). The server is the only source of truth: players subscribe by opening
 * the dashboard, get a fresh snapshot about once a second, and every button is a request that goes through
 * {@link ZooManager} with the usual permission checks.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class DashboardService {
    private static final int REFRESH_TICKS = 20;
    private static final int TOP_COMMENTS = 6;
    private static final Map<UUID, UUID> SUBSCRIPTIONS = new HashMap<>();

    private DashboardService() {
    }

    /** Opens the dashboard for the requested zoo, the zoo the player is standing in, or their only zoo. */
    public static void open(ServerPlayer player, Optional<UUID> requested) {
        ZooManager zoos = ZooManager.get(player.server);
        Optional<Zoo> zoo = requested.flatMap(zoos::byId).filter(z -> ZooManager.can(player, z, ZooPermission.VIEW));
        if (zoo.isEmpty() && requested.isEmpty()) {
            zoo = zoos.zooAt(player.level(), player.blockPosition()).filter(z -> ZooManager.can(player, z, ZooPermission.VIEW));
        }
        if (zoo.isEmpty()) {
            List<Zoo> mine = zoos.managedBy(player).stream().filter(z -> z.isOwnerOrManager(player.getUUID())).toList();
            if (mine.isEmpty() && player.hasPermissions(2)) {
                mine = zoos.managedBy(player);
            }
            if (mine.size() == 1) {
                zoo = Optional.of(mine.getFirst());
            } else if (mine.size() > 1) {
                PacketDistributor.sendToPlayer(player, new DashboardPayloads.ZooList(mine.stream()
                        .map(z -> new DashboardPayloads.ZooList.Entry(z.id(), z.name(), z.open())).toList()));
                return;
            } else {
                player.sendSystemMessage(Component.translatable("zoocraft.cmd.no_zoos").withStyle(ChatFormatting.GRAY));
                return;
            }
        }
        SUBSCRIPTIONS.put(player.getUUID(), zoo.get().id());
        PacketDistributor.sendToPlayer(player, snapshot(player, zoo.get()).withOpenScreen(true));
    }

    public static void close(ServerPlayer player) {
        SUBSCRIPTIONS.remove(player.getUUID());
    }

    /** Whether the player is currently subscribed to the zoo (for tests). */
    public static boolean subscribed(ServerPlayer player, UUID zooId) {
        return zooId.equals(SUBSCRIPTIONS.get(player.getUUID()));
    }

    /** Runs a dashboard button through ZooManager and refreshes everyone watching that zoo. */
    public static ActionResult handle(ServerPlayer player, DashboardPayloads.Action request) {
        ZooManager zoos = ZooManager.get(player.server);
        Optional<Zoo> maybeZoo = zoos.byId(request.zooId());
        if (maybeZoo.isEmpty()) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.zoo_gone"));
        }
        Zoo zoo = maybeZoo.get();
        if (!ZooManager.can(player, zoo, ZooPermission.VIEW)) {
            return ActionResult.fail(Component.translatable("zoocraft.msg.no_permission"));
        }
        ActionResult result = switch (request.action()) {
            case OPEN_ZOO -> zoos.setOpen(player, zoo, true);
            case CLOSE_ZOO -> zoos.setOpen(player, zoo, false);
            case SET_PRICE -> zoos.setPrice(player, zoo, request.number());
            case KEEP_LOADED -> zoos.setKeepLoaded(player, zoo, request.number() != 0);
            case DEPOSIT -> zoos.deposit(player, zoo, request.number());
            case WITHDRAW -> zoos.withdraw(player, zoo, request.number());
            case ADD_MANAGER -> withProfile(player, request.text(), profile -> zoos.addManager(player, zoo, profile));
            case TRANSFER -> withProfile(player, request.text(), profile -> zoos.transfer(player, zoo, profile));
            case REMOVE_MANAGER -> {
                UUID id;
                try {
                    id = UUID.fromString(request.text());
                } catch (IllegalArgumentException e) {
                    yield ActionResult.fail(Component.translatable("zoocraft.msg.no_permission"));
                }
                yield zoos.removeManager(player, zoo, id, PlayerNames.name(player.server, id));
            }
        };
        refreshZoo(player.server, zoo);
        return result;
    }

    private static ActionResult withProfile(ServerPlayer player, String name, java.util.function.Function<GameProfile, ActionResult> action) {
        Optional<GameProfile> profile = PlayerNames.profile(player.server, name);
        return profile.map(action).orElseGet(() -> ActionResult.fail(Component.translatable("zoocraft.msg.unknown_player", name)));
    }

    // ------------------------------------------------------------------ refreshing

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % REFRESH_TICKS == 0 && !SUBSCRIPTIONS.isEmpty()) {
            refreshAll(event.getServer());
        }
    }

    static void refreshAll(MinecraftServer server) {
        ZooManager zoos = ZooManager.get(server);
        for (Map.Entry<UUID, UUID> entry : List.copyOf(SUBSCRIPTIONS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                SUBSCRIPTIONS.remove(entry.getKey());
                continue;
            }
            Optional<Zoo> zoo = zoos.byId(entry.getValue());
            if (zoo.isEmpty() || !ZooManager.can(player, zoo.get(), ZooPermission.VIEW)) {
                SUBSCRIPTIONS.remove(entry.getKey());
                PacketDistributor.sendToPlayer(player, DashboardPayloads.Closed.INSTANCE);
                player.sendSystemMessage(Component.translatable("zoocraft.msg.dashboard_lost_access").withStyle(ChatFormatting.GRAY));
                continue;
            }
            PacketDistributor.sendToPlayer(player, snapshot(player, zoo.get()));
        }
    }

    private static void refreshZoo(MinecraftServer server, Zoo zoo) {
        for (Map.Entry<UUID, UUID> entry : List.copyOf(SUBSCRIPTIONS.entrySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && entry.getValue().equals(zoo.id())) {
                if (ZooManager.can(player, zoo, ZooPermission.VIEW) && ZooManager.get(server).byId(zoo.id()).isPresent()) {
                    PacketDistributor.sendToPlayer(player, snapshot(player, zoo));
                }
            }
        }
    }

    // ------------------------------------------------------------------ snapshot

    public static DashboardSnapshot snapshot(ServerPlayer viewer, Zoo zoo) {
        MinecraftServer server = viewer.server;
        long now = server.overworld().getGameTime();
        ServerLevel level = server.getLevel(zoo.dimension());
        ZooCensus census = AnimalCensus.cached(zoo);
        if (census.takenAt() < 0 && level != null) {
            census = AnimalCensus.refresh(zoo, level);
        }

        List<Component> alerts = new ArrayList<>();
        if (zoo.gates().isEmpty()) {
            alerts.add(Component.translatable("zoocraft.alert.no_gates"));
        }
        for (String key : ZooAlerts.active(zoo)) {
            alerts.add(Component.translatable(key));
        }
        for (Gate gate : zoo.gates()) {
            GateStatus status = zoo.gateStatus(gate, now);
            if (status != GateStatus.OPEN) {
                alerts.add(Component.translatable("zoocraft.alert.gate_" + status.getSerializedName(), gate.pos().getX(), gate.pos().getZ()));
            }
        }
        List<DashboardSnapshot.ExhibitLine> exhibits = new ArrayList<>();
        for (Exhibit exhibit : census.exhibits()) {
            Component label = speciesLabel(exhibit, census);
            boolean problem = exhibit.viewingProblem(now);
            if (problem) {
                alerts.add(Component.translatable("zoocraft.alert.cant_see", label));
            }
            exhibits.add(new DashboardSnapshot.ExhibitLine(label, exhibit.animals().size(), exhibit.viewCount(), problem));
        }
        exhibits.sort(Comparator.comparingInt(DashboardSnapshot.ExhibitLine::views).reversed());

        List<DashboardSnapshot.CommentLine> comments = zoo.stats().commentTally().entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_COMMENTS)
                .map(e -> {
                    CommentType type = CommentType.byKey(e.getKey());
                    Component text = type == null ? Component.literal(e.getKey()) : Component.translatable(type.translationKey());
                    return new DashboardSnapshot.CommentLine(text, e.getValue());
                })
                .toList();

        List<DashboardSnapshot.SpeciesLine> species = new ArrayList<>();
        ZooCensus finalCensus = census;
        census.speciesCounts().forEach((id, count) -> species.add(new DashboardSnapshot.SpeciesLine(
                finalCensus.speciesNames().getOrDefault(id, Component.literal(id)), count)));
        species.sort(Comparator.comparingInt(DashboardSnapshot.SpeciesLine::count).reversed());

        List<DashboardSnapshot.GateLine> gates = zoo.gates().stream()
                .map(g -> new DashboardSnapshot.GateLine(g.pos(), zoo.gateStatus(g, now).ordinal())).toList();
        List<DashboardSnapshot.ManagerLine> managers = zoo.managers().stream()
                .map(id -> new DashboardSnapshot.ManagerLine(id, PlayerNames.name(server, id))).toList();

        return new DashboardSnapshot(false, zoo.id(), zoo.name(), PlayerNames.name(server, zoo.owner()),
                zoo.owner().equals(viewer.getUUID()) || viewer.hasPermissions(2),
                zoo.open(), zoo.admissionPrice(), ZooManager.MAX_PRICE, zoo.keepLoaded(), ZooConfig.ALLOW_ZOO_CHUNK_LOADING.get(),
                zoo.ledger().balance(), zoo.ledger().totalAdmission(), zoo.stats().attendanceToday(), zoo.stats().totalAttendance(),
                zoo.stats().refusedAdmissions(), VisitorTracker.count(zoo.id()), zoo.satisfaction(), zoo.reputation(),
                alerts, comments, species, exhibits, gates, managers);
    }

    private static Component speciesLabel(Exhibit exhibit, ZooCensus census) {
        MutableComponent label = Component.empty();
        boolean first = true;
        for (String species : exhibit.species()) {
            if (!first) {
                label.append(", ");
            }
            label.append(census.speciesNames().getOrDefault(species, Component.literal(species)));
            first = false;
        }
        return label;
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        SUBSCRIPTIONS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SUBSCRIPTIONS.clear();
    }
}
