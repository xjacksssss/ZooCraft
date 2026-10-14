package com.zoocraft.zoo;

import com.mojang.serialization.Codec;
import com.zoocraft.ZooCraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** All zoos on the server, saved with the overworld's data. */
final class ZooSavedData extends SavedData {
    private static final String NAME = ZooCraft.MOD_ID + "_zoos";
    private static final Codec<List<Zoo>> ZOOS_CODEC = Zoo.CODEC.listOf();

    private final Map<UUID, Zoo> zoos = new LinkedHashMap<>();
    private final ZooIndex index = new ZooIndex();
    /** Original data kept when some zoos could not be read, so a save never silently discards them. */
    private Tag unreadableBackup;

    static ZooSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ZooSavedData::new, ZooSavedData::load), NAME);
    }

    private static ZooSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ZooSavedData data = new ZooSavedData();
        Tag list = tag.get("zoos");
        if (list != null) {
            ZOOS_CODEC.parse(NbtOps.INSTANCE, list)
                    .resultOrPartial(error -> {
                        ZooCraft.LOGGER.error("Could not read some zoo data; keeping a backup in the save: {}", error);
                        data.unreadableBackup = list.copy();
                    })
                    .ifPresent(loaded -> loaded.forEach(z -> data.zoos.put(z.id(), z)));
        }
        if (tag.contains("unreadableBackup")) {
            data.unreadableBackup = tag.get("unreadableBackup");
        }
        data.index.rebuild(data.zoos.values());
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ZOOS_CODEC.encodeStart(NbtOps.INSTANCE, List.copyOf(zoos.values()))
                .resultOrPartial(error -> ZooCraft.LOGGER.error("Could not save some zoo data: {}", error))
                .ifPresent(encoded -> tag.put("zoos", encoded));
        if (unreadableBackup != null) {
            tag.put("unreadableBackup", unreadableBackup);
        }
        return tag;
    }

    Collection<Zoo> zoos() {
        return zoos.values();
    }

    Zoo get(UUID id) {
        return zoos.get(id);
    }

    ZooIndex index() {
        return index;
    }

    void put(Zoo zoo) {
        zoos.put(zoo.id(), zoo);
        index.add(zoo);
        setDirty();
    }

    void remove(Zoo zoo) {
        zoos.remove(zoo.id());
        index.remove(zoo);
        setDirty();
    }

    /** Call after a zoo's boundary changes. */
    void reindex(Zoo zoo) {
        index.remove(zoo);
        index.add(zoo);
        setDirty();
    }
}
