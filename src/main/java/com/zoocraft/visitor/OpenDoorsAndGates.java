package com.zoocraft.visitor;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.tslat.smartbrainlib.api.core.behaviour.custom.move.InteractWithDoor;
import net.tslat.smartbrainlib.util.BrainUtils;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/** SmartBrainLib's door behaviour, extended so visitors also open and close fence gates on their path. */
public class OpenDoorsAndGates<E extends LivingEntity> extends InteractWithDoor<E> {
    @Override
    protected boolean isInteractableDoor(BlockState state) {
        return super.isInteractableDoor(state) || state.getBlock() instanceof FenceGateBlock;
    }

    @Override
    protected void tryOpenDoor(ServerLevel level, E entity, BlockState state, BlockPos pos) {
        boolean opened = false;
        if (state.getBlock() instanceof FenceGateBlock) {
            if (!state.getValue(FenceGateBlock.OPEN)) {
                setGateOpen(level, entity, state, pos, true);
                opened = true;
            }
        } else if (state.getBlock() instanceof DoorBlock door && !door.isOpen(state)) {
            door.setOpen(entity, level, state, pos, true);
            opened = true;
        }
        if (opened) {
            Set<GlobalPos> toClose = BrainUtils.getMemory(entity, MemoryModuleType.DOORS_TO_CLOSE);
            if (toClose == null) {
                toClose = new HashSet<>();
                BrainUtils.setMemory(entity, MemoryModuleType.DOORS_TO_CLOSE, toClose);
            }
            toClose.add(new GlobalPos(level.dimension(), pos));
        }
    }

    @Override
    protected void checkAndCloseDoors(ServerLevel level, E entity, Set<GlobalPos> doorsToClose, BlockPos prevNodePos, BlockPos nextNodePos) {
        for (Iterator<GlobalPos> iterator = doorsToClose.iterator(); iterator.hasNext(); ) {
            GlobalPos location = iterator.next();
            BlockPos pos = location.pos();
            if (pos.equals(prevNodePos) || pos.equals(nextNodePos)) {
                continue;
            }
            if (location.dimension() != level.dimension() || !pos.closerToCenterThan(entity.position(), 3)) {
                iterator.remove();
                continue;
            }
            BlockState state = level.getBlockState(pos);
            List<LivingEntity> others = BrainUtils.memoryOrDefault(entity, MemoryModuleType.NEAREST_LIVING_ENTITIES, List::of);
            if (!shouldHoldDoorOpenForOthers(entity, pos, others)) {
                if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN)) {
                    setGateOpen(level, entity, state, pos, false);
                } else if (state.getBlock() instanceof DoorBlock door && door.isOpen(state)) {
                    door.setOpen(entity, level, state, pos, false);
                }
            }
            iterator.remove();
        }
    }

    private static void setGateOpen(ServerLevel level, LivingEntity entity, BlockState state, BlockPos pos, boolean open) {
        level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, open), 10);
        level.playSound(null, pos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }
}
