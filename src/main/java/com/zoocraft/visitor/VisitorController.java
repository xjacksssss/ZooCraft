package com.zoocraft.visitor;

import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.crowd.GateChoice;
import com.zoocraft.core.needs.Errand;
import com.zoocraft.core.visitor.Progress;
import com.zoocraft.core.visitor.StepRates;
import com.zoocraft.feedback.CommentType;
import com.zoocraft.zoo.Gate;
import com.zoocraft.zoo.Standing;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.tslat.smartbrainlib.util.BrainUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Runs one visitor's visit (spec sections 7.4 to 7.7). It only decides phases and walk targets;
 * SmartBrainLib behaviours do the walking. Runs its logic every {@code simulation.visitorStepTicks} ticks.
 */
public final class VisitorController {
    static final float WALK_SPEED = 0.6f;
    /** No progress for this long means the route has failed. */
    static final long NO_PROGRESS_TICKS = 30 * 20;
    static final int MAX_FAILED_ROUTES = 3;
    /** Plans in a row that can't reach a nearby target before the route counts as failed, without waiting. */
    static final int MISSES_BEFORE_GIVING_UP = 2;
    /** How long a visitor may spend trying to leave before it is released where it stands. */
    static final long LEAVE_SAFETY_TICKS = 60 * 20;
    static final long QUEUE_WAIT_TICKS = 20 * 20;
    static final long EXIT_WALK_TICKS = 20 * 20;
    static final double LOW_SATISFACTION = 15;
    static final double EXHIBIT_ARRIVAL = 2.0;
    static final double ERRAND_ARRIVAL = 1.5;
    /** No progress for this long with others pressing round means the visitor is hemmed in (Phase 3 spec section 7.4). */
    static final long CROWD_TICKS = 5 * 20;
    /** More than this many other visitors within a block is a crowd. */
    static final int CROWD_SIZE = 2;
    static final long STEP_ASIDE_TICKS = 3 * 20;
    static final int STEP_ASIDE_RADIUS = 3;
    /** Targets further than this share of the search range are walked via waypoints. */
    static final double WAYPOINT_SHARE = 0.75;
    /** How far ahead a waypoint is placed, and the sideways offsets tried when the straight line is blocked. */
    static final int WAYPOINT_AHEAD = 24;
    private static final int[] WAYPOINT_OFFSETS = {0, 4, -4, 8, -8, 12, -12, 16, -16};
    /** Within this of the waypoint, the next one is chosen. */
    static final double WAYPOINT_REACHED = 4.0;

    private final Visitor visitor;
    private boolean checkedLoad;

    // Movement tracking.
    private @Nullable BlockPos target;
    private final Progress progress = new Progress();
    // Stepping aside from a crowd: once per target, for up to STEP_ASIDE_TICKS.
    private boolean steppedAside;
    private long stepAsideUntil;
    private @Nullable BlockPos asideSpot;
    private int stepAsides;
    // Long walks go via waypoints (Phase 3 spec section 8.4).
    private @Nullable BlockPos waypoint;
    private int waypointTries;
    private int failedRoutes;
    private long lingerUntil;

    // Phase tracking.
    private long phaseStartTime;
    private @Nullable UUID exitGateId;
    private final Set<UUID> triedExitGates = new HashSet<>();
    private boolean saidClosed;
    // Gate queue (Phase 3 spec section 7.3).
    private @Nullable BlockPos queueSlot;
    private long overflowSince;

    VisitorController(Visitor visitor) {
        this.visitor = visitor;
    }

    void onLoaded() {
        checkedLoad = false;
    }

    /** Recomputes path rules and resets per-phase tracking. Call after every phase change. */
    void onPhaseChanged() {
        phaseStartTime = visitor.level().getGameTime();
        target = null;
        failedRoutes = 0;
        lingerUntil = 0;
        BrainUtils.clearMemory(visitor, MemoryModuleType.WALK_TARGET);
        visitor.pathRules = rulesFor(visitor.phase, visitor.zoo().orElse(null));
    }

    /** Called when the zoo's boundary or gates change. */
    public void onZooChanged() {
        visitor.pathRules = rulesFor(visitor.phase, visitor.zoo().orElse(null));
    }

