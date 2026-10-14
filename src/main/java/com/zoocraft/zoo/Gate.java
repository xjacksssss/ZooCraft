package com.zoocraft.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/**
 * A designated entrance and exit.
 *
 * @param pos     where visitors stand when passing through
 * @param outside where arriving visitors queue, just outside the boundary
 * @param inside  where admitted visitors step to, just inside the boundary
 */
public record Gate(UUID id, BlockPos pos, BlockPos outside, BlockPos inside) {
    public static final Codec<Gate> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Gate::id),
            BlockPos.CODEC.fieldOf("pos").forGetter(Gate::pos),
            BlockPos.CODEC.fieldOf("outside").forGetter(Gate::outside),
            BlockPos.CODEC.fieldOf("inside").forGetter(Gate::inside)
    ).apply(i, Gate::new));
}
