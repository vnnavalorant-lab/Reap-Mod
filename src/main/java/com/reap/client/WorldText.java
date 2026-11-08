package com.reap.client;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.joml.Matrix4f;

/**
 * Texto flutuante no mundo (letreiros) com fundo escuro, sem o fundo cobrir o texto.
 *
 * No espaço do texto, +Z = LONGE da câmera e -Z = PERTO da câmera (a matriz tem escala (-s, -s, s)).
 *
 * O problema antigo: o fundo era desenhado na camada SEE_THROUGH (que ignora o teste de profundidade), então ele era
 * pintado por cima do texto e o escurecia. Agora:
 *  1) fundo: camada NORMAL, empurrado para trás (+Z). Quem decide quem fica na frente é a profundidade, não a ordem;
 *  2) texto fraco SEE_THROUGH, sem fundo (só para aparecer através de blocos);
 *  3) texto de verdade na camada POLYGON_OFFSET (puxada para a câmera pela GPU), sempre por cima do fundo.
 */
final class WorldText {
    /** Quanto o fundo fica atrás do texto (unidades do texto; ~0,03 bloco com escala 0.03). */
    private static final float BG_Z = 1.5f;

    private WorldText() {}

    static void draw(TextRenderer tr, String text, float x, float y, int color, int bg,
                     Matrix4f mat, VertexConsumerProvider vcp) {
        if (bg != 0) {
            Matrix4f back = new Matrix4f(mat).translate(0f, 0f, BG_Z);
            // texto com alfa 4 (o shader descarta alfa < 0.1): só o retângulo de fundo aparece, com a largura certa
            tr.draw(text, x, y, 0x04FFFFFF, false, back, vcp, TextRenderer.TextLayerType.NORMAL, bg, 0xF000F0);
        }
        int faint = (color & 0x00FFFFFF) | 0x20000000;
        tr.draw(text, x, y, faint, false, mat, vcp, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
        tr.draw(text, x, y, color, false, mat, vcp, TextRenderer.TextLayerType.POLYGON_OFFSET, 0, 0xF000F0);
    }
}
