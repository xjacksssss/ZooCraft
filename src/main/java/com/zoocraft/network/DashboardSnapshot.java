package com.zoocraft.network;

import com.zoocraft.ZooCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server to client: what the dashboard shows for one zoo. Built fresh by the server about once a second for
 * subscribed players; the client only displays it. The header, alerts, comments and gates are always filled;
 * species and exhibits only for the Animals tab, shops and toilets only for the Shops tab, and managers only for
 * the Settings tab (Phase 2 spec section 4.5).
 *
 * @param openScreen true when the client should open the dashboard, false for a refresh
 * @param tab        ordinal of {@link com.zoocraft.dashboard.DashboardTab} this snapshot was built for
 */
public record DashboardSnapshot(
        boolean openScreen, int tab,
        UUID zooId, String name, String ownerName, boolean viewerIsOwner,
        boolean open, int price, int maxPrice, boolean keepLoaded, boolean chunkLoadingAllowed,
        long treasury, long admissionIncome, long shopIncome, int attendanceToday, long attendanceTotal, long refused, int visitorsNow,
        float satisfaction, float reputation, float cleanliness, long litterCollected,
        List<Component> alerts, List<CommentLine> comments, List<SpeciesLine> species, List<ExhibitLine> exhibits,
        List<GateLine> gates, List<ManagerLine> managers, List<ShopLine> shops, List<ToiletLine> toilets,
        List<Component> advice, int recommended, int recommendSample, VisitorPanel visitors, FinancePanel finances) implements CustomPacketPayload {

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

    public static final int SHOP_OPEN = 0, SHOP_CLOSED = 1, SHOP_SOLD_OUT = 2, SHOP_DETACHED = 3;

    /**
     * @param status       one of {@link #SHOP_OPEN}, {@link #SHOP_CLOSED}, {@link #SHOP_SOLD_OUT}, {@link #SHOP_DETACHED}
     * @param stockUnknown some stock container is in an unloaded area
     */
    public record ShopLine(UUID id, String name, int status, long revenueToday, int containers, boolean stockUnknown,
                           List<ProductLine> products) {
    }

    /** @param category ordinal of {@link com.zoocraft.core.shop.ProductCategory} */
    public record ProductLine(ItemStack icon, String itemId, int category, int stock, int price, int soldToday, int missedToday) {
    }

    public record ToiletLine(BlockPos pos, boolean detached) {
    }

    /** Today's visitors, for the Visitors tab (Phase 2 spec section 10.2). */
    public record VisitorPanel(int arrivals, int refusals, int departures, double averageMinutes, double averageSpend,
                               int budget, int enthusiast, int general, int meals, int hungryUnmet, int toiletVisits, int toiletUnmet) {
        public static final VisitorPanel EMPTY = new VisitorPanel(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    /** One day's income for the bar chart. */
    public record DayBar(long day, long admission, long shop) {
    }

    public record ShopRevenue(String name, long today, long week, long total) {
    }

    /** Money, for the Finances tab (Phase 2 spec section 10.2). */
    public record FinancePanel(long admissionToday, long shopToday, long depositsToday, long withdrawalsToday,
                               List<DayBar> days, List<ShopRevenue> shops, List<Component> recent) {
        public static final FinancePanel EMPTY = new FinancePanel(0, 0, 0, 0, List.of(), List.of(), List.of());
    }

    public DashboardSnapshot withOpenScreen(boolean openScreen) {
        return new DashboardSnapshot(openScreen, tab, zooId, name, ownerName, viewerIsOwner, open, price, maxPrice, keepLoaded,
                chunkLoadingAllowed, treasury, admissionIncome, shopIncome, attendanceToday, attendanceTotal, refused, visitorsNow,
                satisfaction, reputation, cleanliness, litterCollected, alerts, comments, species, exhibits, gates, managers, shops, toilets,
                advice, recommended, recommendSample, visitors, finances);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, DashboardSnapshot> STREAM_CODEC = StreamCodec.of(DashboardSnapshot::write, DashboardSnapshot::read);

    private static final StreamCodec<RegistryFriendlyByteBuf, Component> TEXT = ComponentSerialization.TRUSTED_STREAM_CODEC;
    private static final int MAX_LINES = 256;

    private static void write(RegistryFriendlyByteBuf buf, DashboardSnapshot s) {
        buf.writeBoolean(s.openScreen);
        buf.writeVarInt(s.tab);
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
        buf.writeVarLong(s.shopIncome);
        buf.writeVarInt(s.attendanceToday);
        buf.writeVarLong(s.attendanceTotal);
        buf.writeVarLong(s.refused);
        buf.writeVarInt(s.visitorsNow);
        buf.writeFloat(s.satisfaction);
        buf.writeFloat(s.reputation);
        buf.writeFloat(s.cleanliness);
        buf.writeVarLong(s.litterCollected);
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
        writeList(buf, s.shops, (b, shop) -> {
            b.writeUUID(shop.id());
            b.writeUtf(shop.name());
            b.writeVarInt(shop.status());
            b.writeVarLong(shop.revenueToday());
            b.writeVarInt(shop.containers());
            b.writeBoolean(shop.stockUnknown());
            writeList(b, shop.products(), (pb, p) -> {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(pb, p.icon());
                pb.writeUtf(p.itemId());
                pb.writeVarInt(p.category());
                pb.writeVarInt(p.stock());
                pb.writeVarInt(p.price());
                pb.writeVarInt(p.soldToday());
                pb.writeVarInt(p.missedToday());
            });
        });
        writeList(buf, s.toilets, (b, t) -> {
            b.writeBlockPos(t.pos());
            b.writeBoolean(t.detached());
        });
        writeList(buf, s.advice, (b, c) -> TEXT.encode(b, c));
        buf.writeVarInt(s.recommended);
        buf.writeVarInt(s.recommendSample);
        VisitorPanel v = s.visitors;
        for (int n : new int[]{v.arrivals(), v.refusals(), v.departures(), v.budget(), v.enthusiast(), v.general(), v.meals(),
                v.hungryUnmet(), v.toiletVisits(), v.toiletUnmet()}) {
            buf.writeVarInt(n);
        }
        buf.writeDouble(v.averageMinutes());
        buf.writeDouble(v.averageSpend());
        FinancePanel f = s.finances;
        buf.writeVarLong(f.admissionToday());
        buf.writeVarLong(f.shopToday());
        buf.writeVarLong(f.depositsToday());
        buf.writeVarLong(f.withdrawalsToday());
        writeList(buf, f.days(), (b, d) -> {
            b.writeVarLong(d.day());
            b.writeVarLong(d.admission());
            b.writeVarLong(d.shop());
        });
        writeList(buf, f.shops(), (b, r) -> {
            b.writeUtf(r.name());
            b.writeVarLong(r.today());
            b.writeVarLong(r.week());
            b.writeVarLong(r.total());
        });
        writeList(buf, f.recent(), (b, c) -> TEXT.encode(b, c));
    }

    private static VisitorPanel readVisitors(RegistryFriendlyByteBuf buf) {
        int[] n = new int[10];
        for (int i = 0; i < n.length; i++) {
            n[i] = buf.readVarInt();
        }
        double minutes = buf.readDouble();
        double spend = buf.readDouble();
        return new VisitorPanel(n[0], n[1], n[2], minutes, spend, n[3], n[4], n[5], n[6], n[7], n[8], n[9]);
    }

    private static FinancePanel readFinances(RegistryFriendlyByteBuf buf) {
        return new FinancePanel(buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarLong(),
                readList(buf, b -> new DayBar(b.readVarLong(), b.readVarLong(), b.readVarLong())),
                readList(buf, b -> new ShopRevenue(b.readUtf(), b.readVarLong(), b.readVarLong(), b.readVarLong())),
                readList(buf, TEXT::decode));
    }

    private static DashboardSnapshot read(RegistryFriendlyByteBuf buf) {
        return new DashboardSnapshot(
                buf.readBoolean(), buf.readVarInt(), buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readBoolean(),
                buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(),
                buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarLong(),
                readList(buf, TEXT::decode),
                readList(buf, b -> new CommentLine(TEXT.decode(b), b.readVarInt())),
                readList(buf, b -> new SpeciesLine(TEXT.decode(b), b.readVarInt())),
                readList(buf, b -> new ExhibitLine(TEXT.decode(b), b.readVarInt(), b.readVarInt(), b.readBoolean())),
                readList(buf, b -> new GateLine(b.readBlockPos(), b.readVarInt())),
                readList(buf, b -> new ManagerLine(b.readUUID(), b.readUtf())),
                readList(buf, b -> new ShopLine(b.readUUID(), b.readUtf(), b.readVarInt(), b.readVarLong(), b.readVarInt(), b.readBoolean(),
                        readList(b, pb -> new ProductLine(ItemStack.OPTIONAL_STREAM_CODEC.decode(pb), pb.readUtf(), pb.readVarInt(),
                                pb.readVarInt(), pb.readVarInt(), pb.readVarInt(), pb.readVarInt())))),
                readList(buf, b -> new ToiletLine(b.readBlockPos(), b.readBoolean())),
                readList(buf, TEXT::decode), buf.readVarInt(), buf.readVarInt(), readVisitors(buf), readFinances(buf));
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
