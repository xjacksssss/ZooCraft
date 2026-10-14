package com.zoocraft.client;

import com.zoocraft.network.DashboardPayloads;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Optional;

/** Lets a player who manages several zoos choose which dashboard to open. */
public class ZooPickerScreen extends Screen {
    private static final int ROWS = 8;
    private final List<DashboardPayloads.ZooList.Entry> zoos;
    private int page;

    public ZooPickerScreen(List<DashboardPayloads.ZooList.Entry> zoos) {
        super(Component.translatable("zoocraft.screen.picker.title"));
        this.zoos = zoos;
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - ROWS * 12;
        int start = page * ROWS;
        for (int i = start; i < Math.min(zoos.size(), start + ROWS); i++) {
            DashboardPayloads.ZooList.Entry entry = zoos.get(i);
            Component label = Component.literal(entry.name()).append(" ")
                    .append(Component.translatable(entry.open() ? "zoocraft.status.open" : "zoocraft.status.closed"));
            addRenderableWidget(Button.builder(label, b -> {
                PacketDistributor.sendToServer(new DashboardPayloads.OpenRequest(Optional.of(entry.id())));
                onClose();
            }).bounds(x, y + (i - start) * 24, 200, 20).build());
        }
        int bottom = y + ROWS * 24 + 4;
        if (page > 0) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page--;
                rebuildWidgets();
            }).bounds(x, bottom, 20, 20).build());
        }
        if ((page + 1) * ROWS < zoos.size()) {
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page++;
                rebuildWidgets();
            }).bounds(x + 180, bottom, 20, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(x + 50, bottom, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - ROWS * 12 - 16, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
