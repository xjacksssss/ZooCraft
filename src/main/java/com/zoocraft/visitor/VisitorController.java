package com.zoocraft.visitor;

import com.zoocraft.config.ZooConfig;
import com.zoocraft.core.needs.Errand;
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

    private final Visitor visitor;
    private boolean checkedLoad;

    // Movement tracking.
    private @Nullable BlockPos target;
    private double bestDistance = Double.MAX_VALUE;
    private long lastProgressTime;
    private int failedRoutes;
    private long lingerUntil;

    // Phase tracking.
    private long phaseStartTime;
    private @Nullable UUID exitGateId;
    private final Set<UUID> triedExitGates = new HashSet<>();
    private boolean saidClosed;

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
        BlockPos queueSpot = gate.get().outside();
        if (closeTo(queueSpot, 2.0)) {
            setPhase(VisitorPhase.QUEUEING);
            return;
        }
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
                failedRoutes = 0;
                target = null;
                if (errands.active()) {
                    errands.onUnreachable(zoo, now);
                } else {
                    VisitorExploration.onUnreachable(visitor, zoo);
                    visitor.changeInterest(-10);
                }
            }
            return;
        }
        if (target != null) {
            target = null;
            if (errands.active()) {
                lingerUntil = now + errands.onArrived(zoo, level, now);
            } else {
                // Arrived at an exhibit: stay a while before choosing somewhere else.
                lingerUntil = now + 100 + visitor.getRandom().nextInt(200);
                VisitorExploration.onArrived(visitor, zoo, now);
            }
            return;
        }
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
        return candidates.stream().min(Comparator.comparingDouble(g -> g.pos().distSqr(visitor.blockPosition()))).orElse(null);
    }

    private void stepWalkAway(ServerLevel level, @Nullable Zoo zoo, long now) {
        if (now - phaseStartTime > EXIT_WALK_TICKS) {
            visitor.releaseNow(null);
            return;
        }
        if (target == null) {
            target = awayPoint(level, zoo);
            bestDistance = Double.MAX_VALUE;
            lastProgressTime = now;
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
        onPhaseChanged();
    }

    private void setTarget(BlockPos pos) {
        target = pos;
        bestDistance = Double.MAX_VALUE;
        lastProgressTime = visitor.level().getGameTime();
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
        double distance = Math.sqrt(visitor.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
        if (distance < bestDistance - 0.5) {
            bestDistance = distance;
            lastProgressTime = now;
        }
        if (!BrainUtils.hasMemory(visitor, MemoryModuleType.WALK_TARGET)) {
            BrainUtils.setMemory(visitor, MemoryModuleType.WALK_TARGET, new WalkTarget(pos, WALK_SPEED, 1));
        }
        // A nearby target that path searches keep missing can't be reached (inside a pen, behind a wall): give up on
        // this route now rather than after the no-progress timeout, which costs a full search every few ticks.
        if (visitor.getNavigation() instanceof VisitorNavigation nav && nav.missesInARow(pos) >= MISSES_BEFORE_GIVING_UP) {
            nav.forgetMisses(pos);
            return routeFailed(now);
        }
        if (now - lastProgressTime > NO_PROGRESS_TICKS) {
            return routeFailed(now);
        }
        return false;
    }

    /** Counts a failed route and starts the next attempt afresh; true when the route has failed too often. */
    private boolean routeFailed(long now) {
        failedRoutes++;
        lastProgressTime = now;
        bestDistance = Double.MAX_VALUE;
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
    public @Nullable BlockPos target() {
        return target;
    }

    public long ticksWithoutProgress(long now) {
        return target == null ? 0 : now - lastProgressTime;
    }
}
