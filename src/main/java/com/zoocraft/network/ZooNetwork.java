package com.zoocraft.network;

import com.zoocraft.client.ClientPayloadHandlers;
import com.zoocraft.item.BoundaryDrafts;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers every payload. Client handlers are called through lambdas so that client-only classes are only
 * loaded when a packet actually arrives on the client.
 */
public final class ZooNetwork {
    private static final String VERSION = "1";

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

        registrar.playToServer(CreateZooPayload.TYPE, CreateZooPayload.STREAM_CODEC,
                (payload, context) -> BoundaryDrafts.finishCreate((ServerPlayer) context.player(), payload.name()));
    }
}
