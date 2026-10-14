package com.zoocraft.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.zoocraft.animal.AnimalCensus;
import com.zoocraft.animal.Exhibit;
import com.zoocraft.animal.ZooCensus;
import com.zoocraft.visitor.Visitor;
import com.zoocraft.visitor.VisitorSpawner;
import com.zoocraft.visitor.VisitorTracker;
import com.zoocraft.zoo.ActionResult;
import com.zoocraft.zoo.BoundaryVertex;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Operator tools for testing and diagnosing zoos: {@code /zoo debug ...}. */
final class ZooDebugCommands {
    private ZooDebugCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> debug = Commands.literal("debug").requires(source -> source.hasPermission(2));
        ShopCommands.debugDesignations().forEach(debug::then);
        return debug
                .then(ShopCommands.debugSell())
                .then(Commands.literal("create").then(Commands.argument("name", StringArgumentType.string())
                        .then(Commands.argument("from", BlockPosArgument.blockPos()).then(Commands.argument("to", BlockPosArgument.blockPos())
                                .executes(ZooDebugCommands::create)))))
                .then(Commands.literal("gate").then(Commands.argument("pos", BlockPosArgument.blockPos()).executes(ZooDebugCommands::gate)))
                .then(Commands.literal("spawn").executes(c -> spawn(c, ZooCommands.implicitZoo(c)))
                        .then(Commands.argument("zoo", StringArgumentType.string()).suggests(ZooCommands.ZOO_NAMES).executes(c -> spawn(c, ZooCommands.namedZoo(c)))))
                .then(Commands.literal("census").executes(c -> census(c, ZooCommands.implicitZoo(c)))
                        .then(Commands.argument("zoo", StringArgumentType.string()).suggests(ZooCommands.ZOO_NAMES).executes(c -> census(c, ZooCommands.namedZoo(c)))))
                .then(Commands.literal("needs").then(Commands.argument("hunger", IntegerArgumentType.integer(0, 100))
                        .then(Commands.argument("toilet", IntegerArgumentType.integer(0, 100))
                                .executes(c -> needs(c, ZooCommands.implicitZoo(c)))
                                .then(Commands.argument("zoo", StringArgumentType.string()).suggests(ZooCommands.ZOO_NAMES)
                                        .executes(c -> needs(c, ZooCommands.namedZoo(c)))))))
                .then(Commands.literal("visitors").executes(c -> visitors(c, ZooCommands.implicitZoo(c)))
                        .then(Commands.argument("zoo", StringArgumentType.string()).suggests(ZooCommands.ZOO_NAMES).executes(c -> visitors(c, ZooCommands.namedZoo(c)))));
    }

    /** Creates a rectangular zoo between two corners, owned by the player running the command. */
    private static int create(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        BlockPos a = BlockPosArgument.getBlockPos(c, "from");
        BlockPos b = BlockPosArgument.getBlockPos(c, "to");
        int y = a.getY();
        List<BoundaryVertex> rectangle = List.of(
                new BoundaryVertex(Math.min(a.getX(), b.getX()), y, Math.min(a.getZ(), b.getZ())),
                new BoundaryVertex(Math.max(a.getX(), b.getX()), y, Math.min(a.getZ(), b.getZ())),
                new BoundaryVertex(Math.max(a.getX(), b.getX()), y, Math.max(a.getZ(), b.getZ())),
                new BoundaryVertex(Math.min(a.getX(), b.getX()), y, Math.max(a.getZ(), b.getZ())));
        ActionResult result = ZooManager.get(c.getSource().getServer()).createZoo(player, StringArgumentType.getString(c, "name"),
                player.level().dimension(), rectangle);
        return report(c, result);
    }

    private static int gate(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        return report(c, ZooManager.get(c.getSource().getServer()).addGate(player, player.serverLevel(), BlockPosArgument.getBlockPos(c, "pos")));
    }

    private static int spawn(CommandContext<CommandSourceStack> c, Zoo zoo) {
        ServerLevel level = c.getSource().getServer().getLevel(zoo.dimension());
        List<Visitor> group = level == null ? List.of() : VisitorSpawner.trySpawnGroup(level, zoo);
        c.getSource().sendSuccess(() -> Component.translatable("zoocraft.debug.spawned", group.size(), zoo.name()), false);
        return group.size();
    }

    private static int census(CommandContext<CommandSourceStack> c, Zoo zoo) {
        ServerLevel level = c.getSource().getServer().getLevel(zoo.dimension());
        if (level == null) {
            return 0;
        }
        ZooCensus census = AnimalCensus.refresh(zoo, level);
        c.getSource().sendSuccess(() -> Component.translatable("zoocraft.debug.census", census.animals().size(),
                census.speciesCounts().size(), census.exhibits().size()), false);
        long now = level.getGameTime();
        for (Exhibit exhibit : census.exhibits()) {
            c.getSource().sendSuccess(() -> Component.literal(" - " + String.join(", ", exhibit.species()) + ": "
                    + exhibit.animals().size() + " animals, " + exhibit.viewingSpots().size() + " viewing spots, "
                    + exhibit.viewCount() + " views" + (exhibit.viewingProblem(now) ? " (can't get close)" : "")), false);
        }
        return census.animals().size();
    }

    /** Sets every visitor in the zoo to the given hunger and toilet need, to watch errands without waiting. */
    private static int needs(CommandContext<CommandSourceStack> c, Zoo zoo) {
        int hunger = IntegerArgumentType.getInteger(c, "hunger");
        int toilet = IntegerArgumentType.getInteger(c, "toilet");
        List<Visitor> visitors = VisitorTracker.visitors(zoo.id());
        visitors.forEach(v -> v.testSetNeeds(hunger, toilet));
        c.getSource().sendSuccess(() -> Component.translatable("zoocraft.debug.needs", visitors.size(), hunger, toilet), false);
        return visitors.size();
    }

    private static int visitors(CommandContext<CommandSourceStack> c, Zoo zoo) {
        List<Visitor> visitors = VisitorTracker.visitors(zoo.id());
        c.getSource().sendSuccess(() -> Component.translatable("zoocraft.debug.visitors", visitors.size(), zoo.name()), false);
        for (Visitor v : visitors) {
            c.getSource().sendSuccess(() -> Component.literal(" - " + v.profile() + " " + v.phase() + " budget " + v.budget()
                    + " mood " + Math.round(v.satisfaction()) + " interest " + Math.round(v.interest())
                    + " species " + v.seenSpecies().size() + " hunger " + Math.round(v.needs().hunger())
                    + " toilet " + Math.round(v.needs().toilet()) + " spent " + v.spentInShops()
                    + " at " + v.blockPosition().toShortString()), false);
        }
        return visitors.size();
    }

    private static int report(CommandContext<CommandSourceStack> c, ActionResult result) {
        if (result.success()) {
            c.getSource().sendSuccess(result::message, false);
        } else {
            c.getSource().sendFailure(result.message());
        }
        result.notes().forEach(c.getSource()::sendSystemMessage);
        return result.success() ? 1 : 0;
    }
}
