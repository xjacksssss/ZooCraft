package com.zoocraft.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.UUID;

/**
 * What is remembered of an exhibit across a restart: its identity, the animals in it (mob UUIDs are saved by the
 * game, so the first census after loading can match them again) and how often visitors have seen it.
 */
public record SavedExhibit(UUID id, List<UUID> animals, int views) {
    public static final Codec<SavedExhibit> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(SavedExhibit::id),
            UUIDUtil.CODEC.listOf().fieldOf("animals").forGetter(SavedExhibit::animals),
            Codec.INT.fieldOf("views").forGetter(SavedExhibit::views)
    ).apply(i, SavedExhibit::new));
}
