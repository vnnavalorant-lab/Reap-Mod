package com.reap.client;

import com.reap.ReapMod;
import com.reap.ReturnPadBlock;
import com.reap.ReturnPadBlockEntity;
import com.reap.ReturnPads;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Letreiro flutuante "Return / +N WINS" sobre cada plataforma (um por grupo de plataformas conectadas). */
public class ReturnPadRenderer implements BlockEntityRenderer<ReturnPadBlockEntity> {
    private final TextRenderer tr;

    public ReturnPadRenderer(TextRenderer tr) {
        this.tr = tr;
    }

    @Override
    public void render(ReturnPadBlockEntity be, float td, MatrixStack ms, VertexConsumerProvider vcp, int light, int overlay) {
        World w = be.getWorld();
        if (w == null) return;
        BlockState bs = be.getCachedState();
        // só os blocos do canto (sem vizinho ao norte/oeste) calculam o grupo
        if (bs.get(ReturnPadBlock.NORTH) || bs.get(ReturnPadBlock.WEST)) return;

        long t = w.getTime();
        if (be.group == null || t - be.cachedAt > 20 || t < be.cachedAt) {
            be.group = ReturnPads.group(w, be.getPos());
            be.cachedAt = t;
        }
        ReturnPads.Group g = be.group;
        BlockPos pos = be.getPos();
        if (g.size() == 0 || !g.anchor().equals(pos)) return; // só um letreiro por grupo

        ms.push();
        ms.translate(g.cx() - pos.getX(), 1.75, g.cz() - pos.getZ());
        ms.multiply(MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
        float s = 0.03f;
        ms.scale(-s, -s, s);

        String top = "Return";
        String bottom = ReturnPads.label(g.wins());
        var mat = ms.peek().getPositionMatrix();
        // fundo escuro atrás + texto fraco através de blocos + texto de verdade na frente (ver WorldText)
        draw2(top, -11, 0xFFFFFFFF, 0x66000000, mat, vcp);
        draw2(bottom, 0, 0xFFFFC21A, 0x66000000, mat, vcp);
        ms.pop();
    }

    private void draw2(String text, float y, int color, int bg, org.joml.Matrix4f mat, VertexConsumerProvider vcp) {
        WorldText.draw(tr, text, -tr.getWidth(text) / 2f, y, color, bg, mat, vcp);
    }

    @Override
    public boolean rendersOutsideBoundingBox(ReturnPadBlockEntity be) {
        return true; // o letreiro fica fora da caixa do bloco
    }

    @Override
    public int getRenderDistance() {
        return 64;
    }
}
