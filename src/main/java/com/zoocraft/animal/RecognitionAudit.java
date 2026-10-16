package com.zoocraft.animal;

import com.mojang.authlib.GameProfile;
import com.zoocraft.ZooCraft;
import com.zoocraft.visitor.Visitor;
import com.zoocraft.zoo.ActionResult;
import com.zoocraft.zoo.BoundaryVertex;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /zoo debug recognise audit}: checks every entity type in the game end to end (Phase 3 spec section 9.1, automated).
 * First the type check of {@link RecognitionReport}; then every mob is spawned for real (finalising its spawn the way
 * {@code /summon} does) on a platform inside a temporary zoo, one per tick, and counted by the real census, which also
 * checks that counted animals are made persistent. The results go to {@code logs/zoocraft-recognition-audit.csv}, which
 * {@code tools/atm10/check_recognition.py} compares with the reviewed expectations.
 *
 * <p>Started with {@code -Dzoocraft.recognitionAudit=true} it runs as soon as the server starts and then shuts the
 * game down, so {@code tools/atm10/recognition-audit.sh} can run it unattended in ATM10.
 */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class RecognitionAudit {
    public static final String PROPERTY = "zoocraft.recognitionAudit";
    private static final String ZOO_NAME = "Recognition Audit";
    /** Far enough from spawn that it doesn't touch players' zoos, high enough to stay clear of terrain. */
    private static final int OFFSET_X = 2048;
    private static final int HALF = 8;

    public record Row(ResourceLocation id, String category, RecognitionReport.Entry type, boolean spawned, boolean live,
                      boolean inCensus, boolean kept, String species, String speciesName, String problem) {
    }

    private static final class Run {
        final ServerLevel level;
        final BlockPos centre;
        final Zoo zoo;
        final ServerPlayer owner;
        final Deque<RecognitionReport.Entry> queue = new ArrayDeque<>();
        final List<RecognitionReport.Entry> all;
        final List<Row> rows = new ArrayList<>();
        final List<String> zoosAtStart;
        @Nullable
        final CommandSourceStack source;

        Run(ServerLevel level, BlockPos centre, Zoo zoo, ServerPlayer owner, List<RecognitionReport.Entry> all,
            List<String> zoosAtStart, @Nullable CommandSourceStack source) {
            this.level = level;
            this.centre = centre;
            this.zoo = zoo;
            this.owner = owner;
            this.all = all;
            this.zoosAtStart = zoosAtStart;
            this.source = source;
        }
    }

    @Nullable
    private static Run running;
    private static volatile boolean exitRequested;

    private RecognitionAudit() {
    }

    /** Set when an unattended audit has finished; the client stops the game when it sees it. */
    public static boolean exitRequested() {
        return exitRequested;
    }

    private static boolean unattended() {
        return Boolean.getBoolean(PROPERTY);
    }

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        if (unattended()) {
            Component result = start(event.getServer(), null);
            ZooCraft.LOGGER.info("Recognition audit: {}", result.getString());
            if (running == null) {
                finishUnattended(event.getServer());
            }
        }
    }

    @SubscribeEvent
    static void onServerStopping(ServerStoppingEvent event) {
        running = null;
    }

    /** Starts an audit; the reply says whether it started. */
    public static Component start(MinecraftServer server, @Nullable CommandSourceStack source) {
        if (running != null) {
            return Component.literal("A recognition audit is already running.");
        }
        ServerLevel level = server.overworld();
        List<String> zoosAtStart = ZooManager.get(server).all().stream().map(RecognitionAudit::describe).toList();
        BlockPos spawn = level.getSharedSpawnPos();
        int y = Math.min(level.getMaxBuildHeight() - 24, 240);
        BlockPos centre = new BlockPos((spawn.getX() + OFFSET_X) & ~15 | 8, y, (spawn.getZ() & ~15) | 8);
        level.setChunkForced(centre.getX() >> 4, centre.getZ() >> 4, true);
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dz = -HALF; dz <= HALF; dz++) {
                level.setBlockAndUpdate(centre.offset(dx, -1, dz), Blocks.STONE.defaultBlockState());
                for (int dy = 0; dy < 4; dy++) {
                    level.setBlockAndUpdate(centre.offset(dx, dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        ServerPlayer owner = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes(
                "zoocraft-recognition-audit".getBytes(StandardCharsets.UTF_8)), "[ZooCraft audit]"));
        ZooManager zoos = ZooManager.get(server);
        zoos.byName(ZOO_NAME).ifPresent(old -> zoos.delete(owner, old));
        int x0 = centre.getX() - HALF, x1 = centre.getX() + HALF, z0 = centre.getZ() - HALF, z1 = centre.getZ() + HALF;
        ActionResult created = zoos.createZoo(owner, ZOO_NAME, level.dimension(), List.of(
                new BoundaryVertex(x0, y, z0), new BoundaryVertex(x1, y, z0), new BoundaryVertex(x1, y, z1), new BoundaryVertex(x0, y, z1)));
        if (!created.success()) {
            level.setChunkForced(centre.getX() >> 4, centre.getZ() >> 4, false);
            return Component.literal("Couldn't create the audit zoo: " + created.message().getString());
        }
        Zoo zoo = zoos.byName(ZOO_NAME).orElseThrow();
        List<RecognitionReport.Entry> all = RecognitionReport.run(level, null);
        Run run = new Run(level, centre, zoo, owner, all, zoosAtStart, source);
        for (RecognitionReport.Entry entry : all) {
            if (entry.checked() && !entry.reason().equals("not a mob") && !entry.reason().equals("ZooCraft visitor")) {
                run.queue.add(entry);
            } else {
                run.rows.add(row(entry, null, false, false, false));
            }
        }
        running = run;
        return Component.literal(String.format(Locale.ROOT, "Recognition audit started: %d entity types, %d mobs to spawn at %d %d %d.",
                all.size(), run.queue.size(), centre.getX(), centre.getY(), centre.getZ()));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        Run run = running;
        if (run == null) {
            return;
        }
        if (!run.level.areEntitiesLoaded(ChunkPos.asLong(run.centre))) {
            return; // the census can't see entities in the platform's chunk until its entities have loaded
        }
        RecognitionReport.Entry next = run.queue.poll();
        if (next != null) {
            run.rows.add(spawnAndCount(run, next));
            return;
        }
        running = null;
        finish(event.getServer(), run);
    }

    private static Row spawnAndCount(Run run, RecognitionReport.Entry entry) {
        clear(run);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entry.id());
        Entity entity;
        try {
            entity = type.spawn(run.level, run.centre, MobSpawnType.COMMAND);
        } catch (Exception | LinkageError e) {
            ZooCraft.LOGGER.warn("Recognition audit: spawning {} failed", entry.id(), e);
            clear(run);
            return row(entry, null, false, false, false, "spawn threw " + e.getClass().getSimpleName());
        }
        try {
            if (entity == null || entity.isRemoved()) {
                return row(entry, null, false, false, false);
            }
            boolean live = AnimalRecognition.isExhibitAnimal(entity);
            ZooCensus census = AnimalCensus.refresh(run.zoo, run.level);
            boolean counted = census.speciesOf().containsKey(entity.getUUID());
            boolean kept = entity instanceof Mob mob && mob.isPersistenceRequired();
            return row(entry, entity, live, counted, kept);
        } catch (Exception | LinkageError e) {
            ZooCraft.LOGGER.warn("Recognition audit: checking {} failed", entry.id(), e);
            return row(entry, entity, false, false, false, "check threw " + e.getClass().getSimpleName());
        } finally {
            clear(run);
        }
    }

    /** Removes everything on the platform but players: the last mob and anything its spawn brought along (riders, minions). */
    private static void clear(Run run) {
        AABB box = new AABB(run.centre).inflate(HALF + 8, 16, HALF + 8);
        for (Entity e : run.level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
            try {
                e.discard();
            } catch (Exception | LinkageError ex) {
                ZooCraft.LOGGER.debug("Recognition audit: couldn't discard {}", e, ex);
            }
        }
    }

    private static Row row(RecognitionReport.Entry entry, @Nullable Entity entity, boolean live, boolean counted, boolean kept) {
        return row(entry, entity, live, counted, kept, null);
    }

    private static Row row(RecognitionReport.Entry entry, @Nullable Entity entity, boolean live, boolean counted, boolean kept,
                           @Nullable String failure) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(entry.id());
        boolean spawned = entity != null && failure == null;
        String species = AnimalRecognition.species(type).toString();
        String name = AnimalRecognition.speciesName(type).getString();
        List<String> problems = new ArrayList<>();
        if (failure != null) {
            problems.add(failure);
        }
        if (spawned) {
            if (live != entry.recognised()) {
                problems.add("spawned mob " + (live ? "recognised" : "not recognised") + " unlike its type check");
            }
            if (live != counted) {
                problems.add(counted ? "census counted an unrecognised mob" : "census missed a recognised mob");
            }
            if (counted && !kept) {
                problems.add("counted but not made persistent");
            }
        } else if (entry.recognised() && entry.checked() && failure == null && !(entry.reason().equals("not a mob"))) {
            problems.add("recognised but didn't spawn");
        }
        if (entry.recognised() && name.startsWith("entity.")) {
            problems.add("no translated name");
        }
        return new Row(entry.id(), type.getCategory().getName(), entry, spawned, live, counted, kept, species, name,
                String.join("; ", problems));
    }

    private static void finish(MinecraftServer server, Run run) {
        ZooManager zoos = ZooManager.get(server);
        zoos.byId(run.zoo.id()).ifPresent(z -> zoos.delete(run.owner, z));
        run.level.setChunkForced(run.centre.getX() >> 4, run.centre.getZ() >> 4, false);
        run.rows.sort((a, b) -> a.id().toString().compareTo(b.id().toString()));
        long recognised = run.rows.stream().filter(r -> r.spawned() ? r.live() : r.type().recognised()).count();
        long spawned = run.rows.stream().filter(Row::spawned).count();
        long problems = run.rows.stream().filter(r -> !r.problem().isEmpty()).count();
        String summary = String.format(Locale.ROOT, "Recognition audit done: %d entity types, %d spawned, %d recognised, %d with problems.",
                run.rows.size(), spawned, recognised, problems);
        String where;
        try {
            where = "Results: " + write(server, run, summary);
        } catch (IOException e) {
            where = "Couldn't write the results: " + e.getMessage();
        }
        ZooCraft.LOGGER.info("{} {}", summary, where);
        if (run.source != null) {
            String reply = summary + " " + where;
            run.source.sendSuccess(() -> Component.literal(reply), false);
        }
        if (unattended()) {
            finishUnattended(server);
        }
    }

    private static void finishUnattended(MinecraftServer server) {
        exitRequested = true;
        if (server.isDedicatedServer()) {
            server.halt(false);
        }
    }

    private static Path write(MinecraftServer server, Run run, String summary) throws IOException {
        Path logs = server.getServerDirectory().resolve("logs");
        Files.createDirectories(logs);
        Path csv = logs.resolve("zoocraft-recognition-audit.csv");
        try (BufferedWriter out = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            out.write("entity,mod,category,recognised,reason,constructed,spawned,live_recognised,in_census,kept,species,species_name,problem\n");
            for (Row r : run.rows) {
                boolean verdict = r.spawned() ? r.live() : r.type().recognised();
                out.write(String.join(",", r.id().toString(), r.id().getNamespace(), r.category(), String.valueOf(verdict),
                        r.type().reason(), String.valueOf(r.type().checked()), String.valueOf(r.spawned()), String.valueOf(r.live()),
                        String.valueOf(r.inCensus()), String.valueOf(r.kept()), r.species(), quote(r.speciesName()), quote(r.problem())) + "\n");
            }
        }
        Path txt = logs.resolve("zoocraft-recognition-audit.txt");
        List<String> lines = new ArrayList<>();
        lines.add(summary);
        lines.add("Zoos in the world when the audit started (" + run.zoosAtStart.size() + "):");
        run.zoosAtStart.forEach(z -> lines.add("  " + z));
        Map<String, Long> perMod = new java.util.TreeMap<>();
        run.rows.stream().filter(r -> r.spawned() ? r.live() : r.type().recognised())
                .forEach(r -> perMod.merge(r.id().getNamespace(), 1L, Long::sum));
        lines.add("Recognised per mod:");
        perMod.forEach((mod, n) -> lines.add("  " + mod + ": " + n));
        Files.write(txt, lines, StandardCharsets.UTF_8);
        return csv;
    }

    private static String describe(Zoo zoo) {
        return String.format(Locale.ROOT, "%s: %d gates, %d shops, %d toilets, treasury %d, %s", zoo.name(), zoo.gates().size(),
                zoo.shops().size(), zoo.toilets().size(), zoo.ledger().balance(), zoo.open() ? "open" : "closed");
    }

    private static String quote(String s) {
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }
}
