package com.reap;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

/** Progressão de um jogador (igual ao jogo: nível, XP, moedas, rebirths, multiplicador). */
public class ReapData {
    public int level = 1;
    public long xp = 0;
    public long coins = 0;
    public long wins = 0;
    public int rebirths = 0;
    public boolean enabled = true;

    /** XP necessário para sair do nível dado (nível 9 ≈ 400, como no vídeo). */
    public static long xpNeeded(int level) {
        return Math.max(25L, 5L * level * level);
    }

    /** No jogo: 7 rebirths = x9. */
    public int multiplier() { return rebirths + 2; }

    /** Começa com 2 colheres, ganha +1 a cada 10 níveis ("Next Spoon At: Level 10"). */
    public int spoons() { return Math.min(12, 2 + level / 10); }

    /** Quanto de "vida" da gelatina cada colher tira por tick de contato. Sobe com o nível e os rebirths. */
    public float bitePower() { return (1f + 0.25f * (level - 1)) * (1f + 0.5f * rebirths); }

    public int nextSpoonLevel() { return (level / 10 + 1) * 10; }

    public void write(NbtCompound n) {
        n.putInt("level", level);
        n.putLong("xp", xp);
        n.putLong("coins", coins);
        n.putLong("wins", wins);
        n.putInt("rebirths", rebirths);
        n.putBoolean("enabled", enabled);
    }

    public static ReapData read(NbtCompound n) {
        ReapData d = new ReapData();
        d.level = Math.max(1, n.getInt("level"));
        d.xp = n.getLong("xp");
        d.coins = n.getLong("coins");
        d.wins = n.getLong("wins");
        d.rebirths = n.getInt("rebirths");
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
    }

    public static ReapData read(PacketByteBuf b) {
        ReapData d = new ReapData();
        d.level = b.readVarInt();
        d.xp = b.readVarLong();
        d.coins = b.readVarLong();
        d.rebirths = b.readVarInt();
        d.enabled = b.readBoolean();
        d.wins = b.readVarLong();
        return d;
    }
}
