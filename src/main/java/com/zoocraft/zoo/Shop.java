package com.zoocraft.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A designated shop (Phase 2 spec section 7.1): a counter where customers stand and the containers that hold its
 * stock. The building around it is the player's. Read freely; change only through {@link ZooManager}.
 */
public final class Shop {
    public static final Codec<Shop> CODEC = RecordCodecBuilder.create(i -> i.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(Shop::id),
            Codec.STRING.fieldOf("name").forGetter(Shop::name),
            BlockPos.CODEC.fieldOf("counter").forGetter(Shop::counter),
            BlockPos.CODEC.listOf().fieldOf("containers").forGetter(Shop::containers),
            Codec.BOOL.fieldOf("open").forGetter(Shop::open),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("prices").forGetter(s -> s.prices),
            ShopStats.CODEC.fieldOf("stats").forGetter(Shop::stats)
    ).apply(i, Shop::new));

    private final UUID id;
    private String name;
    private final BlockPos counter;
    private final List<BlockPos> containers;
    private boolean open;
    private final Map<String, Integer> prices;
    private final ShopStats stats;

    public Shop(UUID id, String name, BlockPos counter, List<BlockPos> containers, boolean open,
                Map<String, Integer> prices, ShopStats stats) {
        this.id = id;
        this.name = name;
        this.counter = counter.immutable();
        this.containers = new ArrayList<>(containers);
        this.open = open;
        this.prices = new HashMap<>(prices);
        this.stats = stats;
    }

    static Shop create(String name, BlockPos counter) {
        return new Shop(UUID.randomUUID(), name, counter, List.of(), true, Map.of(), new ShopStats());
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public BlockPos counter() {
        return counter;
    }

    public List<BlockPos> containers() {
        return List.copyOf(containers);
    }

    public boolean open() {
        return open;
    }

    /** Item ID to price, for items whose price has been set or defaulted. */
    public Map<String, Integer> prices() {
        return Map.copyOf(prices);
    }

    public Optional<Integer> price(String itemId) {
        return Optional.ofNullable(prices.get(itemId));
    }

    public ShopStats stats() {
        return stats;
    }

    void setName(String name) {
        this.name = name;
    }

    void setOpen(boolean open) {
        this.open = open;
    }

    void addContainer(BlockPos pos) {
        containers.add(pos.immutable());
    }

    boolean removeContainer(BlockPos pos) {
        return containers.remove(pos);
    }

    void setPrice(String itemId, int price) {
        prices.put(itemId, price);
    }
}
