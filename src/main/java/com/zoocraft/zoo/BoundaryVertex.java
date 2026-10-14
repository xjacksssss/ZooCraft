package com.zoocraft.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zoocraft.core.geometry.Vertex;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A boundary corner. Only X and Z define the shape; Y is kept so outlines can be drawn at the right height. */
public record BoundaryVertex(int x, int y, int z) {
    public static final Codec<BoundaryVertex> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(BoundaryVertex::x),
            Codec.INT.fieldOf("y").forGetter(BoundaryVertex::y),
            Codec.INT.fieldOf("z").forGetter(BoundaryVertex::z)
    ).apply(i, BoundaryVertex::new));

    public static final StreamCodec<ByteBuf, BoundaryVertex> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BoundaryVertex::x,
            ByteBufCodecs.VAR_INT, BoundaryVertex::y,
            ByteBufCodecs.VAR_INT, BoundaryVertex::z,
            BoundaryVertex::new);

    public Vertex toVertex() {
        return new Vertex(x, z);
    }
}
