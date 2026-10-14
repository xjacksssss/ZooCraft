package com.zoocraft.item;

import com.zoocraft.core.geometry.DraftOps;
import com.zoocraft.core.geometry.Vertex;
import com.zoocraft.network.OpenNameZooScreenPayload;
import com.zoocraft.zoo.BoundaryVertex;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import com.zoocraft.zoo.ZooPermission;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/** Draws new zoo boundaries and edits existing ones (spec section 6.3). */
public class BoundaryToolItem extends Item {
    private static final double PICK_DISTANCE = 1.5;

    public BoundaryToolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() instanceof ServerPlayer player && context.getLevel() instanceof ServerLevel level) {
            click(player, level, context.getClickedPos());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            Optional<BlockPos> target = ToolRaycast.target(level, player);
            if (target.isPresent()) {
                click(serverPlayer, serverLevel, target.get());
            } else {
                serverPlayer.displayClientMessage(Component.translatable("zoocraft.tool.no_target"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.zoocraft.boundary_tool.tip1").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.zoocraft.boundary_tool.tip2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.zoocraft.boundary_tool.tip3").withStyle(ChatFormatting.GRAY));
    }

    /** Handles a click on a block, from close up or through the long-range raycast. */
    public static void click(ServerPlayer player, ServerLevel level, BlockPos clicked) {
        boolean sneaking = player.isShiftKeyDown();
        BoundaryVertex vertex = new BoundaryVertex(clicked.getX(), clicked.getY() + 1, clicked.getZ());
        Optional<BoundaryDraft> existing = BoundaryDrafts.get(player);
        if (existing.isPresent() && !existing.get().dimension().equals(level.dimension())) {
            BoundaryDrafts.clear(player);
            existing = Optional.empty();
        }
        if (existing.isEmpty()) {
            start(player, level, clicked, vertex, sneaking);
            return;
        }
        BoundaryDraft draft = existing.get();
        if (draft.mode() == BoundaryDraft.Mode.CREATE) {
            clickCreate(player, draft, vertex, sneaking);
        } else {
            clickEdit(player, draft, vertex, sneaking);
        }
        BoundaryDrafts.sync(player);
    }

    private static void start(ServerPlayer player, ServerLevel level, BlockPos clicked, BoundaryVertex vertex, boolean sneaking) {
        ZooManager zoos = ZooManager.get(player.server);
        Optional<Zoo> zoo = zoos.zooAt(level, clicked);
        if (zoo.isPresent()) {
            if (!ZooManager.can(player, zoo.get(), ZooPermission.EDIT)) {
                player.displayClientMessage(Component.translatable("zoocraft.tool.belongs_to", zoo.get().name()), true);
                return;
            }
            BoundaryDrafts.put(player, BoundaryDraft.edit(zoo.get().id(), level.dimension(), zoo.get().boundary()));
            player.sendSystemMessage(Component.translatable("zoocraft.tool.editing", zoo.get().name())
                    .append(" ").append(button("zoocraft.button.save", "/zoo boundary save", ChatFormatting.GREEN))
                    .append(" ").append(button("zoocraft.button.cancel", "/zoo boundary cancel", ChatFormatting.RED)));
            return;
        }
        if (sneaking) {
            return;
        }
        BoundaryDraft draft = BoundaryDraft.create(level.dimension());
        draft.setVertices(List.of(vertex));
        BoundaryDrafts.put(player, draft);
        player.sendSystemMessage(Component.translatable("zoocraft.tool.started")
                .append(" ").append(button("zoocraft.button.discard", "/zoo boundary discard", ChatFormatting.RED)));
    }

    private static void clickCreate(ServerPlayer player, BoundaryDraft draft, BoundaryVertex vertex, boolean sneaking) {
        List<BoundaryVertex> vertices = draft.vertices();
        if (draft.closed()) {
            // Waiting for a name; clicking again reopens the naming screen.
            PacketDistributor.sendToPlayer(player, OpenNameZooScreenPayload.INSTANCE);
            return;
        }
        if (sneaking) {
            if (vertices.size() <= 1) {
                BoundaryDrafts.clear(player);
                player.displayClientMessage(Component.translatable("zoocraft.tool.discarded"), true);
            } else {
                draft.setVertices(vertices.subList(0, vertices.size() - 1));
                player.displayClientMessage(Component.translatable("zoocraft.tool.undone", vertices.size() - 1), true);
            }
            return;
        }
        BoundaryVertex first = vertices.getFirst();
        if (vertices.size() >= 3 && Math.hypot(first.x() - vertex.x(), first.z() - vertex.z()) <= PICK_DISTANCE) {
            draft.setClosed(true);
            PacketDistributor.sendToPlayer(player, OpenNameZooScreenPayload.INSTANCE);
            return;
        }
        if (vertices.getLast().toVertex().equals(vertex.toVertex())) {
            return;
        }
        draft.setVertices(DraftOps.insertAfter(vertices, vertices.size() - 1, vertex));
        player.displayClientMessage(Component.translatable(vertices.size() + 1 >= 3 ? "zoocraft.tool.corner_added_closeable" : "zoocraft.tool.corner_added",
                vertices.size() + 1), true);
    }

    private static void clickEdit(ServerPlayer player, BoundaryDraft draft, BoundaryVertex vertex, boolean sneaking) {
        List<BoundaryVertex> vertices = draft.vertices();
        List<Vertex> flat = vertices.stream().map(BoundaryVertex::toVertex).toList();
        if (draft.held() >= 0) {
            draft.setVertices(DraftOps.move(vertices, draft.held(), vertex));
            draft.setHeld(-1);
            player.displayClientMessage(Component.translatable("zoocraft.tool.corner_placed"), true);
            return;
        }
        int corner = DraftOps.nearestVertex(flat, vertex.x(), vertex.z(), PICK_DISTANCE);
        if (sneaking) {
            if (corner >= 0) {
                if (vertices.size() <= 3) {
                    player.displayClientMessage(Component.translatable("zoocraft.boundary.too_few_corners"), true);
                    return;
                }
                draft.setVertices(DraftOps.remove(vertices, corner));
                player.displayClientMessage(Component.translatable("zoocraft.tool.corner_deleted"), true);
                return;
            }
            int edge = DraftOps.nearestEdge(flat, vertex.x(), vertex.z(), PICK_DISTANCE);
            if (edge >= 0) {
                draft.setVertices(DraftOps.insertAfter(vertices, edge, vertex));
                player.displayClientMessage(Component.translatable("zoocraft.tool.corner_inserted"), true);
                return;
            }
            player.displayClientMessage(Component.translatable("zoocraft.tool.edit_hint"), true);
            return;
        }
        if (corner >= 0) {
            draft.setHeld(corner);
            player.displayClientMessage(Component.translatable("zoocraft.tool.corner_picked"), true);
        } else {
            player.displayClientMessage(Component.translatable("zoocraft.tool.edit_hint"), true);
        }
    }

    static MutableComponent button(String key, String command, ChatFormatting colour) {
        return Component.literal("[").append(Component.translatable(key)).append("]").withStyle(style -> style
                .withColor(colour)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }
}
