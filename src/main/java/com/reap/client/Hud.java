package com.reap.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reap.ReapData;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** HUD no estilo do jogo: moedas, nível/XP, próxima colher, rebirths e multiplicador. */
public final class Hud {
    private static final Identifier REBIRTH_ICON = new Identifier("reap", "textures/gui/rebirth.png");

    private Hud() {}

    public static void render(DrawContext g, float td) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        ReapData d = ClientData.MAP.get(mc.player.getUuid());
        if (d == null) return;

        TextRenderer tr = mc.textRenderer;
        int W = g.getScaledWindowWidth(), H = g.getScaledWindowHeight();
        int cx = W / 2;

        // moedas e wins: chips de 17 px de altura, conteúdo centralizado (ícone 9x9 + texto, folga igual dos dois lados)
        String coins = fmt(d.coins);
        String wins = fmt(d.wins);
        int cw = 5 + 9 + 4 + tr.getWidth(coins) - 1 + 5;
        int ww = 5 + 9 + 4 + tr.getWidth(wins) - 1 + 5;
        int total = cw + 6 + ww;
        int cl = cx - total / 2;
        int wl = cl + cw + 6;
        int top = 4, ch = 17;

        g.fill(cl, top, cl + cw, top + ch, 0xAA101010);
        g.fill(cl, top, cl + cw, top + 1, 0xFFFFC21A);
        g.fill(cl + 5, top + 4, cl + 14, top + 13, 0xFFB8860B);   // ícone da moeda (9x9)
        g.fill(cl + 6, top + 5, cl + 13, top + 12, 0xFFFFD43B);
        g.fill(cl + 8, top + 7, cl + 11, top + 10, 0xFFFFF0A8);
        g.drawTextWithShadow(tr, coins, cl + 5 + 9 + 4, top + 5, 0xFFFFE066);

        g.fill(wl, top, wl + ww, top + ch, 0xAA101010);
        g.fill(wl, top, wl + ww, top + 1, 0xFFFFC21A);
        int tx = wl + 5, ty = top + 4;                              // troféu (9x9)
        g.fill(tx + 2, ty, tx + 7, ty + 4, 0xFFFFD43B);            // taça
        g.fill(tx, ty + 1, tx + 2, ty + 3, 0xFFB8860B);            // alças
        g.fill(tx + 7, ty + 1, tx + 9, ty + 3, 0xFFB8860B);
        g.fill(tx + 4, ty + 4, tx + 5, ty + 6, 0xFFFFD43B);        // haste
        g.fill(tx + 2, ty + 6, tx + 7, ty + 9, 0xFFB8860B);        // base
        g.fill(tx + 3, ty + 1, tx + 4, ty + 3, 0xFFFFF0A8);        // brilho
        g.drawTextWithShadow(tr, wins, wl + 5 + 9 + 4, top + 5, 0xFFFFE066);

        // próxima colher
        g.drawCenteredTextWithShadow(tr, "Next Spoon At: Level " + d.nextSpoonLevel(), cx, 25, 0xFFFFFFFF);

        // barra de nível
        int bw = 180, bx = cx - bw / 2, by = 36, bh = 13;
        long need = ReapData.xpNeeded(d.level);
        float frac = Math.min(1f, d.xp / (float) need);
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF000000);
        g.fill(bx, by, bx + bw, by + bh, 0xFF0B3B1E);
        int fw = (int) (bw * frac);
        g.fill(bx, by, bx + fw, by + bh, 0xFF35B8FF);
        g.fill(bx, by, bx + fw, by + 2, 0xFF8FDBFF);
        g.drawTextWithShadow(tr, "Level " + d.level, bx + 4, by + 3, 0xFFFFFFFF);
        String xp = d.xp + "/" + need;
        g.drawTextWithShadow(tr, xp, bx + bw - 4 - (tr.getWidth(xp) - 1), by + 3, 0xFFFFFFFF);

        // rebirths / multiplicador
        g.drawTextWithShadow(tr, "Rebirths: " + d.rebirths, 6, H / 2 - 8, 0xFFFFD43B);
        g.drawTextWithShadow(tr, "Multiplier: x" + d.multiplier(), 6, H / 2 + 4, 0xFF4FC3FF);

        // botão de Rebirth (tecla / /reap rebirth): ícone HD + "!" quando dá para renascer
        {
            int ix = 6, iy = H / 2 - 44, sz = 26;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.drawTexture(REBIRTH_ICON, ix, iy, sz, sz, 0, 0, 64, 64, 64, 64);
            RenderSystem.disableBlend();
            if (d.canRebirth()) {
                g.fill(ix + sz - 9, iy - 1, ix + sz + 3, iy + 11, 0xFF000000);
                g.fill(ix + sz - 8, iy, ix + sz + 2, iy + 10, 0xFFE0334A);
                g.drawTextWithShadow(tr, "!", ix + sz - 5, iy + 1, 0xFFFFFFFF);
            }
            String key = ReapClient.REBIRTH_KEY == null ? "R" : ReapClient.REBIRTH_KEY.getBoundKeyLocalizedText().getString();
            String label = "[" + key + "]";
            g.drawTextWithShadow(tr, label, ix + sz / 2 - (tr.getWidth(label) - 1) / 2, iy + sz + 2, 0xFFCCCCCC);
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
