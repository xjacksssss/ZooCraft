package com.zoocraft.registry;

import com.zoocraft.ZooCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ZooTags {
    /** Blocks visitors prefer to walk on. Modpacks can extend this tag. */
    public static final TagKey<Block> VISITOR_PATHS = TagKey.create(Registries.BLOCK, ZooCraft.id("visitor_paths"));

    private ZooTags() {
    }
}
