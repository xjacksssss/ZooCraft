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
    public static final ModConfigSpec.IntValue MAX_VIEWING_SPOTS;
    public static final ModConfigSpec.IntValue VIEWING_SPOT_CAPACITY;
    public static final ModConfigSpec.IntValue MAX_ENCLOSURE_AREA;
    public static final ModConfigSpec.IntValue ENCLOSURE_FILLS_PER_TICK;
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
    public static final ModConfigSpec.IntValue COUNTER_CAPACITY;
    public static final ModConfigSpec.IntValue BIN_CAPACITY;
    public static final ModConfigSpec.IntValue BIN_SEARCH_RADIUS;
    public static final ModConfigSpec.DoubleValue LITTER_CHANCE_WITHOUT_BIN;
    public static final ModConfigSpec.IntValue MAX_LITTER_PER_ZOO;
    public static final ModConfigSpec.IntValue MAX_LITTER_TOTAL;
    public static final ModConfigSpec.IntValue NEED_THRESHOLD;
    public static final ModConfigSpec.IntValue TOILET_CAPACITY;
    public static final ModConfigSpec.IntValue BUDGET_CONSCIOUS_MIN;
    public static final ModConfigSpec.IntValue BUDGET_CONSCIOUS_MAX;
    public static final ModConfigSpec.IntValue ENTHUSIAST_MIN;
    public static final ModConfigSpec.IntValue ENTHUSIAST_MAX;
    public static final ModConfigSpec.IntValue GENERAL_MIN;
    public static final ModConfigSpec.IntValue GENERAL_MAX;
    public static final ModConfigSpec.IntValue VISITOR_STEP_TICKS;
    public static final ModConfigSpec.IntValue VIEWING_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue LITTER_CHECK_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue VISITOR_FOLLOW_RANGE;
    public static final ModConfigSpec.DoubleValue OFF_PATH_COST;
    public static final ModConfigSpec.EnumValue<com.zoocraft.visitor.ShortcutMode> PATH_SHORTCUT_MODE;

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
        MAX_VIEWING_SPOTS = b.comment("Most places visitors stand to see one exhibit.")
                .defineInRange("maxViewingSpots", 12, 1, 64);
        VIEWING_SPOT_CAPACITY = b.comment("How many visitors one viewing spot holds.")
                .defineInRange("viewingSpotCapacity", 2, 1, 16);
        MAX_ENCLOSURE_AREA = b.comment("Largest pen floor, in blocks, searched for one exhibit. Bigger pens count as open land,",
                        "with viewing spots around the animals themselves.")
                .defineInRange("maxEnclosureArea", 1024, 64, 16384);
        ENCLOSURE_FILLS_PER_TICK = b.comment("Most exhibit enclosure searches per server tick, across all zoos.")
                .defineInRange("enclosureFillsPerTick", 2, 1, 32);
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
        COUNTER_CAPACITY = b.comment("How many visitors one shop counter serves at once. The others wait nearby.")
                .defineInRange("counterCapacity", 2, 1, 32);
        b.pop();

        b.push("needs");
        NEED_THRESHOLD = b.comment("How hungry, or how badly in need of a toilet, a visitor gets (0-100) before it goes looking.")
                .defineInRange("needThreshold", 60, 1, 100);
        TOILET_CAPACITY = b.comment("How many visitors one toilet point serves at once.")
                .defineInRange("toiletCapacity", 2, 1, 32);
        b.pop();

        b.push("litter");
        BIN_CAPACITY = b.comment("Pieces of rubbish a bin holds before someone has to empty it.")
                .defineInRange("binCapacity", 32, 1, 10_000);
        BIN_SEARCH_RADIUS = b.comment("How far, in blocks, a visitor carrying rubbish will walk to a bin.")
                .defineInRange("binSearchRadius", 16, 1, 64);
        LITTER_CHANCE_WITHOUT_BIN = b.comment("Chance that a visitor with no bin nearby drops its rubbish instead of taking it home.")
                .defineInRange("litterChanceWithoutBin", 0.75, 0.0, 1.0);
        MAX_LITTER_PER_ZOO = b.comment("Most pieces of litter one zoo can have; beyond this, nearby litter piles up instead.")
                .defineInRange("maxLitterPerZoo", 48, 0, 1000);
        MAX_LITTER_TOTAL = b.comment("Most pieces of litter across all zoos on the server.")
                .defineInRange("maxLitterTotal", 300, 0, 10_000);
        b.pop();

        b.push("budgets");
        BUDGET_CONSCIOUS_MIN = b.comment("Emeralds a budget-conscious visitor brings, at least.").defineInRange("budgetConsciousMin", 4, 0, 10_000);
        BUDGET_CONSCIOUS_MAX = b.comment("Emeralds a budget-conscious visitor brings, at most.").defineInRange("budgetConsciousMax", 12, 0, 10_000);
        ENTHUSIAST_MIN = b.comment("Emeralds an animal enthusiast brings, at least.").defineInRange("animalEnthusiastMin", 6, 0, 10_000);
        ENTHUSIAST_MAX = b.comment("Emeralds an animal enthusiast brings, at most.").defineInRange("animalEnthusiastMax", 18, 0, 10_000);
        GENERAL_MIN = b.comment("Emeralds a general visitor brings, at least.").defineInRange("generalMin", 5, 0, 10_000);
        GENERAL_MAX = b.comment("Emeralds a general visitor brings, at most.").defineInRange("generalMax", 15, 0, 10_000);
        b.pop();

        b.push("chunkLoading");
        ALLOW_ZOO_CHUNK_LOADING = b.comment("Whether zoo owners and managers may keep their zoo loaded when nobody is nearby.")
                .define("allowZooChunkLoading", true);
        MAX_LOADED_CHUNKS_PER_ZOO = b.comment("Most chunks a single zoo may keep loaded.")
                .defineInRange("maxLoadedChunksPerZoo", 64, 1, 4096);
        b.pop();

        b.push("simulation");
        VISITOR_STEP_TICKS = b.comment("How often, in ticks, each visitor makes its decisions. Higher is cheaper but less responsive.")
                .defineInRange("visitorStepTicks", 10, 1, 40);
        VIEWING_INTERVAL_TICKS = b.comment("How often, in ticks, each visitor looks for nearby animals.")
                .defineInRange("viewingIntervalTicks", 20, 5, 200);
        LITTER_CHECK_INTERVAL_TICKS = b.comment("How often, in ticks, each visitor notices litter around it.")
                .defineInRange("litterCheckIntervalTicks", 40, 10, 400);
        VISITOR_FOLLOW_RANGE = b.comment("How far, in blocks, one visitor path search reaches. Longer walks are split into several searches.",
                        "Bigger values reach far targets in one search but each search can cost more.")
                .defineInRange("visitorFollowRange", 48, 16, 128);
        OFF_PATH_COST = b.comment("Extra cost per block for visitors walking off player-built paths. Higher makes them keep to paths more,",
                        "but path searches across open ground get more expensive and fall short sooner. At 0.5 or below visitors",
                        "stop seeking out nearby paths; at 1.0 they can't plan 40 blocks across grass in one search.")
                .defineInRange("offPathCost", 0.75, 0.0, 2.0);
        PATH_SHORTCUT_MODE = b.comment("When visitors try to cut corners on their path. EVERY_NODE is much cheaper than EVERY_TICK and looks the same.")
                .defineEnum("pathShortcutMode", com.zoocraft.visitor.ShortcutMode.EVERY_NODE);
        b.pop();

        SPEC = b.build();
    }

    private ZooConfig() {
    }

    /** The configured budget range for a profile, as {min, max}. */
    public static int[] budgetRange(com.zoocraft.core.visitor.VisitorProfile profile) {
        return switch (profile) {
            case BUDGET_CONSCIOUS -> new int[]{BUDGET_CONSCIOUS_MIN.get(), BUDGET_CONSCIOUS_MAX.get()};
            case ANIMAL_ENTHUSIAST -> new int[]{ENTHUSIAST_MIN.get(), ENTHUSIAST_MAX.get()};
            case GENERAL -> new int[]{GENERAL_MIN.get(), GENERAL_MAX.get()};
        };
    }

    public static BoundaryLimits boundaryLimits() {
        return new BoundaryLimits(MIN_ZOO_AREA.get(), MAX_ZOO_AREA.get(), MAX_BOUNDARY_VERTICES.get());
    }
}
