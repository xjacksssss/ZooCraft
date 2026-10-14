package com.zoocraft.network;

import com.zoocraft.ZooCraft;
import com.zoocraft.zoo.BoundaryVertex;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/** Server to client: the player's boundary draft, for drawing. {@code held} is the picked-up corner or -1. */
public record BoundaryDraftPayload(boolean active, boolean editing, List<BoundaryVertex> vertices, int held) implements CustomPacketPayload {
    public static final Type<BoundaryDraftPayload> TYPE = new Type<>(ZooCraft.id("boundary_draft"));
    public static final BoundaryDraftPayload NONE = new BoundaryDraftPayload(false, false, List.of(), -1);

    public static final StreamCodec<RegistryFriendlyByteBuf, BoundaryDraftPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BoundaryDraftPayload::active,
            ByteBufCodecs.BOOL, BoundaryDraftPayload::editing,
            BoundaryVertex.STREAM_CODEC.apply(ByteBufCodecs.list(1024)), BoundaryDraftPayload::vertices,
            ByteBufCodecs.VAR_INT, BoundaryDraftPayload::held,
            BoundaryDraftPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
