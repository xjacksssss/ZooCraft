package com.zoocraft.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.zoocraft.perf.PerfLog;
import com.zoocraft.perf.PerfReport;
import com.zoocraft.zoo.Zoo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Path;

/** {@code /zoo debug perf}, {@code /zoo debug perf <zoo>} and {@code /zoo debug perf log <seconds>}. */
final class PerfCommands {
    private PerfCommands() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("perf")
                .executes(PerfCommands::server)
                .then(Commands.literal("log").then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                        .executes(PerfCommands::log)))
                .then(Commands.argument("zoo", StringArgumentType.string()).suggests(ZooCommands.ZOO_NAMES)
                        .executes(c -> zoo(c, ZooCommands.namedZoo(c))));
    }

    private static int server(CommandContext<CommandSourceStack> c) {
        PerfReport.server(c.getSource().getServer()).forEach(line -> c.getSource().sendSuccess(() -> line, false));
        return 1;
    }

    private static int zoo(CommandContext<CommandSourceStack> c, Zoo zoo) {
        server(c);
        PerfReport.zoo(c.getSource().getServer(), zoo).forEach(line -> c.getSource().sendSuccess(() -> line, false));
        return 1;
    }

    private static int log(CommandContext<CommandSourceStack> c) {
        int seconds = IntegerArgumentType.getInteger(c, "seconds");
        try {
            Path file = PerfLog.start(c.getSource().getServer(), seconds);
            c.getSource().sendSuccess(() -> Component.literal("Logging ZooCraft performance for " + seconds + " s to " + file), false);
            return 1;
        } catch (IOException e) {
            c.getSource().sendFailure(Component.literal("Couldn't start the perf log: " + e.getMessage()));
            return 0;
        }
    }
}