    static PathRules rulesFor(VisitorPhase phase, @Nullable Zoo zoo) {
        if (zoo == null) {
            return PathRules.ANYWHERE;
        }
        List<BlockPos> gates = zoo.gates().stream().map(Gate::pos).toList();
        return switch (phase) {
            case EXPLORING, LEAVING -> new PathRules(zoo.shape(), PathRules.Mode.INSIDE, gates);
            case ARRIVING, QUEUEING, EXITING, REFUSED -> new PathRules(zoo.shape(), PathRules.Mode.OUTSIDE, gates);
        };
    }

    /**
     * True when a visitor that was saved at {@code lastSavedTime} has been away for longer than a visit can last.
     * Such visitors end their visit as soon as they load.
     */
    static boolean shouldExpireOnLoad(long lastSavedTime, long now, long maxVisitTicks) {
        return lastSavedTime >= 0 && now - lastSavedTime > maxVisitTicks;
    }

    /** The configured visitor step, in ticks. Read every tick, so a config change applies straight away. */
    static int step() {
        return ZooConfig.VISITOR_STEP_TICKS.get();
    }

    static long maxVisitTicks() {
        return ZooConfig.MAX_VISIT_MINUTES.get() * 60L * 20L;
    }

    void tick() {
        if (!(visitor.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (!checkedLoad) {
            checkedLoad = true;
            if (shouldExpireOnLoad(visitor.lastSavedTime, now, maxVisitTicks())) {
                visitor.expireQuietly();
                return;
            }
            onPhaseChanged();
        }
        int step = step();
        if ((now + Math.floorMod(visitor.getId(), step)) % step != 0 || visitor.zooId == null) {
            // Visitors without a zoo (from a spawn egg) just idle.
            return;
        }
        Optional<Zoo> maybeZoo = visitor.zoo();
        if (maybeZoo.isEmpty()) {
            if (visitor.phase == VisitorPhase.EXITING || visitor.phase == VisitorPhase.REFUSED) {
                stepWalkAway(level, null, now);
            } else {
                visitor.releaseNow(null);
            }
            return;
        }
        Zoo zoo = maybeZoo.get();
        switch (visitor.phase) {
            case ARRIVING -> stepArriving(level, zoo, now);
            case QUEUEING -> stepQueueing(level, zoo, now);
            case EXPLORING -> stepExploring(level, zoo, now);
            case LEAVING -> stepLeaving(level, zoo, now);
            case EXITING, REFUSED -> stepWalkAway(level, zoo, now);
        }
    }

    // ------------------------------------------------------------------ phases

    private void stepArriving(ServerLevel level, Zoo zoo, long now) {
        if (!zoo.open()) {
            setPhase(VisitorPhase.REFUSED);
            return;
        }
        Optional<Gate> gate = arrivalGate(zoo, now);
        if (gate.isEmpty()) {
            setPhase(VisitorPhase.REFUSED);
            return;
        }
        queueSlot = GateQueues.claim(level, zoo, gate.get(), visitor.getUUID(), leaderSlot(), now).orElse(null);
        BlockPos queueSpot = queueSlot != null ? queueSlot : GateQueues.overflow(level, zoo, gate.get());
        if (closeTo(queueSpot, 2.0)) {
            if (queueSlot != null) {
                setPhase(VisitorPhase.QUEUEING);
                return;
            }
            // Waiting beyond a full queue isn't a failure to reach the gate. After a while, queue from here.
            if (overflowSince == 0) {
                overflowSince = now;
            }
            target = null;
            BrainUtils.clearMemory(visitor, MemoryModuleType.WALK_TARGET);
            if (now - overflowSince > QUEUE_WAIT_TICKS * 2) {
                setPhase(VisitorPhase.QUEUEING);
            }
            return;
        }
        overflowSince = 0;
        if (walkTowards(queueSpot, now)) {
            // Could not get to this gate from outside: try another one.
            ZooManager.get(level.getServer()).markGateBlocked(zoo, gate.get());
            visitor.gateId = null;
            failedRoutes = 0;
            target = null;
        }
    }

    private Optional<Gate> arrivalGate(Zoo zoo, long now) {
        List<Gate> usable = zoo.usableGates(now);
        if (visitor.gateId != null) {
            for (Gate gate : usable) {
                if (gate.id().equals(visitor.gateId)) {
                    return Optional.of(gate);
                }
            }
        }
        Optional<Gate> nearest = usable.stream().min(Comparator.comparingDouble(g -> g.outside().distSqr(visitor.blockPosition())));
        nearest.ifPresent(g -> visitor.gateId = g.id());
        return nearest;
    }

    private void stepQueueing(ServerLevel level, Zoo zoo, long now) {
        if (!zoo.open()) {
            setPhase(VisitorPhase.REFUSED);
            return;
        }
        if (visitor.admitted) {
            enterZoo(zoo);
            return;
        }
        if (queueSlot != null && visitor.gateId != null) {
            zoo.gate(visitor.gateId).ifPresent(g -> GateQueues.claim(level, zoo, g, visitor.getUUID(), null, now));
        }
        List<Visitor> group = groupMembers();
        Visitor leader = group.stream().filter(Visitor::isLeader).findFirst().orElse(null);
        if (leader == null) {
            visitor.leader = true;
            leader = visitor;
        }
        if (leader != visitor) {
            // Followers wait for the leader's decision, but never forever.
            if (now - phaseStartTime > QUEUE_WAIT_TICKS * 2) {
                visitor.leader = true;
            }
            return;
        }
        boolean everyoneHere = group.stream().allMatch(v -> v.phase == VisitorPhase.QUEUEING || v.isRemoved());
        if (!everyoneHere && now - phaseStartTime < QUEUE_WAIT_TICKS) {
            return;
        }
        List<Visitor> deciding = group.stream().filter(v -> v.phase == VisitorPhase.QUEUEING && !v.isRemoved()).toList();
        boolean admitted = Admission.tryAdmitGroup(ZooManager.get(level.getServer()), zoo, deciding);
        for (Visitor member : deciding) {
            if (admitted) {
                member.controller.enterZoo(zoo);
            } else {
                member.controller.setPhase(VisitorPhase.REFUSED);
            }
        }
    }

    private void enterZoo(Zoo zoo) {
        setPhase(VisitorPhase.EXPLORING);
        zoo.gate(visitor.gateId == null ? new UUID(0, 0) : visitor.gateId).ifPresent(g -> setTarget(g.inside()));
    }

    private void stepExploring(ServerLevel level, Zoo zoo, long now) {
        if (!zoo.open()) {
            if (!saidClosed) {
                saidClosed = true;
                visitor.say(CommentType.ZOO_CLOSED);
            }
            setPhase(VisitorPhase.LEAVING);
            return;
        }
        if (!zoo.contains(visitor.blockPosition()) && !nearAnyGate(zoo, 3.5)) {
            // Pushed out, or the boundary moved: the visit is over and they head off from here.
            setPhase(VisitorPhase.EXITING);
            return;
        }
        if (visitor.satisfaction < LOW_SATISFACTION) {
            setPhase(VisitorPhase.LEAVING);
            return;
        }
        VisitorErrands errands = visitor.errands;
        if (errands.tickNeeds()) {
            // A need went unmet for too long (Phase 2 spec section 8.1).
            setPhase(VisitorPhase.LEAVING);
            return;
        }
        // Wanting to go home is a choice, so it goes through the planner, which may stop for a souvenir first.
        boolean endingByChoice = now - visitor.visitStartTime > maxVisitTicks() || visitor.interest <= 0 || errands.wantsToGoHome();
        visitor.changeInterest(-StepRates.interestDecay(step()));
        VisitorExploration.observe(visitor, zoo, now);
        VisitorExploration.noticeLitter(visitor, zoo, now);
        errands.tickHeld(level, now);
        errands.tickRubbish(zoo, level, now);
        errands.renewClaims(now);
        VisitorExploration.renewSpot(visitor, now);
        if (endingByChoice && target != null && !errands.active()) {
            // Don't finish the walk to another exhibit; decide how to leave now.
            target = null;
        }

        if (now < lingerUntil) {
            return;
        }
        // Shops, toilets and bins must be reached properly; from 2 blocks away a visitor could use one through a wall.
        double arrival = errands.active() ? ERRAND_ARRIVAL : EXHIBIT_ARRIVAL;
        if (target != null && !closeTo(target, arrival)) {
            if (walkTowards(target, now)) {
                BlockPos unreachable = target;
                failedRoutes = 0;
                target = null;
                if (errands.active()) {
                    errands.onUnreachable(zoo, now);
                } else {
                    VisitorExploration.onUnreachable(visitor, zoo, unreachable);
                    visitor.changeInterest(-10);
                }
            }
            return;
        }
        if (target != null) {
            if (errands.active()) {
                target = null;
                failedRoutes = 0; // arrived: earlier trouble on other walks doesn't count against the next one
                lingerUntil = now + errands.onArrived(zoo, level, now);
                return;
            }
            // Arrived at an exhibit (or a wander spot): stay a while, wait for room, or move to a free spot.
            VisitorExploration.Arrival reached = VisitorExploration.onArrived(visitor, zoo, now);
            if (reached.retarget() != null) {
                setTarget(reached.retarget());
                return;
            }
            if (!reached.keepTarget()) {
                target = null;
                failedRoutes = 0;
            }
            lingerUntil = now + reached.linger();
            return;
        }
        VisitorExploration.releaseSpot(visitor); // done here: choosing somewhere else
        VisitorErrands.Choice choice = errands.choose(zoo, level, now, endingByChoice);
        if (choice.errand() == Errand.LEAVE) {
            setPhase(VisitorPhase.LEAVING);
            return;
        }
        choice.target().ifPresent(this::setTarget);
    }

    private void stepLeaving(ServerLevel level, Zoo zoo, long now) {
        if (now - phaseStartTime > LEAVE_SAFETY_TICKS) {
            visitor.releaseNow(CommentType.COULD_NOT_LEAVE);
            return;
        }
        if (!zoo.contains(visitor.blockPosition()) && !nearAnyGate(zoo, 3.5)) {
            setPhase(VisitorPhase.EXITING);
            return;
        }
        Gate gate = exitGateId == null ? null : zoo.gate(exitGateId).orElse(null);
        if (gate == null) {
            gate = chooseExitGate(zoo, now);
            if (gate == null) {
                // Every gate has been tried: wait for the safety net, trying all gates again in case one reopens.
                triedExitGates.clear();
                return;
            }
            exitGateId = gate.id();
            target = null;
            failedRoutes = 0;
        }
        if (closeTo(gate.pos(), 1.5)) {
            setPhase(VisitorPhase.EXITING);
            return;
        }
        if (walkTowards(gate.pos(), now)) {
            ZooManager.get(level.getServer()).markGateBlocked(zoo, gate);
            triedExitGates.add(gate.id());
            exitGateId = null;
        }
    }

    private @Nullable Gate chooseExitGate(Zoo zoo, long now) {
        List<Gate> candidates = new ArrayList<>(zoo.usableGates(now));
        if (candidates.isEmpty()) {
            // A blocked gate is better than none.
            candidates.addAll(zoo.gates().stream().filter(g -> zoo.gateStatus(g, now) != com.zoocraft.zoo.GateStatus.DETACHED).toList());
        }
        candidates.removeIf(g -> triedExitGates.contains(g.id()));
        // The nearest gate, each gate's weight halving for every 8 visitors already heading to it (spec 7.3).
        double[] distances = new double[candidates.size()];
        int[] heading = new int[candidates.size()];
        List<Visitor> others = VisitorTracker.visitors(zoo.id());
        for (int i = 0; i < candidates.size(); i++) {
            Gate gate = candidates.get(i);
            distances[i] = Math.sqrt(gate.pos().distSqr(visitor.blockPosition()));
            for (Visitor other : others) {
                if (other != visitor && other.phase == VisitorPhase.LEAVING && gate.id().equals(other.controller.exitGateId)) {
                    heading[i]++;
                }
            }
        }
        int index = GateChoice.pick(distances, heading);
        return index < 0 ? null : candidates.get(index);
    }

    private void stepWalkAway(ServerLevel level, @Nullable Zoo zoo, long now) {
        if (now - phaseStartTime > EXIT_WALK_TICKS) {
            visitor.releaseNow(null);
            return;
        }
        if (target == null) {
            target = awayPoint(level, zoo);
            progress.onTarget(now);
            steppedAside = false;
            stepAsideUntil = 0;
            waypoint = null;
        }
        if (closeTo(target, 2.0)) {
            visitor.releaseNow(null);
            return;
        }
        if (walkTowards(target, now)) {
            visitor.releaseNow(null);
        }
    }

    private BlockPos awayPoint(ServerLevel level, @Nullable Zoo zoo) {
        double dx, dz;
        if (zoo != null) {
            double[] normal = zoo.shape().outwardNormalAt(visitor.getX(), visitor.getZ());
            dx = normal[0];
            dz = normal[1];
        } else {
            double angle = visitor.getRandom().nextDouble() * Math.PI * 2;
            dx = Math.cos(angle);
            dz = Math.sin(angle);
        }
        int x = (int) Math.floor(visitor.getX() + dx * 16);
        int z = (int) Math.floor(visitor.getZ() + dz * 16);
        if (!level.isLoaded(new BlockPos(x, 0, z))) {
            return new BlockPos(x, visitor.getBlockY(), z);
        }
        return Standing.findInColumn(level, x, visitor.getBlockY(), z, 6).orElse(new BlockPos(x, visitor.getBlockY(), z));
    }

    // ------------------------------------------------------------------ helpers

    void setPhase(VisitorPhase phase) {
        if (visitor.phase == phase) {
            return;
        }
        visitor.phase = phase;
        exitGateId = null;
        triedExitGates.clear();
        overflowSince = 0;
        VisitorExploration.releaseSpot(visitor);
        if (phase != VisitorPhase.QUEUEING) {
            GateQueues.release(visitor.getUUID());
            queueSlot = null;
        }
        onPhaseChanged();
    }

    private void setTarget(BlockPos pos) {
        if (visitor.getNavigation() instanceof VisitorNavigation nav) {
            nav.forgetAllMisses();
        }
        target = pos;
        progress.onTarget(visitor.level().getGameTime());
        steppedAside = false;
        stepAsideUntil = 0;
        waypoint = null;
        waypointTries = 0;
        BrainUtils.setMemory(visitor, MemoryModuleType.WALK_TARGET, new WalkTarget(pos, WALK_SPEED, 1));
    }

    /**
     * Keeps the visitor heading for {@code pos}, re-planning when it stops making progress.
     *
     * @return true when the route has failed {@link #MAX_FAILED_ROUTES} times in a row
     */
    private boolean walkTowards(BlockPos pos, long now) {
        if (!pos.equals(target)) {
            setTarget(pos);
        }
        if (stepAsideUntil != 0) {
            if (now < stepAsideUntil && !closeTo(asideSpot, 1.0)) {
                return false; // still stepping aside
            }
            // Back to the real target, with one fresh plan.
            stepAsideUntil = 0;
            progress.onStepAside(now);
            BrainUtils.clearMemory(visitor, MemoryModuleType.WALK_TARGET);
        }
        double distance = Math.sqrt(visitor.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        progress.update(distance, now);
        BlockPos walkTo = legTowards(pos, distance);
        WalkTarget current = BrainUtils.getMemory(visitor, MemoryModuleType.WALK_TARGET);
        if (current == null || !current.getTarget().currentBlockPosition().equals(walkTo)) {
            BrainUtils.setMemory(visitor, MemoryModuleType.WALK_TARGET, new WalkTarget(walkTo, WALK_SPEED, 1));
        }
        // A nearby target that path searches keep missing can't be reached (inside a pen, behind a wall): give up on
        // this route now rather than after the no-progress timeout, which costs a full search every few ticks.
        if (visitor.getNavigation() instanceof VisitorNavigation nav && nav.missesInARow(pos) >= MISSES_BEFORE_GIVING_UP) {
            nav.forgetMisses(pos);
            return routeFailed(now);
        }
        long stalled = progress.ticksWithoutProgress(now);
        // Hemmed in by other visitors: step aside and try again before counting a failure, so crowding alone never
        // marks an exhibit or facility unreachable (spec 7.4). The entity check only runs once a visitor has stalled.
        if (!steppedAside && stalled > CROWD_TICKS && hemmedIn()) {
            stepAside(now);
            return false;
        }
        if (stalled > NO_PROGRESS_TICKS) {
            return routeFailed(now);
        }
        return false;
    }

    /**
     * Where to walk now: the target itself when one search reaches it, otherwise a waypoint about 24 blocks closer.
     * A partial path towards a far target heads for whatever reachable block lies nearest it, which in a park full of
     * pens is often a dead end against a fence; waypoints are standable blocks outside pens, paths preferred, so each
     * leg is a short plan that arrives (spec 8.4).
     */
    private BlockPos legTowards(BlockPos pos, double distance) {
        if (distance <= ZooConfig.VISITOR_FOLLOW_RANGE.get() * WAYPOINT_SHARE || !(visitor.level() instanceof ServerLevel level)) {
            waypoint = null;
            return pos;
        }
        VisitorNavigation nav = visitor.getNavigation() instanceof VisitorNavigation n ? n : null;
        boolean missed = waypoint != null && nav != null && nav.missesInARow(waypoint) >= MISSES_BEFORE_GIVING_UP;
        if (waypoint == null || missed || closeTo(waypoint, WAYPOINT_REACHED)) {
            if (missed) {
                nav.forgetMisses(waypoint);
                waypointTries++; // start the sideways search further along next time
            }
            waypoint = pickWaypoint(level, pos);
        }
        return waypoint != null ? waypoint : pos;
    }

    private @Nullable BlockPos pickWaypoint(ServerLevel level, BlockPos pos) {
        double dx = pos.getX() + 0.5 - visitor.getX(), dz = pos.getZ() + 0.5 - visitor.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1) {
            return null;
        }
        dx /= length;
        dz /= length;
        Zoo zoo = visitor.zoo().orElse(null);
        boolean inside = zoo != null && (visitor.phase == VisitorPhase.EXPLORING || visitor.phase == VisitorPhase.LEAVING);
        BlockPos fallback = null;
        for (int i = 0; i < WAYPOINT_OFFSETS.length; i++) {
            int offset = WAYPOINT_OFFSETS[(i + waypointTries) % WAYPOINT_OFFSETS.length];
            int x = (int) Math.floor(visitor.getX() + dx * WAYPOINT_AHEAD - dz * offset);
            int z = (int) Math.floor(visitor.getZ() + dz * WAYPOINT_AHEAD + dx * offset);
            if (!level.isLoaded(new BlockPos(x, 0, z)) || zoo != null && com.zoocraft.animal.AnimalCensus.insidePen(zoo, x, z)) {
                continue;
            }
            Optional<BlockPos> feet = Standing.findInColumn(level, x, visitor.getBlockY(), z, 4);
            if (feet.isEmpty() || inside && !goodSpot(level, zoo, feet.get()) || !level.getFluidState(feet.get()).isEmpty()) {
                continue;
            }
            if (level.getBlockState(feet.get().below()).is(com.zoocraft.registry.ZooTags.VISITOR_PATHS)) {
                return feet.get();
            }
            if (fallback == null) {
                fallback = feet.get();
            }
        }
        return fallback;
    }

    private boolean hemmedIn() {
        return visitor.level().getEntitiesOfClass(Visitor.class, visitor.getBoundingBox().inflate(1.0), v -> v != visitor).size() > CROWD_SIZE;
    }

    /** Walks to a random standable block within a few blocks (inside the zoo while visiting), then back to the target. */
    private void stepAside(long now) {
        steppedAside = true;
        if (!(visitor.level() instanceof ServerLevel level)) {
            return;
        }
        Zoo zoo = visitor.zoo().orElse(null);
        boolean inside = zoo != null && (visitor.phase == VisitorPhase.EXPLORING || visitor.phase == VisitorPhase.LEAVING);
        for (int i = 0; i < 10; i++) {
            int x = visitor.getBlockX() + visitor.getRandom().nextInt(STEP_ASIDE_RADIUS * 2 + 1) - STEP_ASIDE_RADIUS;
            int z = visitor.getBlockZ() + visitor.getRandom().nextInt(STEP_ASIDE_RADIUS * 2 + 1) - STEP_ASIDE_RADIUS;
            if (!level.isLoaded(new BlockPos(x, 0, z))) {
                continue;
            }
            Optional<BlockPos> feet = Standing.findInColumn(level, x, visitor.getBlockY(), z, 2);
            if (feet.isPresent() && !feet.get().equals(visitor.blockPosition()) && (!inside || goodSpot(level, zoo, feet.get()))) {
                asideSpot = feet.get();
                stepAsideUntil = now + STEP_ASIDE_TICKS;
                stepAsides++;
                visitor.getNavigation().stop();
                BrainUtils.setMemory(visitor, MemoryModuleType.WALK_TARGET, new WalkTarget(asideSpot, WALK_SPEED, 0));
                return;
            }
        }
    }

    /** Counts a failed route and starts the next attempt afresh; true when the route has failed too often. */
    private boolean routeFailed(long now) {
        failedRoutes++;
        progress.onRouteFailed(now);
        visitor.getNavigation().stop();
        BrainUtils.clearMemory(visitor, MemoryModuleType.WALK_TARGET);
        return failedRoutes >= MAX_FAILED_ROUTES;
    }

    private boolean closeTo(BlockPos pos, double distance) {
        double dx = visitor.getX() - (pos.getX() + 0.5);
        double dz = visitor.getZ() - (pos.getZ() + 0.5);
        double dy = Math.abs(visitor.getY() - pos.getY());
        return dx * dx + dz * dz <= distance * distance && dy <= 2.5;
    }

    private boolean nearAnyGate(Zoo zoo, double distance) {
        for (Gate gate : zoo.gates()) {
            if (closeTo(gate.pos(), distance)) {
                return true;
            }
        }
        return false;
    }

    private List<Visitor> groupMembers() {
        if (visitor.groupId == null || visitor.zooId == null) {
            return List.of(visitor);
        }
        List<Visitor> members = new ArrayList<>();
        for (Visitor other : VisitorTracker.visitors(visitor.zooId)) {
            if (visitor.groupId.equals(other.groupId) && !other.isRemoved()) {
                members.add(other);
            }
        }
        if (!members.contains(visitor)) {
            members.add(visitor);
        }
        return members;
    }

    /** True when the standing spot is usable for exploring. */
    static boolean goodSpot(ServerLevel level, Zoo zoo, BlockPos feet) {
        return level.isLoaded(feet) && zoo.contains(feet) && Standing.isStandable(level, feet)
                && level.getFluidState(feet).isEmpty();
    }

    // Exposed for tests and debugging.
    /** The group leader's queue slot, so the group queues side by side; null for leaders and lone visitors. */
    private @Nullable BlockPos leaderSlot() {
        if (visitor.leader) {
            return null;
        }
        for (Visitor member : groupMembers()) {
            if (member != visitor && member.leader) {
                return member.controller.queueSlot;
            }
        }
        return null;
    }

    /** The gate queue slot this visitor holds, if any. */
    public @Nullable BlockPos queueSlot() {
        return queueSlot;
    }

    /** The gate this visitor is leaving by, if it has chosen one. */
    public @Nullable UUID exitGateId() {
        return exitGateId;
    }

    public @Nullable BlockPos target() {
        return target;
    }

    /** The waypoint on a long walk, if the visitor is using one (for tests and debugging). */
    public @Nullable BlockPos waypoint() {
        return waypoint;
    }

    /** Times this visitor has stepped aside from a crowd (for tests and debugging). */
    public int stepAsides() {
        return stepAsides;
    }

    public int failedRoutesForTest() {
        return failedRoutes;
    }

    /** GameTests only: heads for {@code pos} as an exploring visitor would, with no exhibit or errand. */
    public void testWalkTo(BlockPos pos) {
        setTarget(pos);
    }

    /** How long the visitor has been no closer to its target, through retries and steps aside; 0 with no target. */
    public long stuckTicks(long now) {
        return target == null ? 0 : progress.stuckTicks(now);
    }

    public long ticksWithoutProgress(long now) {
        return target == null ? 0 : progress.ticksWithoutProgress(now);
    }
}
