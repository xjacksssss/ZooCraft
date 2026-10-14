package com.zoocraft.network;

import com.zoocraft.ZooCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: create a zoo from the player's closed draft with this name. An empty name means "cancel". */
public record CreateZooPayload(String name) implements CustomPacketPayload {
    public static final Type<CreateZooPayload> TYPE = new Type<>(ZooCraft.id("create_zoo"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CreateZooPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), CreateZooPayload::name,
            CreateZooPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
