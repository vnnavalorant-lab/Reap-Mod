package com.reap.client;

import com.reap.ReapData;
import com.reap.ReapMod;
import com.reap.TrainJellyBlock;
import com.reap.TrainJellyBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;

/** Letreiro sobre a gelatina de treino: nome em verde (vermelho se ainda bloqueada) e o multiplicador em branco. */
public class TrainJellyRenderer implements BlockEntityRenderer<TrainJellyBlockEntity> {
    private final TextRenderer tr;

    public TrainJellyRenderer(TextRenderer tr) {
        this.tr = tr;
    }

    @Override
    public void render(TrainJellyBlockEntity be, float td, MatrixStack ms, VertexConsumerProvider vcp, int light, int overlay) {
        BlockState bs = be.getCachedState();
        if (!(bs.getBlock() instanceof TrainJellyBlock tb)) return;
        ReapMod.TrainType t = ReapMod.TRAIN[tb.index];

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean locked = false;
        if (mc.player != null) {
            ReapData d = ClientData.MAP.get(mc.player.getUuid());
            locked = d != null && d.rebirths < t.req();
        }

        ms.push();
        ms.translate(0.5, 1.5, 0.5);
        ms.multiply(mc.gameRenderer.getCamera().getRotation());
        float s = 0.03f;
        ms.scale(-s, -s, s);
        var mat = ms.peek().getPositionMatrix();

        String top = t.label();
        String mult = ReapMod.multLabel(t.mult());
        tr.draw(top, -tr.getWidth(top) / 2f, -18, locked ? 0xFFFF5555 : 0xFF55FF55, true, mat, vcp,
                TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        ms.scale(1.6f, 1.6f, 1f);
        tr.draw(mult, -tr.getWidth(mult) / 2f, -6, 0xFFFFFFFF, true, ms.peek().getPositionMatrix(), vcp,
                TextRenderer.TextLayerType.NORMAL, 0, 0xF000F0);
        ms.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(TrainJellyBlockEntity be) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 64;
    }
}
