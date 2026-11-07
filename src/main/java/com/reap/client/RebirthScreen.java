package com.reap.client;

import com.reap.ReapData;
import com.reap.ReapNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * Tela de Rebirth (como no jogo): "You get: +X% -> +Y%", "You need: Level N / M", aviso em vermelho,
 * botões Rebirth e Skip (Skip só funciona para operadores) e X para fechar. Tudo é validado no servidor.
 */
public class RebirthScreen extends Screen {
    private static final int PW = 220, PH = 166;
    private int px, py;

    public RebirthScreen() {
        super(Text.literal("Rebirth"));
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private ReapData data() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? null : ClientData.MAP.get(mc.player.getUuid());
    }

    // áreas clicáveis
    private int closeX() { return px + PW - 22; }
    private int closeY() { return py + 6; }
    private int rbX() { return px + 34; }
    private int rbY() { return py + PH - 40; }
    private static final int RB_W = 100, RB_H = 26, SK_W = 44;
    private int skX() { return rbX() + RB_W + 6; }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render(DrawContext g, int mx, int my, float delta) {
        ReapData d = data();
        TextRenderer tr = textRenderer;
        if (d == null) { close(); return; }

        // painel
        g.fill(px - 1, py - 1, px + PW + 1, py + PH + 1, 0xFF0B2A16);
        g.fill(px, py, px + PW, py + PH, 0xE61C3A2A);

        // título + bolinha
        drawBall(g, px + 54, py + 20, 9);
        g.getMatrices().push();
        g.getMatrices().translate(px + 70, py + 12, 0);
        g.getMatrices().scale(2f, 2f, 1f);
        g.drawTextWithShadow(tr, "Rebirth", 0, 0, 0xFFFFFFFF);
        g.getMatrices().pop();

        // X
        boolean hx = in(mx, my, closeX(), closeY(), 16, 16);
        g.fill(closeX(), closeY(), closeX() + 16, closeY() + 16, hx ? 0xFFFF4040 : 0xFFD01818);
        g.fill(closeX(), closeY(), closeX() + 16, closeY() + 1, 0xFF601010);
        g.drawCenteredTextWithShadow(tr, "X", closeX() + 8, closeY() + 4, 0xFFFFFFFF);

        boolean max = d.rebirths >= ReapData.MAX_REBIRTHS;
        int cx = px + PW / 2;

        // You get:
        g.drawCenteredTextWithShadow(tr, "You get:", cx, py + 40, 0xFFFFFFFF);
        int cy = py + 66;
        drawStrength(g, px + 56, cy, "+" + ReapData.strengthPct(d.rebirths) + "%");
        g.drawCenteredTextWithShadow(tr, "->", cx, cy - 4, 0xFFFFFFFF);
        if (max) drawStrength(g, px + PW - 56, cy, "MAX");
        else drawStrength(g, px + PW - 56, cy, "+" + ReapData.strengthPct(d.rebirths + 1) + "%");
        g.drawCenteredTextWithShadow(tr, "Rebirth " + d.rebirths + " -> " + Math.min(ReapData.MAX_REBIRTHS, d.rebirths + 1)
                + "  (max " + ReapData.MAX_REBIRTHS + ")", cx, py + 88, 0xFFFFD43B);

        // You need:
        g.drawCenteredTextWithShadow(tr, "You need:", cx, py + 100, 0xFFFFFFFF);
        int need = d.rebirthLevelReq();
        int bx = px + 12, bw = PW - 24, by = py + 112, bh = 14;
        boolean ok = d.level >= need;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF000000);
        g.fill(bx, by, bx + bw, by + bh, 0xFF1B5E20);
        int fw = (int) (bw * Math.min(1f, d.level / (float) need));
        g.fill(bx, by, bx + fw, by + bh, ok ? 0xFF22E01B : 0xFF1FA716);
        g.fill(bx, by, bx + fw, by + 3, 0x55FFFFFF);
        g.drawCenteredTextWithShadow(tr, max ? "Max Rebirth!" : "Level " + d.level + " / " + need, cx, by + 3, 0xFFFFFFFF);

