package com.zoocraft.network;

import com.zoocraft.ZooCraft;
import com.zoocraft.zoo.BoundaryVertex;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.UUID;

/** Server to client: outlines and gates of zoos near a player who is holding a zoo tool. */
public record ZooOutlinesPayload(List<Outline> outlines) implements CustomPacketPayload {
    public static final Type<ZooOutlinesPayload> TYPE = new Type<>(ZooCraft.id("zoo_outlines"));
    public static final ZooOutlinesPayload NONE = new ZooOutlinesPayload(List.of());

    /** @param status ordinal of {@link com.zoocraft.zoo.GateStatus} */
    public record GateMarker(BlockPos pos, int status) {
        static final StreamCodec<ByteBuf, GateMarker> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, GateMarker::pos,
                ByteBufCodecs.VAR_INT, GateMarker::status,
                GateMarker::new);
    }

    /** @param ok false when the shop is detached, closed or has no stock containers */
    public record ShopMarker(BlockPos counter, List<BlockPos> containers, boolean ok) {
        static final StreamCodec<ByteBuf, ShopMarker> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ShopMarker::counter,
                BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list(32)), ShopMarker::containers,
                ByteBufCodecs.BOOL, ShopMarker::ok,
                ShopMarker::new);
    }

    /** @param ok false when the toilet is detached */
    public record ToiletMarker(BlockPos pos, boolean ok) {
        static final StreamCodec<ByteBuf, ToiletMarker> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, ToiletMarker::pos,
                ByteBufCodecs.BOOL, ToiletMarker::ok,
                ToiletMarker::new);
    }

    public record FacilityMarkers(List<ShopMarker> shops, List<ToiletMarker> toilets) {
        public static final FacilityMarkers NONE = new FacilityMarkers(List.of(), List.of());
        static final StreamCodec<ByteBuf, FacilityMarkers> STREAM_CODEC = StreamCodec.composite(
                ShopMarker.STREAM_CODEC.apply(ByteBufCodecs.list(256)), FacilityMarkers::shops,
                ToiletMarker.STREAM_CODEC.apply(ByteBufCodecs.list(256)), FacilityMarkers::toilets,
                FacilityMarkers::new);
    }

    public record Outline(UUID id, String name, boolean managed, List<BoundaryVertex> boundary, List<GateMarker> gates,
                          FacilityMarkers facilities) {
        static final StreamCodec<ByteBuf, Outline> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Outline::id,
                ByteBufCodecs.STRING_UTF8, Outline::name,
                ByteBufCodecs.BOOL, Outline::managed,
                BoundaryVertex.STREAM_CODEC.apply(ByteBufCodecs.list(1024)), Outline::boundary,
                GateMarker.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Outline::gates,
                FacilityMarkers.STREAM_CODEC, Outline::facilities,
                Outline::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, ZooOutlinesPayload> STREAM_CODEC = StreamCodec.composite(
            Outline.STREAM_CODEC.apply(ByteBufCodecs.list(64)), ZooOutlinesPayload::outlines,
            ZooOutlinesPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
