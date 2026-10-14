package com.zoocraft.network;

import com.zoocraft.ZooCraft;
import com.zoocraft.dashboard.DashboardAction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The smaller dashboard payloads. */
public final class DashboardPayloads {
    private DashboardPayloads() {
    }

    /** Client to server: open the dashboard for a zoo, or for "the zoo I'm in / my only zoo" when empty. */
    public record OpenRequest(Optional<UUID> zooId) implements CustomPacketPayload {
        public static final Type<OpenRequest> TYPE = new Type<>(ZooCraft.id("dashboard_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OpenRequest> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), OpenRequest::zooId, OpenRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: the player manages several zoos and isn't standing in one; let them pick. */
    public record ZooList(List<Entry> zoos) implements CustomPacketPayload {
        public static final Type<ZooList> TYPE = new Type<>(ZooCraft.id("dashboard_zoo_list"));

        public record Entry(UUID id, String name, boolean open) {
            static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, Entry::id, ByteBufCodecs.STRING_UTF8, Entry::name, ByteBufCodecs.BOOL, Entry::open, Entry::new);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, ZooList> STREAM_CODEC = StreamCodec.composite(
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list(256)), ZooList::zoos, ZooList::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: a button on the dashboard. {@code number} and {@code text} depend on the action. */
    public record Action(UUID zooId, DashboardAction action, int number, String text) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ZooCraft.id("dashboard_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Action::zooId,
                ByteBufCodecs.VAR_INT.map(i -> DashboardAction.values()[Math.floorMod(i, DashboardAction.values().length)], DashboardAction::ordinal), Action::action,
                ByteBufCodecs.VAR_INT, Action::number,
                ByteBufCodecs.stringUtf8(160), Action::text,
                Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the player switched dashboard tab; send that tab's data from now on. */
    public record SetTab(int tab) implements CustomPacketPayload {
        public static final Type<SetTab> TYPE = new Type<>(ZooCraft.id("dashboard_tab"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetTab> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetTab::tab, SetTab::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the dashboard was closed, stop sending updates. */
    public record Close() implements CustomPacketPayload {
        public static final Close INSTANCE = new Close();
        public static final Type<Close> TYPE = new Type<>(ZooCraft.id("dashboard_close"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Close> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: the player lost access (or the zoo was deleted); close the dashboard. */
    public record Closed() implements CustomPacketPayload {
        public static final Closed INSTANCE = new Closed();
        public static final Type<Closed> TYPE = new Type<>(ZooCraft.id("dashboard_closed"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Closed> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
