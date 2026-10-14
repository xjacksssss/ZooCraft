package com.zoocraft.client;

import com.zoocraft.dashboard.DashboardAction;
import com.zoocraft.network.DashboardPayloads;
import com.zoocraft.network.DashboardSnapshot;
import com.zoocraft.zoo.GateStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The zoo dashboard (spec section 9.2): Overview, Animals and Settings. Shows the latest server snapshot and sends
 * button presses to the server; it never changes zoo data itself.
 */
public class DashboardScreen extends Screen {
    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 236;
    private static final int LINE = 11;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int MUTED = 0xFFA0A0A0;
    private static final int HEADING = 0xFFFFD866;
    private static final int WARNING = 0xFFFF7060;

    private enum Tab { OVERVIEW, ANIMALS, SETTINGS }

    private Tab tab = Tab.OVERVIEW;
    private int scroll;
    private Object layoutKey;
    private int left, top;

    // Settings inputs survive widget rebuilds.
    private String priceText = "";
    private String amountText = "";
    private String managerText = "";
    private String transferText = "";
    private boolean confirmTransfer;
    private EditBox priceBox, amountBox, managerBox, transferBox;

    public DashboardScreen() {
        super(Component.translatable("zoocraft.screen.dashboard.title"));
    }

    private DashboardSnapshot snapshot() {
        return ClientDashboardState.current;
    }

    @Override
    protected void init() {
        DashboardSnapshot s = snapshot();
        if (s == null) {
            return;
        }
        left = (width - PANEL_WIDTH) / 2;
        top = (height - PANEL_HEIGHT) / 2;
        layoutKey = layoutKey(s);
        if (priceText.isEmpty()) {
            priceText = Integer.toString(s.price());
        }
        int tabWidth = (PANEL_WIDTH - 16) / 3;
        for (Tab t : Tab.values()) {
            Button button = Button.builder(Component.translatable("zoocraft.screen.dashboard.tab." + t.name().toLowerCase(java.util.Locale.ROOT)), b -> {
                tab = t;
                scroll = 0;
                rebuildWidgets();
            }).bounds(left + 8 + t.ordinal() * tabWidth, top + 22, tabWidth - 2, 18).build();
            button.active = tab != t;
            addRenderableWidget(button);
        }
        if (tab == Tab.SETTINGS) {
            initSettings(s);
        }
    }

