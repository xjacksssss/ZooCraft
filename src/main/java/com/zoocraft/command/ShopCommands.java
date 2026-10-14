package com.zoocraft.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zoocraft.core.shop.PriceRules;
import com.zoocraft.core.shop.SaleResult;
import com.zoocraft.zoo.Shop;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import com.zoocraft.zoo.ZooPermission;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** {@code /zoo shop ...} and {@code /zoo debug sell}. Changes go through {@link ZooManager}, like the dashboard. */
final class ShopCommands {
    private static final String SHOP = "shop";
    private static final String ITEM = "item";

    /** Shop names in the zoo the player is standing in, or their only zoo. */
    private static final SuggestionProvider<CommandSourceStack> SHOP_NAMES = (context, builder) -> {
        try {
            Zoo zoo = ZooCommands.implicitZoo(context);
            return SharedSuggestionProvider.suggest(zoo.shops().stream().map(s -> StringArgumentType.escapeIfRequired(s.name())), builder);
        } catch (CommandSyntaxException e) {
            return builder.buildFuture();
        }
    };

    private ShopCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("shop")
                .then(ZooCommands.withZoo(Commands.literal("list"), ShopCommands::list))
                .then(Commands.literal("price").then(Commands.argument(SHOP, StringArgumentType.string()).suggests(SHOP_NAMES)
                        .then(Commands.argument(ITEM, ResourceLocationArgument.id())
                                .then(ZooCommands.withZoo(Commands.argument("emeralds", IntegerArgumentType.integer(PriceRules.MIN_PRICE, PriceRules.MAX_PRICE)),
                                        (c, zoo) -> ZooCommands.act(c, (zoos, p) -> zoos.setItemPrice(p, zoo, shop(c, zoo).id(),
                                                ResourceLocationArgument.getId(c, ITEM).toString(), IntegerArgumentType.getInteger(c, "emeralds"))))))))
                .then(Commands.literal("open").then(ZooCommands.withZoo(Commands.argument(SHOP, StringArgumentType.string()).suggests(SHOP_NAMES),
                        (c, zoo) -> ZooCommands.act(c, (zoos, p) -> zoos.setShopOpen(p, zoo, shop(c, zoo).id(), true)))))
                .then(Commands.literal("close").then(ZooCommands.withZoo(Commands.argument(SHOP, StringArgumentType.string()).suggests(SHOP_NAMES),
                        (c, zoo) -> ZooCommands.act(c, (zoos, p) -> zoos.setShopOpen(p, zoo, shop(c, zoo).id(), false)))))
                .then(Commands.literal("rename").then(Commands.argument(SHOP, StringArgumentType.string()).suggests(SHOP_NAMES)
                        .then(ZooCommands.withZoo(Commands.argument("name", StringArgumentType.string()),
                                (c, zoo) -> ZooCommands.act(c, (zoos, p) -> zoos.renameShop(p, zoo, shop(c, zoo).id(),
                                        StringArgumentType.getString(c, "name")))))));
    }

    /** {@code /zoo debug shop|link|toilet <pos>}: the designation tool's clicks, for scripted play-tests. */
    static List<LiteralArgumentBuilder<CommandSourceStack>> debugDesignations() {
        return List.of(
                Commands.literal("shop").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> ZooCommands.act(c,
                        (zoos, p) -> zoos.createShop(p, p.serverLevel(), BlockPosArgument.getBlockPos(c, "pos"))))),
                Commands.literal("link").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> ZooCommands.act(c,
                        (zoos, p) -> zoos.linkContainer(p, p.serverLevel(), BlockPosArgument.getBlockPos(c, "pos"))))),
                Commands.literal("toilet").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(c -> ZooCommands.act(c,
                        (zoos, p) -> zoos.createToilet(p, p.serverLevel(), BlockPosArgument.getBlockPos(c, "pos"))))));
    }

    /** {@code /zoo debug sell <shop> <item> [zoo]}: sells one unit to a pretend visitor with plenty of emeralds. */
    static LiteralArgumentBuilder<CommandSourceStack> debugSell() {
        return Commands.literal("sell").then(Commands.argument(SHOP, StringArgumentType.string()).suggests(SHOP_NAMES)
                .then(ZooCommands.withZoo(Commands.argument(ITEM, ResourceLocationArgument.id()), ShopCommands::sell)));
    }

    static Shop shop(CommandContext<CommandSourceStack> context, Zoo zoo) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, SHOP);
        Optional<Shop> shop = zoo.shops().stream().filter(s -> s.name().equalsIgnoreCase(name.trim())).findFirst();
        return shop.orElseThrow(() -> ZooCommands.error(Component.translatable("zoocraft.cmd.no_such_shop", name, zoo.name())));
    }

    private static int list(CommandContext<CommandSourceStack> context, Zoo zoo) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!ZooManager.can(player, zoo, ZooPermission.VIEW)) {
            context.getSource().sendFailure(Component.translatable("zoocraft.msg.no_permission"));
            return 0;
        }
        List<Shop> shops = zoo.shops();
        context.getSource().sendSuccess(() -> Component.translatable("zoocraft.cmd.shop_list_header", zoo.name(), shops.size(), zoo.toilets().size()), false);
        for (Shop shop : shops) {
            String status = zoo.isDetached(shop.counter()) ? "detached" : shop.open() ? "open" : "closed";
            context.getSource().sendSuccess(() -> Component.literal(" - ").append(Component.literal(shop.name()).withStyle(ChatFormatting.AQUA))
                    .append(" ").append(Component.translatable("zoocraft.shop.status." + status))
                    .append(" ").append(Component.translatable("zoocraft.cmd.shop_line", shop.containers().size(),
                            shop.stats().revenueToday(), shop.stats().revenueTotal())), false);
        }
        return shops.size();
    }

    private static int sell(CommandContext<CommandSourceStack> context, Zoo zoo) throws CommandSyntaxException {
        if (!context.getSource().hasPermission(2)) {
            throw ZooCommands.error(Component.translatable("zoocraft.msg.no_permission"));
        }
        Shop shop = shop(context, zoo);
        ServerLevel level = context.getSource().getServer().getLevel(zoo.dimension());
        if (level == null) {
            return 0;
        }
        String item = ResourceLocationArgument.getId(context, ITEM).toString();
        SaleResult result = ZooManager.get(context.getSource().getServer())
                .sell(level, zoo, shop.id(), "debug-sale:" + UUID.randomUUID(), item, 999, "Debug");
        Component message = Component.translatable("zoocraft.debug.sell", item, shop.name(),
                Component.translatable("zoocraft.sale." + result.name().toLowerCase(Locale.ROOT)), zoo.ledger().balance());
        if (result.sold()) {
            context.getSource().sendSuccess(() -> message, false);
            return 1;
        }
        context.getSource().sendFailure(message);
        return 0;
    }
}
