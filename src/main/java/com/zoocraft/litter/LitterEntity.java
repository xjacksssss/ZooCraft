package com.zoocraft.litter;

import com.zoocraft.registry.ZooItems;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

/**
 * A piece of litter (Phase 2 spec section 9.3): a small non-living entity, so it never changes the player's build,
 * never despawns and isn't vacuumed up by hoppers or magnets. Anyone collects it by hitting or using it. A pile counts
 * for up to four pieces.
 */
public class LitterEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(LitterEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PILE = SynchedEntityData.defineId(LitterEntity.class, EntityDataSerializers.INT);

    /** Client only: whether this litter should glow for the local player. Set by the client setup. */
    public static Predicate<LitterEntity> glowOnClient = litter -> false;

    public LitterEntity(EntityType<? extends LitterEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_VARIANT, 0);
        builder.define(DATA_PILE, 1);
    }

    public int variant() {
        return entityData.get(DATA_VARIANT);
    }

    public void setVariant(int variant) {
        entityData.set(DATA_VARIANT, Math.floorMod(variant, ZooItems.RUBBISH_VARIANTS));
    }

    public int pile() {
        return entityData.get(DATA_PILE);
    }

    public void setPile(int pile) {
        entityData.set(DATA_PILE, Math.max(1, pile));
    }

    public ItemStack item() {
        return new ItemStack(ZooItems.rubbish(variant()));
    }

    @Override
    public void tick() {
        super.tick();
        // Falls and settles like a dropped item, but never despawns.
        if (!onGround() || getDeltaMovement().lengthSqr() > 1.0e-6) {
            if (!isNoGravity()) {
                setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
            }
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().multiply(0.6, 0.98, 0.6));
            if (onGround()) {
                setDeltaMovement(getDeltaMovement().multiply(1, -0.5, 1));
            }
        }
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!level().isClientSide && !isRemoved() && source.getEntity() instanceof Player) {
            collect();
            return true;
        }
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!level().isClientSide && !isRemoved()) {
            collect();
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Picked up by a player: gone, and counted by the zoo it lies in. */
    private void collect() {
        if (level().getServer() != null) {
            ZooManager zoos = ZooManager.get(level().getServer());
            zoos.zooAt(level(), blockPosition()).ifPresent(zoos::recordLitterCollected);
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 0.8f + random.nextFloat() * 0.4f);
        discard();
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return (level().isClientSide && glowOnClient.test(this)) || super.isCurrentlyGlowing();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("variant", variant());
        tag.putInt("pile", pile());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setVariant(tag.getInt("variant"));
        setPile(tag.contains("pile") ? tag.getInt("pile") : 1);
    }

    /** Where to put a new piece: on the spot, turned at random. */
    void place(Vec3 at, int variant) {
        moveTo(at.x, at.y + 0.05, at.z, random.nextFloat() * 360f, 0);
        setVariant(variant);
    }
}
