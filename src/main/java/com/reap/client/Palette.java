package com.reap.client;

import com.reap.ReapMod;
import net.minecraft.util.math.MathHelper;

/** Cores por tier de rebirth. Tier 0 = colher branca com rastro ciano (igual ao vídeo). */
final class Palette {
    private static final float[] HUES = {0f, 0.86f, 0.74f, 0.12f, 0.0f, 0.33f, 0.55f};

    private Palette() {}

    static float[] spoon(int rebirths) {
        int t = rebirths % HUES.length;
        return t == 0 ? new float[]{1f, 1f, 1f} : rgb(HUES[t], 0.45f, 1f);
    }

    static float[] trail(int rebirths) {
        int t = rebirths % HUES.length;
        return t == 0 ? new float[]{0.45f, 0.88f, 1f} : rgb(HUES[t], 0.75f, 1f);
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
