package com.zoocraft.shop;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;
import java.util.Optional;

/** Finds item storage at a position and treats both halves of a double chest as one container. */
public final class ContainerPositions {
    private ContainerPositions() {
    }

    /** The item handler at a loaded position, from any mod that registers the NeoForge capability. */
    public static Optional<IItemHandler> handler(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return Optional.empty();
        }
        return Optional.ofNullable(level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null));
    }

    public static boolean hasItemHandler(Level level, BlockPos pos) {
        return handler(level, pos).isPresent();
    }

    /** The positions that make up the container at {@code pos}: two for a double chest, otherwise one. */
    public static List<BlockPos> parts(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            if (level.getBlockState(other).getBlock() instanceof ChestBlock) {
                return List.of(pos.immutable(), other.immutable());
            }
        }
        return List.of(pos.immutable());
    }
}
