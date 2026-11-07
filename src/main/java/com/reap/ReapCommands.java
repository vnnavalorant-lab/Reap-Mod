package com.reap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ReapCommands {
    private ReapCommands() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess access, CommandManager.RegistrationEnvironment env) {
        dispatcher.register(CommandManager.literal("reap")
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    ReapData d = ReapState.get(c.getSource().getServer()).get(p.getUuid());
                    c.getSource().sendFeedback(() -> Text.literal(String.format(
                            "Nível %d (%d/%d XP) | Moedas %d | Rebirths %d | Multiplicador x%d | Colheres %d (próxima no nível %d)",
                            d.level, d.xp, ReapData.xpNeeded(d.level), d.coins, d.rebirths,
                            d.multiplier(), d.spoons(), d.nextSpoonLevel())), false);
                    return 1;
                })
                .then(CommandManager.literal("toggle").executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    ReapData d = ReapState.get(c.getSource().getServer()).get(p.getUuid());
                    d.enabled = !d.enabled;
                    ReapMod.markSync(c.getSource().getServer(), p.getUuid());
                    c.getSource().sendFeedback(() -> Text.literal("Colheres: " + (d.enabled ? "ligadas" : "desligadas")), false);
                    return 1;
                }))
                .then(CommandManager.literal("rebirth").executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    ReapData d = ReapState.get(c.getSource().getServer()).get(p.getUuid());
                    if (d.level < ReapMod.REBIRTH_LEVEL) {
                        c.getSource().sendError(Text.literal("Você precisa do nível " + ReapMod.REBIRTH_LEVEL + " para renascer."));
                        return 0;
                    }
                    d.level = 1;
                    d.xp = 0;
                    d.rebirths++;
                    ReapMod.markSync(c.getSource().getServer(), p.getUuid());
                    c.getSource().sendFeedback(() -> Text.literal("Rebirth " + d.rebirths + "! Multiplicador x" + d.multiplier()), false);
                    return 1;
                }))
                .then(CommandManager.literal("setlevel").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.argument("level", IntegerArgumentType.integer(1, 9999)).executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            ReapData d = ReapState.get(c.getSource().getServer()).get(p.getUuid());
                            d.level = IntegerArgumentType.getInteger(c, "level");
                            d.xp = 0;
                            ReapMod.markSync(c.getSource().getServer(), p.getUuid());
                            return 1;
                        })))
                .then(CommandManager.literal("setrebirths").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.argument("n", IntegerArgumentType.integer(0, 999)).executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            ReapData d = ReapState.get(c.getSource().getServer()).get(p.getUuid());
                            d.rebirths = IntegerArgumentType.getInteger(c, "n");
                            ReapMod.markSync(c.getSource().getServer(), p.getUuid());
                            return 1;
                        }))));
    }
}
