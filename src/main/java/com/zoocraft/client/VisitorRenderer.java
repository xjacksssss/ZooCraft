package com.zoocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zoocraft.visitor.Visitor;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.CrossedArmsItemLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.VillagerProfessionLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Draws visitors exactly like villagers (biome skin, no profession), with their current comment overhead. */
public class VisitorRenderer extends MobRenderer<Visitor, VillagerModel<Visitor>> {
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png");

    public VisitorRenderer(EntityRendererProvider.Context context) {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5f);
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        addLayer(new VillagerProfessionLayer<>(this, context.getResourceManager(), "villager"));
        addLayer(new CrossedArmsItemLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(Visitor visitor) {
        return TEXTURE;
    }

    @Override
    protected void scale(Visitor visitor, PoseStack poseStack, float partialTick) {
        poseStack.scale(0.9375f, 0.9375f, 0.9375f);
    }

    @Override
    protected boolean shouldShowName(Visitor visitor) {
        return (ZooClientConfig.SHOW_VISITOR_COMMENTS.get() && !visitor.currentCommentKey().isEmpty()) || super.shouldShowName(visitor);
    }

    @Override
    protected void renderNameTag(Visitor visitor, Component displayName, PoseStack poseStack, MultiBufferSource buffer, int packedLight, float partialTick) {
        String comment = visitor.currentCommentKey();
        Component text = ZooClientConfig.SHOW_VISITOR_COMMENTS.get() && !comment.isEmpty() ? Component.translatable(comment) : displayName;
        super.renderNameTag(visitor, text, poseStack, buffer, packedLight, partialTick);
    }
}
