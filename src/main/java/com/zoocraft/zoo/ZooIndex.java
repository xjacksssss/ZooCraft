package com.zoocraft.zoo;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Chunk-to-zoo lookup per dimension, so "which zoo is this block in?" never scans every zoo. */
final class ZooIndex {
    private final Map<ResourceKey<Level>, Map<Long, List<Zoo>>> byDimension = new HashMap<>();

    void rebuild(Collection<Zoo> zoos) {
        byDimension.clear();
        zoos.forEach(this::add);
    }

    void add(Zoo zoo) {
        Map<Long, List<Zoo>> chunks = byDimension.computeIfAbsent(zoo.dimension(), k -> new HashMap<>());
        for (long chunk : zoo.shape().coveredChunks()) {
            chunks.computeIfAbsent(chunk, k -> new ArrayList<>(1)).add(zoo);
        }
    }

    void remove(Zoo zoo) {
        Map<Long, List<Zoo>> chunks = byDimension.get(zoo.dimension());
        if (chunks == null) {
            return;
        }
        chunks.values().forEach(list -> list.remove(zoo));
        chunks.values().removeIf(List::isEmpty);
    }

    Optional<Zoo> zooAt(ResourceKey<Level> dimension, BlockPos pos) {
        Map<Long, List<Zoo>> chunks = byDimension.get(dimension);
        if (chunks == null) {
            return Optional.empty();
        }
        List<Zoo> candidates = chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (candidates == null) {
            return Optional.empty();
        }
        for (Zoo zoo : candidates) {
            if (zoo.contains(pos)) {
                return Optional.of(zoo);
            }
        }
        return Optional.empty();
    }

    /** Zoos touching any chunk within {@code chunkRadius} of the position. */
    List<Zoo> zoosNear(ResourceKey<Level> dimension, BlockPos pos, int chunkRadius) {
        Map<Long, List<Zoo>> chunks = byDimension.get(dimension);
        if (chunks == null) {
            return List.of();
        }
        Set<Zoo> result = new LinkedHashSet<>();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                List<Zoo> list = chunks.get(ChunkPos.asLong(cx + dx, cz + dz));
                if (list != null) {
                    result.addAll(list);
                }
            }
        }
        return List.copyOf(result);
    }
}
