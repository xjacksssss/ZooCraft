package com.zoocraft.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zoocraft.core.geometry.BoundaryValidator;
import com.zoocraft.core.geometry.ZooShape;
import com.zoocraft.core.ledger.Ledger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A zoo: a server-side record separate from the blocks inside it (spec section 6.1).
 * Read freely; change only through {@link ZooManager}, which keeps the index, saved data and listeners in step.
 */
public final class Zoo {
    /** Gates further than this from the boundary edge are detached. */
    public static final double GATE_EDGE_DISTANCE = 3.0;

    public static final Codec<Zoo> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Zoo::id),
            Codec.STRING.fieldOf("name").forGetter(Zoo::name),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Zoo::dimension),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Zoo::owner),
            UUIDUtil.CODEC.listOf().fieldOf("managers").forGetter(z -> List.copyOf(z.managers)),
            BoundaryVertex.CODEC.listOf().fieldOf("boundary").forGetter(Zoo::boundary),
            Gate.CODEC.listOf().fieldOf("gates").forGetter(Zoo::gates),
            Codec.BOOL.fieldOf("open").forGetter(Zoo::open),
            Codec.INT.fieldOf("admissionPrice").forGetter(Zoo::admissionPrice),
            Codec.BOOL.fieldOf("keepLoaded").forGetter(Zoo::keepLoaded),
            ZooCodecs.LEDGER.fieldOf("ledger").forGetter(Zoo::ledger),
            ZooStats.CODEC.fieldOf("stats").forGetter(Zoo::stats),
            Codec.FLOAT.fieldOf("satisfaction").forGetter(Zoo::satisfaction),
            Codec.FLOAT.fieldOf("reputation").forGetter(Zoo::reputation)
    ).apply(i, Zoo::new));

    private final UUID id;
    private String name;
    private final ResourceKey<Level> dimension;
    private UUID owner;
    private final Set<UUID> managers;
    private List<BoundaryVertex> boundary;
    private final List<Gate> gates;
    private boolean open;
    private int admissionPrice;
    private boolean keepLoaded;
    private final Ledger ledger;
    private final ZooStats stats;
    private float satisfaction;
    private float reputation;

    // Runtime only, not saved.
    private ZooShape shape;
    private final Map<UUID, Long> gateBlockedUntil = new HashMap<>();

    private Zoo(UUID id, String name, ResourceKey<Level> dimension, UUID owner, List<UUID> managers,
                List<BoundaryVertex> boundary, List<Gate> gates, boolean open, int admissionPrice, boolean keepLoaded,
                Ledger ledger, ZooStats stats, float satisfaction, float reputation) {
        this.id = id;
        this.name = name;
        this.dimension = dimension;
        this.owner = owner;
        this.managers = new LinkedHashSet<>(managers);
        this.boundary = List.copyOf(boundary);
        this.gates = new ArrayList<>(gates);
        this.open = open;
        this.admissionPrice = admissionPrice;
        this.keepLoaded = keepLoaded;
        this.ledger = ledger;
        this.stats = stats;
        this.satisfaction = satisfaction;
        this.reputation = reputation;
    }

    static Zoo create(String name, ResourceKey<Level> dimension, UUID owner, List<BoundaryVertex> boundary) {
        return new Zoo(UUID.randomUUID(), name, dimension, owner, List.of(), boundary, List.of(), false, 2, false,
                new Ledger(), new ZooStats(), 50f, 50f);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public UUID owner() {
        return owner;
    }

    public Set<UUID> managers() {
        return Set.copyOf(managers);
    }

    public boolean isOwnerOrManager(UUID player) {
        return owner.equals(player) || managers.contains(player);
    }

    public List<BoundaryVertex> boundary() {
        return boundary;
    }

    public ZooShape shape() {
        if (shape == null) {
            shape = ZooShape.of(BoundaryValidator.normalise(boundary.stream().map(BoundaryVertex::toVertex).toList()));
        }
        return shape;
    }

    public boolean contains(BlockPos pos) {
        return shape().containsBlock(pos.getX(), pos.getZ());
    }

    public List<Gate> gates() {
        return List.copyOf(gates);
    }

    public Optional<Gate> gate(UUID gateId) {
        return gates.stream().filter(g -> g.id().equals(gateId)).findFirst();
    }

    public GateStatus gateStatus(Gate gate, long gameTime) {
        if (shape().distanceToEdge(gate.pos().getX() + 0.5, gate.pos().getZ() + 0.5) > GATE_EDGE_DISTANCE) {
            return GateStatus.DETACHED;
        }
        Long until = gateBlockedUntil.get(gate.id());
        if (until != null && until > gameTime) {
            return GateStatus.BLOCKED;
        }
        return GateStatus.OPEN;
    }

    /** Gates visitors may use right now. */
    public List<Gate> usableGates(long gameTime) {
        return gates.stream().filter(g -> gateStatus(g, gameTime) == GateStatus.OPEN).toList();
    }

    public boolean open() {
        return open;
    }

    public int admissionPrice() {
        return admissionPrice;
    }

    public boolean keepLoaded() {
        return keepLoaded;
    }

    public Ledger ledger() {
        return ledger;
    }

    public ZooStats stats() {
        return stats;
    }

    public float satisfaction() {
        return satisfaction;
    }

    public float reputation() {
        return reputation;
    }

    // Mutators, package-private: only ZooManager calls these.

    void setName(String name) {
        this.name = name;
    }

    void setOwner(UUID owner) {
        this.owner = owner;
        this.managers.remove(owner);
    }

    void addManager(UUID manager) {
        managers.add(manager);
    }

    boolean removeManager(UUID manager) {
        return managers.remove(manager);
    }

    void setBoundary(List<BoundaryVertex> boundary) {
        this.boundary = List.copyOf(boundary);
        this.shape = null;
    }

    void addGate(Gate gate) {
        gates.add(gate);
    }

    boolean removeGate(UUID gateId) {
        gateBlockedUntil.remove(gateId);
        return gates.removeIf(g -> g.id().equals(gateId));
    }

    void markGateBlocked(UUID gateId, long untilGameTime) {
        gateBlockedUntil.put(gateId, untilGameTime);
    }

    void setOpen(boolean open) {
        this.open = open;
    }

    void setAdmissionPrice(int admissionPrice) {
        this.admissionPrice = admissionPrice;
    }

    void setKeepLoaded(boolean keepLoaded) {
        this.keepLoaded = keepLoaded;
    }

    void setSatisfaction(float satisfaction) {
        this.satisfaction = satisfaction;
    }

    void setReputation(float reputation) {
        this.reputation = reputation;
    }
}
