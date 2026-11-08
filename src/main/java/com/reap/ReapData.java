package com.reap;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/** Progressão de um jogador (igual ao jogo: nível, XP, moedas, rebirths, multiplicador). */
public class ReapData {
    public int level = 1;
    public long xp = 0;
    public long coins = 0;
    public long wins = 0;
    /** Reap (colher) equipada: índice em {@link Reaps#TIERS}. 0 = inicial. Volta a 0 no rebirth. */
    public int reapTier = 0;
    public int rebirths = 0;
    public boolean enabled = true;

    /** XP necessário para sair do nível dado (5 * nível²; nível 50 acumula ~200 mil XP). */
    public static long xpNeeded(int level) {
        return Math.max(40L, 5L * level * level);
    }

    /** Versão do formato salvo. Saves antigos (de balanceamentos anteriores) recomeçam do zero. */
    public static final int DATA_VERSION = 3;
    /** Máximo de rebirths. */
    public static final int MAX_REBIRTHS = 100;
    /** Bônus de força (%) por rebirth: +15% cada (0..100 rebirths = +0%..+1500%). */
    public static final int STRENGTH_PCT_PER_REBIRTH = 15;

    /** Nível necessário para o PRÓXIMO rebirth: 50 no começo, +4 a cada rebirth (70 no 6º, 450 no último). */
    public static int rebirthLevelReq(int rebirths) { return 50 + 4 * rebirths; }
    public int rebirthLevelReq() { return rebirthLevelReq(rebirths); }
    public boolean canRebirth() { return rebirths < MAX_REBIRTHS && level >= rebirthLevelReq(); }

    /** Bônus de força mostrado na tela de Rebirth (+X%). */
    public static int strengthPct(int rebirths) { return rebirths * STRENGTH_PCT_PER_REBIRTH; }
    public static float strengthFactor(int rebirths) { return 1f + strengthPct(rebirths) / 100f; }

    /** Multiplicador de recompensas: x2 no início, +1 a cada 2 rebirths (6 rebirths = x5). */
    public int multiplier() { return 2 + rebirths / 2; }

    /** Máximo de colheres girando em volta do jogador. */
    public static final int MAX_SPOONS = 12;

    /** A cada quantos rebirths ganha uma colher nova. */
    public static final int SPOON_EVERY = 3;

    /** Começa com 1 colher e ganha +1 a cada {@link #SPOON_EVERY} rebirths (nível não dá colher). */
    public int spoons() { return Math.min(MAX_SPOONS, 1 + rebirths / SPOON_EVERY); }

    /** Divisor da Força (moedas): quanto maior, menos a Força acelera a colher. Bônus = sqrt(moedas) / COIN_DIV. */
    public static final double COIN_DIV = 60.0;

    /** Multiplicador de velocidade de destruição vindo da Força (moedas): mais Força = colher mais rápida (cresce com raiz quadrada). */
    public float coinFactor() { return (float) (1.0 + Math.sqrt(Math.max(0L, coins)) / COIN_DIV); }

    /** Bônus de poder de mordida por nível (+10% por nível). */
    public static final float LEVEL_POWER = 0.10f;

    /** Quanto de "vida" da gelatina cada colher tira por tick de contato. Sobe com o nível, os rebirths, a Força (moedas) e a Reap equipada (velocidade de destruição). */
    public float bitePower() { return (1f + LEVEL_POWER * (level - 1)) * strengthFactor(rebirths) * Reaps.speed(reapTier) * coinFactor(); }

    /** Rebirth em que ganha a próxima colher. */
    public int nextSpoonRebirth() { return (rebirths / SPOON_EVERY + 1) * SPOON_EVERY; }

    public void write(NbtCompound n) {
        n.putInt("dv", DATA_VERSION);
        n.putInt("level", level);
        n.putLong("xp", xp);
        n.putLong("coins", coins);
        n.putLong("wins", wins);
        n.putInt("reap", reapTier);
        n.putInt("rebirths", rebirths);
        n.putBoolean("enabled", enabled);
    }

    public static ReapData read(NbtCompound n) {
        ReapData d = new ReapData();
        if (n.getInt("dv") < DATA_VERSION) { // save antigo: recomeça do nível 1, rebirth 0
            d.enabled = !n.contains("enabled") || n.getBoolean("enabled");
            return d;
        }
        d.level = Math.max(1, n.getInt("level"));
        d.xp = n.getLong("xp");
        d.coins = n.getLong("coins");
        d.wins = n.getLong("wins");
        d.reapTier = Reaps.clamp(n.getInt("reap"));
        d.rebirths = Math.max(0, Math.min(MAX_REBIRTHS, n.getInt("rebirths")));
        d.enabled = !n.contains("enabled") || n.getBoolean("enabled");
        return d;
    }

    public void write(PacketByteBuf b) {
        b.writeVarInt(level);
        b.writeVarLong(xp);
        b.writeVarLong(coins);
        b.writeVarInt(rebirths);
        b.writeBoolean(enabled);
        b.writeVarLong(wins);
        b.writeVarInt(reapTier);
    }

    public static ReapData read(PacketByteBuf b) {
        ReapData d = new ReapData();
        d.level = b.readVarInt();
        d.xp = b.readVarLong();
        d.coins = b.readVarLong();
        d.rebirths = b.readVarInt();
        d.enabled = b.readBoolean();
        d.wins = b.readVarLong();
        d.reapTier = Reaps.clamp(b.readVarInt());
        return d;
    }
}
