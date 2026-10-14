package com.zoocraft.item;

import com.zoocraft.zoo.BoundaryVertex;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A boundary a player is drawing (CREATE) or editing (EDIT). Lives on the server only. */
public final class BoundaryDraft {
    public enum Mode { CREATE, EDIT }

    private final Mode mode;
    private final @Nullable UUID zooId;
    private final ResourceKey<Level> dimension;
    private List<BoundaryVertex> vertices;
    private int held = -1;
    private boolean closed;

    private BoundaryDraft(Mode mode, @Nullable UUID zooId, ResourceKey<Level> dimension, List<BoundaryVertex> vertices) {
        this.mode = mode;
        this.zooId = zooId;
        this.dimension = dimension;
        this.vertices = new ArrayList<>(vertices);
    }

    public static BoundaryDraft create(ResourceKey<Level> dimension) {
        return new BoundaryDraft(Mode.CREATE, null, dimension, List.of());
    }

    public static BoundaryDraft edit(UUID zooId, ResourceKey<Level> dimension, List<BoundaryVertex> vertices) {
        return new BoundaryDraft(Mode.EDIT, zooId, dimension, vertices);
    }

    public Mode mode() {
        return mode;
    }

    public @Nullable UUID zooId() {
        return zooId;
    }

    public ResourceKey<Level> dimension() {
        return dimension;
    }

    public List<BoundaryVertex> vertices() {
        return List.copyOf(vertices);
    }

    void setVertices(List<BoundaryVertex> vertices) {
        this.vertices = new ArrayList<>(vertices);
    }

    public int held() {
        return held;
    }

    void setHeld(int held) {
        this.held = held;
    }

    /** True when a CREATE draft has been closed and is waiting for a name. */
    public boolean closed() {
        return closed;
    }

    void setClosed(boolean closed) {
        this.closed = closed;
    }
}