    private void initSettings(DashboardSnapshot s) {
        int x = left + 10;
        int y = top + 48;
        addRenderableWidget(Button.builder(Component.translatable(s.open() ? "zoocraft.screen.dashboard.close_zoo" : "zoocraft.screen.dashboard.open_zoo"),
                b -> send(s.open() ? DashboardAction.CLOSE_ZOO : DashboardAction.OPEN_ZOO, 0, "")).bounds(x, y, 110, 18).build());
        Button keepLoaded = Button.builder(Component.translatable(s.keepLoaded() ? "zoocraft.screen.dashboard.keep_loaded_on" : "zoocraft.screen.dashboard.keep_loaded_off"),
                b -> send(DashboardAction.KEEP_LOADED, s.keepLoaded() ? 0 : 1, "")).bounds(x + 116, y, 150, 18).build();
        keepLoaded.active = s.chunkLoadingAllowed() || s.keepLoaded();
        addRenderableWidget(keepLoaded);

        y += 26;
        priceBox = numberBox(x + 110, y, 50, priceText);
        priceBox.setResponder(text -> priceText = text);
        addRenderableWidget(Button.builder(Component.translatable("zoocraft.screen.dashboard.set"),
                b -> parse(priceText).ifPresent(p -> send(DashboardAction.SET_PRICE, p, ""))).bounds(x + 166, y, 50, 18).build());

        y += 24;
        amountBox = numberBox(x + 110, y, 50, amountText);
        amountBox.setResponder(text -> amountText = text);
        addRenderableWidget(Button.builder(Component.translatable("zoocraft.screen.dashboard.deposit"),
                b -> parse(amountText).ifPresent(a -> send(DashboardAction.DEPOSIT, a, ""))).bounds(x + 166, y, 70, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("zoocraft.screen.dashboard.withdraw"),
                b -> parse(amountText).ifPresent(a -> send(DashboardAction.WITHDRAW, a, ""))).bounds(x + 240, y, 70, 18).build());

        y += 34;
        if (s.viewerIsOwner()) {
            int row = 0;
            for (DashboardSnapshot.ManagerLine manager : s.managers().stream().limit(3).toList()) {
                addRenderableWidget(Button.builder(Component.translatable("zoocraft.screen.dashboard.remove"),
                        b -> send(DashboardAction.REMOVE_MANAGER, 0, manager.id().toString()))
                        .bounds(x + 270, y + row * 12 - 2, 64, 12).build());
                row++;
            }
            y += 38;
            managerBox = textBox(x + 110, y, 150, managerText);
            managerBox.setResponder(text -> managerText = text);
            addRenderableWidget(Button.builder(Component.translatable("zoocraft.screen.dashboard.add_manager"), b -> {
                if (!managerText.isBlank()) {
                    send(DashboardAction.ADD_MANAGER, 0, managerText.trim());
                    managerText = "";
                    managerBox.setValue("");
                }
            }).bounds(x + 266, y, 70, 18).build());

            y += 24;
            transferBox = textBox(x + 110, y, 150, transferText);
            transferBox.setResponder(text -> {
                transferText = text;
                confirmTransfer = false;
            });
            addRenderableWidget(Button.builder(Component.translatable(confirmTransfer ? "zoocraft.screen.dashboard.confirm" : "zoocraft.screen.dashboard.transfer"), b -> {
                if (transferText.isBlank()) {
                    return;
                }
                if (!confirmTransfer) {
                    confirmTransfer = true;
                    rebuildWidgets();
                    return;
                }
                send(DashboardAction.TRANSFER, 0, transferText.trim());
                confirmTransfer = false;
                transferText = "";
                rebuildWidgets();
            }).bounds(x + 266, y, 70, 18).build());
        }
    }

    private EditBox numberBox(int x, int y, int w, String value) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setFilter(text -> text.isEmpty() || text.matches("\\d{0,6}"));
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private EditBox textBox(int x, int y, int w, String value) {
        EditBox box = new EditBox(font, x, y, w, 18, Component.empty());
        box.setMaxLength(16);
        box.setValue(value);
        addRenderableWidget(box);
        return box;
    }

