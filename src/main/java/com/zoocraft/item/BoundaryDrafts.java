package com.zoocraft.item;

import com.zoocraft.ZooCraft;
import com.zoocraft.network.BoundaryDraftPayload;
import com.zoocraft.zoo.ActionResult;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Per-player boundary drafts. Discarded on logout and when the server stops. */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class BoundaryDrafts {
    private static final Map<UUID, BoundaryDraft> DRAFTS = new HashMap<>();

    private BoundaryDrafts() {
    }

    public static Optional<BoundaryDraft> get(ServerPlayer player) {
        return Optional.ofNullable(DRAFTS.get(player.getUUID()));
    }

    public static void put(ServerPlayer player, BoundaryDraft draft) {
        DRAFTS.put(player.getUUID(), draft);
        sync(player);
    }

    public static void clear(ServerPlayer player) {
        DRAFTS.remove(player.getUUID());
        sync(player);
    }

    /** Sends the player's current draft (or none) to their client for drawing. */
    public static void sync(ServerPlayer player) {
        BoundaryDraft draft = DRAFTS.get(player.getUUID());
        BoundaryDraftPayload payload = draft == null
                ? BoundaryDraftPayload.NONE
                : new BoundaryDraftPayload(true, draft.mode() == BoundaryDraft.Mode.EDIT, draft.vertices(), draft.held());
        if (player.connection != null) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** Creates a zoo from a closed CREATE draft. A blank name cancels naming and lets the player keep drawing. */
    public static void finishCreate(ServerPlayer player, String name) {
        BoundaryDraft draft = DRAFTS.get(player.getUUID());
        if (draft == null || draft.mode() != BoundaryDraft.Mode.CREATE || !draft.closed()) {
            return;
        }
        draft.setClosed(false);
        if (name.isBlank()) {
            sync(player);
            return;
        }
        ActionResult result = ZooManager.get(player.server).createZoo(player, name, draft.dimension(), draft.vertices());
        player.sendSystemMessage(result.message().copy().withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (result.success()) {
            clear(player);
        } else {
            sync(player);
        }
    }

    /** Saves an EDIT draft as the zoo's new boundary. Used by {@code /zoo boundary save}. */
    public static ActionResult saveEdit(ServerPlayer player) {
        BoundaryDraft draft = DRAFTS.get(player.getUUID());
        if (draft == null || draft.mode() != BoundaryDraft.Mode.EDIT || draft.zooId() == null) {
            return ActionResult.fail(Component.translatable("zoocraft.tool.no_edit"));
        }
        ZooManager zoos = ZooManager.get(player.server);
        Optional<Zoo> zoo = zoos.byId(draft.zooId());
        if (zoo.isEmpty()) {
            clear(player);
            return ActionResult.fail(Component.translatable("zoocraft.msg.zoo_gone"));
        }
        ActionResult result = zoos.editBoundary(player, zoo.get(), draft.vertices());
        if (result.success()) {
            clear(player);
        }
        return result;
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DRAFTS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        DRAFTS.clear();
    }
}
