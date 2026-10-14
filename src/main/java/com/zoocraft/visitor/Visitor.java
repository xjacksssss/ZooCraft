package com.zoocraft.visitor;

import com.zoocraft.core.visitor.VisitorProfile;
import com.zoocraft.feedback.CommentType;
import com.zoocraft.zoo.Gate;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.tslat.smartbrainlib.api.SmartBrainOwner;
import net.tslat.smartbrainlib.api.core.BrainActivityGroup;
import net.tslat.smartbrainlib.api.core.SmartBrainProvider;
import net.tslat.smartbrainlib.api.core.behaviour.FirstApplicableBehaviour;
import net.tslat.smartbrainlib.api.core.behaviour.custom.look.LookAtTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.FloatToSurfaceOfFluid;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.MoveToWalkTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.SetPlayerLookTarget;
import net.tslat.smartbrainlib.api.core.behaviour.custom.target.SetRandomLookTarget;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyLivingEntitySensor;
import net.tslat.smartbrainlib.api.core.sensor.vanilla.NearbyPlayersSensor;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A zoo visitor: a walking villager with its own budget, profile and mood (spec section 7.1).
 * Movement, doors and looking around come from SmartBrainLib behaviours; the visit itself is run by
 * {@link VisitorController}.
 */
public class Visitor extends PathfinderMob implements SmartBrainOwner<Visitor>, VillagerDataHolder {
    private static final EntityDataAccessor<VillagerData> DATA_VILLAGER = SynchedEntityData.defineId(Visitor.class, EntityDataSerializers.VILLAGER_DATA);
    private static final EntityDataAccessor<String> DATA_COMMENT = SynchedEntityData.defineId(Visitor.class, EntityDataSerializers.STRING);
    private static final int COMMENT_TICKS = 80;

    // Saved state.
    UUID visitId = UUID.randomUUID();
    @Nullable UUID zooId;
    @Nullable UUID groupId;
    boolean leader = true;
    VisitorProfile profile = VisitorProfile.GENERAL;
    int budget;
    double personalFactor = 1.0;
    boolean admitted;
    VisitorPhase phase = VisitorPhase.ARRIVING;
    @Nullable UUID gateId;
    double satisfaction = 50;
    double interest = 100;
    long visitStartTime;
    long lastSavedTime = -1;
    final Set<String> seenSpecies = new HashSet<>();

    // Runtime only.
    final VisitorController controller = new VisitorController(this);
    PathRules pathRules = PathRules.ANYWHERE;
    private int commentTicks;
    private boolean departed;
    /** Shown comments are rate-limited so visitors don't chatter constantly; the effects always apply. */
    private long lastShownComment = Long.MIN_VALUE / 2;
    private static final long COMMENT_GAP_TICKS = 15 * 20;
    // Exhibit tracking for this visit.
    @Nullable UUID currentExhibit;
    final java.util.Deque<UUID> recentExhibits = new java.util.ArrayDeque<>();
    final Set<UUID> exhibitsViewed = new HashSet<>();
    int repeatVisits;
    final java.util.EnumSet<CommentType> saidOnce = java.util.EnumSet.noneOf(CommentType.class);

