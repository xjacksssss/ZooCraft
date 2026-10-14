package com.zoocraft.network;

import com.zoocraft.ZooCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: the boundary has been closed, ask the player for a zoo name. */
public record OpenNameZooScreenPayload() implements CustomPacketPayload {
    public static final OpenNameZooScreenPayload INSTANCE = new OpenNameZooScreenPayload();
    public static final Type<OpenNameZooScreenPayload> TYPE = new Type<>(ZooCraft.id("open_name_zoo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNameZooScreenPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
