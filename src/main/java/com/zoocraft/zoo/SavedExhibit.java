package com.zoocraft.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.List;
import java.util.UUID;

/**
 * What is remembered of an exhibit across a restart: its identity, the animals in it (mob UUIDs are saved by the
 * game, so the first census after loading can match them again) and how often visitors have seen it, in all and on
 * game day {@code day}. Saves from before Phase 3 load with no views today.
 */
public record SavedExhibit(UUID id, List<UUID> animals, int views, int viewsToday, long day) {
    public static final Codec<SavedExhibit> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(SavedExhibit::id),
            UUIDUtil.CODEC.listOf().fieldOf("animals").forGetter(SavedExhibit::animals),
            Codec.INT.fieldOf("views").forGetter(SavedExhibit::views),
            Codec.INT.optionalFieldOf("viewsToday", 0).forGetter(SavedExhibit::viewsToday),
            Codec.LONG.optionalFieldOf("day", -1L).forGetter(SavedExhibit::day)
    ).apply(i, SavedExhibit::new));

    public SavedExhibit(UUID id, List<UUID> animals, int views) {
        this(id, animals, views, 0, -1L);
    }
}
