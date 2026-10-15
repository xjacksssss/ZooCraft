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

    /** What visitors are left holding after eating; dropped as litter when there's no bin (Phase 2 spec section 9.2). */
    public static final DeferredItem<Item> RUBBISH_WRAPPER = ITEMS.registerSimpleItem("rubbish_wrapper");
    public static final DeferredItem<Item> RUBBISH_CUP = ITEMS.registerSimpleItem("rubbish_cup");
    public static final DeferredItem<Item> RUBBISH_PAPER = ITEMS.registerSimpleItem("rubbish_paper");
    public static final int RUBBISH_VARIANTS = 3;

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

    /** The rubbish item for a litter variant (0 wrapper, 1 cup, 2 paper). */
    public static Item rubbish(int variant) {
        return switch (Math.floorMod(variant, RUBBISH_VARIANTS)) {
            case 0 -> RUBBISH_WRAPPER.get();
            case 1 -> RUBBISH_CUP.get();
            default -> RUBBISH_PAPER.get();
        };
    }

    public static boolean isRubbish(ItemStack stack) {
        return stack.is(RUBBISH_WRAPPER.get()) || stack.is(RUBBISH_CUP.get()) || stack.is(RUBBISH_PAPER.get());
    }

    public static boolean isZooTool(ItemStack stack) {
        return stack.is(BOUNDARY_TOOL.get()) || stack.is(DESIGNATION_TOOL.get());
    }
}