    private static java.util.Optional<Integer> parse(String text) {
        try {
            return text.isBlank() ? java.util.Optional.empty() : java.util.Optional.of(Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return java.util.Optional.empty();
        }
    }

    private void send(DashboardAction action, int number, String text) {
        DashboardSnapshot s = snapshot();
        if (s != null) {
            PacketDistributor.sendToServer(new DashboardPayloads.Action(s.zooId(), action, number, text));
        }
    }

    /** Things that change which widgets exist; values shown as text don't need a rebuild. */
    private static Object layoutKey(DashboardSnapshot s) {
        return List.of(s.zooId(), s.viewerIsOwner(), s.open(), s.keepLoaded(), s.chunkLoadingAllowed(),
                s.managers().stream().map(DashboardSnapshot.ManagerLine::id).toList());
    }

    /** Called when a new snapshot arrives. */
    void onSnapshot() {
        DashboardSnapshot s = snapshot();
        if (s != null && !Objects.equals(layoutKey, layoutKey(s))) {
            rebuildWidgets();
        }
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xE0101418);
        graphics.renderOutline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF5A6B4A);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        DashboardSnapshot s = snapshot();
        if (s == null) {
            return;
        }
        Component status = Component.translatable(s.open() ? "zoocraft.status.open" : "zoocraft.status.closed")
                .withStyle(s.open() ? ChatFormatting.GREEN : ChatFormatting.RED);
        graphics.drawString(font, Component.literal(s.name()).withStyle(ChatFormatting.BOLD).append(" ").append(status), left + 10, top + 8, 0xFFFFFFFF);
        Component owner = Component.translatable("zoocraft.screen.dashboard.owner", s.ownerName());
        graphics.drawString(font, owner, left + PANEL_WIDTH - 10 - font.width(owner), top + 8, MUTED);
        switch (tab) {
            case OVERVIEW -> renderOverview(graphics, s);
            case ANIMALS -> renderAnimals(graphics, s);
            case SETTINGS -> renderSettings(graphics, s);
        }
    }

    private void renderOverview(GuiGraphics g, DashboardSnapshot s) {
        int x = left + 10;
        int y = top + 48;
        g.drawString(font, Component.translatable("zoocraft.screen.dashboard.visitors_now", s.visitorsNow()), x, y, TEXT);
        g.drawString(font, Component.translatable("zoocraft.screen.dashboard.visitors_today", s.attendanceToday(), s.attendanceTotal()), x, y += LINE, TEXT);
        g.drawString(font, Component.translatable("zoocraft.screen.dashboard.refused", s.refused()), x, y += LINE, TEXT);
        g.drawString(font, Component.translatable("zoocraft.screen.dashboard.treasury", s.treasury()), x, y += LINE + 4, TEXT);
        g.drawString(font, Component.translatable("zoocraft.screen.dashboard.admission", s.price(), s.admissionIncome()), x, y += LINE, TEXT);
        y += LINE + 4;
        bar(g, x, y, Component.translatable("zoocraft.screen.dashboard.satisfaction"), s.satisfaction());
        bar(g, x, y + LINE + 2, Component.translatable("zoocraft.screen.dashboard.reputation"), s.reputation());
        long openGates = s.gates().stream().filter(gate -> gate.status() == GateStatus.OPEN.ordinal()).count();
        g.drawString(font, Component.translatable("zoocraft.screen.dashboard.gates", s.gates().size(), openGates), x, y + 2 * LINE + 10, MUTED);

        int rx = left + PANEL_WIDTH / 2 + 6;
        int width = PANEL_WIDTH / 2 - 16;
        int ry = top + 48;
        g.drawString(font, Component.translatable("zoocraft.screen.dashboard.attention"), rx, ry, HEADING);
        ry += LINE;
        if (s.alerts().isEmpty()) {
            g.drawString(font, Component.translatable("zoocraft.screen.dashboard.all_good"), rx, ry, MUTED);
            ry += LINE;
        }
        for (Component alert : s.alerts().stream().limit(4).toList()) {
            ry = wrapped(g, alert, rx, ry, width, WARNING);
        }
        ry += 4;
        g.drawString(font, Component.translatable("zoocraft.screen.dashboard.saying"), rx, ry, HEADING);
        ry += LINE;
        if (s.comments().isEmpty()) {
            g.drawString(font, Component.translatable("zoocraft.screen.dashboard.no_comments"), rx, ry, MUTED);
        }
        for (DashboardSnapshot.CommentLine comment : s.comments()) {
            if (ry > top + PANEL_HEIGHT - 14) {
                break;
            }
            ry = wrapped(g, Component.literal(comment.count() + "× ").append(comment.text()), rx, ry, width, TEXT);
        }
    }

    private void renderAnimals(GuiGraphics g, DashboardSnapshot s) {
        List<LineEntry> lines = new ArrayList<>();
        lines.add(new LineEntry(Component.translatable("zoocraft.screen.dashboard.species", s.species().size()), HEADING));
        if (s.species().isEmpty()) {
            lines.add(new LineEntry(Component.translatable("zoocraft.alert.no_animals"), MUTED));
        }
        for (DashboardSnapshot.SpeciesLine species : s.species()) {
            lines.add(new LineEntry(Component.literal("  ").append(species.name()).append(" ×" + species.count()), TEXT));
        }
        lines.add(new LineEntry(Component.empty(), TEXT));
        lines.add(new LineEntry(Component.translatable("zoocraft.screen.dashboard.exhibits", s.exhibits().size()), HEADING));
        for (DashboardSnapshot.ExhibitLine exhibit : s.exhibits()) {
            lines.add(new LineEntry(Component.literal("  ").append(exhibit.label()), TEXT));
            lines.add(new LineEntry(Component.translatable("zoocraft.screen.dashboard.exhibit_line", exhibit.animals(), exhibit.views()), MUTED));
            if (exhibit.problem()) {
                lines.add(new LineEntry(Component.translatable("zoocraft.screen.dashboard.exhibit_problem"), WARNING));
            }
        }
        int visible = (PANEL_HEIGHT - 56) / LINE;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visible)));
        int y = top + 48;
        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
            g.drawString(font, lines.get(i).text(), left + 10, y, lines.get(i).colour());
            y += LINE;
        }
        if (lines.size() > visible) {
            g.drawString(font, Component.translatable("zoocraft.screen.dashboard.scroll"), left + PANEL_WIDTH - 90, top + PANEL_HEIGHT - 12, MUTED);
        }
    }

    private record LineEntry(Component text, int colour) {
    }

    private void renderSettings(GuiGraphics g, DashboardSnapshot s) {
        int x = left + 10;
        int y = top + 48 + 26 + 5;
        g.drawString(font, Component.translatable("zoocraft.screen.dashboard.price_label", s.maxPrice()), x, y, TEXT);
        g.drawString(font, Component.translatable("zoocraft.screen.dashboard.emeralds_label"), x, y += 24, TEXT);
        g.drawString(font, Component.translatable("zoocraft.screen.dashboard.treasury", s.treasury()), x + 110, y + 13, MUTED);
        y += 30;
        g.drawString(font, Component.translatable("zoocraft.screen.dashboard.managers", s.managers().size()), x, y, HEADING);
        int row = 0;
        for (DashboardSnapshot.ManagerLine manager : s.managers().stream().limit(3).toList()) {
            g.drawString(font, manager.name(), x + 110, y + row * 12, TEXT);
            row++;
        }
        if (s.managers().isEmpty()) {
            g.drawString(font, Component.translatable("zoocraft.screen.dashboard.no_managers"), x + 110, y, MUTED);
        }
        if (s.viewerIsOwner()) {
            g.drawString(font, Component.translatable("zoocraft.screen.dashboard.add_manager_label"), x, y + 43, TEXT);
            g.drawString(font, Component.translatable("zoocraft.screen.dashboard.transfer_label"), x, y + 67, TEXT);
        } else {
            g.drawString(font, Component.translatable("zoocraft.screen.dashboard.owner_only"), x, y + 43, MUTED);
        }
    }

    private void bar(GuiGraphics g, int x, int y, Component label, float value) {
        g.drawString(font, label, x, y, TEXT);
        int bx = x + 76, width = 56;
        g.fill(bx, y, bx + width, y + 8, 0xFF303030);
        int colour = value >= 60 ? 0xFF4CAF50 : value >= 35 ? 0xFFE0B040 : 0xFFE05040;
        g.fill(bx, y, bx + Math.round(width * Math.max(0, Math.min(100, value)) / 100f), y + 8, colour);
        g.drawString(font, Math.round(value) + "/100", bx + width + 4, y, MUTED);
    }

    private int wrapped(GuiGraphics g, Component text, int x, int y, int width, int colour) {
        for (FormattedCharSequence line : font.split(FormattedText.composite(text), width)) {
            g.drawString(font, line, x, y, colour);
            y += LINE;
        }
        return y;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.ANIMALS) {
            scroll = Math.max(0, scroll - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void removed() {
        PacketDistributor.sendToServer(DashboardPayloads.Close.INSTANCE);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
