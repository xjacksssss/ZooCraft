package com.zoocraft.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

/** A zoo's shops and toilets, kept in one saved field so Phase 1 zoos load with none. */
public final class ZooFacilities {
    public static final Codec<ZooFacilities> CODEC = RecordCodecBuilder.create(i -> i.group(
            Shop.CODEC.listOf().fieldOf("shops").forGetter(ZooFacilities::shops),
            Toilet.CODEC.listOf().fieldOf("toilets").forGetter(ZooFacilities::toilets)
    ).apply(i, ZooFacilities::new));

    final List<Shop> shops;
    final List<Toilet> toilets;

    public ZooFacilities(List<Shop> shops, List<Toilet> toilets) {
        this.shops = new ArrayList<>(shops);
        this.toilets = new ArrayList<>(toilets);
    }

    public static ZooFacilities empty() {
        return new ZooFacilities(List.of(), List.of());
    }

    public List<Shop> shops() {
        return List.copyOf(shops);
    }

    public List<Toilet> toilets() {
        return List.copyOf(toilets);
    }
}
