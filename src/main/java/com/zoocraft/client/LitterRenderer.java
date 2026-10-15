package com.zoocraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zoocraft.litter.LitterEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Draws litter as its rubbish item lying flat on the ground: one copy per piece in the pile, a little scattered. */
public class LitterRenderer extends EntityRenderer<LitterEntity> {
    private final ItemRenderer itemRenderer;

    public LitterRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.shadowRadius = 0.15f;
    }

    @Override
    public void render(LitterEntity litter, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        ItemStack stack = litter.item();
        int pile = litter.pile();
        for (int i = 0; i < pile; i++) {
            poseStack.pushPose();
            // Spread the pieces of a pile around the centre in a fixed pattern, so they don't flicker.
            double angle = (litter.getId() * 37 + i * 97) % 360 * Math.PI / 180;
            double spread = i == 0 ? 0 : 0.18;
            poseStack.translate(Math.cos(angle) * spread, 0.02 + i * 0.01, Math.sin(angle) * spread);
            poseStack.mulPose(Axis.YP.rotationDegrees(litter.getYRot() + i * 47));
            poseStack.mulPose(Axis.XP.rotationDegrees(90));
            poseStack.scale(0.6f, 0.6f, 0.6f);
            itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, poseStack, buffers,
                    litter.level(), litter.getId() + i);
            poseStack.popPose();
        }
        super.render(litter, entityYaw, partialTick, poseStack, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(LitterEntity litter) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
