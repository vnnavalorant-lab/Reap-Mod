package com.reap.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reap.ReapData;
import com.reap.ReapNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Tela de Rebirth (fiel ao jogo): painel translúcido com borda preta, título com ícone HD, "You get: +X% -> +Y%",
 * "You need: Level N / M", aviso em vermelho e botões Rebirth / Skip (Skip só para operadores) / x.
 * Textos em fonte do Minecraft com contorno preto, todos centralizados nas caixas. Tudo é validado no servidor.
 * Desenhada em coordenadas locais (PW x PH) e reduzida automaticamente se a janela for pequena.
 */
public class RebirthScreen extends Screen {
    private static final Identifier ICON = new Identifier("reap", "textures/gui/rebirth.png");

    // layout local (px de GUI)
    private static final int PW = 240, PH = 196;
    private static final int X_X = PW - 27, X_Y = -10, X_S = 21;
    private static final int RB_X = 38, RB_Y = PH - 36, RB_W = 96, RB_H = 28;
    private static final int SK_X = RB_X + RB_W + 6, SK_Y = RB_Y + 3, SK_W = 38, SK_H = 22;

    private float sc = 1f, ox, oy;

    public RebirthScreen() {
        super(Text.literal("Rebirth"));
    }

    @Override
    protected void init() {
        sc = Math.min(1f, Math.min((height - 12f) / (PH + 22f), (width - 12f) / PW));
        ox = (width - PW * sc) / 2f;
        oy = height / 2f - ((PH - 20) / 2f) * sc;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private ReapData data() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc.player == null ? null : ClientData.MAP.get(mc.player.getUuid());
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------ desenho

    /** Texto com contorno preto, centralizado em (cx, cy). ctr = linha central da fonte (3.5 = maiúsculas, 4.5 = minúsculas). */
    private void otext(DrawContext g, String s, float cx, float cy, float scale, int color, float ctr) {
        MatrixStack m = g.getMatrices();
        m.push();
        m.translate(cx - (textRenderer.getWidth(s) - 1) * scale / 2f, cy - ctr * scale, 0);
        m.scale(scale, scale, 1f);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) g.drawText(textRenderer, s, dx, dy, 0xFF000000, false);
            }
        }
        g.drawText(textRenderer, s, 0, 0, color, false);
        m.pop();
    }

    private static void icon(DrawContext g, int x, int y, int size) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.drawTexture(ICON, x, y, size, size, 0, 0, 64, 64, 64, 64);
        RenderSystem.disableBlend();
    }

    /** Ícone de força (o mesmo das moedas do HUD), ampliado 4x, com contorno preto. */
    private static void strength(DrawContext g, int cx, int cy) {
        int u = 4, x = cx - 5 * u, y = cy - 5 * u;
        g.fill(x - 2, y - 2, x + 10 * u + 2, y + 10 * u + 2, 0xFF000000);
        g.fill(x, y, x + 10 * u, y + 10 * u, 0xFFB8860B);
        g.fill(x + u, y + u, x + 9 * u, y + 9 * u, 0xFFFFD43B);
        g.fill(x + 3 * u, y + 3 * u, x + 7 * u, y + 7 * u, 0xFFFFF0A8);
    }

    private static void arrow(DrawContext g, int x, int cy) {
        int w = 28, head = 8, shaft = 4;
        g.fill(x - 1, cy - shaft / 2 - 1, x + w - head + 1, cy + shaft / 2 + 1, 0xFF000000);
        for (int i = 0; i <= head; i++) {
            int hh = head - i;
            g.fill(x + w - head + i - 1, cy - hh - 1, x + w - head + i + 1, cy + hh + 1, 0xFF000000);
        }
        g.fill(x, cy - shaft / 2, x + w - head, cy + shaft / 2, 0xFFFFFFFF);
        for (int i = 0; i < head; i++) {
            int hh = head - i;
            g.fill(x + w - head + i, cy - hh, x + w - head + i + 1, cy + hh, 0xFFFFFFFF);
        }
    }

    @Override
    public void render(DrawContext g, int mxRaw, int myRaw, float delta) {
        ReapData d = data();
        if (d == null) { close(); return; }
        double mx = (mxRaw - ox) / sc, my = (myRaw - oy) / sc;
        boolean max = d.rebirths >= ReapData.MAX_REBIRTHS;
        boolean can = d.canRebirth();
        int cx = PW / 2;

        g.getMatrices().push();
        g.getMatrices().translate(ox, oy, 0);
        g.getMatrices().scale(sc, sc, 1f);

        // painel: fundo todo preto com 80% de opacidade
        g.fill(0, 0, PW, PH, 0xCC000000);

        // título: ícone HD + "Rebirth" (grupo centralizado)
        int tw = (int) ((textRenderer.getWidth("Rebirth") - 1) * 2f);
        int group = 34 + 4 + tw;
        int gx = cx - group / 2;
        icon(g, gx, -20, 34);
        otext(g, "Rebirth", gx + 34 + 4 + tw / 2f, -3, 2f, 0xFFFFFFFF, 3.5f);

        // botão x (vermelho, "x" minúsculo centralizado)
        boolean hx = in(mx, my, X_X, X_Y, X_S, X_S);
        g.fill(X_X - 1, X_Y - 1, X_X + X_S + 1, X_Y + X_S + 1, 0xFF000000);
        g.fill(X_X, X_Y, X_X + X_S, X_Y + X_S, hx ? 0xFFFF3A3A : 0xFFFF0000);
        g.fill(X_X, X_Y, X_X + X_S, X_Y + 1, 0xFFFF8A8A);
        otext(g, "x", X_X + X_S / 2f, X_Y + X_S / 2f, 2f, 0xFFFFFFFF, 4.5f);

        // You get:
        otext(g, "You get:", cx, 35, 1.5f, 0xFFFFFFFF, 3.5f);
        strength(g, 58, 68);
        strength(g, PW - 58, 68);
        otext(g, "+" + ReapData.strengthPct(d.rebirths) + "%", 58, 68, 1.5f, 0xFFFFFFFF, 3.5f);
        otext(g, max ? "MAX" : "+" + ReapData.strengthPct(d.rebirths + 1) + "%", PW - 58, 68, 1.5f, 0xFFFFFFFF, 3.5f);
        arrow(g, cx - 14, 68);

        // You need:
        otext(g, "You need:", cx, 109, 1.5f, 0xFFFFFFFF, 3.5f);
        int bx = 8, by = 118, bw = PW - 16, bh = 17;
        int need = d.rebirthLevelReq();
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF000000);
        g.fill(bx, by, bx + bw, by + bh, 0xFF262336);
        int fw = max ? bw : (int) (bw * Math.min(1f, d.level / (float) need));
        g.fill(bx, by, bx + fw, by + bh, 0xFF00EB00);
        g.fill(bx, by, bx + fw, by + 2, 0xFF78FF78);
        otext(g, max ? "Max Rebirth!" : "Level " + d.level + " / " + need, bx + bw / 2f, by + bh / 2f, 1.5f, 0xFFFFFFFF, 3.5f);

        otext(g, "Resets Strength, wins and Reap. Auras are kept!", cx, 146, 0.75f, 0xFFFF2020, 3.5f);

        // Rebirth
        boolean hr = can && in(mx, my, RB_X, RB_Y, RB_W, RB_H);
        g.fill(RB_X - 1, RB_Y - 1, RB_X + RB_W + 1, RB_Y + RB_H + 1, 0xFF000000);
        int outer = can ? (hr ? 0xFF2BD42B : 0xFF00C800) : 0xFF626262;
        int inner = can ? (hr ? 0xFF6BFF6B : 0xFF28EB28) : 0xFF767676;
        g.fill(RB_X, RB_Y, RB_X + RB_W, RB_Y + RB_H, outer);
        g.fill(RB_X + 1, RB_Y + 1, RB_X + RB_W - 1, RB_Y + RB_H - 1, inner);
        otext(g, "Rebirth", RB_X + RB_W / 2f, RB_Y + RB_H / 2f, 2f, 0xFFFFFFFF, 3.5f);

        // Skip (arco-íris, só operadores)
        boolean hs = !max && in(mx, my, SK_X, SK_Y, SK_W, SK_H);
        g.fill(SK_X - 1, SK_Y - 1, SK_X + SK_W + 1, SK_Y + SK_H + 1, 0xFF000000);
        for (int i = 0; i < SK_W; i++) {
            int col = MathHelper.hsvToRgb(1f - i / (float) SK_W * 0.8f, hs ? 0.6f : 0.9f, 1f) | 0xFF000000;
            g.fill(SK_X + i, SK_Y, SK_X + i + 1, SK_Y + SK_H, col);
        }
        otext(g, "Skip", SK_X + SK_W / 2f, SK_Y + SK_H / 2f, 1.5f, 0xFFFFFFFF, 3.5f);

        g.getMatrices().pop();

        if (hs) g.drawTooltip(textRenderer, Text.literal("Skip the level requirement (operators only)"), mxRaw, myRaw);
    }

    // ------------------------------------------------------------ clique

    @Override
    public boolean mouseClicked(double mxRaw, double myRaw, int button) {
        ReapData d = data();
        double mx = (mxRaw - ox) / sc, my = (myRaw - oy) / sc;
        if (button == 0 && d != null) {
            if (in(mx, my, X_X, X_Y, X_S, X_S)) {
                click();
                close();
                return true;
            }
            if (d.canRebirth() && in(mx, my, RB_X, RB_Y, RB_W, RB_H)) {
                click();
                send(false);
                return true;
            }
            if (d.rebirths < ReapData.MAX_REBIRTHS && in(mx, my, SK_X, SK_Y, SK_W, SK_H)) {
                click();
                send(true);
                return true;
            }
        }
        return super.mouseClicked(mxRaw, myRaw, button);
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
