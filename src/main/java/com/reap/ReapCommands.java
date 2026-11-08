package com.reap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class ReapCommands {
    private ReapCommands() {}

    /** Zera a Força (moedas) do jogador; ele perde o bônus de velocidade que ela dava. */
    private static void resetStrength(ServerCommandSource src, java.util.Collection<ServerPlayerEntity> targets) {
        ReapState st = ReapState.get(src.getServer());
        for (ServerPlayerEntity t : targets) {
            st.get(t.getUuid()).coins = 0;
            ReapMod.markSync(src.getServer(), t.getUuid());
            t.sendMessage(Text.literal("Sua Força foi zerada."), false);
        }
        int n = targets.size();
        src.sendFeedback(() -> Text.literal("Força zerada para " + n + (n == 1 ? " jogador." : " jogadores.")), true);
    }

    /** Reseta a loja: o jogador volta para a primeira Reap (os wins não são devolvidos). */
    private static void resetShop(ServerCommandSource src, java.util.Collection<ServerPlayerEntity> targets) {
        ReapState st = ReapState.get(src.getServer());
        for (ServerPlayerEntity t : targets) {
            st.get(t.getUuid()).reapTier = 0;
            ReapMod.markSync(src.getServer(), t.getUuid());
            t.sendMessage(Text.literal("Sua loja de Reaps foi resetada: você voltou para a primeira Reap."), false);
        }
        int n = targets.size();
        src.sendFeedback(() -> Text.literal("Loja resetada para " + n + (n == 1 ? " jogador." : " jogadores.")), true);
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess access, CommandManager.RegistrationEnvironment env) {
        dispatcher.register(CommandManager.literal("reap")
                .executes(c -> {
                    ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                    ReapData d = ReapState.get(c.getSource().getServer()).get(p.getUuid());
                    c.getSource().sendFeedback(() -> Text.literal(String.format(
                            "Nível %d (%d/%d XP) | Moedas %d | Wins %d | Rebirths %d | Multiplicador x%d | Colheres %d (próxima no rebirth %d) | Reap %s (velocidade %s)",
                            d.level, d.xp, ReapData.xpNeeded(d.level), d.coins, d.wins, d.rebirths,
                            d.multiplier(), d.spoons(), d.nextSpoonRebirth(),
                            Reaps.get(d.reapTier).namePt(), Reaps.speedLabel(Reaps.speed(d.reapTier)))), false);
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
                    ReapNet.sendOpenRebirth(c.getSource().getPlayerOrThrow());
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
                .then(CommandManager.literal("setwins").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.argument("n", IntegerArgumentType.integer(0, 1_000_000_000)).executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            ReapData d = ReapState.get(c.getSource().getServer()).get(p.getUuid());
                            d.wins = IntegerArgumentType.getInteger(c, "n");
                            ReapMod.markSync(c.getSource().getServer(), p.getUuid());
                            return 1;
                        })))
                .then(CommandManager.literal("setreap").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.argument("n", IntegerArgumentType.integer(0, Reaps.MAX)).executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            ReapData d = ReapState.get(c.getSource().getServer()).get(p.getUuid());
                            d.reapTier = IntegerArgumentType.getInteger(c, "n");
                            ReapMod.markSync(c.getSource().getServer(), p.getUuid());
                            return 1;
                        })))
                .then(CommandManager.literal("resetshop").requires(s -> s.hasPermissionLevel(2))
                        .executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            resetShop(c.getSource(), java.util.List.of(p));
                            return 1;
                        })
                        .then(CommandManager.argument("players", EntityArgumentType.players()).executes(c -> {
                            java.util.Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(c, "players");
                            resetShop(c.getSource(), targets);
                            return targets.size();
                        })))
                .then(CommandManager.literal("resetstrength").requires(s -> s.hasPermissionLevel(2))
                        .executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            resetStrength(c.getSource(), java.util.List.of(p));
                            return 1;
                        })
                        .then(CommandManager.argument("players", EntityArgumentType.players()).executes(c -> {
                            java.util.Collection<ServerPlayerEntity> targets = EntityArgumentType.getPlayers(c, "players");
                            resetStrength(c.getSource(), targets);
                            return targets.size();
                        })))
                .then(CommandManager.literal("setrebirths").requires(s -> s.hasPermissionLevel(2))
                        .then(CommandManager.argument("n", IntegerArgumentType.integer(0, ReapData.MAX_REBIRTHS)).executes(c -> {
                            ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
                            ReapData d = ReapState.get(c.getSource().getServer()).get(p.getUuid());
                            d.rebirths = IntegerArgumentType.getInteger(c, "n");
                            ReapMod.markSync(c.getSource().getServer(), p.getUuid());
                            return 1;
                        }))));
    }
}
