package com.zoocraft.config;

import com.zoocraft.core.geometry.BoundaryLimits;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side settings (spec section 10). Values are read live, so changes apply without a restart. */
public final class ZooConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_VISITORS_PER_ZOO;
    public static final ModConfigSpec.IntValue MAX_VISITORS_TOTAL;
    public static final ModConfigSpec.IntValue SPAWN_INTERVAL_SECONDS;
    public static final ModConfigSpec.IntValue MAX_VISIT_MINUTES;
    public static final ModConfigSpec.IntValue CENSUS_INTERVAL_SECONDS;
    public static final ModConfigSpec.IntValue VIEWING_RADIUS;
    public static final ModConfigSpec.IntValue MIN_ZOO_AREA;
    public static final ModConfigSpec.IntValue MAX_ZOO_AREA;
    public static final ModConfigSpec.IntValue MAX_BOUNDARY_VERTICES;
    public static final ModConfigSpec.BooleanValue ALLOW_ZOO_CHUNK_LOADING;
    public static final ModConfigSpec.IntValue MAX_LOADED_CHUNKS_PER_ZOO;
    public static final ModConfigSpec.IntValue MAX_SHOPS_PER_ZOO;
    public static final ModConfigSpec.IntValue MAX_CONTAINERS_PER_SHOP;
    public static final ModConfigSpec.IntValue SHOP_LINK_RADIUS;
    public static final ModConfigSpec.IntValue DEFAULT_FOOD_PRICE;
    public static final ModConfigSpec.IntValue DEFAULT_DRINK_PRICE;
    public static final ModConfigSpec.IntValue DEFAULT_SOUVENIR_PRICE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("visitors");
        MAX_VISITORS_PER_ZOO = b.comment("Most visitors one zoo can have at a time.")
                .defineInRange("maxVisitorsPerZoo", 12, 0, 200);
        MAX_VISITORS_TOTAL = b.comment("Most visitors across all zoos on the server. Lower this on weaker servers.")
                .defineInRange("maxVisitorsTotal", 40, 0, 1000);
        SPAWN_INTERVAL_SECONDS = b.comment("How often each open zoo considers spawning a visitor group.")
                .defineInRange("spawnIntervalSeconds", 10, 1, 600);
        MAX_VISIT_MINUTES = b.comment("Longest a single visit can last, in real minutes.")
                .defineInRange("maxVisitMinutes", 15, 1, 120);
        b.pop();

        b.push("animals");
        CENSUS_INTERVAL_SECONDS = b.comment("How often each open zoo counts its animals.")
                .defineInRange("censusIntervalSeconds", 10, 1, 600);
        VIEWING_RADIUS = b.comment("How close, in blocks, a visitor must be to see an animal.")
                .defineInRange("viewingRadius", 6, 2, 32);
        b.pop();

        b.push("boundaries");
        MIN_ZOO_AREA = b.comment("Smallest allowed zoo area in square blocks.")
                .defineInRange("minZooArea", 64, 1, 1_000_000);
        MAX_ZOO_AREA = b.comment("Largest allowed zoo area in square blocks.")
                .defineInRange("maxZooArea", 65_536, 1, 16_777_216);
        MAX_BOUNDARY_VERTICES = b.comment("Most corners a zoo boundary can have.")
                .defineInRange("maxBoundaryVertices", 64, 3, 1024);
        b.pop();

        b.push("shops");
        MAX_SHOPS_PER_ZOO = b.comment("Most shops one zoo can have.")
                .defineInRange("maxShopsPerZoo", 16, 1, 256);
        MAX_CONTAINERS_PER_SHOP = b.comment("Most stock containers one shop can link.")
                .defineInRange("maxContainersPerShop", 4, 1, 32);
        SHOP_LINK_RADIUS = b.comment("How far, in blocks, a stock container may be from its shop counter.")
                .defineInRange("shopLinkRadius", 6, 1, 32);
        DEFAULT_FOOD_PRICE = b.comment("Starting price, in emeralds, for food when it first appears in a shop.")
                .defineInRange("defaultFoodPrice", 2, 1, 999);
        DEFAULT_DRINK_PRICE = b.comment("Starting price, in emeralds, for drinks when they first appear in a shop.")
                .defineInRange("defaultDrinkPrice", 1, 1, 999);
        DEFAULT_SOUVENIR_PRICE = b.comment("Starting price, in emeralds, for souvenirs when they first appear in a shop.")
                .defineInRange("defaultSouvenirPrice", 3, 1, 999);
        b.pop();

        b.push("chunkLoading");
        ALLOW_ZOO_CHUNK_LOADING = b.comment("Whether zoo owners and managers may keep their zoo loaded when nobody is nearby.")
                .define("allowZooChunkLoading", true);
        MAX_LOADED_CHUNKS_PER_ZOO = b.comment("Most chunks a single zoo may keep loaded.")
                .defineInRange("maxLoadedChunksPerZoo", 64, 1, 4096);
        b.pop();

        SPEC = b.build();
    }

    private ZooConfig() {
    }

    public static BoundaryLimits boundaryLimits() {
        return new BoundaryLimits(MIN_ZOO_AREA.get(), MAX_ZOO_AREA.get(), MAX_BOUNDARY_VERTICES.get());
    }
}
