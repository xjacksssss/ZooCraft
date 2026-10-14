package com.zoocraft.client;

import com.zoocraft.network.CreateZooPayload;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Asks for a name after a new boundary is closed. Cancelling keeps the draft so the player can carry on. */
public class NameZooScreen extends Screen {
    private EditBox nameBox;
    private Button createButton;
    private boolean sent;

    public NameZooScreen() {
        super(Component.translatable("zoocraft.screen.name_zoo.title"));
    }

    @Override
    protected void init() {
        int centreX = width / 2;
        int y = height / 2 - 30;
        nameBox = new EditBox(font, centreX - 100, y, 200, 20, Component.translatable("zoocraft.screen.name_zoo.field"));
        nameBox.setMaxLength(ZooManager.MAX_NAME_LENGTH);
        nameBox.setResponder(text -> createButton.active = !text.isBlank());
        addRenderableWidget(nameBox);
        createButton = addRenderableWidget(Button.builder(Component.translatable("zoocraft.screen.name_zoo.create"), b -> send(nameBox.getValue()))
                .bounds(centreX - 100, y + 30, 98, 20).build());
        createButton.active = false;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose())
                .bounds(centreX + 2, y + 30, 98, 20).build());
        setInitialFocus(nameBox);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && !nameBox.getValue().isBlank()) {
            send(nameBox.getValue());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 55, 0xFFFFFF);
    }

    private void send(String name) {
        sent = true;
        PacketDistributor.sendToServer(new CreateZooPayload(name.trim()));
        super.onClose();
    }

    @Override
    public void onClose() {
        if (!sent) {
            PacketDistributor.sendToServer(new CreateZooPayload(""));
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
