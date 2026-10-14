package com.zoocraft.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.UUID;

/** A designated toilet point (Phase 2 spec section 8.4): where a visitor stands to use the toilet. */
public record Toilet(UUID id, BlockPos pos) {
    public static final Codec<Toilet> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Toilet::id),
            BlockPos.CODEC.fieldOf("pos").forGetter(Toilet::pos)
    ).apply(i, Toilet::new));
}
