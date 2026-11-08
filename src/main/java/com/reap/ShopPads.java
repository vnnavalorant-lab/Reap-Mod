package com.reap;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Lógica da loja: ao pisar num bloco de loja o jogador compra aquela Reap com seus wins. */
public final class ShopPads {
    /** Última plataforma de loja já "processada" por jogador (para não repetir enquanto ele fica em cima). */
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private ShopPads() {}

    public static void tick(MinecraftServer server) {
        ReapState st = null;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.isSpectator() || !p.isAlive() || !p.isOnGround()) continue;

            ServerWorld w = p.getServerWorld();
            BlockPos feet = p.getBlockPos();
            BlockState bs = w.getBlockState(feet);
            if (!(bs.getBlock() instanceof ShopPadBlock pad)) {
                LAST.remove(p.getUuid());
                continue;
            }
            // só age uma vez a cada vez que o jogador pisa (precisa sair e pisar de novo para tentar outra vez)
            Long last = LAST.get(p.getUuid());
            if (last != null && last == feet.asLong()) continue;
            LAST.put(p.getUuid(), feet.asLong());

            if (st == null) st = ReapState.get(server);
            ReapData d = st.get(p.getUuid());
            Reaps.Tier t = Reaps.get(pad.tier);

            if (d.reapTier >= pad.tier) {
                p.sendMessage(Text.literal("Você já tem a Reap " + t.namePt() + " (ou uma melhor).")
                        .formatted(Formatting.GRAY), true);
                continue;
            }
            if (pad.tier != d.reapTier + 1) { // compras em ordem: só a Reap seguinte
                p.sendMessage(Text.literal("Compre as Reaps em ordem: a próxima é a " + Reaps.get(d.reapTier + 1).namePt() + ".")
                        .formatted(Formatting.RED, Formatting.BOLD), true);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_VILLAGER_NO,
                        SoundCategory.PLAYERS, 0.8f, 1f);
                continue;
            }
            if (d.wins < t.cost()) {
                long falta = t.cost() - d.wins;
                p.sendMessage(Text.literal("Faltam " + Reaps.abbrev(falta) + (falta == 1 ? " win" : " wins") + " para a Reap " + t.namePt() + "!")
                        .formatted(Formatting.RED, Formatting.BOLD), true);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_VILLAGER_NO,
                        SoundCategory.PLAYERS, 0.8f, 1f);
                continue;
            }

            d.wins -= t.cost();
            d.reapTier = pad.tier;
            ReapMod.markSync(server, p.getUuid());
            p.sendMessage(Text.literal("Reap " + t.namePt() + " comprada! Velocidade de destruição "
                    + Reaps.speedLabel(t.speed())).formatted(Formatting.GREEN, Formatting.BOLD), true);
            w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS, 0.9f, 1.1f);
        }
    }
}
