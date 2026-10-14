package com.zoocraft.registry;

import com.zoocraft.ZooCraft;
import com.zoocraft.item.BoundaryToolItem;
import com.zoocraft.item.DesignationToolItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ZooItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ZooCraft.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ZooCraft.MOD_ID);

    public static final DeferredItem<BoundaryToolItem> BOUNDARY_TOOL =
            ITEMS.registerItem("boundary_tool", BoundaryToolItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredItem<DesignationToolItem> DESIGNATION_TOOL =
            ITEMS.registerItem("designation_tool", DesignationToolItem::new, new Item.Properties().stacksTo(1));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("zoocraft", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.zoocraft"))
            .icon(() -> new ItemStack(BOUNDARY_TOOL.get()))
            .displayItems((params, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    private ZooItems() {
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    public static boolean isZooTool(ItemStack stack) {
        return stack.is(BOUNDARY_TOOL.get()) || stack.is(DESIGNATION_TOOL.get());
    }
}
