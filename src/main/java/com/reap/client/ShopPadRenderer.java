package com.reap.client;

import com.reap.ReapData;
import com.reap.Reaps;
import com.reap.ShopPadBlock;
import com.reap.ShopPadBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;

/** Loja de Reap: colher giratória (na cor da Reap) e letreiro "N Wins / xV Speed / Buy" sobre a plataforma. */
public class ShopPadRenderer implements BlockEntityRenderer<ShopPadBlockEntity> {
    /** Textura azul da plataforma (a mesma da plataforma de retorno azul). */
    private static final Identifier BLUE = new Identifier("reap", "textures/block/return_300.png");
    private static final Identifier BLUE_SIDE = new Identifier("reap", "textures/block/return_300_side.png");

    private final TextRenderer tr;

    public ShopPadRenderer(TextRenderer tr) {
        this.tr = tr;
    }

    @Override
    public void render(ShopPadBlockEntity be, float td, MatrixStack ms, VertexConsumerProvider vcp, int light, int overlay) {
        World w = be.getWorld();
        if (w == null) return;
        BlockState bs = be.getCachedState();
        if (!(bs.getBlock() instanceof ShopPadBlock pad)) return;
        Reaps.Tier t = Reaps.get(pad.tier);
        float time = w.getTime() + td;

        // colher em pé, girando devagar e flutuando (a concha do modelo aponta para cima)
        float[] tint = t.rainbow()
                ? Palette.spoon(rainbowOf(pad.tier))
                : Palette.unpack(t.rgb());
        ms.push();
        ms.translate(0.5, 0.35 + Math.sin(time * 0.08) * 0.05, 0.5);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 3f));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f));
        ms.scale(0.62f, 0.62f, 0.62f);
        // mesma convenção do SpoonRenderer (modelo de entidade do Blockbench)
        ms.scale(-1f, -1f, 1f);
        ms.translate(0, -1.501, 0);
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(SpoonRenderer.TEX));
        SpoonModel.get().render(ms, vc, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV, tint[0], tint[1], tint[2], 1f);
        ms.pop();

        // estado para o jogador local
        MinecraftClient mc = MinecraftClient.getInstance();
        ReapData d = mc.player == null ? null : ClientData.MAP.get(mc.player.getUuid());
        int cur = d == null ? 0 : d.reapTier;

        // Reap já comprada: toda a parte vermelha (topo + laterais) vira azul, só para quem comprou, e sem texto
        if (cur >= pad.tier) {
            MatrixStack.Entry e = ms.peek();
            int lt = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            final float lo = 1f / 16f, hi = 15f / 16f, y0 = 2f / 16f, y1 = 3f / 16f, o = 0.002f;
            VertexConsumer top = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(BLUE));
            quad(top, e, lt, 0, 1, 0, lo, y1 + o, lo, lo, y1 + o, hi, hi, y1 + o, hi, hi, y1 + o, lo);
            VertexConsumer side = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(BLUE_SIDE));
            quad(side, e, lt, 0, 0, -1, lo, y0, lo - o, hi, y0, lo - o, hi, y1, lo - o, lo, y1, lo - o); // norte
            quad(side, e, lt, 0, 0, 1, lo, y0, hi + o, hi, y0, hi + o, hi, y1, hi + o, lo, y1, hi + o);   // sul
            quad(side, e, lt, -1, 0, 0, lo - o, y0, lo, lo - o, y0, hi, lo - o, y1, hi, lo - o, y1, lo);  // oeste
            quad(side, e, lt, 1, 0, 0, hi + o, y0, lo, hi + o, y0, hi, hi + o, y1, hi, hi + o, y1, lo);   // leste
            return;
        }
        // só a Reap seguinte mostra custo e quanto de velocidade ela adiciona; as mais distantes ficam sem texto
        if (pad.tier != cur + 1) return;

        int costColor = d != null && d.wins < t.cost() ? 0xFFFF8A8A : 0xFFFFC21A;
        String status;
        int statusColor;
        if (d != null && d.wins < t.cost()) {
            status = "Need " + (t.cost() - d.wins) + " more";
            statusColor = 0xFFFF5555;
        } else {
            status = "Buy";
            statusColor = 0xFF3CFF3C;
        }
        float add = t.speed() - Reaps.speed(cur);
        String cost = t.cost() + (t.cost() == 1 ? " Win" : " Wins");
        String speed = "+" + Reaps.speedLabel(add) + " Speed";

        ms.push();
        ms.translate(0.5, 2.55, 0.5);
        ms.multiply(mc.gameRenderer.getCamera().getRotation());
        float s = 0.03f;
        ms.scale(-s, -s, s);
        var mat = ms.peek().getPositionMatrix();
        draw2(cost, -11, costColor, mat, vcp);
        draw2(speed, 0, 0xFFFFFFFF, mat, vcp);
        draw2(status, 11, statusColor, mat, vcp);
        ms.pop();
    }

    /** Quad com 4 vértices (x,y,z cada) e normal (nx,ny,nz); UV cobre a textura inteira. */
    private static void quad(VertexConsumer vc, MatrixStack.Entry e, int light, float nx, float ny, float nz,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4) {
        vtx(vc, e, x1, y1, z1, 0f, 0f, light, nx, ny, nz);
        vtx(vc, e, x2, y2, z2, 0f, 1f, light, nx, ny, nz);
        vtx(vc, e, x3, y3, z3, 1f, 1f, light, nx, ny, nz);
        vtx(vc, e, x4, y4, z4, 1f, 0f, light, nx, ny, nz);
    }

    private static void vtx(VertexConsumer vc, MatrixStack.Entry e, float x, float y, float z, float u, float v,
                            int light, float nx, float ny, float nz) {
        vc.vertex(e.getPositionMatrix(), x, y, z).color(255, 255, 255, 255).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e.getNormalMatrix(), nx, ny, nz).next();
    }

    /** Dados falsos só para reaproveitar a cor arco-íris da Palette. */
    private static ReapData rainbowOf(int tier) {
        ReapData d = new ReapData();
        d.reapTier = tier;
        return d;
    }

    private void draw2(String text, float y, int color, org.joml.Matrix4f mat, VertexConsumerProvider vcp) {
        float x = -tr.getWidth(text) / 2f;
        int faint = (color & 0x00FFFFFF) | 0x20000000;
        tr.draw(text, x, y, faint, false, mat, vcp, TextRenderer.TextLayerType.SEE_THROUGH, 0x66000000, 0xF000F0);
        tr.draw(text, x, y, color, false, mat, vcp, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
    }

    @Override
    public boolean rendersOutsideBoundingBox(ShopPadBlockEntity be) {
        return true; // colher e letreiro ficam fora da caixa do bloco
    }

    @Override
    public int getRenderDistance() {
        return 64;
    }
}
