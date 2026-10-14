package com.zoocraft.item;

import com.zoocraft.ZooCraft;
import com.zoocraft.registry.ZooComponents;
import com.zoocraft.registry.ZooItems;
import com.zoocraft.shop.ContainerPositions;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;
import java.util.Optional;

/**
 * Designates gates, shops and toilets (spec section 6.5, Phase 2 spec section 6). Sneak and scroll, or sneak and
 * use on the air, to change the mode.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public class DesignationToolItem extends Item {
    public DesignationToolItem(Properties properties) {
        super(properties);
    }

    public static DesignationMode mode(ItemStack stack) {
        return stack.getOrDefault(ZooComponents.DESIGNATION_MODE.get(), DesignationMode.GATE);
    }

    public static void setMode(ItemStack stack, DesignationMode mode) {
        stack.set(ZooComponents.DESIGNATION_MODE.get(), mode);
    }

    /** Steps the held tool's mode and tells the player. */
    public static void cycleMode(ServerPlayer player, ItemStack stack, int direction) {
        DesignationMode mode = mode(stack).next(direction);
        setMode(stack, mode);
        player.displayClientMessage(Component.translatable("zoocraft.tool.mode", mode.displayName()).withStyle(ChatFormatting.GOLD), true);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable("item.zoocraft.designation_tool.with_mode", super.getName(stack), mode(stack).displayName());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player && context.getLevel() instanceof ServerLevel level) {
            click(player, level, context.getClickedPos(), mode(context.getItemInHand()));
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            Optional<BlockPos> target = ToolRaycast.target(level, player);
            if (target.isPresent()) {
                click(serverPlayer, serverLevel, target.get(), mode(stack));
            } else if (player.isShiftKeyDown()) {
                cycleMode(serverPlayer, stack, 1);
            } else {
                serverPlayer.displayClientMessage(Component.translatable("zoocraft.tool.no_target"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String mode = mode(stack).getSerializedName();
        tooltip.add(Component.translatable("item.zoocraft.designation_tool." + mode + ".tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.zoocraft.designation_tool." + mode + ".tip2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.zoocraft.designation_tool.mode_tip").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** A Phase 1 style gate click. */
    public static void click(ServerPlayer player, ServerLevel level, BlockPos clicked) {
        click(player, level, clicked, DesignationMode.GATE);
    }

    public static void click(ServerPlayer player, ServerLevel level, BlockPos clicked, DesignationMode mode) {
        ZooManager zoos = ZooManager.get(player.server);
        boolean sneak = player.isShiftKeyDown();
        ActionResult result = switch (mode) {
            case GATE -> {
                BlockPos feet = feetPosition(level, clicked);
                yield sneak ? zoos.removeGate(player, level, feet) : zoos.addGate(player, level, feet);
            }
            case SHOP -> {
                if (ContainerPositions.hasItemHandler(level, clicked)) {
                    yield sneak ? zoos.unlinkContainer(player, level, clicked) : zoos.linkContainer(player, level, clicked);
                }
                BlockPos feet = feetPosition(level, clicked);
                yield sneak ? zoos.removeShop(player, level, feet) : zoos.createShop(player, level, feet);
            }
            case TOILET -> {
                BlockPos feet = feetPosition(level, clicked);
                yield sneak ? zoos.removeToilet(player, level, feet) : zoos.createToilet(player, level, feet);
            }
        };
        player.sendSystemMessage(result.message().copy().withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        result.notes().forEach(note -> player.sendSystemMessage(note.copy().withStyle(ChatFormatting.GOLD)));
    }

    /** Clicking the ground puts the designation on top of it; clicking a door, fence gate or open space uses that block. */
    private static BlockPos feetPosition(ServerLevel level, BlockPos clicked) {
        BlockState state = level.getBlockState(clicked);
        boolean passable = state.getCollisionShape(level, clicked).isEmpty()
                || state.is(BlockTags.DOORS) || state.is(BlockTags.FENCE_GATES);
        if (passable && (state.is(BlockTags.DOORS) && level.getBlockState(clicked.below()).is(BlockTags.DOORS))) {
            return clicked.below();
        }
        return passable ? clicked : clicked.above();
    }

    /** With the tool in hand, clicking a chest or door designates it instead of opening it. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().is(ZooItems.DESIGNATION_TOOL.get())) {
            event.setUseBlock(TriState.FALSE);
        }
    }
}
