package com.zoocraft.network;

import com.zoocraft.client.ClientPayloadHandlers;
import com.zoocraft.dashboard.DashboardService;
import com.zoocraft.item.BoundaryDrafts;
import com.zoocraft.item.DesignationToolItem;
import com.zoocraft.registry.ZooItems;
import net.minecraft.world.item.ItemStack;
import com.zoocraft.zoo.ActionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers every payload. Client handlers are called through lambdas so that client-only classes are only
 * loaded when a packet actually arrives on the client.
 */
public final class ZooNetwork {
    private static final String VERSION = "2";

    private ZooNetwork() {
    }

    public static void register(IEventBus modBus) {
        modBus.addListener(ZooNetwork::onRegister);
    }

    private static void onRegister(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        registrar.playToClient(BoundaryDraftPayload.TYPE, BoundaryDraftPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.boundaryDraft(payload));
        registrar.playToClient(ZooOutlinesPayload.TYPE, ZooOutlinesPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.outlines(payload));
        registrar.playToClient(OpenNameZooScreenPayload.TYPE, OpenNameZooScreenPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.openNameZooScreen());

        registrar.playToClient(DashboardSnapshot.TYPE, DashboardSnapshot.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.dashboard(payload));
        registrar.playToClient(DashboardPayloads.ZooList.TYPE, DashboardPayloads.ZooList.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.zooList(payload));
        registrar.playToClient(DashboardPayloads.Closed.TYPE, DashboardPayloads.Closed.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.dashboardClosed());

        registrar.playToServer(CreateZooPayload.TYPE, CreateZooPayload.STREAM_CODEC,
                (payload, context) -> BoundaryDrafts.finishCreate((ServerPlayer) context.player(), payload.name()));
        registrar.playToServer(ToolModePayload.TYPE, ToolModePayload.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ItemStack held = player.getMainHandItem();
            if (held.is(ZooItems.DESIGNATION_TOOL.get())) {
                DesignationToolItem.cycleMode(player, held, Integer.signum(payload.direction()));
            }
        });
        registrar.playToServer(DashboardPayloads.OpenRequest.TYPE, DashboardPayloads.OpenRequest.STREAM_CODEC,
                (payload, context) -> DashboardService.open((ServerPlayer) context.player(), payload.zooId()));
        registrar.playToServer(DashboardPayloads.SetTab.TYPE, DashboardPayloads.SetTab.STREAM_CODEC,
                (payload, context) -> DashboardService.setTab((ServerPlayer) context.player(), payload.tab()));
        registrar.playToServer(DashboardPayloads.Close.TYPE, DashboardPayloads.Close.STREAM_CODEC,
                (payload, context) -> DashboardService.close((ServerPlayer) context.player()));
        registrar.playToServer(DashboardPayloads.Action.TYPE, DashboardPayloads.Action.STREAM_CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            ActionResult result = DashboardService.handle(player, payload);
            player.displayClientMessage(result.message().copy().withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
            result.notes().forEach(note -> player.sendSystemMessage(note.copy().withStyle(ChatFormatting.GOLD)));
        });
    }
}
