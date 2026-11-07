package com.reap.client;

import com.reap.ReapData;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** HUD no estilo do jogo: moedas, nível/XP, próxima colher, rebirths e multiplicador. */
public final class Hud {
    private Hud() {}

    public static void render(DrawContext g, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        ReapData d = ClientData.MAP.get(mc.player.getUuid());
        if (d == null) return;

        TextRenderer tr = mc.textRenderer;
        int W = g.getScaledWindowWidth(), H = g.getScaledWindowHeight();
        int cx = W / 2;

        // moedas
        String coins = fmt(d.coins);
        int cw = tr.getWidth(coins) + 26;
        int cl = cx - cw / 2;
        g.fill(cl, 4, cl + cw, 20, 0xAA101010);
        g.fill(cl, 4, cl + cw, 5, 0xFFFFC21A);
        g.fill(cl + 5, 8, cl + 15, 18, 0xFFB8860B);
        g.fill(cl + 6, 9, cl + 14, 17, 0xFFFFD43B);
        g.fill(cl + 8, 11, cl + 12, 15, 0xFFFFF0A8);
        g.drawTextWithShadow(tr, coins, cl + 19, 8, 0xFFFFE066);

        // wins (troféu) ao lado das moedas
        String wins = fmt(d.wins);
        int ww = tr.getWidth(wins) + 26;
        int wl = cl + cw + 6;
        g.fill(wl, 4, wl + ww, 20, 0xAA101010);
        g.fill(wl, 4, wl + ww, 5, 0xFFFFC21A);
        g.fill(wl + 6, 8, wl + 14, 13, 0xFFFFD43B);   // taça
        g.fill(wl + 5, 9, wl + 6, 12, 0xFFB8860B);    // alças
        g.fill(wl + 14, 9, wl + 15, 12, 0xFFB8860B);
        g.fill(wl + 9, 13, wl + 11, 16, 0xFFFFD43B);  // haste
        g.fill(wl + 7, 16, wl + 13, 18, 0xFFB8860B);  // base
        g.fill(wl + 7, 9, wl + 9, 12, 0xFFFFF0A8);    // brilho
        g.drawTextWithShadow(tr, wins, wl + 19, 8, 0xFFFFE066);

        // próxima colher
        g.drawCenteredTextWithShadow(tr, "Next Spoon At: Level " + d.nextSpoonLevel(), cx, 24, 0xFFFFFFFF);

        // barra de nível
        int bw = 180, bx = cx - bw / 2, by = 35, bh = 12;
        long need = ReapData.xpNeeded(d.level);
        float frac = Math.min(1f, d.xp / (float) need);
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF000000);
        g.fill(bx, by, bx + bw, by + bh, 0xFF0B3B1E);
        int fw = (int) (bw * frac);
        g.fill(bx, by, bx + fw, by + bh, 0xFF35B8FF);
        g.fill(bx, by, bx + fw, by + 3, 0xFF8FDBFF);
        g.drawTextWithShadow(tr, "Level " + d.level, bx + 4, by + 2, 0xFFFFFFFF);
        String xp = d.xp + "/" + need;
        g.drawTextWithShadow(tr, xp, bx + bw - 4 - tr.getWidth(xp), by + 2, 0xFFFFFFFF);

        // rebirths / multiplicador
        g.drawTextWithShadow(tr, "Rebirths: " + d.rebirths, 6, H / 2 - 8, 0xFFFFD43B);
        g.drawTextWithShadow(tr, "Multiplier: x" + d.multiplier(), 6, H / 2 + 4, 0xFF4FC3FF);

        // botão de Rebirth (abre com a tecla, /reap rebirth): bolinha + "!" quando dá para renascer
        {
            int ix = 14, iy = H / 2 - 30, r = 9;
            for (int y = -r; y <= r; y++) {
                int hw = (int) Math.round(Math.sqrt(r * r - y * y));
                int col = y < -1 ? 0xFFE0334A : (y > 1 ? 0xFFFFFFFF : 0xFF222222);
                g.fill(ix - hw, iy + y, ix + hw + 1, iy + y + 1, col);
            }
            g.fill(ix - 2, iy - 2, ix + 3, iy + 3, 0xFF222222);
            g.fill(ix - 1, iy - 1, ix + 2, iy + 2, 0xFFFFFFFF);
            if (d.canRebirth()) {
                g.fill(ix + 4, iy - 13, ix + 12, iy - 5, 0xFFE0334A);
                g.drawTextWithShadow(tr, "!", ix + 7, iy - 12, 0xFFFFFFFF);
            }
            String key = ReapClient.REBIRTH_KEY == null ? "R" : ReapClient.REBIRTH_KEY.getBoundKeyLocalizedText().getString();
            g.drawTextWithShadow(tr, "[" + key + "]", ix + 13, iy - 4, 0xFFCCCCCC);
        }

        // LEVEL UP!
        long el = System.currentTimeMillis() - ClientData.levelUpAt;
        if (el >= 0 && el < 2400) {
            float t = el / 2400f;
            float alpha = t < 0.7f ? 1f : 1f - (t - 0.7f) / 0.3f;
            float pop = 1f + 0.35f * Math.max(0f, 1f - t * 7f);
            int a = Math.max(5, (int) (alpha * 255)) << 24;
            g.getMatrices().push();
            g.getMatrices().translate(cx, H * 0.27f, 0);
            g.getMatrices().scale(3f * pop, 3f * pop, 1f);
            g.drawCenteredTextWithShadow(tr, "LEVEL UP!", 0, -4, a | 0x4FD0FF);
            g.getMatrices().pop();
            g.getMatrices().push();
            g.getMatrices().translate(cx, H * 0.27f + 20, 0);
            g.getMatrices().scale(1.6f, 1.6f, 1f);
            g.drawCenteredTextWithShadow(tr, "Level " + ClientData.levelUpLevel, 0, 0, a | 0xC6FF5A);
            g.getMatrices().pop();
        }
    }

    private static String fmt(long v) {
        if (v >= 1_000_000_000L) return String.format("%.2fB", v / 1e9);
        if (v >= 1_000_000L) return String.format("%.2fM", v / 1e6);
        if (v >= 1_000L) return String.format("%.2fK", v / 1e3);
        return Long.toString(v);
    }
}
