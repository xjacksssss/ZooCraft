package com.zoocraft.shop;

import com.zoocraft.core.shop.ProductCategory;
import com.zoocraft.core.shop.StockSource;
import com.zoocraft.zoo.Shop;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A shop's physical stock: the item handlers of its linked containers (Phase 2 spec section 7.4). Items that can't
 * be sold, such as a shulker box with contents, are never taken.
 */
public final class ShopStock implements StockSource {
    private final ServerLevel level;
    private final Shop shop;
    private IItemHandler lastHandler;
    private ItemStack lastTaken = ItemStack.EMPTY;

    public ShopStock(ServerLevel level, Shop shop) {
        this.level = level;
        this.shop = shop;
    }

    @Override
    public boolean canTake(String itemId) {
        return find(itemId, true) != null;
    }

    @Override
    public boolean take(String itemId) {
        return find(itemId, false) != null;
    }

    @Override
    public void undoTake() {
        if (lastTaken.isEmpty()) {
            return;
        }
        ItemStack rest = ItemHandlerHelper.insertItem(lastHandler, lastTaken, false);
        if (!rest.isEmpty()) {
            Block.popResource(level, shop.counter(), rest);
        }
        lastTaken = ItemStack.EMPTY;
    }

    /** Extracts (or simulates extracting) one sellable unit of the item; null when there is none. */
    private ItemStack find(String itemId, boolean simulate) {
        for (BlockPos pos : shop.containers()) {
            Optional<IItemHandler> maybe = ContainerPositions.handler(level, pos);
            if (maybe.isEmpty()) {
                continue;
            }
            IItemHandler handler = maybe.get();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack inSlot = handler.getStackInSlot(slot);
                if (inSlot.isEmpty() || !ProductCatalog.itemId(inSlot).equals(itemId)
                        || ProductCatalog.category(inSlot) == ProductCategory.UNSELLABLE) {
                    continue;
                }
                ItemStack extracted = handler.extractItem(slot, 1, simulate);
                if (extracted.isEmpty() || !ProductCatalog.itemId(extracted).equals(itemId)) {
                    continue;
                }
                if (!simulate) {
                    lastHandler = handler;
                    lastTaken = extracted;
                }
                return extracted;
            }
        }
        return null;
    }

    /** One product line: every unit of an item across the shop's containers. */
    public record StockLine(String itemId, ItemStack icon, ProductCategory category, int count) {
    }

    /** @param unknown true when some container is in an unloaded chunk, so the counts may be low */
    public record Reading(List<StockLine> lines, boolean unknown) {
    }

    /** Counts the shop's stock by item, in the order items are first found. */
    public static Reading read(ServerLevel level, Shop shop) {
        Map<String, StockLine> lines = new LinkedHashMap<>();
        boolean unknown = false;
        for (BlockPos pos : shop.containers()) {
            if (!level.isLoaded(pos)) {
                unknown = true;
                continue;
            }
            Optional<IItemHandler> maybe = ContainerPositions.handler(level, pos);
            if (maybe.isEmpty()) {
                continue;
            }
            IItemHandler handler = maybe.get();
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                String id = ProductCatalog.itemId(stack);
                ProductCategory category = ProductCatalog.category(stack);
                StockLine old = lines.get(id);
                if (old == null) {
                    lines.put(id, new StockLine(id, stack.copyWithCount(1), category, stack.getCount()));
                } else {
                    // A sellable stack makes the line sellable even if another stack of the item isn't (a full shulker box).
                    ProductCategory merged = old.category() == ProductCategory.UNSELLABLE ? category : old.category();
                    lines.put(id, new StockLine(id, old.icon(), merged, old.count() + stack.getCount()));
                }
            }
        }
        return new Reading(new ArrayList<>(lines.values()), unknown);
    }
}
