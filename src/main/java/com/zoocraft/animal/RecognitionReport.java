package com.zoocraft.animal;

import com.zoocraft.ZooCraft;
import com.zoocraft.core.animal.RecognitionRules;
import com.zoocraft.core.animal.RecognitionVerdict;
import com.zoocraft.visitor.Visitor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * {@code /zoo debug recognise}: the recognition verdict for every entity type (Phase 3 spec section 6.3). Entities are
 * constructed but never added to the world.
 */
public final class RecognitionReport {
    public record Entry(ResourceLocation id, boolean recognised, String reason, boolean checked) {
    }

    private RecognitionReport() {
    }

    public static List<Entry> run(ServerLevel level, @Nullable String namespace) {
        return run(level, namespace, type -> type.create(level));
    }

    public static List<Entry> run(ServerLevel level, @Nullable String namespace, Function<EntityType<?>, Entity> factory) {
        List<Entry> entries = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (namespace == null || id.getNamespace().equals(namespace)) {
                entries.add(check(id, type, level, factory));
            }
        }
        entries.sort(Comparator.comparing(e -> e.id().toString()));
        return entries;
    }

    public static Entry check(ResourceLocation id, EntityType<?> type, ServerLevel level, Function<EntityType<?>, Entity> factory) {
        Entity entity;
        try {
            entity = factory.apply(type);
        } catch (Exception | LinkageError e) {
            ZooCraft.LOGGER.debug("Couldn't construct {} for the recognition report", id, e);
            entity = null;
        }
        if (entity == null) {
            RecognitionVerdict verdict = RecognitionRules.decide(AnimalRecognition.factsForType(type));
            return new Entry(id, verdict.recognised(), verdict.reason().label(), false);
        }
        try {
            if (entity instanceof Visitor) {
                return new Entry(id, false, "ZooCraft visitor", true);
            }
            if (!(entity instanceof Mob)) {
                return new Entry(id, false, "not a mob", true);
            }
            RecognitionVerdict verdict = RecognitionRules.decide(AnimalRecognition.facts(entity));
            return new Entry(id, verdict.recognised(), verdict.reason().label(), true);
        } finally {
            entity.discard();
        }
    }

    public static Path writeCsv(MinecraftServer server, List<Entry> entries) throws IOException {
        Path file = server.getServerDirectory().resolve("logs").resolve("zoocraft-recognition.csv");
        Files.createDirectories(file.getParent());
        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write("entity,recognised,reason,checked\n");
            for (Entry e : entries) {
                out.write(e.id() + "," + e.recognised() + "," + e.reason() + "," + e.checked() + "\n");
            }
        }
        return file;
    }
}
