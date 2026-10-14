package com.zoocraft.network;

import com.zoocraft.ZooCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server to client: everything the dashboard shows for one zoo. Built fresh by the server about once a second
 * for subscribed players; the client only displays it.
 *
 * @param openScreen true when the client should open the dashboard, false for a refresh
 */
public record DashboardSnapshot(
        boolean openScreen,
        UUID zooId, String name, String ownerName, boolean viewerIsOwner,
        boolean open, int price, int maxPrice, boolean keepLoaded, boolean chunkLoadingAllowed,
        long treasury, long admissionIncome, int attendanceToday, long attendanceTotal, long refused, int visitorsNow,
        float satisfaction, float reputation,
        List<Component> alerts, List<CommentLine> comments, List<SpeciesLine> species, List<ExhibitLine> exhibits,
        List<GateLine> gates, List<ManagerLine> managers) implements CustomPacketPayload {

    public static final Type<DashboardSnapshot> TYPE = new Type<>(ZooCraft.id("dashboard_snapshot"));

    public record CommentLine(Component text, int count) {
    }

    public record SpeciesLine(Component name, int count) {
    }

    /** @param problem true when visitors can't get close enough */
    public record ExhibitLine(Component label, int animals, int views, boolean problem) {
    }

    /** @param status ordinal of {@link com.zoocraft.zoo.GateStatus} */
    public record GateLine(BlockPos pos, int status) {
    }

    public record ManagerLine(UUID id, String name) {
    }

    public DashboardSnapshot withOpenScreen(boolean openScreen) {
        return new DashboardSnapshot(openScreen, zooId, name, ownerName, viewerIsOwner, open, price, maxPrice, keepLoaded,
                chunkLoadingAllowed, treasury, admissionIncome, attendanceToday, attendanceTotal, refused, visitorsNow,
                satisfaction, reputation, alerts, comments, species, exhibits, gates, managers);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DashboardSnapshot> STREAM_CODEC = StreamCodec.of(DashboardSnapshot::write, DashboardSnapshot::read);

    private static final StreamCodec<RegistryFriendlyByteBuf, Component> TEXT = ComponentSerialization.TRUSTED_STREAM_CODEC;
    private static final int MAX_LINES = 256;

    private static void write(RegistryFriendlyByteBuf buf, DashboardSnapshot s) {
        buf.writeBoolean(s.openScreen);
        buf.writeUUID(s.zooId);
        buf.writeUtf(s.name);
        buf.writeUtf(s.ownerName);
        buf.writeBoolean(s.viewerIsOwner);
        buf.writeBoolean(s.open);
        buf.writeVarInt(s.price);
        buf.writeVarInt(s.maxPrice);
        buf.writeBoolean(s.keepLoaded);
        buf.writeBoolean(s.chunkLoadingAllowed);
        buf.writeVarLong(s.treasury);
        buf.writeVarLong(s.admissionIncome);
        buf.writeVarInt(s.attendanceToday);
        buf.writeVarLong(s.attendanceTotal);
        buf.writeVarLong(s.refused);
        buf.writeVarInt(s.visitorsNow);
        buf.writeFloat(s.satisfaction);
        buf.writeFloat(s.reputation);
        writeList(buf, s.alerts, (b, c) -> TEXT.encode(b, c));
        writeList(buf, s.comments, (b, c) -> {
            TEXT.encode(b, c.text());
            b.writeVarInt(c.count());
        });
        writeList(buf, s.species, (b, c) -> {
            TEXT.encode(b, c.name());
            b.writeVarInt(c.count());
        });
        writeList(buf, s.exhibits, (b, e) -> {
            TEXT.encode(b, e.label());
            b.writeVarInt(e.animals());
            b.writeVarInt(e.views());
            b.writeBoolean(e.problem());
        });
        writeList(buf, s.gates, (b, g) -> {
            b.writeBlockPos(g.pos());
            b.writeVarInt(g.status());
        });
        writeList(buf, s.managers, (b, m) -> {
            b.writeUUID(m.id());
            b.writeUtf(m.name());
        });
    }

    private static DashboardSnapshot read(RegistryFriendlyByteBuf buf) {
        return new DashboardSnapshot(
                buf.readBoolean(), buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readBoolean(),
                buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(),
                readList(buf, TEXT::decode),
                readList(buf, b -> new CommentLine(TEXT.decode(b), b.readVarInt())),
                readList(buf, b -> new SpeciesLine(TEXT.decode(b), b.readVarInt())),
                readList(buf, b -> new ExhibitLine(TEXT.decode(b), b.readVarInt(), b.readVarInt(), b.readBoolean())),
                readList(buf, b -> new GateLine(b.readBlockPos(), b.readVarInt())),
                readList(buf, b -> new ManagerLine(b.readUUID(), b.readUtf())));
    }

    private interface Writer<T> {
        void write(RegistryFriendlyByteBuf buf, T value);
    }

    private interface Reader<T> {
        T read(RegistryFriendlyByteBuf buf);
    }

    private static <T> void writeList(RegistryFriendlyByteBuf buf, List<T> list, Writer<T> writer) {
        int size = Math.min(list.size(), MAX_LINES);
        buf.writeVarInt(size);
        for (int i = 0; i < size; i++) {
            writer.write(buf, list.get(i));
        }
    }

    private static <T> List<T> readList(RegistryFriendlyByteBuf buf, Reader<T> reader) {
        int size = Math.min(buf.readVarInt(), MAX_LINES);
        List<T> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(reader.read(buf));
        }
        return list;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
