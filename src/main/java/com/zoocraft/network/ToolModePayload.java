package com.zoocraft.network;

import com.zoocraft.ZooCraft;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: step the held designation tool's mode by {@code direction} (sneak and scroll). */
public record ToolModePayload(int direction) implements CustomPacketPayload {
    public static final Type<ToolModePayload> TYPE = new Type<>(ZooCraft.id("tool_mode"));
    public static final StreamCodec<ByteBuf, ToolModePayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ToolModePayload::new, ToolModePayload::direction);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
