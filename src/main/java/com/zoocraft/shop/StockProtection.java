package com.zoocraft.shop;

import com.zoocraft.ZooCraft;
import com.zoocraft.zoo.ShopRef;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import com.zoocraft.zoo.ZooPermission;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Optional;

/**
 * Only the owner, managers and operators can open or break a shop's stock containers (Phase 2 spec section 7.6).
 * Hoppers and modded logistics are deliberately untouched: they are how players automate restocking.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class StockProtection {
    private StockProtection() {
    }

    public static boolean mayAccess(ServerPlayer player, Zoo zoo) {
        return ZooManager.can(player, zoo, ZooPermission.EDIT);
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && refuse(player, event.getPos())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && refuse(player, event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static boolean refuse(ServerPlayer player, net.minecraft.core.BlockPos pos) {
        Optional<ShopRef> ref = ZooManager.get(player.server).shopOwningContainer(player.level(), pos);
        if (ref.isEmpty() || mayAccess(player, ref.get().zoo())) {
            return false;
        }
        player.displayClientMessage(Component.translatable("zoocraft.msg.stock_protected", ref.get().shop().name())
                .withStyle(ChatFormatting.RED), true);
        return true;
    }
}
