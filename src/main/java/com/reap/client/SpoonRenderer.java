package com.reap.client;

import com.reap.ReapData;
import com.reap.SpoonLogic;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/** Desenha o modelo 3D das colheres (reap.bbmodel) girando em volta de cada jogador. */
public final class SpoonRenderer {
    static final Identifier TEX = new Identifier("reap", "textures/entity/spoon.png");

    private SpoonRenderer() {}

    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientWorld w = mc.world;
        VertexConsumerProvider vcp = ctx.consumers();
        if (w == null || vcp == null) return;

        MatrixStack ms = ctx.matrixStack();
        Vec3d cam = ctx.camera().getPos();
        float td = ctx.tickDelta();
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEX));

        for (AbstractClientPlayerEntity p : w.getPlayers()) {
            ReapData d = ClientData.MAP.get(p.getUuid());
            if (d == null || !d.enabled || p.isSpectator()) continue;

            int n = d.spoons();
            Vec3d c = p.getLerpedPos(td).add(0, SpoonLogic.CENTER_Y, 0);
            float[] tint = Palette.spoon(d.rebirths);

            for (int i = 0; i < n; i++) {
                double a = SpoonLogic.angle(w.getTime(), td, i, n);
                double y = c.y + SpoonLogic.bob(w.getTime(), td, i);

                ms.push();
                ms.translate(c.x - cam.x, y - cam.y, c.z - cam.z);
                // -z do modelo (a concha) aponta para fora, na direção do ângulo a
                ms.multiply(RotationAxis.POSITIVE_Y.rotation((float) (-a - Math.PI / 2)));
                ms.translate(0, 0, -(SpoonLogic.HANDLE_R + 0.19));
                // mesma convenção dos modelos de entidade (Blockbench "Modded Entity")
                ms.scale(-1f, -1f, 1f);
                ms.translate(0, -1.501, 0);
                SpoonModel.get().render(ms, vc, LightmapTextureManager.MAX_LIGHT_COORDINATE,
                        OverlayTexture.DEFAULT_UV, tint[0], tint[1], tint[2], 1f);
                ms.pop();
            }
        }
    }
}
