package com.reap.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/** Lote de quads texturizados (posição + uv + cor). */
final class QB {
    private final BufferBuilder b = Tessellator.getInstance().getBuffer();
    private final Matrix4f m;
    private int n;

    QB(Identifier tex, Matrix4f m) {
        this.m = m;
        RenderSystem.setShaderTexture(0, tex);
        b.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
    }

    void v(double x, double y, double z, float u, float v, float[] rgb, float a) {
        b.vertex(m, (float) x, (float) y, (float) z).texture(u, v).color(rgb[0], rgb[1], rgb[2], a).next();
        n++;
    }

    void flush() {
        BufferBuilder.BuiltBuffer built = b.end();
        if (n > 0) BufferRenderer.drawWithGlobalProgram(built);
        else built.release();
    }
}
