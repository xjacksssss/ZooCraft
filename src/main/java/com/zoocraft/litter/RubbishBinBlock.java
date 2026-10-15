package com.zoocraft.litter;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The rubbish bin, ZooCraft's only dedicated zoo block (Phase 2 spec section 9.1). Visitors put rubbish in it until
 * it is full; anyone can empty it with an empty hand. The {@link #FILL} stage only drives the model.
 */
public class RubbishBinBlock extends BaseEntityBlock {
    public static final MapCodec<RubbishBinBlock> CODEC = simpleCodec(RubbishBinBlock::new);
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 3);
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);

    public RubbishBinBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FILL, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILL);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RubbishBinBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            emptyBin(level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Empties the bin at {@code pos}. The rubbish is thrown away; nothing is given back. */
    public static void emptyBin(Level level, BlockPos pos, @Nullable Player player) {
        if (!(level.getBlockEntity(pos) instanceof RubbishBinBlockEntity bin)) {
            return;
        }
        int had = bin.fill();
        bin.empty();
        if (had > 0) {
            level.playSound(null, pos, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable("zoocraft.msg.bin_emptied", had).withStyle(ChatFormatting.GREEN), true);
        }
    }
}
