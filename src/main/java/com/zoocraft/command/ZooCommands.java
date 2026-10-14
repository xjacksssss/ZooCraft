package com.zoocraft.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zoocraft.ZooCraft;
import com.zoocraft.dashboard.DashboardService;
import com.zoocraft.item.BoundaryDrafts;
import com.zoocraft.zoo.ActionResult;
import com.zoocraft.zoo.Gate;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import com.zoocraft.zoo.ZooPermission;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@code /zoo} commands. Every change goes through {@link ZooManager}, exactly like the tools and dashboard. */
@EventBusSubscriber(modid = ZooCraft.MOD_ID)
public final class ZooCommands {
    private static final String ZOO = "zoo";

    static final SuggestionProvider<CommandSourceStack> ZOO_NAMES = (context, builder) -> {
        ServerPlayer player = context.getSource().getPlayer();
        ZooManager zoos = ZooManager.get(context.getSource().getServer());
        List<Zoo> visible = player == null ? List.copyOf(zoos.all()) : zoos.managedBy(player);
        return SharedSuggestionProvider.suggest(visible.stream().map(z -> StringArgumentType.escapeIfRequired(z.name())), builder);
    };

    private ZooCommands() {
    }

    @SubscribeEvent
    static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("zoo")
                .executes(c -> {
                    DashboardService.open(c.getSource().getPlayerOrException(), Optional.empty());
                    return 1;
                })
                .then(ZooDebugCommands.build())
                .then(Commands.literal("list").executes(ZooCommands::list))
                .then(withZoo(Commands.literal("info"), (c, zoo) -> info(c, zoo)))
                .then(withZoo(Commands.literal("open"), (c, zoo) -> act(c, (zoos, p) -> zoos.setOpen(p, zoo, true))))
                .then(withZoo(Commands.literal("close"), (c, zoo) -> act(c, (zoos, p) -> zoos.setOpen(p, zoo, false))))
                .then(Commands.literal("price").then(withZoo(Commands.argument("amount", IntegerArgumentType.integer(0, ZooManager.MAX_PRICE)),
                        (c, zoo) -> act(c, (zoos, p) -> zoos.setPrice(p, zoo, IntegerArgumentType.getInteger(c, "amount"))))))
                .then(Commands.literal("deposit").then(withZoo(Commands.argument("amount", IntegerArgumentType.integer(1)),
                        (c, zoo) -> act(c, (zoos, p) -> zoos.deposit(p, zoo, IntegerArgumentType.getInteger(c, "amount"))))))
                .then(Commands.literal("withdraw").then(withZoo(Commands.argument("amount", IntegerArgumentType.integer(1)),
                        (c, zoo) -> act(c, (zoos, p) -> zoos.withdraw(p, zoo, IntegerArgumentType.getInteger(c, "amount"))))))
                .then(Commands.literal("keeploaded").then(withZoo(Commands.argument("enabled", BoolArgumentType.bool()),
                        (c, zoo) -> act(c, (zoos, p) -> zoos.setKeepLoaded(p, zoo, BoolArgumentType.getBool(c, "enabled"))))))
                .then(Commands.literal("rename").then(withZoo(Commands.argument("name", StringArgumentType.string()),
                        (c, zoo) -> act(c, (zoos, p) -> zoos.rename(p, zoo, StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("manager")
                        .then(Commands.literal("add").then(withZoo(Commands.argument("player", GameProfileArgument.gameProfile()),
                                (c, zoo) -> act(c, (zoos, p) -> zoos.addManager(p, zoo, profile(c))))))
                        .then(Commands.literal("remove").then(withZoo(Commands.argument("player", GameProfileArgument.gameProfile()),
                                (c, zoo) -> act(c, (zoos, p) -> {
                                    GameProfile target = profile(c);
                                    return zoos.removeManager(p, zoo, target.getId(), target.getName());
                                })))))
                .then(Commands.literal("transfer").then(withZoo(Commands.argument("player", GameProfileArgument.gameProfile()),
                        (c, zoo) -> act(c, (zoos, p) -> zoos.transfer(p, zoo, profile(c))))))
                .then(withZoo(Commands.literal("delete"), (c, zoo) -> act(c, (zoos, p) -> zoos.delete(p, zoo))))
                .then(Commands.literal("boundary")
                        .then(Commands.literal("save").executes(c -> report(c, BoundaryDrafts.saveEdit(c.getSource().getPlayerOrException()))))
                        .then(Commands.literal("cancel").executes(ZooCommands::discardDraft))
                        .then(Commands.literal("discard").executes(ZooCommands::discardDraft))));
    }

    @FunctionalInterface
    private interface ZooCommand {
        int run(CommandContext<CommandSourceStack> context, Zoo zoo) throws CommandSyntaxException;
    }

    /** Adds an optional trailing zoo name; without it, the zoo the player is standing in (or their only zoo) is used. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withZoo(T node, ZooCommand command) {
        RequiredArgumentBuilder<CommandSourceStack, String> named = Commands.argument(ZOO, StringArgumentType.string()).suggests(ZOO_NAMES)
                .executes(c -> command.run(c, namedZoo(c)));
        return node.executes(c -> command.run(c, implicitZoo(c))).then(named);
    }

    static Zoo namedZoo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, ZOO);
        return ZooManager.get(context.getSource().getServer()).byName(name)
                .orElseThrow(() -> error(Component.translatable("zoocraft.cmd.no_such_zoo", name)));
    }

    static Zoo implicitZoo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ZooManager zoos = ZooManager.get(context.getSource().getServer());
        Optional<Zoo> here = zoos.zooAt(player.level(), player.blockPosition());
        if (here.isPresent()) {
            return here.get();
        }
        List<Zoo> mine = zoos.managedBy(player).stream().filter(z -> z.isOwnerOrManager(player.getUUID())).toList();
        if (mine.size() == 1) {
            return mine.getFirst();
        }
        throw error(Component.translatable("zoocraft.cmd.which_zoo"));
    }

    private static GameProfile profile(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<GameProfile> profiles = GameProfileArgument.getGameProfiles(context, "player");
        if (profiles.size() != 1) {
            throw error(Component.translatable("zoocraft.cmd.one_player"));
        }
        return profiles.iterator().next();
    }

    @FunctionalInterface
    private interface Action {
        ActionResult apply(ZooManager zoos, ServerPlayer player) throws CommandSyntaxException;
    }

    private static int act(CommandContext<CommandSourceStack> context, Action action) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return report(context, action.apply(ZooManager.get(context.getSource().getServer()), player));
    }

    private static int report(CommandContext<CommandSourceStack> context, ActionResult result) {
        CommandSourceStack source = context.getSource();
        if (result.success()) {
            source.sendSuccess(result::message, false);
        } else {
            source.sendFailure(result.message());
        }
        result.notes().forEach(note -> source.sendSystemMessage(note.copy().withStyle(ChatFormatting.GOLD)));
        return result.success() ? 1 : 0;
    }

    private static int discardDraft(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BoundaryDrafts.clear(context.getSource().getPlayerOrException());
        context.getSource().sendSuccess(() -> Component.translatable("zoocraft.tool.discarded"), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        ZooManager zoos = ZooManager.get(context.getSource().getServer());
        List<Zoo> visible = zoos.managedBy(player);
        if (visible.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("zoocraft.cmd.no_zoos"), false);
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("zoocraft.cmd.list_header", visible.size()), false);
        for (Zoo zoo : visible) {
            context.getSource().sendSuccess(() -> Component.literal(" - ").append(Component.literal(zoo.name()).withStyle(ChatFormatting.AQUA))
                    .append(" ").append(Component.translatable(zoo.open() ? "zoocraft.status.open" : "zoocraft.status.closed")), false);
        }
        return visible.size();
    }

    private static int info(CommandContext<CommandSourceStack> context, Zoo given) throws CommandSyntaxException {
        Zoo zoo = given != null ? given : implicitZoo(context);
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!ZooManager.can(player, zoo, ZooPermission.VIEW)) {
            context.getSource().sendFailure(Component.translatable("zoocraft.msg.no_permission"));
            return 0;
        }
        MinecraftServer server = context.getSource().getServer();
        long gameTime = server.overworld().getGameTime();
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal(zoo.name()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(" ").append(Component.translatable(zoo.open() ? "zoocraft.status.open" : "zoocraft.status.closed")), false);
        source.sendSuccess(() -> Component.translatable("zoocraft.info.owner", name(server, zoo.owner())), false);
        if (!zoo.managers().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("zoocraft.info.managers",
                    String.join(", ", zoo.managers().stream().map(id -> name(server, id)).toList())), false);
        }
        source.sendSuccess(() -> Component.translatable("zoocraft.info.money", zoo.admissionPrice(), zoo.ledger().balance(), zoo.ledger().totalAdmission()), false);
        source.sendSuccess(() -> Component.translatable("zoocraft.info.attendance", zoo.stats().attendanceToday(), zoo.stats().totalAttendance()), false);
        source.sendSuccess(() -> Component.translatable("zoocraft.info.ratings", Math.round(zoo.satisfaction()), Math.round(zoo.reputation())), false);
        MutableComponent gates = Component.translatable("zoocraft.info.gates", zoo.gates().size());
        for (Gate gate : zoo.gates()) {
            gates.append(Component.literal(" (" + gate.pos().getX() + ", " + gate.pos().getY() + ", " + gate.pos().getZ() + ") "))
                    .append(Component.translatable("zoocraft.gate_status." + zoo.gateStatus(gate, gameTime).getSerializedName()));
        }
        source.sendSuccess(() -> gates, false);
        return 1;
    }

    static String name(MinecraftServer server, UUID id) {
        return com.zoocraft.zoo.PlayerNames.name(server, id);
    }

    private static CommandSyntaxException error(Component message) {
        return new SimpleCommandExceptionType(message).create();
    }
}
