package com.reap.client;

import com.reap.ReapData;
import com.reap.ReapMod;
import com.reap.Reaps;
import net.minecraft.util.math.MathHelper;

/** Cores das Reaps (colheres) e dos efeitos. A cor da colher/rastro vem da Reap equipada (ver {@link Reaps}). */
final class Palette {
    private Palette() {}

    /** Cor (tint) da colher: a Reap inicial é branca; a final muda de cor sozinha (arco-íris). */
    static float[] spoon(ReapData d) {
        Reaps.Tier t = Reaps.get(d.reapTier);
        if (t.rainbow()) return rgb((System.currentTimeMillis() % 3000L) / 3000f, 0.55f, 1f);
        return unpack(t.rgb());
    }

    /** Cor do rastro: a Reap inicial deixa rastro ciano (igual ao vídeo); as outras, na cor da própria Reap. */
    static float[] trail(ReapData d) {
        if (d.reapTier <= 0) return new float[]{0.45f, 0.88f, 1f};
        Reaps.Tier t = Reaps.get(d.reapTier);
        if (t.rainbow()) return rgb((System.currentTimeMillis() % 3000L) / 3000f, 0.8f, 1f);
        return unpack(t.rgb());
    }

    static float[] unpack(int c) {
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    /** Cor dos respingos de cada tipo de gelatina (a preta é clareada para os respingos aparecerem). */
    static float[] goo(int tier) {
        int t = Math.max(0, Math.min(ReapMod.TYPES.length - 1, tier));
        int c = ReapMod.TYPES[t].rgb();
        float[] f = {((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
        float lum = 0.3f * f[0] + 0.59f * f[1] + 0.11f * f[2];
        if (lum < 0.3f) {
            float k = 0.45f;
            for (int i = 0; i < 3; i++) f[i] = f[i] + (1f - f[i]) * k;
        }
        return f;
    }

    /** Cor das gelatinas de treino (índice em ReapMod.TRAIN). */
    static float[] train(int i) {
        int t = Math.max(0, Math.min(ReapMod.TRAIN.length - 1, i));
        int c = ReapMod.TRAIN[t].rgb();
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }

    private static float[] rgb(float h, float s, float v) {
        int c = MathHelper.hsvToRgb(h, s, v);
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }
}