    public Visitor(EntityType<? extends Visitor> type, Level level) {
        super(type, level);
        setCanPickUpLoot(false);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    /** Sets up a freshly spawned visitor. */
    public void begin(Zoo zoo, Gate gate, UUID groupId, boolean leader) {
        this.zooId = zoo.id();
        this.gateId = gate.id();
        this.groupId = groupId;
        this.leader = leader;
        java.util.Random random = new java.util.Random(getRandom().nextLong());
        this.profile = VisitorProfile.roll(random);
        this.budget = profile.rollBudget(random);
        this.personalFactor = VisitorProfile.rollPersonalFactor(random);
        this.visitStartTime = level().getGameTime();
        this.phase = VisitorPhase.ARRIVING;
        if (level() instanceof ServerLevel serverLevel) {
            setVillagerData(getVillagerData().setType(VillagerType.byBiome(serverLevel.getBiome(blockPosition()))));
        }
        controller.onPhaseChanged();
    }

    // ------------------------------------------------------------------ brain

    @Override
    protected Brain.Provider<?> brainProvider() {
        return new SmartBrainProvider<>(this);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new VisitorNavigation(this, level);
    }

    @Override
    public List<? extends ExtendedSensor<? extends Visitor>> getSensors() {
        return List.of(
                new NearbyPlayersSensor<>(),
                new NearbyLivingEntitySensor<Visitor>().setPredicate((target, self) -> target instanceof Visitor));
    }

    @Override
    public BrainActivityGroup<? extends Visitor> getCoreTasks() {
        return BrainActivityGroup.coreTasks(
                new FloatToSurfaceOfFluid<>(),
                new LookAtTarget<>(),
                new MoveToWalkTarget<>(),
                new OpenDoorsAndGates<>());
    }

    @Override
    public BrainActivityGroup<? extends Visitor> getIdleTasks() {
        return BrainActivityGroup.idleTasks(
                new FirstApplicableBehaviour<Visitor>(
                        new SetPlayerLookTarget<>(),
                        new SetRandomLookTarget<>()));
    }

    @Override
    protected void customServerAiStep() {
        tickBrain(this);
        controller.tick();
        if (commentTicks > 0 && --commentTicks == 0) {
            entityData.set(DATA_COMMENT, "");
        }
    }

    // ------------------------------------------------------------------ visit API

    public Optional<Zoo> zoo() {
        if (zooId == null || level().getServer() == null) {
            return Optional.empty();
        }
        return ZooManager.get(level().getServer()).byId(zooId);
    }

    public @Nullable UUID zooId() {
        return zooId;
    }

    public UUID visitId() {
        return visitId;
    }

    public @Nullable UUID groupId() {
        return groupId;
    }

    public boolean isLeader() {
        return leader;
    }

    public VisitorProfile profile() {
        return profile;
    }

    public int budget() {
        return budget;
    }

    public double personalFactor() {
        return personalFactor;
    }

    public boolean admitted() {
        return admitted;
    }

    public VisitorPhase phase() {
        return phase;
    }

    public double satisfaction() {
        return satisfaction;
    }

    public double interest() {
        return interest;
    }

    public Set<String> seenSpecies() {
        return seenSpecies;
    }

    public PathRules pathRules() {
        return pathRules;
    }

    public VisitorController controller() {
        return controller;
    }

    /** Called by admission: pay from this visitor's own budget. */
    void markAdmitted(int pricePaid) {
        budget = Math.max(0, budget - pricePaid);
        admitted = true;
    }

    void changeSatisfaction(double delta) {
        satisfaction = Math.max(0, Math.min(100, satisfaction + delta));
    }

    void changeInterest(double delta) {
        interest = Math.max(0, Math.min(100, interest + delta));
    }

    /** Shows a comment above the visitor's head, applies its mood effect and tallies it on the zoo. */
    public void say(CommentType type) {
        lastShownComment = level().getGameTime();
        entityData.set(DATA_COMMENT, type.translationKey());
        commentTicks = COMMENT_TICKS;
        react(type);
    }

    /** Like {@link #say}, but only shows the text if the visitor hasn't spoken recently. The effects always apply. */
    public void remark(CommentType type) {
        if (level().getGameTime() - lastShownComment >= COMMENT_GAP_TICKS) {
            say(type);
        } else {
            react(type);
        }
    }

    /** {@link #remark} at most once per visit. */
    public void remarkOnce(CommentType type) {
        if (saidOnce.add(type)) {
            remark(type);
        }
    }

    private void react(CommentType type) {
        changeSatisfaction(type.satisfactionEffect());
        if (level().getServer() != null) {
            zoo().ifPresent(zoo -> ZooManager.get(level().getServer()).recordComment(zoo, type.key()));
        }
    }

    public String currentCommentKey() {
        return entityData.get(DATA_COMMENT);
    }

    /**
     * Ends the visit now: optionally says why, reports the visitor's final mood to the zoo if it was admitted,
     * and fades out with a puff of particles.
     */
    public void releaseNow(@Nullable CommentType reason) {
        if (departed) {
            return;
        }
        departed = true;
        if (reason != null) {
            say(reason);
        }
        if (admitted && level().getServer() != null) {
            zoo().ifPresent(zoo -> ZooManager.get(level().getServer()).recordDeparture(zoo, satisfaction));
        }
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF, getX(), getY() + 1, getZ(), 12, 0.3, 0.5, 0.3, 0.02);
        }
        discard();
    }

    /** Removes the visitor without affecting ratings (used when its visit has long expired while unloaded). */
    void expireQuietly() {
        departed = true;
        discard();
    }

    // ------------------------------------------------------------------ test hooks (GameTests only)

    public void testSetBudget(VisitorProfile profile, int budget, double personalFactor) {
        this.profile = profile;
        this.budget = budget;
        this.personalFactor = personalFactor;
    }

    public void testSetPhase(VisitorPhase phase) {
        if (phase == VisitorPhase.EXPLORING || phase == VisitorPhase.LEAVING) {
            admitted = true;
        }
        controller.setPhase(phase);
    }

    // ------------------------------------------------------------------ entity behaviour

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_VILLAGER, new VillagerData(VillagerType.PLAINS, VillagerProfession.NONE, 1));
        builder.define(DATA_COMMENT, "");
    }

    @Override
    public VillagerData getVillagerData() {
        return entityData.get(DATA_VILLAGER);
    }

    @Override
    public void setVillagerData(VillagerData data) {
        entityData.set(DATA_VILLAGER, data);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean startRiding(Entity vehicle, boolean force) {
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            player.sendSystemMessage(Component.translatable("zoocraft.visitor.info",
                    Component.translatable("zoocraft.profile." + profile.name().toLowerCase(java.util.Locale.ROOT)),
                    budget, Math.round(satisfaction), Component.translatable("zoocraft.phase." + phase.name().toLowerCase(java.util.Locale.ROOT)))
                    .withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // ------------------------------------------------------------------ saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag visit = new CompoundTag();
        visit.putUUID("visitId", visitId);
        if (zooId != null) visit.putUUID("zooId", zooId);
        if (groupId != null) visit.putUUID("groupId", groupId);
        if (gateId != null) visit.putUUID("gateId", gateId);
        visit.putBoolean("leader", leader);
        visit.putString("profile", profile.name());
        visit.putInt("budget", budget);
        visit.putDouble("personalFactor", personalFactor);
        visit.putBoolean("admitted", admitted);
        visit.putString("phase", phase.name());
        visit.putDouble("satisfaction", satisfaction);
        visit.putDouble("interest", interest);
        visit.putLong("visitStartTime", visitStartTime);
        visit.putLong("lastSavedTime", level().getGameTime());
        ListTag species = new ListTag();
        seenSpecies.forEach(s -> species.add(StringTag.valueOf(s)));
        visit.put("seenSpecies", species);
        tag.put("ZooVisit", visit);
        VillagerData.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, getVillagerData()).result().ifPresent(t -> tag.put("VillagerData", t));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        CompoundTag visit = tag.getCompound("ZooVisit");
        if (visit.hasUUID("visitId")) visitId = visit.getUUID("visitId");
        zooId = visit.hasUUID("zooId") ? visit.getUUID("zooId") : null;
        groupId = visit.hasUUID("groupId") ? visit.getUUID("groupId") : null;
        gateId = visit.hasUUID("gateId") ? visit.getUUID("gateId") : null;
        leader = visit.getBoolean("leader");
        try {
            profile = VisitorProfile.valueOf(visit.getString("profile"));
        } catch (IllegalArgumentException e) {
            profile = VisitorProfile.GENERAL;
        }
        budget = visit.getInt("budget");
        personalFactor = visit.contains("personalFactor") ? visit.getDouble("personalFactor") : 1.0;
        admitted = visit.getBoolean("admitted");
        try {
            phase = VisitorPhase.valueOf(visit.getString("phase"));
        } catch (IllegalArgumentException e) {
            phase = admitted ? VisitorPhase.LEAVING : VisitorPhase.REFUSED;
        }
        satisfaction = visit.contains("satisfaction") ? visit.getDouble("satisfaction") : 50;
        interest = visit.contains("interest") ? visit.getDouble("interest") : 100;
        visitStartTime = visit.getLong("visitStartTime");
        lastSavedTime = visit.contains("lastSavedTime") ? visit.getLong("lastSavedTime") : -1;
        seenSpecies.clear();
        visit.getList("seenSpecies", Tag.TAG_STRING).forEach(t -> seenSpecies.add(t.getAsString()));
        if (tag.contains("VillagerData")) {
            VillagerData.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("VillagerData")).result().ifPresent(this::setVillagerData);
        }
        controller.onLoaded();
    }
}
