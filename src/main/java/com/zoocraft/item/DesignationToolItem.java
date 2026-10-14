package com.zoocraft.item;

import com.zoocraft.zoo.ActionResult;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/** Designates gates in Phase 1; shops and toilets join it in Phase 2 (spec section 6.5). */
public class DesignationToolItem extends Item {
    public DesignationToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player && context.getLevel() instanceof ServerLevel level) {
            click(player, level, context.getClickedPos());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            Optional<BlockPos> target = ToolRaycast.target(level, player);
            if (target.isPresent()) {
                click(serverPlayer, serverLevel, target.get());
            } else {
                serverPlayer.displayClientMessage(Component.translatable("zoocraft.tool.no_target"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.zoocraft.designation_tool.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.zoocraft.designation_tool.tip2").withStyle(ChatFormatting.GRAY));
    }

    public static void click(ServerPlayer player, ServerLevel level, BlockPos clicked) {
        BlockPos feet = feetPosition(level, clicked);
        ZooManager zoos = ZooManager.get(player.server);
        ActionResult result = player.isShiftKeyDown()
                ? zoos.removeGate(player, level, feet)
                : zoos.addGate(player, level, feet);
        player.sendSystemMessage(result.message().copy().withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        result.notes().forEach(note -> player.sendSystemMessage(note.copy().withStyle(ChatFormatting.GOLD)));
    }

    /** Clicking the ground puts the gate on top of it; clicking a door, fence gate or open space uses that block. */
    private static BlockPos feetPosition(ServerLevel level, BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        boolean passable = state.getCollisionShape(level, clicked).isEmpty()
                || state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES);
        if (passable && (state.is(BlockTags.DOORS) && level.getBlockState(clicked.below()).is(BlockTags.DOORS))) {
            return clicked.below();
        }
        return passable ? clicked : clicked.above();
    }
}
