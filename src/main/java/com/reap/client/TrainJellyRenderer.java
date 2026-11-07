package com.reap.client;

import com.reap.ReapData;
import com.reap.ReapMod;
import com.reap.TrainJellyBlock;
import com.reap.TrainJellyBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;

/**
 * Desenha a gelatina de treino GRANDE: placa 5x5 + gelatina (~3 blocos de largura, 2,7 de altura), usando o modelo
 * "*_big" ampliado 5x, com um balanço de gelatina viva (mais forte quando as colheres acertam), e o letreiro em cima:
 * nome em verde (vermelho se ainda bloqueada) e o multiplicador em branco.
 */
public class TrainJellyRenderer implements BlockEntityRenderer<TrainJellyBlockEntity> {
    private static final float SCALE = 5f;

    // último "acerto" de colher (para a gelatina balançar)
    private static double hitX, hitZ;
    private static long hitNs = 0;

    private final TextRenderer tr;

    public TrainJellyRenderer(TextRenderer tr) {
        this.tr = tr;
    }

    /** Chamado pelo Fx quando um respingo de treino acontece perto de (x, z). */
    static void kick(double x, double z) {
        hitX = x;
        hitZ = z;
        hitNs = System.nanoTime();
    }

    @Override
    public void render(TrainJellyBlockEntity be, float td, MatrixStack ms, VertexConsumerProvider vcp, int light, int overlay) {
        BlockState bs = be.getCachedState();
        if (!(bs.getBlock() instanceof TrainJellyBlock tb)) return;
        ReapMod.TrainType t = ReapMod.TRAIN[tb.index];
        MinecraftClient mc = MinecraftClient.getInstance();
        if (be.getWorld() == null) return;

        double cx = be.getPos().getX() + 0.5, cz = be.getPos().getZ() + 0.5;
        double time = (be.getWorld().getTime() + td) / 20.0;

        // balanço: sempre um pouquinho, e mais forte logo depois de uma colherada perto dela
        float amp = 0.012f;
        double dt = (System.nanoTime() - hitNs) / 1e9;
        double dx = hitX - cx, dz = hitZ - cz;
        if (dt < 0.8 && dx * dx + dz * dz < 3.2 * 3.2) amp += 0.055f * (float) Math.exp(-dt * 5.0);
        float wob = amp * (float) Math.sin(time * (dt < 0.8 ? 22.0 : 2.4));

        // ---- gelatina + placa (modelo escondido *_big ampliado)
        BlockState vis = ReapMod.TRAIN_VIS[tb.index].getDefaultState();
        ms.push();
        ms.translate(0.5 - SCALE / 2, 0, 0.5 - SCALE / 2);
        ms.scale(SCALE, SCALE, SCALE);
        // squash & stretch com o volume mantido (a base fica no chão)
        ms.translate(0.5, 0, 0.5);
        ms.scale(1f - wob * 0.5f, 1f + wob, 1f - wob * 0.5f);
        ms.translate(-0.5, 0, -0.5);
        mc.getBlockRenderManager().renderBlockAsEntity(vis, ms, vcp, 0xF000F0, OverlayTexture.DEFAULT_UV);
        ms.pop();

        // ---- letreiro
        boolean locked = false;
        if (mc.player != null) {
            ReapData d = ClientData.MAP.get(mc.player.getUuid());
            locked = d != null && d.rebirths < t.req();
        }
        ms.push();
        ms.translate(0.5, 3.45, 0.5);
        ms.multiply(mc.gameRenderer.getCamera().getRotation());
        float s = 0.05f;
        ms.scale(-s, -s, s);
        String top = t.label();
        String mult = ReapMod.multLabel(t.mult());
        tr.draw(top, -tr.getWidth(top) / 2f, -18, locked ? 0xFFFF5555 : 0xFF55FF55, true,
                ms.peek().getPositionMatrix(), vcp, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        ms.scale(1.6f, 1.6f, 1f);
        tr.draw(mult, -tr.getWidth(mult) / 2f, -6, 0xFFFFFFFF, true,
                ms.peek().getPositionMatrix(), vcp, TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        ms.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(TrainJellyBlockEntity be) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 96;
    }
}