        g.drawCenteredTextWithShadow(tr, "Resets Strength, wins and spoons. Auras are kept!", cx, py + 131, 0xFFFF3030);

        // Rebirth
        boolean can = d.canRebirth();
        boolean hr = can && in(mx, my, rbX(), rbY(), RB_W, RB_H);
        g.fill(rbX() - 1, rbY() - 1, rbX() + RB_W + 1, rbY() + RB_H + 1, 0xFF000000);
        g.fill(rbX(), rbY(), rbX() + RB_W, rbY() + RB_H, can ? (hr ? 0xFF4CFF3A : 0xFF22E01B) : 0xFF4A5A4A);
        g.getMatrices().push();
        g.getMatrices().translate(rbX() + RB_W / 2f, rbY() + 6, 0);
        g.getMatrices().scale(1.6f, 1.6f, 1f);
        g.drawCenteredTextWithShadow(tr, "Rebirth", 0, 0, can ? 0xFFFFFFFF : 0xFF9AA59A);
        g.getMatrices().pop();

        // Skip (arco-íris)
        boolean hs = !max && in(mx, my, skX(), rbY() + 4, SK_W, RB_H - 8);
        g.fill(skX() - 1, rbY() + 3, skX() + SK_W + 1, rbY() + RB_H - 3, 0xFF000000);
        for (int i = 0; i < SK_W; i++) {
            int col = MathHelper.hsvToRgb(i / (float) SK_W * 0.85f, hs ? 0.6f : 0.85f, 1f) | 0xFF000000;
            g.fill(skX() + i, rbY() + 4, skX() + i + 1, rbY() + RB_H - 4, col);
        }
        g.drawCenteredTextWithShadow(tr, "Skip", skX() + SK_W / 2, rbY() + 9, 0xFFFFFFFF);

        if (hs) g.drawTooltip(tr, Text.literal("Skip the level requirement (operators only)"), mx, my);
    }

    private void drawStrength(DrawContext g, int x, int y, String text) {
        // "bíceps" simplificado: moeda dourada grande com o texto por cima
        for (int dy = -11; dy <= 11; dy++) {
            int hw = (int) Math.round(Math.sqrt(121 - dy * dy));
            g.fill(x - hw, y + dy, x + hw + 1, y + dy + 1, dy < -5 ? 0xFFFFF0A8 : 0xFFFFC21A);
        }
        g.drawCenteredTextWithShadow(textRenderer, text, x, y - 4, 0xFFFFFFFF);
    }

    private static void drawBall(DrawContext g, int ix, int iy, int r) {
        for (int y = -r; y <= r; y++) {
            int hw = (int) Math.round(Math.sqrt(r * r - y * y));
            int col = y < -1 ? 0xFFE0334A : (y > 1 ? 0xFFFFFFFF : 0xFF222222);
            g.fill(ix - hw, iy + y, ix + hw + 1, iy + y + 1, col);
        }
        g.fill(ix - 3, iy - 3, ix + 4, iy + 4, 0xFF222222);
        g.fill(ix - 2, iy - 2, ix + 3, iy + 3, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        ReapData d = data();
        if (button == 0 && d != null) {
            if (in(mx, my, closeX(), closeY(), 16, 16)) {
                click();
                close();
                return true;
            }
            if (d.canRebirth() && in(mx, my, rbX(), rbY(), RB_W, RB_H)) {
                click();
                send(false);
                return true;
            }
            if (d.rebirths < ReapData.MAX_REBIRTHS && in(mx, my, skX(), rbY() + 4, SK_W, RB_H - 8)) {
                click();
                send(true);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private static void send(boolean skip) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(skip);
        ClientPlayNetworking.send(ReapNet.REBIRTH_REQ, buf);
    }

    private static void click() {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
