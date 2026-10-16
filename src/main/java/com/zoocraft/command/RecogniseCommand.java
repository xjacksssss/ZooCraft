package com.zoocraft.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.zoocraft.animal.RecognitionAudit;
import com.zoocraft.animal.RecognitionReport;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** {@code /zoo debug recognise [namespace]} (Phase 3 spec section 6.3). */
final class RecogniseCommand {
    private static final int LISTED = 40;
    private static final SuggestionProvider<CommandSourceStack> NAMESPACES = (c, b) -> SharedSuggestionProvider.suggest(
            BuiltInRegistries.ENTITY_TYPE.keySet().stream().map(ResourceLocation::getNamespace).distinct(), b);

    private RecogniseCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("recognise")
                .executes(c -> run(c, null))
                .then(Commands.literal("audit").executes(c -> {
                    Component reply = RecognitionAudit.start(c.getSource().getServer(), c.getSource());
                    c.getSource().sendSuccess(() -> reply, false);
                    return 1;
                }))
                .then(Commands.argument("namespace", StringArgumentType.word()).suggests(NAMESPACES)
                        .executes(c -> run(c, StringArgumentType.getString(c, "namespace"))));
    }

    private static int run(CommandContext<CommandSourceStack> c, @Nullable String namespace) {
        CommandSourceStack source = c.getSource();
        List<RecognitionReport.Entry> entries = RecognitionReport.run(source.getLevel(), namespace);
        long recognised = entries.stream().filter(RecognitionReport.Entry::recognised).count();
        long unchecked = entries.stream().filter(e -> !e.checked()).count();
        String where;
        try {
            Path file = RecognitionReport.writeCsv(source.getServer(), entries);
            where = "Full list: " + file;
        } catch (IOException e) {
            where = "Couldn't write the full list: " + e.getMessage();
        }
        String scope = namespace == null ? "all mods" : namespace;
        String summary = String.format(Locale.ROOT, "%d entity types checked in %s: %d recognised, %d not, %d could not be checked. %s",
                entries.size(), scope, recognised, entries.size() - recognised, unchecked, where);
        source.sendSuccess(() -> Component.literal(summary), false);
        if (namespace != null) {
            entries.stream().limit(LISTED).forEach(e -> source.sendSuccess(() -> Component.literal(" - " + e.id() + ": "
                    + (e.recognised() ? "recognised" : "not recognised") + " (" + e.reason() + (e.checked() ? "" : ", not constructed") + ")"), false));
            if (entries.size() > LISTED) {
                source.sendSuccess(() -> Component.literal(" ... and " + (entries.size() - LISTED) + " more in the file."), false);
            }
        }
        return (int) recognised;
    }
}
