package com.zoocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zoocraft.ZooCraft;
import com.zoocraft.network.BoundaryDraftPayload;
import com.zoocraft.network.ZooOutlinesPayload;
import com.zoocraft.registry.ZooItems;
import com.zoocraft.zoo.BoundaryVertex;
import com.zoocraft.zoo.GateStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

/** Draws zoo outlines, gates and the player's draft while they hold a zoo tool. */
@EventBusSubscriber(modid = ZooCraft.MOD_ID, value = Dist.CLIENT)
public final class BoundaryRenderer {
    private static final float POST_HEIGHT = 2.5f;

    private BoundaryRenderer() {
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(ZooItems.isZooTool(player.getMainHandItem()) || ZooItems.isZooTool(player.getOffhandItem()))) {
            return;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        for (ZooOutlinesPayload.Outline outline : ClientBoundaryState.outlines.outlines()) {
            float[] colour = outline.managed() ? new float[]{0.2f, 0.9f, 0.3f} : new float[]{0.8f, 0.8f, 0.8f};
            drawRing(poseStack, lines, outline.boundary(), true, colour, -1);
            for (ZooOutlinesPayload.GateMarker gate : outline.gates()) {
                float[] c = switch (GateStatus.values()[Math.min(gate.status(), GateStatus.values().length - 1)]) {
                    case OPEN -> new float[]{0.2f, 1.0f, 0.2f};
                    case BLOCKED -> new float[]{1.0f, 0.2f, 0.2f};
                    case DETACHED -> new float[]{0.5f, 0.5f, 0.5f};
                };
                LevelRenderer.renderLineBox(poseStack, lines, new AABB(gate.pos()).setMaxY(gate.pos().getY() + 2), c[0], c[1], c[2], 1.0f);
            }
            drawFacilities(poseStack, lines, outline.facilities());
        }

        BoundaryDraftPayload draft = ClientBoundaryState.draft;
        if (draft.active()) {
            drawRing(poseStack, lines, draft.vertices(), draft.editing(), new float[]{1.0f, 0.85f, 0.1f}, draft.held());
        }

        poseStack.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static final float[] SHOP = {1.0f, 0.75f, 0.1f};
    private static final float[] TOILET = {0.3f, 0.6f, 1.0f};
    private static final float[] PROBLEM = {1.0f, 0.2f, 0.2f};

    /** Shop counters in gold with lines to their stock, toilets in blue, anything not working in red. */
    private static void drawFacilities(PoseStack poseStack, VertexConsumer lines, ZooOutlinesPayload.FacilityMarkers facilities) {
        PoseStack.Pose pose = poseStack.last();
        for (ZooOutlinesPayload.ShopMarker shop : facilities.shops()) {
            float[] c = shop.ok() ? SHOP : PROBLEM;
            LevelRenderer.renderLineBox(poseStack, lines, new AABB(shop.counter()).deflate(0.1).setMaxY(shop.counter().getY() + 0.25),
                    c[0], c[1], c[2], 1.0f);
            for (BlockPos container : shop.containers()) {
                LevelRenderer.renderLineBox(poseStack, lines, new AABB(container).inflate(0.02), c[0], c[1], c[2], 1.0f);
                line(lines, pose, shop.counter().getX() + 0.5f, shop.counter().getY() + 0.2f, shop.counter().getZ() + 0.5f,
                        container.getX() + 0.5f, container.getY() + 0.5f, container.getZ() + 0.5f, c);
            }
        }
        for (ZooOutlinesPayload.ToiletMarker toilet : facilities.toilets()) {
            float[] c = toilet.ok() ? TOILET : PROBLEM;
            LevelRenderer.renderLineBox(poseStack, lines, new AABB(toilet.pos()).deflate(0.15).setMaxY(toilet.pos().getY() + 1.8),
                    c[0], c[1], c[2], 1.0f);
        }
    }

    private static void drawRing(PoseStack poseStack, VertexConsumer lines, List<BoundaryVertex> vertices, boolean closed, float[] colour, int highlighted) {
        PoseStack.Pose pose = poseStack.last();
        int count = vertices.size();
        for (int i = 0; i < count; i++) {
            BoundaryVertex v = vertices.get(i);
            float x = v.x() + 0.5f, y = v.y() + 0.05f, z = v.z() + 0.5f;
            float[] postColour = i == highlighted ? new float[]{1.0f, 0.2f, 0.2f} : colour;
            line(lines, pose, x, y, z, x, y + POST_HEIGHT, z, postColour);
            if (i + 1 < count || (closed && count > 2)) {
                BoundaryVertex n = vertices.get((i + 1) % count);
                float nx = n.x() + 0.5f, ny = n.y() + 0.05f, nz = n.z() + 0.5f;
                line(lines, pose, x, y, z, nx, ny, nz, colour);
                line(lines, pose, x, y + 1, z, nx, ny + 1, nz, colour);
            }
        }
    }

    private static void line(VertexConsumer lines, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, float[] c) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1e-4f) {
            return;
        }
        dx /= length;
        dy /= length;
        dz /= length;
        lines.addVertex(pose, x1, y1, z1).setColor(c[0], c[1], c[2], 1.0f).setNormal(pose, dx, dy, dz);
        lines.addVertex(pose, x2, y2, z2).setColor(c[0], c[1], c[2], 1.0f).setNormal(pose, dx, dy, dz);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientBoundaryState.reset();
    }
}
