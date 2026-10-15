package com.zoocraft.litter;

import com.zoocraft.config.ZooConfig;
import com.zoocraft.registry.ZooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** How full a rubbish bin is. Bins inside a zoo are found by the facility census; nothing registers them. */
public class RubbishBinBlockEntity extends BlockEntity {
    private int fill;

    public RubbishBinBlockEntity(BlockPos pos, BlockState state) {
        super(ZooBlocks.RUBBISH_BIN_ENTITY.get(), pos, state);
    }

    public int fill() {
        return fill;
    }

    public static int capacity() {
        return ZooConfig.BIN_CAPACITY.get();
    }

    public boolean full() {
        return fill >= capacity();
    }

    /** Adds one piece of rubbish; false, changing nothing, when the bin is full. */
    public boolean deposit() {
        if (full()) {
            return false;
        }
        fill++;
        changed();
        return true;
    }

    public void empty() {
        fill = 0;
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            int stage = fill == 0 ? 0 : Math.min(3, (int) Math.ceil(fill * 3.0 / capacity()));
            BlockState state = getBlockState();
            if (state.hasProperty(RubbishBinBlock.FILL) && state.getValue(RubbishBinBlock.FILL) != stage) {
                level.setBlock(worldPosition, state.setValue(RubbishBinBlock.FILL, stage), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("fill", fill);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fill = tag.getInt("fill");
    }
}
