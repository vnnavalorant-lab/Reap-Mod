package com.reap.client;

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

    private static float[] rgb(float h, float s, float v) {
        int c = MathHelper.hsvToRgb(h, s, v);
        return new float[]{((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f, (c & 255) / 255f};
    }
}
